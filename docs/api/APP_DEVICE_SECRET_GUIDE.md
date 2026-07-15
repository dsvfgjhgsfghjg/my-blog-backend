# App 设备码（Device Secret）重构指南

## 概述

本文档描述 App 端设备码机制的完整重构方案。设备码用于实现持久化的设备身份验证，确保 App 关闭后数据不丢失，重新登录时能识别同一设备。

## 核心概念

### 设备码（Device Secret）

- **deviceId**：服务端生成的唯一设备标识，格式为 `svr_<UUID>`（不可伪造）
- **deviceSecret**：服务端生成的 32 字节随机密钥（64 位 hex 字符串），BCrypt 哈希存储
- **deviceSecretHint**：密钥提示（前4位 + ... + 后4位），用于 UI 展示

### 设计原则

1. **服务端生成**：设备码由服务端生成，客户端不可伪造
2. **持久化存储**：App 端将设备码写入本地存储（SharedPreferences/DataStore）
3. **幂等绑定**：同一用户 + 同一 deviceId → 更新设备信息，不创建新记录
4. **加密信道下发**：设备码通过 DH 协商的 AES-GCM 加密通道下发
5. **密钥验证**：绑定和 WebSocket 连接时验证 deviceSecret

## API 接口

### 1. 生成设备码

```
POST /api/auth/app/device-secret
```

**请求体**：无（不需要认证）

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "svr_a1b2c3d4e5f6...",
    "deviceSecret": "a3f8b2c1...64位hex...",
    "hint": "a3f8...e5d6"
  }
}
```

**调用时机**：
- App 首次启动时
- 本地存储的设备码丢失时

### 2. 密码绑定（带设备密钥）

```
POST /api/auth/app/bind
```

**请求体**：
```json
{
  "username": "user@example.com",
  "password": "your_password",
  "deviceId": "svr_a1b2c3d4e5f6...",
  "deviceName": "Pixel 7",
  "deviceType": "ANDROID",
  "deviceSecret": "a3f8b2c1...64位hex..."
}
```

**响应**：
```json
{
  "code": 200,
  "message": "绑定成功",
  "data": {
    "token": "jwt_token...",
    "deviceId": "svr_a1b2c3d4e5f6..."
  }
}
```

### 3. 扫码绑定（带设备密钥）

```
POST /api/auth/app/bind/qrcode
```

**请求体**：
```json
{
  "pairCode": "123456",
  "deviceId": "svr_a1b2c3d4e5f6...",
  "deviceName": "Pixel 7",
  "deviceSecret": "a3f8b2c1...64位hex..."
}
```

## WebSocket 协议增强

### Bind 消息（新增 deviceSecret 字段）

```json
{
  "type": "bind",
  "token": "jwt_token...",
  "deviceId": "svr_a1b2c3d4e5f6...",
  "deviceSecret": "a3f8b2c1...64位hex..."
}
```

**增强验证流程**：
1. 验证 JWT Token 有效性
2. 验证设备是否属于该用户
3. 验证 deviceSecret（如果设备已有）
4. 关闭该设备的旧 WebSocket 连接（防止多设备重用同一 deviceId）

## App 端实现步骤

### 1. 本地存储

使用 Android SharedPreferences 或 DataStore 持久化存储设备码：

```kotlin
// 存储设备码
fun saveDeviceSecret(deviceId: String, deviceSecret: String) {
    preferences.edit()
        .putString("device_id", deviceId)
        .putString("device_secret", deviceSecret)
        .apply()
}

// 读取设备码
fun getDeviceSecret(): Pair<String?, String?> {
    val deviceId = preferences.getString("device_id", null)
    val deviceSecret = preferences.getString("device_secret", null)
    return Pair(deviceId, deviceSecret)
}

// 清除设备码（用户主动清除数据时）
fun clearDeviceSecret() {
    preferences.edit()
        .remove("device_id")
        .remove("device_secret")
        .apply()
}
```

### 2. 启动流程

```
App 启动
  ├─ 读取本地存储的设备码
  │    ├─ 存在 → 使用现有设备码
  │    └─ 不存在 → 调用 POST /api/auth/app/device-secret 获取新设备码
  │                  └─ 存储到本地
  ├─ 检查是否有 JWT Token
  │    ├─ 有 → 尝试 WebSocket 连接（带上 deviceId + deviceSecret）
  │    └─ 无 → 显示登录页面
  └─ 用户登录
       ├─ 调用 POST /api/auth/app/bind（带上 deviceId + deviceSecret）
       ├─ 获取 JWT Token
       └─ 建立 WebSocket 连接
```

### 3. 重新连接流程

```
WebSocket 断开
  ├─ 读取本地存储的 deviceId + deviceSecret
  ├─ 建立新 WebSocket 连接
  ├─ 发送 bind 消息（带上 token + deviceId + deviceSecret）
  └─ 服务端验证通过后，关闭旧连接，注册新连接
```

### 4. 数据丢失处理

如果用户清除 App 数据或卸载重装：
1. 本地存储的设备码丢失
2. 调用 `POST /api/auth/app/device-secret` 获取新设备码
3. 新设备码对应一条新的设备记录（未绑定用户）
4. 用户重新登录时，使用新设备码绑定
5. 服务端创建新的设备记录

## 数据库变更

```sql
ALTER TABLE t_app_device 
ADD COLUMN IF NOT EXISTS device_secret VARCHAR(64) DEFAULT NULL,
ADD COLUMN IF NOT EXISTS device_secret_hint VARCHAR(20) DEFAULT NULL;

COMMENT ON COLUMN t_app_device.device_secret IS '设备密钥（BCrypt 哈希，服务端生成）';
COMMENT ON COLUMN t_app_device.device_secret_hint IS '设备密钥提示（前4位+后4位，如 a3f8...e5d6）';
```

## 安全注意事项

1. **deviceSecret 不可明文存储**：服务端使用 BCrypt 哈希存储
2. **加密信道传输**：设备码通过 DH 协商的 AES-GCM 加密通道下发
3. **设备码不可伪造**：deviceId 格式为 `svr_<UUID>`，由服务端生成
4. **重放攻击防护**：每次 WebSocket 连接都验证 deviceSecret
5. **旧连接关闭**：同一 deviceId 的新连接会强制关闭旧连接

## 兼容性说明

- 旧版 App（无 deviceSecret）仍然可以正常绑定和连接
- 服务端对无 deviceSecret 的设备不做密钥验证
- 建议 App 端尽快升级以支持设备码机制
