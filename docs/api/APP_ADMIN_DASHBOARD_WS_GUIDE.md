# App 管理后台仪表盘 WebSocket API 指南

## 概述

本文档描述如何通过加密 WebSocket 通道从 App（MyAuthenticator）获取管理后台仪表盘数据。

### 设计原则

- **复用现有加密通道**：使用已有的 DH 密钥交换 + AES/SM4-GCM 加密通道
- **独立 WS API**：不重用后端 REST API，创建新的 WS 消息类型
- **权限控制**：尊重管理员身份和 CBAC（Capability-Based Access Control）能力系统
- **请求/响应模式**：不实时推送，App 主动请求，服务端响应
- **无需 TOTP**：App 已有 PIN/指纹保护

### 权限矩阵

| 用户类型 | 行为 |
|---------|------|
| 非管理员 | 返回 error: "权限不足，需要管理员权限" |
| 超级管理员 | 返回完整仪表盘数据 |
| 管理员（有能力） | 返回完整仪表盘数据 |
| 管理员（无能力） | 返回 status=no_ability: "请联系超级管理员分配权限" |

---

## 协议流程

### 前提条件

1. WebSocket 连接已建立
2. DH 密钥交换已完成（`dh_ready` 已收到）
3. 加密通道已就绪

### 请求格式

App 通过加密通道发送以下消息：

```json
{"type":"admin_dashboard_request"}
```

**注意**：此消息必须通过加密通道发送，即外层包装为：

```json
{"type":"encrypted","iv":"...","ciphertext":"..."}
```

其中 `ciphertext` 是上述 JSON 的 AES-GCM/SM4-GCM 加密结果。

### 响应格式

#### 成功响应（超级管理员/管理员有能力）

```json
{
  "type": "admin_dashboard_response",
  "status": "ok",
  "data": {
    "loginInfo": {
      "currentIp": "unknown",
      "lastLoginIp": "192.168.1.1",
      "lastLoginTime": "2026-06-19T15:30:00",
      "loginCount": 42
    },
    "statsCards": {
      "totalUsers": {
        "current": 128,
        "percentageChange": 12.5,
        "trend": "up"
      },
      "totalPosts": {
        "current": 56,
        "percentageChange": -3.2,
        "trend": "down"
      },
      "todayVisits": {
        "current": 89,
        "percentageChange": 5.0,
        "trend": "up"
      }
    },
    "lastUpdateTime": "2026-06-19T14:30:00",
    "quickActions": [
      {"action": "new_post", "label": "写文章", "icon": "edit", "route": "/admin/posts/create"},
      {"action": "manage_posts", "label": "文章管理", "icon": "list", "route": "/admin/posts"},
      {"action": "manage_comments", "label": "评论管理", "icon": "message-square", "route": "/admin/comments"},
      {"action": "manage_users", "label": "用户管理", "icon": "users", "route": "/admin/users"},
      {"action": "system_config", "label": "系统设置", "icon": "settings", "route": "/admin/settings"},
      {"action": "media_library", "label": "媒体库", "icon": "image", "route": "/admin/media"}
    ],
    "recentActivities": [
      {
        "id": 1,
        "type": "post_publish",
        "content": "发布了新文章《Spring Boot 入门指南》",
        "time": "2026-06-19T14:00:00",
        "username": "管理员"
      }
    ]
  }
}
```

**注意**：`loginInfo.currentIp` 在 WebSocket 上下文中固定为 `"unknown"`，因为 WS 无法获取 HTTP 请求的客户端 IP。

#### 无能力响应（管理员但无任何管理能力）

```json
{
  "type": "admin_dashboard_response",
  "status": "no_ability",
  "message": "请联系超级管理员分配权限"
}
```

#### 错误响应（非管理员）

通过明文 error 消息返回：

```json
{
  "type": "error",
  "message": "权限不足，需要管理员权限"
}
```

---

## Android 客户端使用示例

### 1. 请求仪表盘数据

```kotlin
// 在 WebSocket 连接就绪后（dh_ready 已收到）
webSocketClient.requestAdminDashboard()
```

### 2. 处理响应

在 `WebSocketListenerCallback` 中实现回调：

```kotlin
override fun onAdminDashboardResponse(data: JsonObject) {
    val status = data.get("status")?.asString
    
    when (status) {
        "ok" -> {
            // 解析仪表盘数据
            val dashboardData = data.getAsJsonObject("data")
            val loginInfo = dashboardData.getAsJsonObject("loginInfo")
            val statsCards = dashboardData.getAsJsonObject("statsCards")
            val quickActions = dashboardData.getAsJsonArray("quickActions")
            val recentActivities = dashboardData.getAsJsonArray("recentActivities")
            
            // 更新 UI
            runOnUiThread {
                // 显示数据
            }
        }
        "no_ability" -> {
            val message = data.get("message")?.asString ?: "请联系超级管理员分配权限"
            // 显示提示信息
            runOnUiThread {
                showNoAbilityMessage(message)
            }
        }
    }
}
```

### 3. 错误处理

```kotlin
override fun onError(message: String) {
    if (message.contains("权限不足")) {
        // 非管理员用户
        showToast("您不是管理员，无法查看管理后台")
    }
}
```

---

## 后端实现说明

### 文件变更

| 文件 | 变更 |
|------|------|
| `AuthWebSocketHandler.java` | 新增 `handleAdminDashboardRequest()` 和 `sendDashboardResponse()` 方法 |
| `AdminDashboardService.java` | 新增 `getDashboardForWs()` 和 `buildLoginInfoForWs()` 方法 |

### 关键设计决策

1. **`getDashboardForWs()` vs `getDashboard()`**：WS 版本不依赖 HTTP `ServletRequest`，`currentIp` 固定返回 `"unknown"`
2. **能力检查**：使用 `AbilityValidator.validateAnyAbility()` 检查管理员是否有任何管理能力
3. **超级管理员绕过**：超级管理员直接返回完整数据，无需检查能力
4. **加密传输**：所有响应都通过加密通道传输，确保数据安全

---

## 安全注意事项

1. 所有仪表盘数据通过加密通道传输，防止中间人窃听
2. 权限检查在服务端执行，客户端无法绕过
3. 非管理员无法获取任何仪表盘数据
4. 管理员无能力时仅返回提示信息，不暴露任何数据
