package com.leo.myblog.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SVG安全清洗服务
 * 防止SVG XSS攻击，实现三层防护：
 * 1. XML解析器禁用外部实体（XXE防护）
 * 2. 按**白名单**重建元素树（{@link #SAFE_ELEMENTS}），未知元素一律丢弃
 * 3. 按白名单复制属性，并额外过滤 on* 事件、javascript:/data:/vbscript: URL
 * <p>
 * 注意第 2 层是白名单而不是黑名单：早期实现只维护了一份危险元素名单，
 * 名单之外的元素会原样放行 —— 这种结构天然会漏。现在只放行认识的元素。
 * <p>
 * 还有两点容易踩：SVG 是 XML，<b>元素名与属性名都区分大小写</b>
 * （{@code viewBox} / {@code linearGradient}），清洗过程中必须原样保留；
 * 而输出的 Document 也必须由 XML parser 建出来，否则 jsoup 会按 HTML 规则把名字转小写。
 */
@Slf4j
@Service
public class SvgSanitizerService {
    
    // 需要移除的高危SVG元素。
    // 有了 SAFE_ELEMENTS 白名单之后，这份名单在功能上是冗余的（黑名单里的元素本来也不在白名单里），
    // 保留它是为了让日志能区分「这是已知的危险元素」和「这只是不在白名单里」——
    // 前者值得告警，后者多半是 Inkscape/sodipodi 之类的编辑器元数据，属正常情况。
    private static final Set<String> DANGEROUS_ELEMENTS = new HashSet<>(Arrays.asList(
        "script",           // JavaScript脚本
        "foreignobject",    // 可嵌入HTML，可能包含恶意内容
        "iframe",           // 内嵌框架
        "embed",            // 嵌入对象
        "object",           // 对象元素
        "applet",           // Java小程序（已废弃但仍需防范）
        "meta",             // 元数据
        "link",             // 链接
        "style",            // 样式（可能包含expression）
        "animate",          // 动画（可能被用于攻击）
        "set",              // 设置属性
        "discard"           // 丢弃元素
    ));
    
    // 需要移除的危险属性模式
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("^on\\w+", Pattern.CASE_INSENSITIVE);

    /**
     * URL 归一化时要抹掉的字符：C0 控制符、空格、DEL
     * <p>
     * 浏览器解析 URL 前同样会丢掉这些字符，所以 {@code java\tscript:alert(1)} 与
     * {@code javascript:alert(1)} 等价。不做这一步，前缀判断就能被这个变形绕过。
     */
    private static final Pattern URL_STRIPPED_CHARS = Pattern.compile("[\\u0000-\\u0020\\u007F]");

    // 危险 URL 协议前缀。注意这里**只做前缀判断**（startsWith），不要用正则 matches() ——
    // matches() 要求整串完全匹配，`^\s*javascript:` 碰上 "javascript:alert(1)" 会返回 false，
    // 于是危险的 href 会被原样放行。这个 bug 在引入测试前一直存在。
    private static final List<String> DANGEROUS_URL_SCHEMES =
            List.of("javascript:", "vbscript:", "data:");

    // 允许保留的安全属性
    // 注意：比对时统一用小写，但**写回时保留原始大小写** —— SVG 是 XML，属性名区分大小写，
    // 把 viewBox 写成 viewbox 渲染器会直接忽略，图就变形了。此处的常量一律小写。
    private static final Set<String> SAFE_ATTRIBUTES = lowercasedSet(
        "id", "class", "style",
        "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r",
        "fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity",
        "transform", "viewBox", "preserveAspectRatio",
        "d", "points", "x1", "y1", "x2", "y2",
        "xmlns", "xmlns:xlink", "version",
        "text-anchor", "font-family", "font-size", "font-weight",
        "display", "visibility", "clip-path", "mask",
        "filter", "gradientUnits", "gradientTransform",
        "offset", "stop-color", "stop-opacity",
        "href", "xlink:href"  // href需要进一步验证，见 isSafeUrl
    );

    /**
     * 允许保留的 SVG 元素（白名单）
     * <p>
     * 之前这里只造了一个 Jsoup {@code Safelist} 却从未拿去过滤，实际生效的只有
     * {@link #DANGEROUS_ELEMENTS} 黑名单 —— 黑名单天然漏，任何一个没想到的元素都会放行。
     * 现在改成显式白名单，未知元素一律丢弃（fail closed）。
     */
    private static final Set<String> SAFE_ELEMENTS = lowercasedSet(
        // 容器与结构
        "svg", "g", "defs", "symbol", "use", "marker", "pattern", "clippath", "mask",
        "a", "switch", "view", "title", "desc",
        // 形状
        "rect", "circle", "ellipse", "line", "polyline", "polygon", "path",
        // 文本
        "text", "tspan", "textpath",
        // 渐变与滤镜
        "lineargradient", "radialgradient", "stop", "filter", "feblend",
        "fecolormatrix", "fecomponenttransfer", "fecomposite", "feconvolvematrix",
        "fediffuselighting", "fedisplacementmap", "fedistantlight", "feflood",
        "fefunca", "fefuncb", "fefuncg", "fefuncr", "fegaussianblur", "feimage",
        "femerge", "femergenode", "femorphology", "feoffset", "fepointlight",
        "fespecularlighting", "fespotlight", "fetile", "feturbulence",
        // 图像
        "image"
    );

    /**
     * 构造小写集合，避免上面两处常量里的大小写写法不一致导致「属性/元素被静默丢掉」
     */
    private static Set<String> lowercasedSet(String... values) {
        Set<String> set = new HashSet<>(values.length * 2);
        for (String value : values) {
            set.add(value.toLowerCase());
        }
        return set;
    }

    /**
     * 清洗SVG内容
     * @param svgContent 原始SVG内容
     * @return 清洗后的安全SVG内容
     */
    public String sanitizeSvg(String svgContent) {
        if (svgContent == null || svgContent.trim().isEmpty()) {
            throw new IllegalArgumentException("SVG内容不能为空");
        }
        
        try {
            log.debug("开始清洗SVG内容，原始长度: {}", svgContent.length());
            
            // 第1步：使用安全的XML解析器验证并禁用外部实体
            String validatedSvg = validateWithSecureParser(svgContent);
            
            // 第2步：使用Jsoup移除高危元素和属性
            String sanitizedSvg = removeDangerousElementsAndAttributes(validatedSvg);
            
            log.debug("SVG清洗完成，清洗后长度: {}", sanitizedSvg.length());
            
            return sanitizedSvg;
            
        } catch (Exception e) {
            log.error("SVG清洗失败", e);
            throw new IllegalArgumentException("SVG文件格式不安全或已损坏");
        }
    }
    
    /**
     * 第1步：使用安全的XML解析器验证SVG，禁用外部实体（XXE防护）
     */
    private String validateWithSecureParser(String svgContent) throws Exception {
        try {
            // 创建安全的DocumentBuilderFactory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            
            // 禁用外部实体（XXE防护）
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            
            // 创建解析器
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            // 解析SVG（验证XML格式）
            InputSource inputSource = new InputSource(new StringReader(svgContent));
            builder.parse(inputSource);
            
            log.debug("SVG XML格式验证通过，外部实体已禁用");
            
            // 返回原始内容（仅用于验证，不修改）
            return svgContent;
            
        } catch (Exception e) {
            log.warn("SVG XML验证失败: {}", e.getMessage());
            throw new IllegalArgumentException("SVG文件格式无效或包含禁止的外部实体引用");
        }
    }
    
    /**
     * 第2步：使用Jsoup按白名单重建文档，丢弃高危元素和危险属性
     */
    private String removeDangerousElementsAndAttributes(String svgContent) {
        // 解析SVG（使用xmlParser保持SVG结构）
        Document dirtyDoc = Jsoup.parse(svgContent, "", org.jsoup.parser.Parser.xmlParser());

        // 目标文档用 XML parser 建出来（而不是 new Document("")），这样 createElement 时
        // Tag 的解析会走 preserveCase，元素名的大小写得以保留。
        // 属性名的大小写**不能**依赖这一点：实测 Element.attr() 无论文档怎么建都会把
        // 属性名转小写，必须走 putAttribute() 直接写属性表，详见那个方法的注释。
        Document cleanDoc = Jsoup.parse("", "", org.jsoup.parser.Parser.xmlParser());
        cleanDoc.outputSettings()
                .prettyPrint(false)
                .syntax(Document.OutputSettings.Syntax.xml);

        // 从文档节点的**子节点**开始遍历，而不是文档节点本身。
        // 文档节点的 tagName 是 "#root" 这个 Jsoup 内部伪节点，拿它建元素会把结果包成
        // <#root><svg>…</svg></#root> —— 不是合法 SVG，渲染器直接不认。
        for (Node child : dirtyDoc.childNodes()) {
            processNode(child, cleanDoc, cleanDoc);
        }

        return cleanDoc.html();
    }

    /**
     * 递归处理节点：按白名单重建元素、复制安全属性、搬运纯文本
     *
     * @param sourceNode   源节点（元素或文本）
     * @param targetParent 目标父元素
     * @param targetDoc    目标文档（用于创建元素）
     */
    private void processNode(Node sourceNode, Element targetParent, Document targetDoc) {
        // 文本节点单独处理。
        // 这里**不能**用 element.text()：它返回的是所有后代文字的拼接，而
        // Element.text(String) 会清空该元素已有的子节点。原先就是这么写的，后果是
        // <svg><text>hi</text></svg> 被压成裸的 "hi"，svg / rect / path 全部消失。
        if (sourceNode instanceof TextNode textNode) {
            String text = textNode.getWholeText();
            if (text != null && !text.isEmpty()) {
                targetParent.appendChild(new TextNode(text));
            }
            return;
        }

        if (!(sourceNode instanceof Element sourceElement)) {
            // 注释、CDATA、XML 声明等一律丢弃
            return;
        }

        String tagName = sourceElement.tagName().toLowerCase();

        // 危险元素：连同子树一起丢掉（不保留其内容）
        if (DANGEROUS_ELEMENTS.contains(tagName)) {
            log.warn("移除危险SVG元素: {}", tagName);
            return;
        }

        // 白名单之外的元素同样丢掉。黑名单天然漏 —— 没列到的元素会原样放行，
        // 所以这里 fail closed：不认识就不放过去。
        if (!SAFE_ELEMENTS.contains(tagName)) {
            log.warn("移除白名单外的SVG元素: {}", tagName);
            return;
        }

        // 元素名用**原始大小写**建立：SVG 是 XML，linearGradient / clipPath /
        // feGaussianBlur / textPath 这些名字区分大小写，压成小写就不再是有效元素了。
        // 上面只把 tagName 用小写形式做白名单比对。
        Element targetElement = targetDoc.createElement(sourceElement.tagName());
        copySafeAttributes(sourceElement, targetElement);

        // 先挂到父节点再递归，保证子节点有确定的落点
        targetParent.appendChild(targetElement);

        for (Node child : sourceElement.childNodes()) {
            processNode(child, targetElement, targetDoc);
        }
    }

    /**
     * 复制安全属性，过滤危险属性
     */
    private void copySafeAttributes(Element sourceElement, Element targetElement) {
        for (org.jsoup.nodes.Attribute attr : sourceElement.attributes()) {
            // 保留原始大小写用于**写回**，只把小写形式用于**比对**。
            // XML 属性名区分大小写：把 viewBox 落成 viewbox，SVG 渲染器会忽略它，图就变形了。
            String attrName = attr.getKey();
            String lowerName = attrName.toLowerCase();
            String attrValue = attr.getValue();

            // 检查是否为危险属性
            if (isDangerousAttribute(lowerName, attrValue)) {
                log.warn("移除危险属性: {}={}", attrName, attrValue);
                continue;
            }

            // 只保留白名单中的属性
            if (SAFE_ATTRIBUTES.contains(lowerName) || lowerName.startsWith("aria-") || lowerName.startsWith("data-")) {
                // 对href等特殊属性进行额外验证
                if (lowerName.equals("href") || lowerName.equals("xlink:href")) {
                    if (isSafeUrl(attrValue)) {
                        putAttribute(targetElement, attrName, attrValue);
                    } else {
                        log.warn("移除危险的URL属性: {}={}", attrName, attrValue);
                    }
                } else {
                    putAttribute(targetElement, attrName, attrValue);
                }
            }
        }
    }

    /**
     * 写入属性，保留原始大小写
     * <p>
     * <b>不要改成 {@code element.attr(name, value)}</b>：jsoup 的 {@code Node.attr} 会先过一遍
     * {@code NodeUtils.parser(this).settings().normalizeAttribute()}，把属性名转成小写。
     * SVG 是 XML，属性名区分大小写 —— viewBox 落成 viewbox 后渲染器直接忽略它，图会缩放出错。
     * 直接写 {@code attributes()} 不经过这层归一化，实测能原样保留。
     */
    private void putAttribute(Element targetElement, String name, String value) {
        targetElement.attributes().put(name, value);
    }
    
    /**
     * 检查属性是否危险
     */
    private boolean isDangerousAttribute(String attrName, String attrValue) {
        // 检查事件处理器（on*）
        if (EVENT_HANDLER_PATTERN.matcher(attrName).matches()) {
            return true;
        }
        
        // 检查style中是否包含expression或javascript
        if (attrName.equals("style")) {
            String lowerStyle = attrValue.toLowerCase();
            if (lowerStyle.contains("expression") || 
                lowerStyle.contains("javascript:") ||
                lowerStyle.contains("vbscript:")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查URL是否安全
     */
    private boolean isSafeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return true; // 空URL允许
        }

        String normalized = URL_STRIPPED_CHARS.matcher(url).replaceAll("").toLowerCase();

        // 禁止 javascript: / vbscript: / data: 协议
        for (String scheme : DANGEROUS_URL_SCHEMES) {
            if (normalized.startsWith(scheme)) {
                return false;
            }
        }

        // 允许相对路径和绝对路径
        return true;
    }
    
    /**
     * 验证文件是否为有效的SVG格式
     * @param content 文件内容
     * @return true-是SVG，false-不是SVG
     */
    public boolean isValidSvg(byte[] content) {
        try {
            String contentStr = new String(content, StandardCharsets.UTF_8);
            
            // 检查是否以<?xml或<svg开头（忽略大小写和空白）
            String trimmed = contentStr.trim().toLowerCase();
            
            // 跳过XML声明
            if (trimmed.startsWith("<?xml")) {
                int svgStart = trimmed.indexOf("<svg");
                if (svgStart == -1) {
                    return false;
                }
            } else if (!trimmed.startsWith("<svg")) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.debug("SVG格式验证失败: {}", e.getMessage());
            return false;
        }
    }
}
