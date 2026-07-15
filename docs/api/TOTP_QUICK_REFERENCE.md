# TOTP 双因素认证 - 快速参考卡片

## 🚀 5 分钟快速上手

### 1. 请求头要求

所有需要 TOTP 的接口必须携带：

```http
X-TOTP-Code: 123456
```

---

### 2. 核心代码示例

#### JavaScript (Fetch API)

```javascript
// 调用敏感操作接口
async function deletePost(postId, totpCode) {
  const response = await fetch(`/api/posts/${postId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-TOTP-Code': totpCode  // ← 关键！
    }
  });
  
  return await response.json();
}

// 使用
const result = await deletePost(123, '456789');
```

---

#### Axios 拦截器（推荐）

```javascript
// 请求拦截器
axios.interceptors.request.use(config => {
  if (needsTotp(config.url)) {
    const code = prompt('请输入 TOTP 验证码：');
    config.headers['X-TOTP-Code'] = code;
  }
  return config;
});

// 响应拦截器
axios.interceptors.response.use(response => {
  if (response.config.headers['X-TOTP-Code']) {
    // 标记为已验证（5分钟内免验证）
    localStorage.setItem('totp_verified', Date.now());
  }
  return response;
});
```

---

### 3. 会话管理（5分钟免重复验证）

```javascript
function isTotpSessionValid() {
  const timestamp = localStorage.getItem('totp_verified');
  if (!timestamp) return false;
  
  const elapsed = Date.now() - parseInt(timestamp);
  return elapsed < 5 * 60 * 1000; // 5分钟
}

// 使用
if (isTotpSessionValid()) {
  // 直接调用，无需 TOTP
  await deletePost(123);
} else {
  // 需要用户提供 TOTP
  const code = promptForTotp();
  await deletePost(123, code);
}
```

---

## 📋 受保护的接口速查表

### 🔴 高优先级（必须 TOTP）

| 操作 | 接口 | 方法 |
|------|------|------|
| 修改密码 | `/api/users/password` | PUT |
| 删除文章 | `/api/posts/{id}` | DELETE |
| 重置密码 | `/api/users/admin/{id}/reset-password` | PUT |
| 设置管理员 | `/api/users/admin/{id}/role` | PUT |
| 文章审核 | `/api/posts/audit/{id}` | POST |
| 修改系统配置 | `/api/system/config` | PUT |

---

### 🟡 中优先级（建议 TOTP）

| 操作 | 接口 | 方法 |
|------|------|------|
| 停用账户 | `/api/users/admin/{id}/status` | PUT |
| 创建标签 | `/api/tags` | POST |
| 更新标签 | `/api/tags/{id}` | PUT |
| 删除标签 | `/api/tags/{id}` | DELETE |
| 查看审计日志 | `/api/system/audit-logs` | GET |
| 查看系统设置 | `/api/system/config` | GET |

---

## ⚠️ 错误处理

### 常见错误码

```javascript
try {
  await sensitiveOperation();
} catch (error) {
  if (error.response?.status === 401) {
    const message = error.response.data.message;
    
    if (message.includes('双因素认证')) {
      // 需要 TOTP
      showTotpInputDialog();
    } else if (message.includes('验证码错误')) {
      // TOTP 无效
      alert('验证码错误，请重试');
    }
  }
}
```

---

## 🎯 最佳实践

### ✅ 推荐做法

1. **提前提示**：在执行敏感操作前告知用户需要准备 TOTP
2. **会话缓存**：5 分钟内不重复询问
3. **备份码支持**：提供"使用备份码"选项
4. **友好提示**：显示"请打开 Microsoft Authenticator"

### ❌ 避免做法

1. ~~每次都要求输入 TOTP~~
2. ~~不明确提示需要 TOTP~~
3. ~~不提供错误说明~~

---

## 📱 Microsoft Authenticator 使用

1. 打开 Microsoft Authenticator 应用
2. 找到 "MyBlog" 账户
3. 查看 6 位数字（每 30 秒刷新）
4. 在 30 秒内输入到网站

---

## 🔗 完整文档

- [详细 API 文档](./TOTP_SENSITIVE_OPERATIONS_API.md)
- [实施总结](./TOTP_2FA_IMPLEMENTATION_SUMMARY.md)

---

**最后更新**: 2026-05-17  
**版本**: v1.0
