# Authenticator 首页聚合 API 使用指南

## 概述

`GET /api/auth/app/dashboard` 是 MyAuthenticator Android App 首页（Authenticator 首页）的数据聚合接口。一次请求即可获取首页所需的所有数据，减少 App 启动时的网络请求次数。

## 接口信息

| 项目 | 值 |
|------|-----|
| 方法 | `GET` |
| 路径 | `/api/auth/app/dashboard` |
| 认证 | JWT Token（`Authorization: Bearer <token>`） |
| 内容类型 | `application/json` |

## 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `deviceId` | Query | String | 否 | 当前设备 ID。传入后可获取该设备的详细信息（加密算法偏好、绑定时间等） |

## 响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user": {
      "userId": 123456789,
      "username": "leoxiao",
      "nickname": "Leo",
      "email": "leo@example.com",
      "avatarUrl": "/api/users/avatar/public/123456789",
      "deviceCount": 2
    },
    "device": {
      "deviceId": "svr_abc123...",
      "deviceName": "Xiaomi 14",
      "cipherPref": "AES-256-GCM",
      "boundAt": "2026-06-15T10:30:00"
    },
    "recentHistory": {
      "total": 42,
      "records": [
        {
          "id": 1001,
          "challengeId": "challenge_uuid_xxx",
          "deviceName": "Xiaomi 14",
          "status": "approved",
          "requestedAt": "2026-06-20T10:00:00",
          "respondedAt": "2026-06-20T10:00:05",
          "responseTimeMs": 5000
        }
      ]
    },
    "lastAuthResult": {
      "status": "approved",
      "deviceName": "Xiaomi 14",
      "requestedAt": "2026-06-20T10:00:00"
    },
    "lastLoginAt": "2026-06-20T09:30:00"
  }
}
```

## 字段说明

### user（用户信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID（雪花算法） |
| username | String | 用户名 |
| nickname | String | 昵称（可能为 null） |
| email | String | 邮箱（可能为 null） |
| avatarUrl | String | 头像公开访问 URL，格式：`/api/users/avatar/public/{userId}` |
| deviceCount | Integer | 该用户已绑定的设备总数 |

> **注意**：`avatarUrl` 是相对路径，App 需要拼接后端基础 URL。建议 App 端在 URL 后追加时间戳参数（如 `?t=1718000000`）防止 CDN 缓存。

### device（当前设备信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | String | 设备唯一标识（格式：`svr_<UUID>`） |
| deviceName | String | 设备显示名称 |
| cipherPref | String | 设备首选加密算法：`AES-256-GCM` 或 `SM4-GCM` |
| boundAt | String | 设备绑定时间（ISO8601 格式），未传入 deviceId 时为 null |

> 仅在请求时传入了 `deviceId` 且该设备属于当前用户时，此字段才会返回数据。

### recentHistory（最近认证记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| total | Long | 该用户的总认证记录数 |
| records | Array | 最近 5 条认证记录（按发起时间降序） |

#### records[]. 单条记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| challengeId | String | 挑战 ID |
| deviceName | String | 发起挑战的设备名称 |
| status | String | 状态：`approved` / `rejected` / `expired` |
| requestedAt | String | 挑战发起时间（ISO8601） |
| respondedAt | String | 用户响应时间（ISO8601，未响应时为 null） |
| responseTimeMs | Long | 响应耗时（毫秒），未响应时为 null |

### lastAuthResult（上次认证结果）

| 字段 | 类型 | 说明 |
|------|------|------|
| status | String | 状态：`approved` / `rejected` / `expired`，无记录时为 null |
| deviceName | String | 发起挑战的设备名称 |
| requestedAt | String | 挑战发起时间（ISO8601） |

> 如果用户没有任何认证记录，`lastAuthResult` 字段为 null。

### lastLoginAt（最后登录时间）

| 字段 | 类型 | 说明 |
|------|------|------|
| lastLoginAt | String | 用户最后登录时间（ISO8601 格式） |

## 错误响应

```json
{
  "code": 401,
  "message": "未登录或 Token 已过期",
  "data": null
}
```

| HTTP 状态码 | code | 说明 |
|-------------|------|------|
| 401 | 401 | Token 缺失或无效 |
| 500 | 500 | 服务器内部错误 |

## App 端使用建议

### 1. 请求时机
- App 启动时调用一次
- 从后台切回前台时调用一次
- 下拉刷新时调用

### 2. 缓存策略
- 建议 App 端缓存 `device` 信息（特别是 `cipherPref`），避免每次启动都请求
- `user.avatarUrl` 建议 App 端拼接完整 URL 后缓存到本地图片缓存

### 3. 头像 URL 拼接
```kotlin
// Kotlin 示例
val baseUrl = "https://your-backend.com"
val avatarUrl = "$baseUrl${dashboard.user.avatarUrl}?t=${System.currentTimeMillis()}"
```

### 4. 空值处理
- `nickname`、`email` 可能为 null，UI 上应显示占位符
- `device` 在未传 `deviceId` 时为 null
- `lastAuthResult` 在无认证记录时为 null
- `respondedAt`、`responseTimeMs` 在未响应时为 null

## 实现参考

后端实现涉及三个文件：

| 文件 | 说明 |
|------|------|
| `DashboardResponse.java` | 响应 DTO，包含所有内部类定义 |
| `AppAuthService.java` | `getDashboard()` 方法实现聚合逻辑 |
| `AppAuthController.java` | `GET /dashboard` 端点定义 |

提交记录：`023af5e`
