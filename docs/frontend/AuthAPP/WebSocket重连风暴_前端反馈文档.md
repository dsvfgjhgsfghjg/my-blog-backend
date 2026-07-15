# WebSocket 重连风暴 — 后端修复完成 & 前端配合指南

> **文档日期:** 2026-06-18  
> **最后更新:** 2026-06-19  
> **关联文档:** [WebSocket重连风暴分析与后端修复指南.md](../../../AndroidStudioProjects/MyAuthenticator/MYbugreport/WebSocket重连风暴分析与后端修复指南.md)  
> **涉及项目:** MyAuthenticator Android App + Vue3 管理后台

---

## 📋 概述

2026-06-18 上午，App 端出现 WebSocket 重连风暴（持续 80 分钟反复断连）。经排查，**根本原因在服务端**，现已修复。本文档说明：

1. ✅ 后端已修复的内容
2. ✅ 前端/App 需要配合做的改动（已全部实现）
3. ✅ 前端管理后台新增的 Bug 日志功能

---

## 一、后端已修复的内容

### 1.1 根因：dh_ready 后误关连接 ❌ → ✅

**问题：** 旧代码在 `handleDhResponse()` 中发送 `dh_ready` 后，可能在某些异常路径下错误地关闭了 WebSocket session。

**修复：** 当前 `AuthWebSocketHandler.java` 第 311 行发送 `dh_ready` 后，**不再关闭 session**，而是继续发送 challenge：

```java
// 发送 dh_ready 并保持连接
session.sendMessage(new TextMessage(dhReadyMsg));
// ✅ 正确的做法：这里什么都不做，保持连接打开
log.info("DH 密钥交换完成: sessionId={}, cipher={}", session.getId(), selectedCipher);
```

### 1.2 防御性关闭检查 ✅

`afterConnectionClosed()` 增加了 sessionId 匹配检查，防止旧 session 关闭事件误删新 session：

```java
// 只移除如果该 sessionId 确实是当前活跃的 session
String currentDeviceId = sessionManager.getDeviceIdBySessionId(sessionId);
if (currentDeviceId != null) {
    WebSocketSession currentSession = sessionManager.getSession(currentDeviceId);
    if (currentSession != null && currentSession.getId().equals(sessionId)) {
        sessionManager.removeSession(sessionId);
    } else {
        log.debug("忽略旧 session 关闭事件: sessionId={}（已被新 session 替换）", sessionId);
    }
}
```

### 1.3 先注册新 session，再关旧 session ✅

`WebSocketSessionManager.registerSession()` 改为**先注册新 session，再关闭旧 session**，彻底消除竞态条件：

```java
// 1. 先记录新 session 的映射关系
sessionDevices.put(newSessionId, deviceId);
deviceSessions.put(deviceId, session);
deviceUsers.put(deviceId, userId);

// 2. 再关闭旧 session（如果有且不是同一个 session）
WebSocketSession oldSession = deviceSessions.get(deviceId);
if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(newSessionId)) {
    oldSession.close();
}
```

### 1.4 catch 块不关闭连接 ✅

`handleTextMessage()` 的 catch 块明确注释了**不关闭连接**，只发送错误消息：

```java
catch (Exception e) {
    log.error("处理消息异常: sessionId={}, type={}", session.getId(), type, e);
    sendError(session, "处理消息失败");
    // 不关闭连接，只发送错误消息，防止异常传播到框架层导致连接被关闭
}
```

---

## 二、前端/App 需要配合做的改动（✅ 全部已实现）

### 2.1 重连退避策略（✅ 已修复）

旧版客户端代码在 `onConnected()` 中无条件重置退避延迟为最小值 1s，导致重连风暴。

**已修复：** 在 `WebSocketService.kt` 中实现了基于连接稳定性的指数退避策略 + 快速断连检测机制。

#### 基础指数退避

| 重连次数 | 退避时间 |
|---------|---------|
| 第 1 次 | 1 秒 |
| 第 2 次 | 2 秒 |
| 第 3 次 | 4 秒 |
| 第 4 次 | 8 秒 |
| 第 5 次 | 16 秒 |
| 第 6+ 次 | 30 秒（封顶） |

#### 快速断连检测（增强保护）

除基础退避外，额外实现了**快速断连检测**机制：

```
- 阈值：10 秒内断开视为"快速断连"
- 连续 3 次快速断连 → 退避时间 × 4 倍
- onDhReady 时检查连接持续 ≥ 15 秒 → 重置所有退避计数
```

**核心代码（App 端 `WebSocketService.kt`）：**

```kotlin
private fun scheduleReconnect() {
    // 检测快速断连
    val timeSinceConnected = now - connectionEstablishedTime
    if (timeSinceConnected < QUICK_DISCONNECT_WINDOW_MS) {
        quickDisconnectCount++
    }

    // 快速断连时应用额外退避
    val effectiveDelay = if (quickDisconnectCount >= 3) {
        (reconnectDelayMs * QUICK_DISCONNECT_BACKOFF_MULTIPLIER)
            .coerceAtMost(RECONNECT_MAX_DELAY_MS)
    } else {
        reconnectDelayMs
    }

    mainHandler.postDelayed(reconnectRunnable, effectiveDelay)

    // 指数退避
    reconnectDelayMs = (reconnectDelayMs * RECONNECT_MULTIPLIER)
        .coerceAtMost(RECONNECT_MAX_DELAY_MS)
}
```

### 2.2 连接状态监控（✅ 已实现）

调试页面新增了完整的连接监控指标，可在 `DebugInfoFragment` 查看：

| 指标 | 说明 | 状态 |
|------|------|------|
| 当前连接状态 | ❌未连接 / ⏳连接中 / ✅加密通道就绪 | ✅ 已实现 |
| 重连次数 | 本次会话的重连总次数 | ✅ 已实现 |
| 上次断连时间 | 最近一次断开的时间戳（HH:mm:ss） | ✅ 已实现 |
| 上次断连原因 | close code + reason 或错误信息 | ✅ 已实现 |

### 2.3 异常重连自动上报日志（✅ 已实现）

当检测到异常重连（短时间内重连次数 > 3 次），`WebSocketService` 自动收集日志并调用 `BugReportManager` 上报给后端：

```kotlin
// WebSocketService.kt onDhReady() 中
if (reconnectCount > ABNORMAL_RECONNECT_THRESHOLD && !reportedAbnormalReconnect) {
    reportedAbnormalReconnect = true
    autoSubmitBugReport(reconnectCount)
}

// 自动上报方法
private fun autoSubmitBugReport(reconnectCount: Int) {
    val logText = buildString {
        appendLine("=== WebSocket 异常重连自动上报 ===")
        appendLine("已重连次数: $reconnectCount")
        appendLine("快速断连计数: $quickDisconnectCount")
        appendLine("当前退避延迟: ${reconnectDelayMs}ms")
        appendLine("上次断开时间: ${lastDisconnectTime ?: "N/A"}")
        appendLine("上次断开原因: ${lastDisconnectReason ?: "N/A"}")
    }
    // 异步调用 BugReportManager.submitLog()
}
```

### 2.4 用户手动上报日志功能（✅ 已实现）

在"我的"页面（`ProfileFragment`）的菜单中新增了**提交反馈**入口，用户可手动填写问题描述并上传日志：

| 功能 | 说明 | 状态 |
|------|------|------|
| 反馈对话框 | Material Design Outlined TextInput + 提交取消按钮 | ✅ 已实现 |
| 日志压缩 | Gzip + Base64 压缩上报 | ✅ 已实现 |
| 设备信息 | 自动采集 App 版本/设备型号/系统版本 | ✅ 已实现 |

**对话框布局文件：** `res/layout/dialog_bug_report.xml`

---

## 三、前端管理后台新增功能：Bug 日志查看

### 3.1 功能说明

后端新增了 Bug 日志上报功能，App 端可以将日志（Gzip+Base64 压缩）上传到后端，存储在 MinIO。

**前端管理页面已实现：** `/admin/bug-reports`

### 3.2 页面功能

| 功能 | 说明 |
|------|------|
| 日志列表 | 分页展示所有 Bug 日志，支持按状态筛选（全部/未读/已读/已解决） |
| 查看详情 | 弹窗展示日志元数据（用户ID、App版本、设备型号等）+ 完整日志内容 |
| 标记已读 | 查看详情时自动标记为已读 |
| 标记已解决 | 填写解决备注后标记 |
| 删除日志 | 同时删除 MinIO 中的文件 |

### 3.3 API 接口（仅管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/bug-reports` | 分页查询列表 |
| GET | `/api/admin/bug-reports/{id}` | 获取详情（含日志内容） |
| PUT | `/api/admin/bug-reports/{id}/status` | 更新状态 |
| DELETE | `/api/admin/bug-reports/{id}` | 删除日志 |

### 3.4 前端文件清单

| 文件 | 说明 |
|------|------|
| `src/services/bugReport.ts` | API 服务封装 |
| `src/views/admin/BugReportManagement.vue` | 管理页面组件 |
| `src/router/index.ts` | 路由配置（`/admin/bug-reports`） |
| `src/layouts/AdminLayout.vue` | 管理员菜单（Bug 日志入口） |

---

## 四、验证修复是否成功

### 4.1 App 端验证

打开 App 调试页面，检查以下指标：

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 握手状态 | ❌ 未连接 | 🎉 加密通道就绪 |
| 重连次数 | 持续为 1~2 | 0（稳定连接） |
| 加密通道 | 瞬闪就绪 | 持续保持 |
| 设备在线状态 | ❌ 离线 | ✅ 在线 |
| Challenge 接收 | 无法收到 | 可正常接收并弹窗 |

### 4.2 管理后台验证

1. 登录管理后台 → 左侧菜单「Bug 日志」
2. 查看是否有 App 上报的日志记录
3. 点击查看详情，确认日志内容完整
4. 测试状态流转：未读 → 已读 → 已解决

---

## 五、后续优化建议

### 5.1 服务端（已完成 ✅）

- [x] dh_ready 后不关闭连接
- [x] 防御性 session 关闭检查
- [x] 先注册新 session 再关旧 session
- [x] catch 块不关闭连接

### 5.2 服务端（待优化）

- [ ] 增加 WebSocket 连接监控端点（REST API 查询活跃连接）
- [ ] 心跳超时设置（60~90 秒无消息判定断开）
- [ ] 优雅关闭机制（关闭前发送 error 消息带原因）

### 5.3 App 端（✅ 全部已完成）

- [x] 指数退避重连（含快速断连检测 + 额外退避）
- [x] 连接状态监控面板（握手状态 + 重连次数 + 断连时间/原因）
- [x] 异常重连自动上报日志（>3 次自动上报）
- [x] 用户手动上报日志功能（"我的"页面 → 提交反馈）

### 5.4 管理后台（已完成 ✅）

- [x] Bug 日志列表查看
- [x] 日志详情查看（含完整内容）
- [x] 状态管理（已读/已解决）
- [x] 日志删除

---

## 六、相关文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 重连风暴分析 | `MYbugreport/WebSocket重连风暴分析与后端修复指南.md` | 问题根因分析 |
| 后端 API 指南 | `docs/frontend/AuthAPP/BACKEND_API_GUIDE.md` | WebSocket 协议说明 |
| 前端管理指南 | `docs/api/BUG_REPORT_FRONTEND.md` | Bug 日志管理页面 |
| Android 接入指南 | `docs/api/BUG_REPORT_ANDROID_GUIDE.md` | App 端日志上报实现 |