package com.leo.myblog.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SVG安全清洗服务
 * 防止SVG XSS攻击，实现三层防护：
 * 1. XML解析器禁用外部实体（XXE防护）
 * 2. 移除高危元素（script、foreignObject等）
 * 3. 删除危险属性（on*事件、javascript: href等）
 */
@Slf4j
@Service
public class SvgSanitizerService {
    
    // 需要移除的高危SVG元素
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
    private static final Pattern JAVASCRIPT_HREF_PATTERN = Pattern.compile("^\\s*javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^\\s*data:", Pattern.CASE_INSENSITIVE);
    private static final Pattern VBSCRIPT_PATTERN = Pattern.compile("^\\s*vbscript:", Pattern.CASE_INSENSITIVE);
    
    // 允许保留的安全属性
    private static final Set<String> SAFE_ATTRIBUTES = new HashSet<>(Arrays.asList(
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
        "href", "xlink:href"  // href需要进一步验证
    ));
    
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
     * 第2步：使用Jsoup移除高危元素和危险属性
     */
    private String removeDangerousElementsAndAttributes(String svgContent) {
        // 创建白名单
        Safelist safelist = Safelist.relaxed();
        
        // 添加SVG相关标签到白名单
        addSvgTagsToSafelist(safelist);
        
        // 解析SVG（使用xmlParser保持SVG结构）
        Document dirtyDoc = Jsoup.parse(svgContent, "", org.jsoup.parser.Parser.xmlParser());
        
        // 清理文档
        Document cleanDoc = new Document("");
        cleanDoc.outputSettings().prettyPrint(false);
        
        // 递归处理所有元素
        Element rootElement = dirtyDoc.root();
        processElement(rootElement, cleanDoc, cleanDoc);
        
        return cleanDoc.html();
    }
    
    /**
     * 添加SVG标签到白名单
     */
    private void addSvgTagsToSafelist(Safelist safelist) {
        // 基本SVG容器
        safelist.addTags("svg", "g", "defs", "symbol", "use", "marker", "pattern", "clippath", "mask");
        
        // 形状元素
        safelist.addTags("rect", "circle", "ellipse", "line", "polyline", "polygon", "path");
        
        // 文本元素
        safelist.addTags("text", "tspan", "textpath");
        
        // 渐变和滤镜
        safelist.addTags("lineargradient", "radialgradient", "stop", "filter", "feblend", 
                        "fecolormatrix", "fecomponenttransfer", "fecomposite", "feconvolvematrix",
                        "fediffuselighting", "fedisplacementmap", "fedistantlight", "feflood",
                        "fefunca", "fefuncb", "fefuncg", "fefuncr", "fegaussianblur", "feimage",
                        "femerge", "femergenode", "femorphology", "feoffset", "fepointlight",
                        "fespecularlighting", "fespotlight", "fetile", "feturbulence");
        
        // 图像元素
        safelist.addTags("image");
        
        // 注意：故意不包含 script, foreignobject, iframe 等危险元素
    }
    
    /**
     * 递归处理元素，移除危险内容和属性
     */
    private void processElement(Element sourceElement, Element targetParent, Document targetDoc) {
        String tagName = sourceElement.tagName().toLowerCase();
        
        // 跳过危险元素
        if (DANGEROUS_ELEMENTS.contains(tagName)) {
            log.warn("移除危险SVG元素: {}", tagName);
            return;
        }
        
        // 创建新元素
        Element targetElement = targetDoc.createElement(tagName);
        
        // 复制安全属性
        copySafeAttributes(sourceElement, targetElement);
        
        // 添加到父元素
        targetParent.appendChild(targetElement);
        
        // 递归处理子元素
        for (Element child : sourceElement.children()) {
            processElement(child, targetElement, targetDoc);
        }
        
        // 复制文本内容（如果有）
        String text = sourceElement.text();
        if (text != null && !text.isEmpty()) {
            targetElement.text(text);
        }
    }
    
    /**
     * 复制安全属性，过滤危险属性
     */
    private void copySafeAttributes(Element sourceElement, Element targetElement) {
        for (org.jsoup.nodes.Attribute attr : sourceElement.attributes()) {
            String attrName = attr.getKey().toLowerCase();
            String attrValue = attr.getValue();
            
            // 检查是否为危险属性
            if (isDangerousAttribute(attrName, attrValue)) {
                log.warn("移除危险属性: {}={}", attrName, attrValue);
                continue;
            }
            
            // 只保留白名单中的属性
            if (SAFE_ATTRIBUTES.contains(attrName) || attrName.startsWith("aria-") || attrName.startsWith("data-")) {
                // 对href等特殊属性进行额外验证
                if (attrName.equals("href") || attrName.equals("xlink:href")) {
                    if (isSafeUrl(attrValue)) {
                        targetElement.attr(attrName, attrValue);
                    } else {
                        log.warn("移除危险的URL属性: {}={}", attrName, attrValue);
                    }
                } else {
                    targetElement.attr(attrName, attrValue);
                }
            }
        }
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
        
        String lowerUrl = url.toLowerCase().trim();
        
        // 禁止javascript:协议
        if (JAVASCRIPT_HREF_PATTERN.matcher(lowerUrl).matches()) {
            return false;
        }
        
        // 禁止vbscript:协议
        if (VBSCRIPT_PATTERN.matcher(lowerUrl).matches()) {
            return false;
        }
        
        // 禁止data: URI（可能包含恶意代码）
        if (DATA_URI_PATTERN.matcher(lowerUrl).matches()) {
            return false;
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
