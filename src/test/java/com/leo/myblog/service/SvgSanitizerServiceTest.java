package com.leo.myblog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SVG 清洗服务测试
 * <p>
 * 这个服务此前只有实现、没有任何测试，也从未在生产验证过。补测试的过程直接暴露出四个 bug，
 * 下面几条用例就是它们的回归防线（每条都标注了「回归」）：
 * <ol>
 *   <li>输出被包在 {@code <#root>} 里 —— 遍历起点用了 Jsoup 的文档伪节点，产出的根本不是合法 SVG；</li>
 *   <li>含文字的结构被压扁 —— 用 {@code element.text()}（所有后代拼接）覆盖子节点；</li>
 *   <li>{@code javascript:} / {@code data:} / {@code vbscript:} 的 href 全部放行 ——
 *       正则用了 {@code matches()}，要求整串完全匹配 {@code ^\s*javascript:}，
 *       碰上真实的 {@code javascript:alert(1)} 反而返回 false。**这是可利用的 XSS**；</li>
 *   <li>{@code viewBox} 等驼峰属性被压成小写后丢失 —— XML 属性名区分大小写。</li>
 * </ol>
 * 本类只依赖 SvgSanitizerService 自身（无外部依赖），所以是纯单元测试，不起 Spring 容器。
 */
public class SvgSanitizerServiceTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private final SvgSanitizerService service = new SvgSanitizerService();

    private String svg(String body) {
        return "<svg xmlns=\"" + SVG_NS + "\">" + body + "</svg>";
    }

    @Nested
    @DisplayName("结构保全（回归：清洗不能把图洗坏）")
    class Structure {

        @Test
        @DisplayName("含文字的元素保留完整父子结构")
        void keepsStructureAroundText() {
            // 回归 bug #2：旧实现下这里会输出裸的 "hi"，svg / rect 全部消失
            String out = service.sanitizeSvg(svg("<rect width=\"10\" height=\"10\"/><text>hi</text>"));

            assertTrue(out.contains("<rect"), "rect 不能因为同级有文字就被丢掉，实际输出: " + out);
            assertTrue(out.contains("<text"), "text 元素本身要保留，实际输出: " + out);
            assertTrue(out.contains("hi"), "文字内容要保留，实际输出: " + out);
        }

        @Test
        @DisplayName("嵌套结构逐层保留")
        void keepsNestedStructure() {
            String out = service.sanitizeSvg(svg("<g><path d=\"M0 0 L10 10\"/><circle cx=\"5\" cy=\"5\" r=\"3\"/></g>"));

            assertTrue(out.contains("<g"), "g 要保留: " + out);
            assertTrue(out.contains("<path"), "path 要保留: " + out);
            assertTrue(out.contains("<circle"), "circle 要保留: " + out);
            assertTrue(out.contains("M0 0 L10 10"), "路径数据要保留: " + out);
        }

        @Test
        @DisplayName("输出本身就是 SVG，不套额外的伪节点")
        void outputIsBareSvg() {
            // 回归 bug #1：旧实现输出 <#root><svg>…</svg></#root>
            String out = service.sanitizeSvg(svg("<rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("#root"), "不能出现 Jsoup 的 #root 伪节点，实际输出: " + out);
            assertTrue(out.stripLeading().startsWith("<svg"), "输出要以 <svg 开头，实际输出: " + out);
            assertTrue(out.stripTrailing().endsWith("</svg>"), "输出要以 </svg> 结尾，实际输出: " + out);
        }

        @Test
        @DisplayName("驼峰属性名大小写原样保留（回归 bug #4）")
        void preservesCamelCaseAttributeNames() {
            String out = service.sanitizeSvg(
                    "<svg xmlns=\"" + SVG_NS + "\" viewBox=\"0 0 100 100\" preserveAspectRatio=\"xMidYMid\">"
                            + "<rect width=\"10\" height=\"10\"/></svg>");

            // XML 属性名区分大小写：写成 viewbox 渲染器会直接忽略，图就缩放出错
            assertTrue(out.contains("viewBox="), "viewBox 的大小写必须保留，实际输出: " + out);
            assertTrue(out.contains("preserveAspectRatio="), "preserveAspectRatio 的大小写必须保留，实际输出: " + out);
        }

        @Test
        @DisplayName("驼峰元素名大小写原样保留")
        void preservesCamelCaseElementNames() {
            String out = service.sanitizeSvg(svg("<linearGradient id=\"g\"><stop offset=\"0\"/></linearGradient>"));

            // 压成 lineargradient 就不是有效的 SVG 元素了
            assertTrue(out.contains("<linearGradient"), "linearGradient 的大小写必须保留，实际输出: " + out);
        }
    }

    @Nested
    @DisplayName("危险元素")
    class DangerousElements {

        @Test
        @DisplayName("script 元素连同内容一起移除")
        void removesScript() {
            String out = service.sanitizeSvg(svg("<script>alert(1)</script><rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("script"), "script 必须移除: " + out);
            assertFalse(out.contains("alert(1)"), "script 的内容也不能残留: " + out);
            assertTrue(out.contains("<rect"), "同级的安全元素要保留: " + out);
        }

        @Test
        @DisplayName("foreignObject 连子树一起移除（可内嵌 HTML）")
        void removesForeignObjectSubtree() {
            String out = service.sanitizeSvg(svg(
                    "<foreignObject><div xmlns=\"http://www.w3.org/1999/xhtml\">pwned</div></foreignObject>"
                            + "<rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("foreignObject"), "foreignObject 必须移除: " + out);
            assertFalse(out.contains("pwned"), "其子树内容也要一起丢掉: " + out);
            assertTrue(out.contains("<rect"), "同级的安全元素要保留: " + out);
        }

        @Test
        @DisplayName("style 元素被移除（可藏 expression）")
        void removesStyle() {
            String out = service.sanitizeSvg(svg("<style>*{x:expression(alert(1))}</style><rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("<style"), "style 必须移除: " + out);
            assertFalse(out.contains("expression"), "样式内容不能残留: " + out);
        }

        @Test
        @DisplayName("白名单外的元素被丢弃（fail closed）")
        void dropsUnknownElements() {
            // 旧实现只靠黑名单，任何没列到的元素都会原样放行
            String out = service.sanitizeSvg(svg("<blink>hi</blink><rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("blink"), "白名单外的元素要丢弃: " + out);
            assertTrue(out.contains("<rect"), "同级的安全元素要保留: " + out);
        }

        @Test
        @DisplayName("大小写变形的大小写不敏感命中危险元素")
        void dangerousElementMatchIsCaseInsensitive() {
            String out = service.sanitizeSvg(svg("<SCRIPT>alert(1)</SCRIPT><rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("alert(1)"), "大写 SCRIPT 同样要拦下: " + out);
        }
    }

    @Nested
    @DisplayName("危险属性")
    class DangerousAttributes {

        @Test
        @DisplayName("on* 事件属性被移除")
        void removesEventHandler() {
            String out = service.sanitizeSvg(
                    "<svg xmlns=\"" + SVG_NS + "\" onload=\"alert(1)\"><rect width=\"10\" height=\"10\" onclick=\"alert(2)\"/></svg>");

            assertFalse(out.contains("onload"), "onload 必须移除: " + out);
            assertFalse(out.contains("onclick"), "onclick 必须移除: " + out);
            assertFalse(out.contains("alert"), "事件处理器内容不能残留: " + out);
        }

        @Test
        @DisplayName("javascript: 的 href 被移除（回归 bug #3：这是可利用的 XSS）")
        void removesJavascriptHref() {
            String out = service.sanitizeSvg(svg("<a xlink:href=\"javascript:alert(1)\"><text>hi</text></a>"));

            assertFalse(out.contains("javascript:"), "javascript: 协议必须拦下: " + out);
            assertFalse(out.contains("alert(1)"), "载荷不能残留: " + out);
            assertTrue(out.contains("<text>hi</text>"), "链接里的文字要保留: " + out);
        }

        @Test
        @DisplayName("data: URI 被移除")
        void removesDataUri() {
            String out = service.sanitizeSvg(svg(
                    "<image xlink:href=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\"/>"
                            + "<rect width=\"10\" height=\"10\"/>"));

            assertFalse(out.contains("data:"), "data: URI 必须拦下: " + out);
            assertTrue(out.contains("<image"), "image 元素本身可留（属性已被清掉）: " + out);
        }

        @Test
        @DisplayName("vbscript: 的 href 被移除")
        void removesVbscriptHref() {
            String out = service.sanitizeSvg(svg("<a xlink:href=\"vbscript:msgbox(1)\"><text>hi</text></a>"));

            assertFalse(out.contains("vbscript:"), "vbscript: 协议必须拦下: " + out);
        }

        @Test
        @DisplayName("夹了制表符/换行的协议写法同样拦下")
        void removesObfuscatedScheme() {
            // 浏览器解析 URL 前会丢掉 tab / 换行，所以 java\tscript: 实际就是 javascript:
            String out = service.sanitizeSvg(svg("<a xlink:href=\"java\tscript:alert(1)\"><text>hi</text></a>"));
            assertFalse(out.contains("alert(1)"), "制表符变形的 javascript: 也要拦下: " + out);

            String out2 = service.sanitizeSvg(svg("<a xlink:href=\"  JaVaScRiPt:alert(1)\"><text>hi</text></a>"));
            assertFalse(out2.contains("alert(1)"), "大小写 + 前导空格的变形也要拦下: " + out2);
        }

        @Test
        @DisplayName("白名单外的属性被丢弃")
        void dropsUnknownAttributes() {
            String out = service.sanitizeSvg(svg("<rect width=\"10\" height=\"10\" data-evil=\"x\" contenteditable=\"true\"/>"));

            assertFalse(out.contains("contenteditable"), "白名单外的属性要丢弃: " + out);
            // data-* 是刻意放行的（前端会用到），确认这条边界没被误伤
            assertTrue(out.contains("data-evil"), "data-* 属性是允许的: " + out);
        }

        @Test
        @DisplayName("正常的外部链接 href 保留")
        void keepsSafeHref() {
            String out = service.sanitizeSvg(svg("<a href=\"https://example.com/a\"><text>hi</text></a>"));

            assertTrue(out.contains("https://example.com/a"), "普通链接不该被误删: " + out);
        }
    }

    @Nested
    @DisplayName("XXE 与畸形输入")
    class Parsing {

        @Test
        @DisplayName("带 DOCTYPE 实体声明的输入被拒绝（XXE 防护）")
        void rejectsDoctypeEntity() {
            String xxe = "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                    + "<svg xmlns=\"" + SVG_NS + "\"><text>&xxe;</text></svg>";

            // 解析器禁用了 DOCTYPE，必须在清洗阶段就拒绝，而不是把实体展开后再处理
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg(xxe));
        }

        @Test
        @DisplayName("畸形 XML 被拒绝")
        void rejectsMalformedXml() {
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg("<svg xmlns=\"" + SVG_NS + "\"><rect"));
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg("not xml at all"));
        }

        @Test
        @DisplayName("空内容被拒绝")
        void rejectsEmptyInput() {
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg(null));
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg(""));
            assertThrows(IllegalArgumentException.class, () -> service.sanitizeSvg("   "));
        }

        @Test
        @DisplayName("正常 SVG 不抛异常")
        void acceptsNormalSvg() {
            assertDoesNotThrow(() -> service.sanitizeSvg(svg("<rect width=\"10\" height=\"10\"/>")));
        }
    }

    @Nested
    @DisplayName("isValidSvg 格式判定")
    class Validity {

        @Test
        @DisplayName("识别以 <svg 开头的合法输入")
        void acceptsSvgPrefix() {
            assertTrue(service.isValidSvg(svg("<rect/>").getBytes(StandardCharsets.UTF_8)));
            assertTrue(service.isValidSvg(("<?xml version=\"1.0\"?>" + svg("<rect/>")).getBytes(StandardCharsets.UTF_8)));
            assertTrue(service.isValidSvg(("  <SVG></SVG>").getBytes(StandardCharsets.UTF_8)), "大小写与空白应容忍");
        }

        @Test
        @DisplayName("拒绝非 SVG 输入")
        void rejectsNonSvg() {
            assertFalse(service.isValidSvg("<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8)));
            assertFalse(service.isValidSvg("<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8)),
                    "有 XML 声明但不是 svg 根节点");
        }
    }
}
