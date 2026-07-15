# App 双因素认证系统 v2 — Android 端新增 API 集成指南

> **版本:** v2.0  
> **更新日期:** 2026-06-14  
> **新增 API:** 加密算法信息、用户个人信息、认证历史  
> **前置文档:** [APP_AUTH_ANDROID_GUIDE.md](./APP_AUTH_ANDROID_GUIDE.md)（v1 基础协议）

---

## 📋 目录

1. [概述](#-概述)
2. [新增 REST API](#-新增-rest-api)
   - [2.1 获取加密算法信息](#21-获取加密算法信息)
   - [2.2 获取用户个人信息](#22-获取用户个人信息)
   - [2.3 获取认证历史](#23-获取认证历史)
3. [Android 端实现](#-android-端实现)
   - [3.1 新增 API 接口定义](#31-新增-api-接口定义)
   - [3.2 加密算法信息展示](#32-加密算法信息展示)
   - [3.3 用户个人信息页面](#33-用户个人信息页面)
   - [3.4 认证历史页面](#34-认证历史页面)
4. [完整对接示例](#-完整对接示例)
5. [错误处理](#-错误处理)

---

## 📖 概述

v2.0 新增了 3 个 REST API，用于增强 App 的用户体验：

| API | 端点 | 用途 |
|-----|------|------|
| 加密算法信息 | `GET /api/auth/app/cipher-info` | 展示当前 WebSocket 连接使用的加密算法 |
| 用户个人信息 | `GET /api/user/profile` | App 端个人资料页面 |
| 认证历史 | `GET /api/auth/app/history` | 查看历史认证记录 |

> ⚠️ **注意：** 这三个 API 的路径前缀与网页端不同：
> - App 端用户信息：`/api/user/profile`（网页端是 `/api/users/profile`）
> - App 端认证相关：`/api/auth/app/*`（与 v1 一致）

---

## 🌐 新增 REST API

### 2.1 获取加密算法信息

返回当前 WebSocket 连接使用的加密算法信息，包括密钥交换算法和数据加密算法。

#### 请求

```
GET /api/auth/app/cipher-info?deviceId=ANDROID_abc123_def456
Authorization: Bearer <JWT_TOKEN>
```

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `deviceId` | Query | 否 | 设备 ID，传入后可获取该设备首选的加密算法 |

#### 响应

```json
{
    "code": 200,
    "data": {
        "keyExchange": {
            "algorithm": "DH",
            "keySize": 2048,
            "group": "MODP Group (RFC 3526)",
            "hash": "SHA-256"
        },
        "dataEncryption": {
            "algorithm": "AES-256-GCM",
            "keySize": 256,
            "ivLength": 12,
            "tagLength": 16,
            "mode": "GCM",
            "padding": "NoPadding"
        },
        "availableAlgorithms": [
            {
                "name": "DH-2048",
                "type": "key_exchange",
                "keySize": 2048,
                "description": "Diffie-Hellman 2048-bit MODP Group (RFC 3526)"
            },
            {
                "name": "AES-256-GCM",
                "type": "data_encryption",
                "keySize": 256,
                "description": "AES-256-GCM 认证加密，12字节IV，16字节GCM Tag"
            },
            {
                "name": "AES-128-GCM",
                "type": "data_encryption",
                "keySize": 128,
                "description": "AES-128-GCM 认证加密，12字节IV，16字节GCM Tag"
            },
            {
                "name": "SM4-GCM",
                "type": "data_encryption",
                "keySize": 128,
                "description": "国密 SM4-GCM 认证加密，12字节IV，16字节GCM Tag"
            }
        ],
        "deviceCipherPref": "AES-256-GCM"
    }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `keyExchange.algorithm` | String | 密钥交换算法名称（当前固定为 `DH`） |
| `keyExchange.keySize` | int | 密钥长度（2048 bit） |
| `keyExchange.group` | String | DH 群组描述 |
| `keyExchange.hash` | String | 密钥派生哈希算法（SHA-256） |
| `dataEncryption.algorithm` | String | 当前使用的数据加密算法 |
| `dataEncryption.keySize` | int | 加密密钥长度（bit） |
| `dataEncryption.ivLength` | int | IV 长度（字节） |
| `dataEncryption.tagLength` | int | GCM 认证标签长度（字节） |
| `dataEncryption.mode` | String | 加密模式 |
| `dataEncryption.padding` | String | 填充方式 |
| `availableAlgorithms[]` | Array | 系统支持的所有加密算法列表 |
| `deviceCipherPref` | String | 设备首选的加密算法（传入 deviceId 时返回） |

#### 用途

- **App 设置页面**：展示当前连接使用的加密算法，增加用户信任感
- **算法协商**：App 可以根据 `availableAlgorithms` 了解服务端支持哪些算法
- **设备偏好**：通过 `deviceCipherPref` 了解当前设备配置的加密偏好

---

### 2.2 获取用户个人信息

返回当前登录用户的个人信息，包括绑定设备信息。

#### 请求

```
GET /api/user/profile
Authorization: Bearer <JWT_TOKEN>
```

> ⚠️ **注意路径：** 此接口路径为 `/api/user/profile`（单数 `user`），**不是**网页端的 `/api/users/profile`（复数 `users`）。

#### 响应

```json
{
    "code": 200,
    "data": {
        "userId": 1,
        "username": "leoadmin",
        "email": "admin@example.com",
        "avatarUrl": "https://leo-blog.top/avatars/default.png",
        "boundAt": "2026-06-12T10:30:00",
        "lastLoginAt": "2026-06-14T08:00:00",
        "deviceCount": 2
    }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | Long | 用户 ID |
| `username` | String | 用户名 |
| `email` | String | 邮箱地址 |
| `avatarUrl` | String | 头像 URL |
| `boundAt` | String (ISO datetime) | 首次绑定 App 设备的时间 |
| `lastLoginAt` | String (ISO datetime) | 最后登录时间 |
| `deviceCount` | Integer | 已绑定的 App 设备数量 |

#### 用途

- **App 个人中心页面**：展示用户基本信息
- **设备管理**：显示绑定时间和设备数量
- **安全信息**：用户可查看自己的账号安全状态

---

### 2.3 获取认证历史

分页查询 App 双因素认证的挑战历史记录。

#### 请求

```
GET /api/auth/app/history?page=1&size=20&status=approved
Authorization: Bearer <JWT_TOKEN>
```

| 参数 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | Query | 否 | 1 | 页码（从 1 开始） |
| `size` | Query | 否 | 20 | 每页条数（最大 50） |
| `status` | Query | 否 | 全部 | 筛选状态：`approved` / `rejected` / `expired` |

#### 响应

```json
{
    "code": 200,
    "data": {
        "records": [
            {
                "id": 2054000000000002001,
                "challengeId": "a1b2c3d4e5f67890abcdef1234567890",
                "deviceId": "ANDROID_abc123_def456",
                "deviceName": "Pixel 7",
                "status": "approved",
                "requestedAt": "2026-06-14T12:00:00",
                "respondedAt": "2026-06-14T12:00:15"
            },
            {
                "id": 2054000000000002002,
                "challengeId": "b2c3d4e5f67890abcdef1234567890a1",
                "deviceId": "ANDROID_abc123_def456",
                "deviceName": "Pixel 7",
                "status": "rejected",
                "requestedAt": "2026-06-14T11:30:00",
                "respondedAt": "2026-06-14T11:30:10"
            }
        ],
        "total": 42,
        "page": 1,
        "size": 20,
        "pages": 3
    }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `records[].id` | Long | 记录 ID |
| `records[].challengeId` | String | 挑战 ID |
| `records[].deviceId` | String | 响应设备 ID |
| `records[].deviceName` | String | 设备名称 |
| `records[].status` | String | 状态：`pending` / `approved` / `rejected` / `expired` |
| `records[].requestedAt` | String | 挑战发起时间 |
| `records[].respondedAt` | String | 用户响应时间（pending 状态时为 null） |
| `total` | Long | 总记录数 |
| `page` | Long | 当前页码 |
| `size` | Long | 每页条数 |
| `pages` | Long | 总页数 |

#### 状态说明

| 状态 | 图标 | 说明 |
|------|------|------|
| `pending` | ⏳ | 等待用户响应（可能已超时） |
| `approved` | ✅ | 用户选择正确，审批通过 |
| `rejected` | ❌ | 用户选择错误，审批拒绝 |
| `expired` | ⏰ | 挑战超时（60 秒未响应） |

#### 用途

- **安全审计**：用户可查看自己的认证历史记录
- **异常检测**：如果发现大量 `rejected` 记录，可能有人尝试暴力破解
- **设备活动追踪**：查看哪些设备在何时发起了认证请求

---

## 📱 Android 端实现

### 3.1 新增 API 接口定义

在 Retrofit API 接口中添加新方法：

```kotlin
// ApiService.kt — 新增接口定义
interface ApiService {

    // ===== 已有接口（v1） =====

    @POST("api/auth/app/bind")
    suspend fun bindWithPassword(@Body request: BindPasswordRequest): Response<ApiResponse<BindData>>

    @POST("api/auth/app/bind/qrcode")
    suspend fun bindWithQrCode(
        @Header("Authorization") token: String,
        @Body request: BindQrCodeRequest
    ): Response<ApiResponse<QrCodeBindData>>

    @POST("api/auth/app/generate-pair-code")
    suspend fun generatePairCode(
        @Header("Authorization") token: String
    ): Response<ApiResponse<PairCodeData>>

    @GET("api/auth/app/devices")
    suspend fun getDevices(
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<AppDevice>>>

    @GET("api/auth/app/status")
    suspend fun checkStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse<BindStatusData>>

    @DELETE("api/auth/app/devices/{deviceId}")
    suspend fun unbindDevice(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<Unit>>

    // ===== 新增接口（v2） =====

    /**
     * 获取加密算法信息
     * @param deviceId 可选，传入后可获取设备首选的加密算法
     */
    @GET("api/auth/app/cipher-info")
    suspend fun getCipherInfo(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String? = null
    ): Response<ApiResponse<CipherInfoResponse>>

    /**
     * 获取用户个人信息
     * 注意路径是 /api/user/profile（单数），不是 /api/users/profile
     */
    @GET("api/user/profile")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): Response<ApiResponse<UserProfileResponse>>

    /**
     * 获取认证历史（分页）
     * @param page 页码（从 1 开始）
     * @param size 每页条数（最大 50）
     * @param status 筛选状态：approved/rejected/expired（可选）
     */
    @GET("api/auth/app/history")
    suspend fun getAuthHistory(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("status") status: String? = null
    ): Response<ApiResponse<AuthHistoryPageData>>
}
```

#### 数据类定义

```kotlin
// ===== 加密算法信息 =====

data class CipherInfoResponse(
    val keyExchange: KeyExchangeInfo,
    val dataEncryption: DataEncryptionInfo,
    val availableAlgorithms: List<AlgorithmInfo>,
    val deviceCipherPref: String?  // 可能为 null
)

data class KeyExchangeInfo(
    val algorithm: String,      // "DH"
    val keySize: Int,           // 2048
    val group: String,          // "MODP Group (RFC 3526)"
    val hash: String            // "SHA-256"
)

data class DataEncryptionInfo(
    val algorithm: String,      // "AES-256-GCM"
    val keySize: Int,           // 256
    val ivLength: Int,          // 12
    val tagLength: Int,         // 16
    val mode: String,           // "GCM"
    val padding: String         // "NoPadding"
)

data class AlgorithmInfo(
    val name: String,           // "DH-2048"
    val type: String,           // "key_exchange" / "data_encryption"
    val keySize: Int,           // 2048
    val description: String     // 算法描述
)

// ===== 用户个人信息 =====

data class UserProfileResponse(
    val userId: Long,
    val username: String,
    val email: String,
    val avatarUrl: String?,
    val boundAt: String?,       // ISO datetime 或 null
    val lastLoginAt: String?,   // ISO datetime 或 null
    val deviceCount: Int
)

// ===== 认证历史 =====

data class AuthHistoryPageData(
    val records: List<AuthHistoryRecord>,
    val total: Long,
    val page: Long,
    val size: Long,
    val pages: Long
)

data class AuthHistoryRecord(
    val id: Long,
    val challengeId: String,
    val deviceId: String?,
    val deviceName: String?,
    val status: String,         // "pending" / "approved" / "rejected" / "expired"
    val requestedAt: String,    // ISO datetime
    val respondedAt: String?    // ISO datetime 或 null
)

// ===== 通用响应包装 =====

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)
```

---

### 3.2 加密算法信息展示

在 App 设置页面或安全页面展示加密算法信息。

```kotlin
// SecurityInfoFragment.kt — 安全信息页面
class SecurityInfoFragment : Fragment() {

    private lateinit var binding: FragmentSecurityInfoBinding
    private val apiService: ApiService by lazy {
        RetrofitClient.createService()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSecurityInfoBinding.bind(view)
        loadCipherInfo()
    }

    private fun loadCipherInfo() {
        val token = AuthManager.getToken(requireContext()) ?: return
        val deviceId = AuthManager.getDeviceId(requireContext())

        lifecycleScope.launch {
            try {
                val response = apiService.getCipherInfo("Bearer $token", deviceId)
                if (response.isSuccessful && response.body()?.data != null) {
                    displayCipherInfo(response.body()!!.data!!)
                }
            } catch (e: Exception) {
                Log.e("SecurityInfo", "获取加密算法信息失败", e)
            }
        }
    }

    private fun displayCipherInfo(info: CipherInfoResponse) {
        // 密钥交换算法
        binding.tvKeyExchange.text = """
            算法: ${info.keyExchange.algorithm}
            密钥长度: ${info.keyExchange.keySize} bit
            群组: ${info.keyExchange.group}
            哈希: ${info.keyExchange.hash}
        """.trimIndent()

        // 数据加密算法
        binding.tvDataEncryption.text = """
            算法: ${info.dataEncryption.algorithm}
            密钥长度: ${info.dataEncryption.keySize} bit
            IV 长度: ${info.dataEncryption.ivLength} 字节
            Tag 长度: ${info.dataEncryption.tagLength} 字节
            模式: ${info.dataEncryption.mode}
            填充: ${info.dataEncryption.padding}
        """.trimIndent()

        // 可用算法列表
        binding.rvAvailableAlgorithms.adapter = AlgorithmAdapter(info.availableAlgorithms)

        // 设备首选算法
        info.deviceCipherPref?.let { pref ->
            binding.tvDeviceCipherPref.text = "设备首选: $pref"
            binding.tvDeviceCipherPref.visibility = View.VISIBLE
        }
    }
}
```

#### 布局文件示例

```xml
<!-- fragment_security_info.xml -->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- 密钥交换算法 -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="🔑 密钥交换算法"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvKeyExchange"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:background="@drawable/bg_info_card"
            android:padding="12dp"
            android:textSize="14sp" />

        <!-- 数据加密算法 -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="🔒 数据加密算法"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvDataEncryption"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:background="@drawable/bg_info_card"
            android:padding="12dp"
            android:textSize="14sp" />

        <!-- 可用算法列表 -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="📋 可用算法"
            android:textSize="18sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvAvailableAlgorithms"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp" />

        <!-- 设备首选算法 -->
        <TextView
            android:id="@+id/tvDeviceCipherPref"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:padding="12dp"
            android:textSize="14sp"
            android:visibility="gone" />

    </LinearLayout>
</ScrollView>
```

---

### 3.3 用户个人信息页面

```kotlin
// ProfileFragment.kt — 个人中心页面
class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private val apiService: ApiService by lazy {
        RetrofitClient.createService()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentProfileBinding.bind(view)
        loadProfile()
    }

    private fun loadProfile() {
        val token = AuthManager.getToken(requireContext()) ?: return

        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            try {
                val response = apiService.getUserProfile("Bearer $token")
                if (response.isSuccessful && response.body()?.data != null) {
                    displayProfile(response.body()!!.data!!)
                }
            } catch (e: Exception) {
                Log.e("Profile", "获取用户信息失败", e)
                Snackbar.make(binding.root, "加载失败，请检查网络", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun displayProfile(profile: UserProfileResponse) {
        // 使用 Glide 或 Coil 加载头像
        // Glide.with(this).load(profile.avatarUrl).into(binding.ivAvatar)

        binding.tvUsername.text = profile.username
        binding.tvEmail.text = profile.email

        // 格式化时间
        profile.boundAt?.let { boundAt ->
            binding.tvBoundAt.text = "绑定时间: ${formatDateTime(boundAt)}"
        } ?: run {
            binding.tvBoundAt.text = "尚未绑定设备"
        }

        profile.lastLoginAt?.let { lastLogin ->
            binding.tvLastLogin.text = "最后登录: ${formatDateTime(lastLogin)}"
        }

        binding.tvDeviceCount.text = "已绑定 ${profile.deviceCount} 台设备"
    }

    private fun formatDateTime(isoDateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(isoDateTime)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            isoDateTime // 解析失败则原样显示
        }
    }
}
```

---

### 3.4 认证历史页面

```kotlin
// AuthHistoryFragment.kt — 认证历史页面
class AuthHistoryFragment : Fragment() {

    private lateinit var binding: FragmentAuthHistoryBinding
    private val apiService: ApiService by lazy {
        RetrofitClient.createService()
    }

    private val historyAdapter = AuthHistoryAdapter()
    private var currentPage = 1
    private var totalPages = 1
    private var currentStatus: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAuthHistoryBinding.bind(view)

        setupRecyclerView()
        setupFilterChips()
        loadHistory()
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        // 加载更多（分页）
        binding.rvHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (lastVisibleItem >= historyAdapter.itemCount - 3
                    && currentPage < totalPages) {
                    currentPage++
                    loadHistory()
                }
            }
        })
    }

    private fun setupFilterChips() {
        // 使用 ChipGroup 实现状态筛选
        binding.chipAll.setOnClickListener { filterBy(null) }
        binding.chipApproved.setOnClickListener { filterBy("approved") }
        binding.chipRejected.setOnClickListener { filterBy("rejected") }
        binding.chipExpired.setOnClickListener { filterBy("expired") }
    }

    private fun filterBy(status: String?) {
        currentStatus = status
        currentPage = 1
        historyAdapter.clear()
        loadHistory()
    }

    private fun loadHistory() {
        val token = AuthManager.getToken(requireContext()) ?: return

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getAuthHistory(
                    "Bearer $token",
                    page = currentPage,
                    size = 20,
                    status = currentStatus
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    val pageData = response.body()!!.data!!
                    totalPages = pageData.pages.toInt()
                    historyAdapter.addAll(pageData.records)
                    binding.tvEmpty.visibility =
                        if (historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Log.e("AuthHistory", "获取认证历史失败", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}

// AuthHistoryAdapter.kt — 认证历史列表适配器
class AuthHistoryAdapter : RecyclerView.Adapter<AuthHistoryAdapter.ViewHolder>() {

    private val records = mutableListOf<AuthHistoryRecord>()

    fun clear() {
        records.clear()
        notifyDataSetChanged()
    }

    fun addAll(newRecords: List<AuthHistoryRecord>) {
        val startPos = records.size
        records.addAll(newRecords)
        notifyItemRangeInserted(startPos, newRecords.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_auth_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
        private val tvDeviceName = itemView.findViewById<TextView>(R.id.tvDeviceName)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val tvChallengeId = itemView.findViewById<TextView>(R.id.tvChallengeId)

        fun bind(record: AuthHistoryRecord) {
            // 状态图标和颜色
            val (icon, color) = when (record.status) {
                "approved" -> "✅" to Color.GREEN
                "rejected" -> "❌" to Color.RED
                "expired" -> "⏰" to Color.GRAY
                "pending" -> "⏳" to Color.parseColor("#FFA500") // 橙色
                else -> "❓" to Color.GRAY
            }
            tvStatus.text = "$icon ${record.status.uppercase()}"
            tvStatus.setTextColor(color)

            tvDeviceName.text = record.deviceName ?: record.deviceId ?: "未知设备"
            tvTime.text = formatTime(record.requestedAt)
            tvChallengeId.text = "ID: ${record.challengeId.take(16)}..."
        }

        private fun formatTime(isoDateTime: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(isoDateTime)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                isoDateTime
            }
        }
    }
}
```

#### 列表项布局

```xml
<!-- item_auth_history.xml -->
<androidx.cardview.widget.CardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="4dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvDeviceName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/tvTime"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="12sp"
            android:textColor="@android:color/darker_gray" />

        <TextView
            android:id="@+id/tvChallengeId"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="11sp"
            android:textColor="@android:color/darker_gray" />

    </LinearLayout>
</androidx.cardview.widget.CardView>
```

---

## 📱 完整对接示例

### 统一数据加载管理器

```kotlin
// AppDataManager.kt — 统一管理 App 数据加载
class AppDataManager(
    private val context: Context,
    private val apiService: ApiService
) {
    /**
     * 加载所有 App 首页数据（并行请求）
     */
    suspend fun loadHomeData(): HomeData {
        val token = AuthManager.getToken(context) ?: throw AuthException("未登录")

        // 并行请求三个 API
        val profileDeferred = async {
            apiService.getUserProfile("Bearer $token")
        }
        val cipherInfoDeferred = async {
            val deviceId = AuthManager.getDeviceId(context)
            apiService.getCipherInfo("Bearer $token", deviceId)
        }
        val historyDeferred = async {
            apiService.getAuthHistory("Bearer $token", page = 1, size = 5)
        }

        return HomeData(
            profile = profileDeferred.await().body()?.data,
            cipherInfo = cipherInfoDeferred.await().body()?.data,
            recentHistory = historyDeferred.await().body()?.data?.records ?: emptyList()
        )
    }
}

data class HomeData(
    val profile: UserProfileResponse?,
    val cipherInfo: CipherInfoResponse?,
    val recentHistory: List<AuthHistoryRecord>
)
```

### 使用示例

```kotlin
// MainActivity.kt — 主页面
class MainActivity : AppCompatActivity() {

    private val dataManager by lazy {
        AppDataManager(this, RetrofitClient.createService())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 加载首页数据
        lifecycleScope.launch {
            try {
                val homeData = dataManager.loadHomeData()
                updateUI(homeData)
            } catch (e: AuthException) {
                // Token 过期，跳转到登录页
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } catch (e: Exception) {
                Log.e("MainActivity", "加载数据失败", e)
            }
        }
    }

    private fun updateUI(homeData: HomeData) {
        // 更新个人资料卡片
        homeData.profile?.let { profile ->
            findViewById<TextView>(R.id.tvUsername).text = profile.username
            findViewById<TextView>(R.id.tvDeviceCount).text = "${profile.deviceCount} 台设备"
        }

        // 更新加密信息卡片
        homeData.cipherInfo?.let { cipher ->
            findViewById<TextView>(R.id.tvCipherInfo).text =
                "🔒 ${cipher.keyExchange.algorithm}-${cipher.keyExchange.keySize} | " +
                "🔑 ${cipher.dataEncryption.algorithm}"
        }

        // 更新最近认证记录
        // ...
    }
}
```

---

## ⚠️ 错误处理

### 通用错误码

| HTTP 状态码 | 说明 | App 端处理 |
|------------|------|-----------|
| 200 | 成功 | 正常解析 data |
| 400 | 参数错误 | 检查请求参数 |
| 401 | 未授权 / Token 过期 | 跳转到登录页重新获取 Token |
| 403 | 无权限 | 提示用户无权限 |
| 429 | 请求过于频繁 | 显示"操作过于频繁，请稍后重试" |
| 500 | 服务端错误 | 显示"服务器繁忙，请稍后重试" |

### Token 过期处理

```kotlin
// RetrofitClient.kt — 添加 Token 过期自动拦截
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if (response.code == 401) {
            // Token 过期，清除本地 Token
            AuthManager.clearToken(context)

            // 发送广播通知所有页面跳转到登录页
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
        return response
    }
}
```

### 网络错误处理

```kotlin
// 在 ViewModel 或 Fragment 中统一处理网络错误
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// 使用示例
val profileState: StateFlow<UiState<UserProfileResponse>> = flow {
    emit(UiState.Loading)
    try {
        val response = apiService.getUserProfile("Bearer $token")
        if (response.isSuccessful && response.body()?.data != null) {
            emit(UiState.Success(response.body()!!.data!!))
        } else {
            emit(UiState.Error("获取用户信息失败"))
        }
    } catch (e: IOException) {
        emit(UiState.Error("网络连接失败，请检查网络"))
    } catch (e: Exception) {
        emit(UiState.Error("未知错误: ${e.message}"))
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
```

---

## 📚 参考

- [APP_AUTH_ANDROID_GUIDE.md](./APP_AUTH_ANDROID_GUIDE.md) — v1 基础协议（WebSocket、DH、AES-GCM）
- [APP_AUTH_API_REFERENCE.md](./APP_AUTH_API_REFERENCE.md) — 完整 API 参考
- [BACKEND_API_GUIDE.md](../frontend/AuthAPP/BACKEND_API_GUIDE.md) —