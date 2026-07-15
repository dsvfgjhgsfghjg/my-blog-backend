# TOTP 双因素认证使用指南

## 📋 概述

本系统已集成 TOTP（Time-based One-Time Password）双因素认证，兼容 **Microsoft Authenticator**、**Google Authenticator** 等标准 TOTP 应用。

### 安全特性

- ✅ **双重验证**：管理员密码 + TOTP 验证码
- ✅ **备份码机制**：10 个一次性备份码，防止手机丢失
- ✅ **离线工作**：TOTP 基于时间算法，无需网络连接
- ✅ **30秒过期**：动态验证码每 30 秒更新一次
- ✅ **RFC 6238 标准**：行业标准协议

---

## 🔧 启用 TOTP

### Step 1: 生成密钥和二维码

**接口**: `POST /api/auth/totp/generate`

**请求**:
```bash
curl -X POST http://localhost:8081/api/auth/totp/generate \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "qrCodeUrl": "otpauth://totp/MyBlog:user_123?secret=JBSWY3DPEHPK3PXP&issuer=MyBlog",
    "secret": "JBSWY3DPEHPK3PXP",
    "backupCodes": [
      "12345678",
      "23456789",
      "34567890",
      ...
    ]
  }
}
```

**重要**：
- ⚠️ **立即保存备份码**！关闭页面后将无法再次查看
- ⚠️ 备份码存储在 Redis 中，30 天后过期

---

### Step 2: 扫描二维码绑定

#### 方法 A：使用 Microsoft Authenticator（推荐）

1. 打开 Microsoft Authenticator 应用
2. 点击 "+" 添加账户
3. 选择 "其他账户（Google、Facebook 等）"
4. 扫描返回的二维码（或使用 `qrCodeUrl` 生成的二维码图片）
5. 应用会显示 6 位动态验证码

#### 方法 B：手动输入密钥

如果无法扫描二维码：

1. 打开 Microsoft Authenticator
2. 选择 "手动输入代码"
3. 输入：
   - **账户名**: `user_123`（或自定义）
   - **密钥**: `JBSWY3DPEHPK3PXP`（从响应中获取）
4. 点击 "添加"

---

### Step 3: 验证并启用

**接口**: `POST /api/auth/totp/enable`

**请求**:
```bash
curl -X POST http://localhost:8081/api/auth/totp/enable \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

**说明**：
- `code`: 从 Microsoft Authenticator 中看到的 6 位数字
- 必须在 30 秒内提交（验证码会刷新）

**响应**:
```json
{
  "code": 200,
  "message": "TOTP 启用成功"
}
```

---

## 🔍 查询 TOTP 状态

**接口**: `GET /api/auth/totp/status`

**请求**:
```bash
curl -X GET http://localhost:8081/api/auth/totp/status \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "enabled": true,
    "remainingBackupCodes": 8
  }
}
```

---

## ❌ 禁用 TOTP

**接口**: `POST /api/auth/totp/disable`

**请求**:
```bash
curl -X POST http://localhost:8081/api/auth/totp/disable \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"password": "your_password"}'
```

**说明**：
- 必须提供当前管理员密码（二次验证）
- 禁用后密钥和备份码将被删除

**响应**:
```json
{
  "code": 200,
  "message": "TOTP 已禁用"
}
```

---

## 🚦 预检是否需要 TOTP 验证

### 智能前置检查

在发起敏感操作前，可以调用此接口判断是否需要弹出 TOTP 验证对话框，避免不必要的弹窗：

**接口**: `GET /api/auth/totp/check-required`

**参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `moduleKey` | String | 否 | 模块配置键，如 `security.totp.system_config`。如果不传，仅检查用户是否启用和会话状态 |

**请求**:
```bash
curl "http://localhost:8081/api/auth/totp/check-required?moduleKey=security.totp.system_config" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "needTotp": true,        // 是否需要弹出 TOTP 验证
    "userHasTotp": true,     // 用户是否已启用 TOTP
    "sessionValid": false    // 当前是否有有效 TOTP 会话
  }
}
```

**前端决策逻辑**：
- `needTotp == false` → 不发弹窗，直接发起请求
- `needTotp == true && userHasTotp == true` → 弹出验证码输入框
- `needTotp == true && userHasTotp == false` → 提示用户先去安全设置启用 TOTP

---

## 🔐 使用 TOTP 进行敏感操作验证

### 验证流程

当执行敏感操作（如重置密码、修改角色）时：

1. **前端先调用 `/api/auth/totp/check-required` 判断是否需要验证**
2. **如果需要，提示用户输入 TOTP 验证码**
3. **调用验证接口**:

**接口**: `POST /api/auth/totp/verify`

**请求**:
```bash
curl -X POST http://localhost:8081/api/auth/totp/verify \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

**响应**:
```json
{
  "code": 200,
  "data": true  // 或 false
}
```

**注意**：如果用户未启用 TOTP，会返回：
```json
{
  "code": 400,
  "message": "您尚未启用双因素认证，请先到安全设置中绑定 TOTP",
  "data": null
}
```

4. **验证通过后创建 TOTP 会话（5 分钟有效），后续敏感操作无需再次输入验证码**
5. **直接执行敏感操作**

---

## 🆘 使用备份码

### 何时使用备份码？

- 手机丢失或损坏
- Microsoft Authenticator 无法打开
- 时钟不同步导致验证码错误

### 如何使用？

备份码是 8 位数字，使用方法与 TOTP 验证码相同：

```bash
curl -X POST http://localhost:8081/api/auth/totp/verify \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code": "12345678"}'  # 8 位备份码
```

**重要规则**：
- ⚠️ 每个备份码只能使用**一次**
- ⚠️ 使用后自动失效
- ⚠️ 用完后需要重新生成 TOTP

### 查看剩余备份码数量

```bash
curl -X GET http://localhost:8081/api/auth/totp/status \
  -H "Authorization: Bearer YOUR_TOKEN"
```

响应中的 `remainingBackupCodes` 字段显示剩余数量。

---

## 📱 Microsoft Authenticator 使用技巧

### 1. 云备份（推荐）

Microsoft Authenticator 支持云备份，防止手机丢失：

1. 打开应用 → 设置
2. 启用 "云备份"
3. 使用 Microsoft 账户登录
4. 更换手机时可恢复所有账户

### 2. 多设备同步

可以在多个设备上绑定同一个 TOTP 密钥：

1. 在 Step 1 生成密钥后，**不要立即验证**
2. 在多个设备上分别扫描同一个二维码
3. 所有设备会生成相同的验证码
4. 最后在任意一个设备上完成验证

### 3. 时钟同步问题

如果验证码一直错误，可能是时钟不同步：

**解决方法**：
- Android: 设置 → 日期和时间 → 自动设置时间
- iOS: 设置 → 通用 → 日期与时间 → 自动设置

---

## 🔒 安全建议

### ✅ 最佳实践

1. **立即保存备份码**
   - 打印出来存放在安全的地方
   - 或使用密码管理器保存（如 Bitwarden、1Password）

2. **定期更换 TOTP**
   - 建议每 6-12 个月重新生成密钥
   - 旧密钥会自动失效

3. **启用云备份**
   - 防止手机丢失导致无法登录
   - Microsoft Authenticator 和 Google Authenticator 都支持

4. **保护管理员密码**
   - 使用强密码（12+ 字符，包含大小写、数字、符号）
   - 不要在公共场合输入密码

### ❌ 避免的操作

1. **不要截图保存密钥**
   - 截图可能泄露到云端相册
   - 应该直接扫描或手动输入

2. **不要分享备份码**
   - 备份码等同于密码
   - 任何人拿到都可以绕过 TOTP

3. **不要禁用 TOTP**
   - 除非确实不再需要
   - 临时问题应使用时钟同步或备份码解决

---

## 🛠️ 技术细节

### TOTP 工作原理

```
密钥 (Secret) + 当前时间 → HMAC-SHA1 → 截取 → 6 位数字
```

- **时间窗口**: 30 秒
- **哈希算法**: HMAC-SHA1
- **验证码长度**: 6 位
- **允许误差**: ±1 个时间窗口（防止时钟不同步）

### 数据存储

| 数据 | 存储位置 | 有效期 |
|------|---------|--------|
| TOTP 密钥 | Redis (`totp:secret:{userId}`) | 永久 |
| 备份码 | Redis (`totp:backup:{userId}`) | 30 天 |
| 临时密钥（未确认） | Redis | 10 分钟 |

### 兼容性

完全兼容以下应用：
- ✅ Microsoft Authenticator
- ✅ Google Authenticator
- ✅ Authy
- ✅ FreeOTP
- ✅ 1Password
- ✅ Bitwarden

---

## 📞 常见问题

### Q1: 验证码一直错误怎么办？

**A**: 检查以下几点：
1. 手机时钟是否准确（开启自动同步）
2. 是否在 30 秒内提交
3. 是否正确扫描了二维码
4. 尝试使用备份码

### Q2: 手机丢了怎么办？

**A**: 
1. 使用备份码登录（如果有保存）
2. 联系超级管理员重置 TOTP
3. 重新生成密钥并绑定新设备

### Q3: 可以同时使用多个 TOTP 应用吗？

**A**: 可以！在生成密钥后，用多个应用扫描同一个二维码即可。

### Q4: 备份码用完了怎么办？

**A**: 
1. 先禁用 TOTP（需要密码）
2. 重新生成新的密钥和备份码
3. 重新绑定设备

### Q5: TOTP 会影响性能吗？

**A**: 几乎不会。TOTP 验证是纯计算操作，耗时 < 1ms。

---

## 📚 相关文档

- [RFC 6238 - TOTP 标准](https://tools.ietf.org/html/rfc6238)
- [Microsoft Authenticator 官方文档](https://support.microsoft.com/zh-cn/account-billing/%E4%B8%8B%E8%BD%BD%E5%B9%B6%E5%AE%89%E8%A3%85-microsoft-authenticator-%E5%BA%94%E7%94%A8-3c3f4b6e-6c8a-4b6e-8f6e-6c8a4b6e8f6e)
- [Google Authenticator 项目](https://github.com/google/google-authenticator)

---

**最后更新**: 2026-05-17  
**版本**: v1.0
