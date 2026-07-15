# App 设备特征码绑定指南

> **版本:** v2.0  
> **更新日期:** 2026-06-19  
> **适用:** Android App 端开发  
> **关联文档:** 
> - [APP_AUTH_ANDROID_GUIDE_V2.md](./APP_AUTH_ANDROID_GUIDE_V2.md)（完整认证协议）
> - [APP_DEVICE_SECRET_GUIDE.md](./APP_DEVICE_SECRET_GUIDE.md)（设备码重构指南）


---

## 问题背景

之前的实现中，App 每次登录都会生成一个新的 `deviceId`，导致：
- 数据库中产生大量重复设备记录
- 旧设备无法收到推送挑战
- 管理后台看到的设备列表混乱

**根本原因：** `deviceId` 必须是设备的**稳定唯一标识**，在同一台物理设备上永远不变。

---

## 核心规则

| 规则 | 说明 |
|------|------|
| **稳定不变** | 同一台物理设备，无论重启、重装、清除缓存，`deviceId` 必须相同 |
| **全局唯一** | 不同物理设备的 `deviceId` 不能相同 |
| **首次生成，永久存储** | 首次安装时生成一次，存入持久化存储，后续永远复用 |
| **不可猜测** | 使用安全的随机生成方式，防止伪造 |

---

## deviceId 生成方案

### 推荐方案：ANDROID_ID + 应用标识

```
deviceId = "ANDROID_" + MD5(ANDROID_ID + PACKAGE_NAME) + "_" + RANDOM_HEX
```

**步骤：**

1. 获取 `Settings.Secure.ANDROID_ID`（设备级唯一标识，恢复出厂才变）
2. 拼接应用包名，做 MD5 哈希（防止跨应用碰撞）
3. 追加随机后缀（首次生成时）
4. 存入 `SharedPreferences`（MODE_PRIVATE）

### Android 实现代码

```kotlin
// DeviceIdManager.kt

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object DeviceIdManager {

    private const val PREF_NAME = "device_id_prefs"
    private const val KEY_DEVICE_ID = "stable_device_id"

    /**
     * 获取稳定的设备 ID
     * 
     * 优先从 SharedPreferences 读取已保存的 ID。
     * 如果不存在（首次安装），则生成并持久化。
     * 
     * 返回格式示例: "ANDROID_a3f8b2c1d4e5_5237ba0e"
     */
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 1. 优先读取已存储的 deviceId
        val existingId = prefs.getString(KEY_DEVICE_ID, null)
        if (!existingId.isNullOrBlank()) {
            return existingId
        }

        // 2. 首次安装：生成新的 deviceId
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN"

        val packageName = context.packageName
        
        // MD5(ANDROID_ID + 包名) 取前 12 位
        val hash = md5(androidId + packageName).substring(0, 12)
        
        // 随机后缀 8 位
        val randomSuffix = UUID.randomUUID().toString()
            .replace("-", "")
            .substring(0, 8)

        val deviceId = "ANDROID_${hash}_${randomSuffix}"

        // 3. 持久化存储（关键！）
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()

        return deviceId
    }

    /**
     * 获取设备名称（用于显示）
     * 
     * 返回格式示例: "Pixel 7 Pro" 或 "Samsung Galaxy S24"
     */
    fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

### 调用时机

```kotlin
// 在 Application.onCreate() 或首次 Activity 中初始化
class MyApplication : Application() {
    
    lateinit var deviceId: String
        private set
    
    override fun onCreate() {
        super.onCreate()
        // 应用启动时获取并缓存 deviceId
        deviceId = DeviceIdManager.getOrCreateDeviceId(this)
    }
}
```

---

## 绑定流程

### 场景 1：首次绑定（账号密码方式）

```kotlin
// 用户输入用户名和密码后
suspend fun login(username: String, password: String) {
    val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
    val deviceName = DeviceIdManager.getDeviceName()

    val response = apiClient.post("/api/auth/app/bind/password") {
        jsonBody {
            "username" to username
            "password" to password
            "deviceId" to deviceId       // 稳定设备特征码
            "deviceName" to deviceName   // "Pixel 7 Pro"
            "deviceType" to "ANDROID"
        }
    }

    // 保存返回的 token
    val token = response.data.getString("token")
    val confirmedDeviceId = response.data.getString("deviceId")
    
    // 持久化 token（用于 WebSocket 连接）
    TokenManager.saveToken(token)
}
```

### 场景 2：重新登录（同一设备）

```kotlin
// 用户重新登录（退出后再登录，或 App 数据被清除后重新安装）
suspend fun login(username: String, password: String) {
    val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
    // ↑ 即使 App 数据被清除，如果 ANDROID_ID 未变，
    //   新生成的 deviceId 可能不同。
    //   但后端已支持幂等绑定：
    //   - 同一用户 + 同一 deviceId → 更新已有记录，返回新 Token
    //   - 同一用户 + 新 deviceId → 创建新设备记录
    //   所以两种情况都能正常工作。

    val response = apiClient.post("/api/auth/app/bind/password") { ... }
    // 后端会自动识别已有设备并更新，不会创建重复记录
}
```

### 场景 3：扫码绑定

```kotlin
// App 端扫描或输入配对码
suspend fun bindWithPairCode(pairCode: String) {
    val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
    val deviceName = DeviceIdManager.getDeviceName()

    val response = apiClient.post("/api/auth/app/bind/qrcode") {
        jsonBody {
            "pairCode" to pairCode
            "deviceId" to deviceId
            "deviceName" to deviceName
        }
    }
}
```

### 场景 4：WebSocket 连接

```kotlin
// 使用已绑定的 deviceId 建立 WebSocket 连接
fun connectWebSocket() {
    val deviceId = DeviceIdManager.getOrCreateDeviceId(context)
    val token = TokenManager.getToken()

    val wsUrl = "wss://your-server.com/ws/app/auth/native"
    webSocket = OkHttpClient().newWebSocket(...)
    
    // 连接成功后发送 bind 消息
    val bindMsg = JSONObject().apply {
        put("type", "bind")
        put("token", token)
        put("deviceId", deviceId)  // 必须与绑定时使用的 deviceId 一致
    }
    webSocket.send(bindMsg.toString())
}
```

---

## 后端行为说明

### 幂等绑定

后端已支持幂等绑定，行为如下：

| 场景 | 后端行为 |
|------|----------|
| 同一用户 + 同一 deviceId + 首次绑定 | 创建设备记录，返回 Token |
| 同一用户 + 同一 deviceId + 重复绑定 | **更新设备名称和时间**，返回新 Token |
| 同一用户 + 新 deviceId | 创建新设备记录（上限 5 台） |
| 不同用户 + 已被绑定的 deviceId | **拒绝**：该设备已被其他账号绑定 |
| 同一用户已有 5 台设备 + 新 deviceId | **拒绝**：已达到最大绑定设备数 |

### 错误码

| HTTP 状态码 | 错误信息 | 处理方式 |
|-------------|---------|---------|
| 200 | 绑定成功 | 保存 token |
| 400 | "该设备已被其他账号绑定，请先解绑" | 提示用户先在其他账号解绑 |
| 400 | "已达到最大绑定设备数（5），请先解绑旧设备" | 引导用户在管理后台解绑旧设备 |
| 400 | "用户名或密码错误" | 提示重新输入 |
| 400 | "绑定尝试次数过多，请 X 秒后重试" | 等待后重试 |

---

## 持久化存储方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| `SharedPreferences` | 简单易用，App 级持久化 | 清除应用数据会丢失 | ★★★★ 推荐 |
| `Settings.Secure.ANDROID_ID` | 恢复出厂才变 | 某些厂商设备可能相同 | ★★★ 作为哈希输入 |
| `MediaDrm` (Widevine) | 即使清除数据也不变 | API 复杂，需要额外权限 | ★★★★★ 最佳 |
| `UUID.randomUUID()` 每次生成 | 简单 | **每次不同，导致重复设备** | ❌ 禁止 |

### 最佳实践：SharedPreferences + ANDROID_ID 组合

```kotlin
// 推荐方案：即使 SharedPreferences 被清除，
// 也能通过 ANDROID_ID 重新生成相同的 hash 前缀

fun getOrCreateDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences("device_id_prefs", Context.MODE_PRIVATE)
    
    // 优先读缓存
    prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
    
    // 缓存被清除了，但 ANDROID_ID 还在
    // 通过 ANDROID_ID 重新生成 hash 部分（前缀不变）
    // 但随机后缀会变 → 后端视为新设备 → 创建新记录
    // 这是可接受的行为（用户清除了 App 数据 ≈ 视为新安装）
    val androidId = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: "UNKNOWN"
    
    val hash = md5(androidId + context.packageName).substring(0, 12)
    val randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
    val deviceId = "ANDROID_${hash}_${randomSuffix}"
    
    prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    return deviceId
}
```

> **注意：** 如果用户清除了 App 数据，随机后缀会变，后端会创建新设备记录。但由于后端已支持幂等绑定，旧设备会自动过期。如需更严格的方案，使用 `MediaDrm` 获取硬件级不可变 ID。

---

## deviceId 格式规范

| 部分 | 说明 | 示例 |
|------|------|------|
| 前缀 | 固定 `ANDROID_` | `ANDROID_` |
| Hash | `MD5(ANDROID_ID + PACKAGE_NAME)` 前 12 位 | `a3f8b2c1d4e5` |
| 分隔符 | `_` | `_` |
| 随机后缀 | UUID 前 8 位 hex | `5237ba0e` |
| **完整示例** | | `ANDROID_a3f8b2c1d4e5_5237ba0e` |

**长度：** 固定 27 个字符  
**字符集：** `ANDROID_` + 小写 hex + `_` + 小写 hex

---

## 注意事项

### 必须遵守

1. **首次生成后永久存储** — 使用 `SharedPreferences` 或更持久的方案
2. **不要每次登录重新生成** — `deviceId` 必须跨登录保持不变
3. **不要在内存中生成** — App 被杀死后内存数据丢失
4. **绑定和连接使用同一个 deviceId** — `bind` API 和 WebSocket `bind` 消息必须一致

### 常见错误

| 错误做法 | 后果 | 正确做法 |
|----------|------|----------|
| `UUID.randomUUID()` 每次生成 | 每次登录创建新设备 | 首次生成后存入 SharedPreferences |
| 只在内存中保存 | App 重启后 deviceId 变化 | 持久化到磁盘 |
| 使用 `Build.SERIAL` | Android 10+ 已不可访问 | 使用 `ANDROID_ID` |
| 使用 IMEI | 需要权限且 Android 10+ 受限 | 使用 `ANDROID_ID` |
| 硬编码固定值 | 所有设备 ID 相同 | 基于设备特征生成 |

### 测试验证

```kotlin
// 验证 deviceId 稳定性
@Test
fun testDeviceIdStability() {
    val id1 = DeviceIdManager.getOrCreateDeviceId(context)
    val id2 = DeviceIdManager.getOrCreateDeviceId(context)
    assertEquals(id1, id2, "deviceId 必须在同一设备上保持不变")
    
    // 验证格式
    assertTrue(id1.startsWith("ANDROID_"))
    assertEquals(27, id1.length)
    assertTrue(id1.matches(Regex("ANDROID_[0-9a-f]{12}_[0-9a-f]{8}")))
}
```

---

## 完整登录时序图

```
App                              后端
 |                                |
 |-- 1. 获取 deviceId ------------|
 |   (从 SharedPreferences 读取   |
 |    或首次生成并存储)            |
 |                                |
 |-- POST /bind/password -------->|
 |   {                            |
 |     username: "user",          |
 |     password: "pass",          |
 |     deviceId: "ANDROID_xxx",   |  ← 稳定设备特征码
 |     deviceName: "Pixel 7"      |
 |   }                            |
 |                                |
 |   后端检查：                    |
 |   - deviceId 已属于该用户？     |
 |     → 更新设备信息，返回 Token   |  ← 幂等：不创建重复设备
 |   - deviceId 不存在？           |
 |     → 创建新设备记录             |
 |   - deviceId 属于其他用户？      |
 |     → 拒绝                      |
 |                                |
 |<-- { token, deviceId } --------|
 |                                |
 |-- WebSocket connect ---------->|
 |   { type: "bind",              |
 |     token: "jwt...",           |
 |     deviceId: "ANDROID_xxx" }  |  ← 同一个 deviceId
 |                                |
 |<-- { type: "bind_ack" } -------|
 |<-- { type: "dh_init" } -------|
 |   ... DH 密钥交换 ...          |
 |<-- { type: "challenge" } -----|
 |   ... 三选一响应 ...            |
 |<-- { type: "auth_result" } ----|
```
