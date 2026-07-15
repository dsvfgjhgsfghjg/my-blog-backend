# Markdown 换行符丢失问题修复

## 问题描述

在文章创建和更新过程中，`Jsoup.clean()` 方法默认启用 `prettyPrint(true)`，会把多个空白字符（包括换行符）压缩，导致 Markdown 格式混乱（段落消失、列表断裂、代码块错乱）。

## 问题原因

### 根本原因

在 `PostServiceImpl.java` 中，文章内容的处理流程存在两个阶段的问题：

#### 第一阶段（最初）：错误使用 `sanitizeHtmlContent`
- `Jsoup.clean(content, safelist)` 默认 `prettyPrint=true`，会规范化 whitespace（包括换行符）
- Markdown 依赖换行符来表示段落、列表等结构，这种规范化会破坏格式

#### 第二阶段（过渡方案）：使用 `checkMarkdownSafety` 正则检查
- 改用纯正则检查，虽然保留了换行符，但**缺乏白名单清洗能力**
- 攻击者仍可通过白名单标签嵌入 XSS（如 `<img src=x onerror=alert(1)>`）
- 只能检测到危险模式，无法彻底移除危险属性

#### 最终方案（当前）：Jsoup 白名单 + `prettyPrint(false)`
- 使用 `Jsoup.clean()` 进行白名单清洗，同时通过 `Document.OutputSettings.prettyPrint(false)` 保留换行符
- 添加 `cleanMarkdownUrls()` 清洗 Markdown 链接协议的额外层
- 同时兼具 **白名单安全性** 和 **格式保留性**

## 最终解决方案

### 核心修复

在 `PostServiceImpl.java` 中新增三个方法，替换旧的 `checkMarkdownSafety`：

#### 1. `cleanMarkdownContent()` - 主清洗方法

```java
private String cleanMarkdownContent(String markdownContent) {
    if (markdownContent == null || markdownContent.isEmpty()) {
        return markdownContent;
    }
    
    // Step 1: Jsoup 白名单清洗，prettyPrint(false) 防止换行符丢失
    Document.OutputSettings outputSettings = new Document.OutputSettings()
            .prettyPrint(false)
            .charset(StandardCharsets.UTF_8);
    
    String cleaned = Jsoup.clean(markdownContent, "", POST_CONTENT_SAFELIST, outputSettings);
    
    // Step 2: 额外清理 Markdown 链接中的危险协议
    cleaned = cleanMarkdownUrls(cleaned);
    
    return cleaned;
}
```

#### 2. `cleanMarkdownUrls()` - Markdown 链接协议清洗

```java
private String cleanMarkdownUrls(String markdownContent) {
    // 使用正则匹配 [text](url) 语法
    // 检查 javascript:/vbscript:/data:/file: 协议 → 移除链接，保留文本
    // 使用 Matcher.appendReplacement/appendTail 安全替换
}
```

#### 3. `cleanRenderedHtmlContent()` - 渲染后 HTML 清洗（备用）

```java
private String cleanRenderedHtmlContent(String htmlContent) {
    // 同样使用 prettyPrint(false) 保留结构
    Document.OutputSettings outputSettings = new Document.OutputSettings()
            .prettyPrint(false)
            .charset(StandardCharsets.UTF_8);
    return Jsoup.clean(htmlContent, "", POST_CONTENT_SAFELIST, outputSettings);
}
```

#### 旧方法降级为 `@Deprecated` 委托

```java
@Deprecated
private String checkMarkdownSafety(String markdownContent) {
    return cleanMarkdownContent(markdownContent);  // 委托给新方法
}

@Deprecated
private String sanitizeHtmlContent(String content) {
    return cleanRenderedHtmlContent(content);  // 委托给新方法
}
```

### 调用点更新

```java
// createPost 方法
// OLD: String safeContent = checkMarkdownSafety(request.getContent());
// NEW:
String safeContent = cleanMarkdownContent(request.getContent());

// updatePost 方法同理
```

## 技术说明

### 为什么 `Jsoup.clean` + `prettyPrint(false)` 是正确方案

| 方案 | 换行符保留 | XSS防护强度 | 适用场景 |
|------|-----------|------------|---------|
| ❌ `Jsoup.clean` (默认prettyPrint=true) | ❌ 丢失 | ✅ 强（白名单） | HTML 内容 |
| ⚠️ 正则检查 (`checkMarkdownSafety`) | ✅ 保留 | ⚠️ 弱（黑名单） | 纯文本检查 |
| ✅ `Jsoup.clean` + `prettyPrint(false)` | ✅ 保留 | ✅ 强（白名单） | Markdown 内容 |

### 纵深防御体系

```
前端输入
     ↓
┌─────────────────────────────────────┐
│ 第1层：后端 Jsoup 白名单清洗         │  ← cleanMarkdownContent()
│   - 只保留白名单标签                 │
│   - prettyPrint(false) 保留换行符    │
│   - cleanMarkdownUrls 清洗危险链接   │
└─────────────────────────────────────┘
     ↓
┌─────────────────────────────────────┐
│ 第2层：传输层安全                    │
│   - HttpOnly Cookie（JWT 防窃取）    │
│   - CSP 响应头（限制脚本执行）        │
│   - HSTS（强制 HTTPS）               │
└─────────────────────────────────────┘
     ↓
┌─────────────────────────────────────┐
│ 第3层：前端 DOMPurify 二次清洗       │  ← 前端需要实现
│   - marked(content) → HTML          │
│   - DOMPurify.sanitize(html)        │
│   - v-html 安全渲染                  │
└─────────────────────────────────────┘
     ↓
  安全渲染到页面
```

## 安全考虑

`cleanMarkdownContent()` 提供了三重防护：

1. **Jsoup 白名单清洗**：
   - 只允许 `POST_CONTENT_SAFELIST` 中明确定义的标签和属性
   - 移除所有事件处理器（`onclick=`、`onerror=` 等）
   - 显式限制 `href` 和 `src` 协议为 `https/http/mailto`

2. **Markdown 链接协议清洗**：
   - 检测 `[text](javascript:...)` 语法
   - 检测 `[text](data:...)`、`[text](vbscript:...)`、`[text](file:...)`
   - 将危险链接降级为纯文本

3. **纵深防御**：
   - 前端还应使用 DOMPurify 进行二次清洗（详见 `ARTICLE_RENDERING_GUIDE.md`）

## 测试验证

### 构建验证
```bash
./gradlew compileJava
# ✅ BUILD SUCCESSFUL
```

### 测试场景

| 测试用例 | 输入 | 预期结果 |
|---------|------|---------|
| 多行段落 | `"line1\n\nline2"` | 换行符保留 |
| Markdown链接XSS | `[click](javascript:alert(1))` | 降级为纯文本 `click` |
| 嵌入式HTML-XSS | `<img src=x onerror=alert(1)>` | `onerror` 被移除 |
| 白名单标签 | `<strong>bold</strong>` | 保留 `<strong>` |
| 危险协议链接 | `[test](data:text/html,...)` | 降级为纯文本 |

## 相关文件

- `src/main/java/com/leo/myblog/service/PostServiceImpl.java` - 主要修复文件
- `docs/frontend/ARTICLE_RENDERING_GUIDE.md` - 前端集成指南（含 DOMPurify）

## 影响范围

- ✅ **向后兼容**：不影响现有功能，旧 API 调用仍然正常
- ✅ **安全性大幅提升**：从黑名单正则检查升级为白名单 Jsoup 清洗
- ✅ **格式保留**：`prettyPrint(false)` 确保 Markdown 换行符完整保留
- ✅ **性能影响极小**：Jsoup 清洗效率远低于正则，但每次请求只清洗一次

## 后续建议

1. ✅ **前端防护**：在 `PostDetail.vue` 中添加 DOMPurify（已提供集成指南）
2. 🔄 **自动化测试**：添加单元测试覆盖 `cleanMarkdownContent` 和 `cleanMarkdownUrls`
3. 🔄 **持续监控**：定期检查新发现的 XSS 攻击向量，更新白名单配置

---

**修复日期**：2026-05-21（最终方案）  
**修复人员**：Leo  
**问题状态**：✅ 已修复  

### 修复历程

| 阶段 | 方案 | 日期 | 状态 |
|------|------|------|------|
| V1 | 正则黑名单检查 `checkMarkdownSafety` | 2026-05-13 | ⚠️ 过渡方案 |
| V2（最终） | Jsoup 白名单 + `prettyPrint(false)` + `cleanMarkdownUrls` | 2026-05-21 | ✅ 当前方案 |
