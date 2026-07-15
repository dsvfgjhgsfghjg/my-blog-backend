# App 推送测试功能 — Android 端实现指南

> **版本:** v1.0  
> **更新日期:** 2026-06-15  
> **关联文档:** [APP_AUTH_ANDROID_GUIDE_V2.md](./APP_AUTH_ANDROID_GUIDE_V2.md)（基础认证协议）

---

## 📋 目录

1. [概述](#-概述)
2. [功能流程](#-功能流程)
3. [WebSocket 协议](#-websocket-协议)
   - [3.1 挑战推送（服务端 → App）](#31-挑战推送服务端--app)
   - [3.2 挑战响应（App → 服务端）](#32-挑战响应app--服务端)
   - [3.3 认证结果（服务端 → App）](#33-认证结果服务端--app)
4. [Android 端实现](#-android-端实现)
   - [4.1 挑战消息处理](#41-挑战消息处理)
   - [4.2 3 选 1 界面](#42-3-选-1-界面)
   - [4.3 响应发送](#43-响应发送)
5. [完整示例](#-完整示例)
6. [常见问题](#-常见问题)

---

## 📖 概述

App 推送测试功能允许管理后台向已绑定设备的 App 用户推送 **3 选 1 挑战**，用于测试双因素认证流程。该功能基于已有的 WebSocket 加密通信协议实现。

### WebSocket 端点

| 端点路径 | 说明 | 适用场景 |
|----------|------|----------|
| `ws://host/ws/app/auth` | 带 SockJS 降级的端点 | 浏览器端 |
| `ws://host/ws/app/auth/native` | **原生 WebSocket 端点（推荐）** | App 端 |

> **⚠️ 重要：** App 端应使用 `/ws/app/auth/native` 端点进行连接。`/ws/app/auth` 端点配置了 SockJS 降级，可能对原生 WebSocket 客户端造成干扰。

### 核心流程

```
管理后台                   后端服务                  App 设备
    |                        |                        |
    |--- POST /push -------->|                        |
    |                        |--- challenge(加密) ---->|
    |                        |                        |--- 显示 3 个选项
    |                        |                        |--- 用户选择
    |                        |<-- challenge_response -|
    |                        |--- auth_result(加密) -->|
    |                        |                        |
    |<-- 轮询结果 -----------|                        |
```

### 关键特性

- 复用已有的 WebSocket 加密通道（DH + AES-GCM/SM4-GCM）
- 挑战消息使用加密传输
- 支持多设备同时推送
- 挑战结果通过 REST API 轮询获取

---

## 🔄 功能流程

### 1. 设备绑定与加密通道建立

App 需先完成 WebSocket 连接和 DH 密钥交换（详见 [APP_AUTH_ANDROID_GUIDE_V2.md](./APP_AUTH_ANDROID_GUIDE_V2.md)）：

```
bind → dh_init → dh_response → dh_ready
```

### 2. 接收挑战

加密通道建立后，服务端可随时推送挑战消息。

### 3. 用户选择

App 显示 3 个数字选项，用户选择其中一个。

### 4. 发送响应

App 将用户选择的数字加密后发送回服务端。

### 5. 获取结果

服务端验证后返回认证结果（approved/rejected/expired）。

---

## 🌐 WebSocket 协议

### 3.1 挑战推送（服务端 → App）

当管理后台触发推送时，服务端通过 WebSocket 发送加密挑战消息。

#### 加密前消息体

```json
{
  "type": "challenge",
  "challengeId": "CHALLENGE_abc123_def456",
  "numbers": [7, 42, 88],
  "expiresAt": 1778947200000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `"challenge"` |
| `challengeId` | string | 挑战唯一标识 |
| `numbers` | int[3] | 3 个数字选项（0-99），其中一个是正确答案 |
| `expiresAt` | long | 挑战过期时间戳（毫秒） |

#### 加密后传输格式

```json
{
  "type": "encrypted",
  "iv": "AABBCCDDEEFFGGHHIIJJKKLL",
  "ciphertext": "BASE64_ENCODED_CIPHERTEXT"
}
```

> **注意：** 此消息使用 DH 协商的 AES-GCM/SM4-GCM 密钥加密。解密后得到上述 JSON 消息体。

### 3.2 挑战响应（App → 服务端）

用户选择后，App 将选择的数字加密发送。

#### 加密前消息体

```json
{
  "type": "challenge_response",
  "challengeId": "CHALLENGE_abc123_def456",
  "selectedNumber": 42
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `"challenge_response"` |
| `challengeId` | string | 对应挑战的 ID |
| `selectedNumber` | int | 用户选择的数字（0-99） |

#### 加密后传输格式

```json
{
  "type": "encrypted",
  "iv": "BASE64_IV",
  "ciphertext": "BASE64_ENCODED_CIPHERTEXT"
}
```

### 3.3 认证结果（服务端 → App）

服务端验证后返回结果。

#### 加密前消息体

**批准：**
```json
{
  "type": "auth_result",
  "status": "approved"
}
```

**拒绝：**
```json
{
  "type": "auth_result",
  "status": "rejected",
  "reason": "选择的数字不匹配"
}
```

**过期：**
```json
{
  "type": "auth_result",
  "status": "expired",
  "reason": "挑战已过期"
}
```

---

## 🤖 Android 端实现

### 4.1 挑战消息处理

在 WebSocket 消息处理逻辑中，增加对 `challenge` 类型的处理：

```kotlin
// WebSocketMessageHandler.kt

private fun handleEncryptedMessage(decryptedJson: String) {
    val message = JSONObject(decryptedJson)
    when (message.getString("type")) {
        "challenge" -> {
            handleChallenge(message)
        }
        "auth_result" -> {
            handleAuthResult(message)
        }
        // ... 其他消息类型
    }
}

private fun handleChallenge(message: JSONObject) {
    val challengeId = message.getString("challengeId")
    val numbers = message.getJSONArray("numbers")
    val expiresAt = message.getLong("expiresAt")
    
    // 解析 3 个数字
    val options = listOf(
        numbers.getInt(0),
        numbers.getInt(1),
        numbers.getInt(2)
    )
    
    // 在主线程显示挑战界面
    runOnUiThread {
        showChallengeDialog(challengeId, options, expiresAt)
    }
}
```

### 4.2 3 选 1 界面

创建一个对话框或 Activity 来展示 3 个选项：

```kotlin
// ChallengeDialog.kt

class ChallengeDialog(
    private val context: Context,
    private val challengeId: String,
    private val options: List<Int>,
    private val expiresAt: Long,
    private val onSelected: (Int) -> Unit
) : Dialog(context) {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_challenge)
        
        // 显示 3 个选项按钮
        findViewById<Button>(R.id.btn_option_1).text = options[0].toString()
        findViewById<Button>(R.id.btn_option_2).text = options[1].toString()
        findViewById<Button>(R.id.btn_option_3).text = options[2].toString()
        
        // 设置点击事件
        findViewById<Button>(R.id.btn_option_1).setOnClickListener {
            onSelected(options[0])
            dismiss()
        }
        findViewById<Button>(R.id.btn_option_2).setOnClickListener {
            onSelected(options[1])
            dismiss()
        }
        findViewById<Button>(R.id.btn_option_3).setOnClickListener {
            onSelected(options[2])
            dismiss()
        }
        
        // 启动过期倒计时
        startExpiryCountdown()
    }
    
    private fun startExpiryCountdown() {
        val remaining = expiresAt - System.currentTimeMillis()
        if (remaining <= 0) {
            // 挑战已过期
            dismiss()
            return
        }
        // 设置倒计时
        findViewById<TextView>(R.id.tv_expiry).text = 
            "剩余 ${remaining / 1000} 秒"
    }
}
```

布局文件 `dialog_challenge.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="请选择正确的数字"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <TextView
        android:id="@+id/tv_expiry"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#E6A23C"
        android:layout_marginBottom="24dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:spaceEvenly="true">

        <Button
            android:id="@+id/btn_option_1"
            style="@style/ChallengeOptionButton" />

        <Button
            android:id="@+id/btn_option_2"
            style="@style/ChallengeOptionButton" />

        <Button
            android:id="@+id/btn_option_3"
            style="@style/ChallengeOptionButton" />
    </LinearLayout>
</LinearLayout>
```

### 4.3 响应发送

用户选择后，将选择的数字加密发送：

```kotlin
// WebSocketManager.kt

fun sendChallengeResponse(challengeId: String, selectedNumber: Int) {
    try {
        // 构建消息体
        val message = JSONObject().apply {
            put("type", "challenge_response")
            put("challengeId", challengeId)
            put("selectedNumber", selectedNumber)
        }
        
        // 使用 DH 协商的密钥加密
        val encrypted = encryptMessage(message.toString())
        
        // 发送加密消息
        webSocket.send(encrypted)
        
        Log.d(TAG, "挑战响应已发送: challengeId=$challengeId, selected=$selectedNumber")
    } catch (e: Exception) {
        Log.e(TAG, "发送挑战响应失败", e)
    }
}

private fun encryptMessage(plaintext: String): String {
    val cipher = getCipher() // AES-GCM 或 SM4-GCM
    val iv = generateIV()
    val ciphertext = cipher.doFinal(plaintext.toByteArray())
    
    return JSONObject().apply {
        put("type", "encrypted")
        put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
    }.toString()
}
```

---

## 📱 完整示例

### 集成到现有 WebSocket 处理流程

```kotlin
// MainWebSocketService.kt

class MainWebSocketService : Service() {
    
    private lateinit var webSocketManager: WebSocketManager
    private var currentChallenge: ChallengeInfo? = null
    
    // 消息分发
    private fun onMessageReceived(rawMessage: String) {
        val root = JSONObject(rawMessage)
        val type = root.optString("type")
        
        when (type) {
            "encrypted" -> {
                // 解密消息
                val decrypted = decryptMessage(root)
                if (decrypted != null) {
                    handleDecryptedMessage(decrypted)
                }
            }
            "error" -> {
                val errorMsg = root.optString("message", "未知错误")
                Log.e(TAG, "服务端错误: $errorMsg")
            }
            "pong" -> {
                // 心跳响应，无需处理
            }
        }
    }
    
    private fun handleDecryptedMessage(decryptedJson: String) {
        val message = JSONObject(decryptedJson)
        when (message.getString("type")) {
            "challenge" -> {
                val challengeId = message.getString("challengeId")
                val numbers = message.getJSONArray("numbers")
                val expiresAt = message.getLong("expiresAt")
                
                currentChallenge = ChallengeInfo(
                    challengeId = challengeId,
                    numbers = listOf(
                        numbers.getInt(0),
                        numbers.getInt(1),
                        numbers.getInt(2)
                    ),
                    expiresAt = expiresAt
                )
                
                // 发送广播通知 Activity 显示挑战界面
                val intent = Intent(ACTION_SHOW_CHALLENGE).apply {
                    putExtra("challengeId", challengeId)
                    putExtra("option1", numbers.getInt(0))
                    putExtra("option2", numbers.getInt(1))
                    putExtra("option3", numbers.getInt(2))
                    putExtra("expiresAt", expiresAt)
                }
                sendBroadcast(intent)
            }
            "auth_result" -> {
                val status = message.getString("status")
                val reason = message.optString("reason", "")
                
                // 发送广播通知 Activity 显示结果
                val intent = Intent(ACTION_AUTH_RESULT).apply {
                    putExtra("status", status)
                    putExtra("reason", reason)
                }
                sendBroadcast(intent)
                
                currentChallenge = null
            }
        }
    }
    
    fun sendChallengeResponse(selectedNumber: Int) {
        val challenge = currentChallenge ?: return
        webSocketManager.sendChallengeResponse(
            challenge.challengeId, 
            selectedNumber
        )
    }
    
    companion object {
        const val ACTION_SHOW_CHALLENGE = "com.example.SHOW_CHALLENGE"
        const val ACTION_AUTH_RESULT = "com.example.AUTH_RESULT"
    }
    
    data class ChallengeInfo(
        val challengeId: String,
        val numbers: List<Int>,
        val expiresAt: Long
    )
}
```

### Activity 中接收挑战

```kotlin
// MainActivity.kt

class MainActivity : AppCompatActivity() {
    
    private val challengeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MainWebSocketService.ACTION_SHOW_CHALLENGE -> {
                    val challengeId = intent.getStringExtra("challengeId")!!
                    val option1 = intent.getIntExtra("option1", 0)
                    val option2 = intent.getIntExtra("option2", 0)
                    val option3 = intent.getIntExtra("option3", 0)
                    val expiresAt = intent.getLongExtra("expiresAt", 0)
                    
                    showChallengeDialog(
                        challengeId,
                        listOf(option1, option2, option3),
                        expiresAt
                    )
                }
                MainWebSocketService.ACTION_AUTH_RESULT -> {
                    val status = intent.getStringExtra("status")!!
                    val reason = intent.getStringExtra("reason") ?: ""
                    showResultToast(status, reason)
                }
            }
        }
    }
    
    private fun showChallengeDialog(
        challengeId: String,
        options: List<Int>,
        expiresAt: Long
    ) {
        val dialog = ChallengeDialog(this, challengeId, options, expiresAt) { selected ->
            // 用户选择了某个数字，发送响应
            val intent = Intent(this, MainWebSocketService::class.java)
            // 通过绑定 Service 或广播方式发送
            serviceConnection.service?.sendChallengeResponse(selected)
        }
        dialog.show()
    }
}
```

---

## ❓ 常见问题

### Q1: App 在后台时收到挑战怎么办？

建议使用前台服务（Foreground Service）保持 WebSocket 连接。收到挑战时，通过系统通知提醒用户，点击通知后打开 App 显示挑战界面。

### Q2: 挑战过期了怎么办？

App 端应检查 `expiresAt` 时间戳，如果已过期则自动忽略该挑战，并显示"挑战已过期"提示。服务端也会在验证时返回 `expired` 状态。

### Q3: 加密密钥丢失怎么办？

如果 WebSocket 连接断开，DH 会话状态会丢失。App 需要重新建立 WebSocket 连接并完成 DH 密钥交换。

### Q4: 如何区分测试推送和正常认证？

消息格式完全相同，App 无需区分。测试推送的挑战与正常认证流程中的挑战行为一致。

### Q5: 多个挑战同时到达怎么办？

App 应维护一个挑战队列，依次处理。建议一次只显示一个挑战界面，用户完成后再处理下一个。

---

## 🔍 调试指南（排查"设备不在线"问题）

如果管理后台的"App 推送测试"页面显示设备离线，请按以下步骤排查。

### 后端已提供的调试工具

管理后台的"App 推送测试"页面已包含以下调试能力：

1. **设备列表页面**：显示每个设备的在线/离线状态、设备 ID、最后活跃时间
2. **调试接口**：`GET /api/auth/app/test-push/debug/sessions` — 返回当前 WebSocket 会话管理器的详细状态
3. **后端日志**：每次查询设备列表时，会输出详细的日志（设备 ID、在线状态等）

### App 端需要添加的调试日志

App 开发者在 WebSocket 连接的关键节点添加日志，便于定位问题：

```kotlin
// WebSocketManager.kt — 添加调试日志

class WebSocketManager {
    
    companion object {
        private const val TAG = "WebSocketDebug"
    }
    
    // 1. WebSocket 连接时 — 记录连接地址和参数
    fun connect(wsUrl: String, token: String, deviceId: String) {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "WebSocket 连接开始")
        Log.d(TAG, "连接地址: $wsUrl")
        Log.d(TAG, "设备 ID: $deviceId")
        Log.d(TAG, "Token 前缀: ${token.take(20)}...")
        Log.d(TAG, "═══════════════════════════════════════")
        
        // 建议使用原生端点（无 SockJS）
        // 正确的地址: ws://host/ws/app/auth/native
        // 错误的地址: ws://host/ws/app/auth（带 SockJS，可能不兼容）
        
        // ... 连接逻辑
    }
    
    // 2. WebSocket 连接成功/失败时
    fun onOpen() {
        Log.d(TAG, "✅ WebSocket 连接已建立")
    }
    
    fun onFailure(t: Throwable) {
        Log.e(TAG, "❌ WebSocket 连接失败: ${t.message}")
        Log.e(TAG, "   异常类型: ${t.javaClass.simpleName}")
        t.printStackTrace()
    }
    
    fun onClosed(code: Int, reason: String) {
        Log.w(TAG, "🔌 WebSocket 连接已关闭: code=$code, reason=$reason")
    }
    
    // 3. 发送 bind 消息时
    fun sendBind(token: String, deviceId: String) {
        Log.d(TAG, "📤 发送 bind 消息")
        Log.d(TAG, "   deviceId: $deviceId")
        // ... 发送逻辑
    }
    
    // 4. 收到 bind_ack 时
    fun onBindAck(userId: Long) {
        Log.d(TAG, "📥 收到 bind_ack, userId=$userId ✅ 绑定成功")
    }
    
    // 5. 收到 dh_init 时
    fun onDhInit(serverPublicKey: String) {
        Log.d(TAG, "📥 收到 dh_init")
        Log.d(TAG, "   服务端公钥前缀: ${serverPublicKey.take(30)}...")
        // ... DH 交换逻辑
    }
    
    // 6. 发送 dh_response 时
    fun sendDhResponse(clientPublicKey: String) {
        Log.d(TAG, "📤 发送 dh_response")
        Log.d(TAG, "   客户端公钥前缀: ${clientPublicKey.take(30)}...")
    }
    
    // 7. 收到 dh_ready 时
    fun onDhReady(cipher: String) {
        Log.d(TAG, "📥 收到 dh_ready, cipher=$cipher ✅ DH 密钥交换完成")
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "🎉 设备现在应该显示为【在线】状态！")
        Log.d(TAG, "═══════════════════════════════════════")
    }
    
    // 8. 收到加密消息时
    fun onEncryptedMessage(iv: String, ciphertext: String) {
        Log.d(TAG, "📥 收到加密消息")
        Log.d(TAG, "   iv 前缀: ${iv.take(16)}...")
        Log.d(TAG, "   密文长度: ${ciphertext.length}")
        
        // 解密后记录内部消息类型
        val decrypted = decrypt(iv, ciphertext)
        Log.d(TAG, "   解密后消息: $decrypted")
    }
    
    // 9. 收到 error 消息时
    fun onError(message: String) {
        Log.e(TAG, "❌ 服务端错误: $message")
    }
    
    // 10. 连接断开时 — 记录会话持续时间
    private var connectTime: Long = 0
    
    fun onConnected() {
        connectTime = System.currentTimeMillis()
    }
    
    fun onDisconnected() {
        val duration = (System.currentTimeMillis() - connectTime) / 1000
        Log.w(TAG, "⚠️ WebSocket 连接已断开，持续了 ${duration}s")
    }
}
```

### 排查步骤

#### 步骤 1：确认 WebSocket 连接地址

```
✅ 正确: ws://your-server.com/ws/app/auth/native
❌ 错误: ws://your-server.com/ws/app/auth（带 SockJS，App 端不兼容）
```

> **关键：** App 端必须使用 `/ws/app/auth/native` 端点！`/ws/app/auth` 端点配置了 SockJS 降级，原生 WebSocket 客户端连接时可能被拒绝。

#### 步骤 2：检查 bind 流程

查看日志中是否有以下顺序的输出：
```
📤 发送 bind 消息
📥 收到 bind_ack, userId=xxx ✅ 绑定成功
```

如果没有 `bind_ack`，说明：
- Token 可能无效（检查 Token 是否过期）
- deviceId 可能未绑定（检查设备是否已通过 REST API 绑定）

#### 步骤 3：检查 DH 密钥交换

查看日志中是否有以下顺序的输出：
```
📥 收到 dh_init
📤 发送 dh_response
📥 收到 dh_ready, cipher=AES-256-GCM ✅ DH 密钥交换完成
🎉 设备现在应该显示为【在线】状态！
```

如果没有 `dh_ready`，说明 DH 交换失败，可能原因：
- 客户端公钥格式不正确
- 共享密钥计算失败

#### 步骤 4：检查连接是否意外断开

如果设备短暂显示在线后立即离线，说明连接被意外关闭：
```
🔌 WebSocket 连接已关闭: code=1006, reason=Connection was closed abnormally
```

常见原因：
- App 端收到加密的 challenge 消息后，解密失败导致异常崩溃
- App 端没有正确处理 `encrypted` 消息类型，导致 JSON 解析异常
- 网络不稳定导致连接断开

#### 步骤 5：使用管理后台调试接口

在浏览器中访问（需登录管理后台）：
```
GET /api/auth/app/test-push/debug/sessions
```

返回示例：
```json
{
  "userId": 1,
  "deviceCount": 2,
  "totalRegisteredSessions": 1,
  "deviceDetails": [
    {
      "deviceId": "device_abc",
      "deviceName": "小米13",
      "online": true,
      "sessionExists": true,
      "sessionId": "ws_session_xxx",
      "sessionOpen": true
    },
    {
      "deviceId": "device_def",
      "deviceName": "华为P60",
      "online": false,
      "sessionExists": false
    }
  ]
}
```

通过此接口可以确认：
- `sessionExists`：WebSocket 会话是否已注册
- `sessionOpen`：会话是否仍处于打开状态
- `totalRegisteredSessions`：当前总共注册了多少个会话

### 常见问题速查表

| 现象 | 可能原因 | 解决方法 |
|------|----------|----------|
| 没有 `bind_ack` | Token 无效或 deviceId 未绑定 | 检查 Token 有效期，确认设备已通过 REST API 绑定 |
| 没有 `dh_ready` | DH 公钥格式错误 | 检查客户端 DH 公钥的编码方式（Base64） |
| 收到 `dh_ready` 后立即断开 | 加密 challenge 处理异常 | 检查解密逻辑，确认能正确处理 `encrypted` 消息 |
| 连接被拒绝（1006） | 使用了带 SockJS 的端点 | 改用 `/ws/app/auth/native` 端点 |
| 设备间歇性在线/离线 | 心跳机制问题 | 实现 ping/pong 心跳保持连接 |
| 设备从未在线 | 根本没有建立 WebSocket 连接 | 检查 App 启动时是否调用了 WebSocket 连接方法 |
