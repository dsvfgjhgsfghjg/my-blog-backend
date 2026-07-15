# App 管理后台 Dashboard 页面开发指南

## 概述

本文档指导 Android 开发者在 MyAuthenticator App 中实现管理后台 Dashboard（仪表盘）页面。

Dashboard 页面用于展示博客后台的核心运营数据，包括：
- 登录信息（当前 IP、上次登录时间、登录次数）
- 统计卡片（用户数、文章数、今日访问量 + 百分比变化）
- 快捷操作（写文章、文章管理、评论管理等）
- 最近动态（发布文章、注册用户等操作记录）

---

## 前置条件

### 1. 权限要求

只有 **管理员用户** 才能查看 Dashboard 数据。权限矩阵：

| 用户类型 | 行为 |
|---------|------|
| 非管理员 | 提示"权限不足，需要管理员权限" |
| 超级管理员 | 显示完整 Dashboard |
| 管理员（有管理能力） | 显示完整 Dashboard |
| 管理员（无能力） | 提示"请联系超级管理员分配权限" |

### 2. 网络要求

- WebSocket 连接已建立
- DH 密钥交换已完成（`dh_ready` 已收到）
- 加密通道已就绪

---

## API 参考

### 请求

通过加密 WebSocket 通道发送：

```
消息类型: admin_dashboard_request（加密通道内）
外层包装: {"type":"encrypted","iv":"...","ciphertext":"..."}
内部明文: {"type":"admin_dashboard_request"}
```

### 响应

响应通过加密通道返回，解密后得到内部消息。

#### 成功响应（status=ok）

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

#### 无能力响应（status=no_ability）

```json
{
  "type": "admin_dashboard_response",
  "status": "no_ability",
  "message": "请联系超级管理员分配权限"
}
```

---

## Android 实现步骤

### 第一步：创建 Dashboard 数据模型

在 `model/` 包下创建 `DashboardModels.kt`：

```kotlin
package top.leoblog.myauthenticator.model

/**
 * 管理后台仪表盘数据模型
 */
data class AdminDashboardData(
    val loginInfo: LoginInfo,
    val statsCards: StatsCards,
    val lastUpdateTime: String,
    val quickActions: List<QuickAction>,
    val recentActivities: List<RecentActivity>
)

data class LoginInfo(
    val currentIp: String,
    val lastLoginIp: String,
    val lastLoginTime: String?,
    val loginCount: Long
)

data class StatsCards(
    val totalUsers: TotalCount,
    val totalPosts: TotalCount,
    val todayVisits: TotalCount
)

data class TotalCount(
    val current: Long,
    val percentageChange: Double,
    val trend: String  // "up", "down", "flat"
)

data class QuickAction(
    val action: String,
    val label: String,
    val icon: String,
    val route: String
)

data class RecentActivity(
    val id: Long,
    val type: String,
    val content: String,
    val time: String,
    val username: String?
)
```

### 第二步：在 WebSocket 客户端中请求数据

`AppWebSocketClient.kt` 中已提供 `requestAdminDashboard()` 方法，直接调用即可：

```kotlin
// 在加密通道就绪后调用
webSocketClient.requestAdminDashboard()
```

### 第三步：处理响应回调

在 `WebSocketListenerCallback` 中实现 `onAdminDashboardResponse`：

```kotlin
override fun onAdminDashboardResponse(data: JsonObject) {
    val status = data.get("status")?.asString
    
    when (status) {
        "ok" -> {
            val dashboardData = data.getAsJsonObject("data")
            // 解析数据
            val dashboard = parseDashboard(dashboardData)
            // 更新 UI
            runOnUiThread {
                viewModel.onDashboardLoaded(dashboard)
            }
        }
        "no_ability" -> {
            val message = data.get("message")?.asString ?: "请联系超级管理员分配权限"
            runOnUiThread {
                viewModel.onNoAbility(message)
            }
        }
    }
}

private fun parseDashboard(data: JsonObject): AdminDashboardData {
    val gson = Gson()
    return gson.fromJson(data, AdminDashboardData::class.java)
}
```

### 第四步：创建 Dashboard ViewModel

```kotlin
package top.leoblog.myauthenticator.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import top.leoblog.myauthenticator.model.AdminDashboardData
import top.leoblog.myauthenticator.network.AppWebSocketClient

class DashboardViewModel : ViewModel() {
    
    private val _dashboardData = MutableLiveData<AdminDashboardData>()
    val dashboardData: LiveData<AdminDashboardData> = _dashboardData
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _noAbilityMessage = MutableLiveData<String>()
    val noAbilityMessage: LiveData<String> = _noAbilityMessage
    
    fun onDashboardLoaded(data: AdminDashboardData) {
        _isLoading.value = false
        _dashboardData.value = data
    }
    
    fun onNoAbility(message: String) {
        _isLoading.value = false
        _noAbilityMessage.value = message
    }
    
    fun onError(message: String) {
        _isLoading.value = false
        _errorMessage.value = message
    }
    
    fun loadDashboard(webSocketClient: AppWebSocketClient) {
        _isLoading.value = true
        webSocketClient.requestAdminDashboard()
    }
}
```

### 第五步：创建 Dashboard UI 页面

```kotlin
package top.leoblog.myauthenticator.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import top.leoblog.myauthenticator.databinding.FragmentDashboardBinding
import top.leoblog.myauthenticator.network.AppWebSocketClient

class DashboardFragment : Fragment() {
    
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: DashboardViewModel
    private lateinit var webSocketClient: AppWebSocketClient
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        
        // 观察数据
        viewModel.dashboardData.observe(viewLifecycleOwner) { data ->
            bindDashboardData(data)
        }
        
        viewModel.noAbilityMessage.observe(viewLifecycleOwner) { message ->
            binding.noAbilityLayout.visibility = View.VISIBLE
            binding.noAbilityMessage.text = message
            binding.dashboardContent.visibility = View.GONE
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            binding.errorText.text = message
            binding.errorLayout.visibility = View.VISIBLE
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }
        
        // 加载数据
        viewModel.loadDashboard(webSocketClient)
    }
    
    private fun bindDashboardData(data: AdminDashboardData) {
        binding.dashboardContent.visibility = View.VISIBLE
        binding.noAbilityLayout.visibility = View.GONE
        
        // 登录信息
        binding.lastLoginTime.text = "上次登录: ${data.loginInfo.lastLoginTime ?: "未知"}"
        binding.loginCount.text = "登录次数: ${data.loginInfo.loginCount}"
        
        // 统计卡片
        binding.totalUsers.text = "${data.statsCards.totalUsers.current}"
        binding.totalUsersChange.text = formatChange(data.statsCards.totalUsers)
        
        binding.totalPosts.text = "${data.statsCards.totalPosts.current}"
        binding.totalPostsChange.text = formatChange(data.statsCards.totalPosts)
        
        binding.todayVisits.text = "${data.statsCards.todayVisits.current}"
        binding.todayVisitsChange.text = formatChange(data.statsCards.todayVisits)
        
        // 快捷操作
        binding.quickActionsRecyclerView.adapter = QuickActionAdapter(data.quickActions)
        
        // 最近动态
        binding.recentActivitiesRecyclerView.adapter = RecentActivityAdapter(data.recentActivities)
    }
    
    private fun formatChange(count: TotalCount): String {
        val arrow = when (count.trend) {
            "up" -> "↑"
            "down" -> "↓"
            else -> "→"
        }
        return "$arrow ${count.percentageChange}%"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 第六步：创建布局文件

`res/layout/fragment_dashboard.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- 加载指示器 -->
        <ProgressBar
            android:id="@+id/loading_indicator"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:visibility="gone" />

        <!-- 无能力提示 -->
        <LinearLayout
            android:id="@+id/no_ability_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="32dp"
            android:visibility="gone">

            <TextView
                android:id="@+id/no_ability_message"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textColor="#666" />
        </LinearLayout>

        <!-- 错误提示 -->
        <LinearLayout
            android:id="@+id/error_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="32dp"
            android:visibility="gone">

            <TextView
                android:id="@+id/error_text"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textColor="#f44336" />
        </LinearLayout>

        <!-- Dashboard 内容 -->
        <LinearLayout
            android:id="@+id/dashboard_content"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">

            <!-- 登录信息卡片 -->
            <CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:cardCornerRadius="8dp"
                app:cardElevation="2dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="登录信息"
                        android:textSize="18sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/last_login_time"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/login_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textSize="14sp" />
                </LinearLayout>
            </CardView>

            <!-- 统计卡片 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:orientation="horizontal"
                android:weightSum="3">

                <!-- 用户数 -->
                <CardView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginEnd="4dp"
                    app:cardCornerRadius="8dp"
                    app:cardElevation="2dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="12dp"
                        android:gravity="center">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="用户"
                            android:textSize="12sp"
                            android:textColor="#666" />

                        <TextView
                            android:id="@+id/total_users"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="24sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/total_users_change"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="12sp" />
                    </LinearLayout>
                </CardView>

                <!-- 文章数 -->
                <CardView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginHorizontal="4dp"
                    app:cardCornerRadius="8dp"
                    app:cardElevation="2dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="12dp"
                        android:gravity="center">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="文章"
                            android:textSize="12sp"
                            android:textColor="#666" />

                        <TextView
                            android:id="@+id/total_posts"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="24sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/total_posts_change"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="12sp" />
                    </LinearLayout>
                </CardView>

                <!-- 今日访问 -->
                <CardView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="4dp"
                    app:cardCornerRadius="8dp"
                    app:cardElevation="2dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="12dp"
                        android:gravity="center">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="今日"
                            android:textSize="12sp"
                            android:textColor="#666" />

                        <TextView
                            android:id="@+id/today_visits"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="24sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/today_visits_change"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textSize="12sp" />
                    </LinearLayout>
                </CardView>
            </LinearLayout>

            <!-- 快捷操作 -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="快捷操作"
                android:textSize="18sp"
                android:textStyle="bold" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/quick_actions_recycler_view"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                android:nestedScrollingEnabled="false" />

            <!-- 最近动态 -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="最近动态"
                android:textSize="18sp"
                android:textStyle="bold" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/recent_activities_recycler_view"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                android:nestedScrollingEnabled="false" />

        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

### 第七步：创建 Adapter

```kotlin
// QuickActionAdapter.kt
package top.leoblog.myauthenticator.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import top.leoblog.myauthenticator.databinding.ItemQuickActionBinding
import top.leoblog.myauthenticator.model.QuickAction

class QuickActionAdapter(
    private val actions: List<QuickAction>
) : RecyclerView.Adapter<QuickActionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuickActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(actions[position])
    }

    override fun getItemCount() = actions.size

    class ViewHolder(private val binding: ItemQuickActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(action: QuickAction) {
            binding.actionLabel.text = action.label
        }
    }
}
```

---

## 完整调用流程

```
App 启动
  ↓
WebSocket 连接建立
  ↓
bind → bind_ack → dh_init → dh_response → dh_ready
  ↓
加密通道就绪
  ↓
用户进入 Dashboard 页面
  ↓
viewModel.loadDashboard(webSocketClient)
  ↓
webSocketClient.requestAdminDashboard()
  ↓  (加密通道)
服务端处理 → 权限检查 → 返回数据
  ↓  (加密通道)
onAdminDashboardResponse(data)
  ↓
解析数据 → 更新 UI
```

---

## 常见问题

### Q: 请求后没有收到响应？
A: 检查 WebSocket 连接状态和加密通道是否就绪（`handshakeState == DH_READY`）。

### Q: 收到 error "权限不足"？
A: 当前用户不是管理员，需要在 Web 后台将用户设为管理员。

### Q: 收到 status=no_ability？
A: 用户是管理员但没有分配任何管理能力，需要超级管理员在后台分配能力。

### Q: currentIp 为什么是 "unknown"？
A: WebSocket 连接无法获取 HTTP 请求的客户端 IP，这是正常行为。
