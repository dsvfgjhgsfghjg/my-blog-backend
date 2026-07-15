# App 推送测试功能 — Vue3 前端实现指南

> **版本:** v1.0  
> **更新日期:** 2026-06-15  
> **适用项目:** 管理后台（Admin Panel）

---

## 📋 目录

1. [概述](#-概述)
2. [后端 API](#-后端-api)
   - [2.1 获取绑定设备列表](#21-获取绑定设备列表)
   - [2.2 推送挑战](#22-推送挑战)
   - [2.3 轮询挑战结果](#23-轮询挑战结果)
3. [前端实现](#-前端实现)
   - [3.1 API 服务层](#31-api-服务层)
   - [3.2 页面组件](#32-页面组件)
   - [3.3 路由配置](#33-路由配置)
   - [3.4 菜单配置](#34-菜单配置)
4. [核心逻辑详解](#-核心逻辑详解)
   - [4.1 设备列表加载](#41-设备列表加载)
   - [4.2 推送挑战](#42-推送挑战)
   - [4.3 轮询机制](#43-轮询机制)
5. [UI 组件说明](#-ui-组件说明)
6. [错误处理](#-错误处理)
7. [完整代码结构](#-完整代码结构)

---

## 📖 概述

App 推送测试功能是管理后台的一个测试工具页面，用于向已绑定设备的 App 用户推送 **3 选 1 挑战**，并通过轮询方式获取挑战结果。

### 功能列表

| 功能 | 说明 |
|------|------|
| 设备列表展示 | 显示当前用户绑定的所有设备及其在线状态 |
| 推送挑战 | 向所有在线设备推送 3 选 1 挑战 |
| 轮询结果 | 每 2 秒查询一次挑战结果，完成后自动停止 |
| 结果通知 | 每个挑战完成时弹出通知 |

### 页面布局

```
┌─────────────────────────────────────────┐
│  绑定设备列表          [在线数] [离线数] [刷新] │
│  ┌─────────────────────────────────────┐ │
│  │ 🤖 设备名称 [在线]                  │ │
│  │    ID: xxx  类型: ANDROID           │ │
│  │    加密偏好: AES-GCM  最后活跃: ... │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │ 🍎 设备名称 [离线]                  │ │
│  │    ...                              │ │
│  └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│  推送 3 选 1 挑战                        │
│  [推送挑战（N 个在线设备）] [停止轮询]    │
│  ⏳ 正在轮询挑战结果...                  │
├─────────────────────────────────────────┤
│  推送结果              2 成功 / 0 失败   │
│  ✅ device_xxx    推送成功              │
│     Challenge ID: CHALLENGE_abc...      │
├─────────────────────────────────────────┤
│  挑战结果              轮询中...         │
│  ⏳ 挑战 abc12345...                    │
│     设备: 设备名称                      │
│     状态: 等待中                        │
│     请求时间: ...  响应时间: ...        │
└─────────────────────────────────────────┘
```

---

## 🌐 后端 API

### 2.1 获取绑定设备列表

获取当前用户的所有绑定设备及其在线状态。

```
GET /api/auth/app/test-push/devices
Authorization: Bearer <JWT_TOKEN>
```

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "devices": [
      {
        "deviceId": "ANDROID_abc123_def456",
        "deviceName": "Xiaomi 14",
        "deviceType": "ANDROID",
        "cipherPref": "AES_GCM",
        "lastActiveAt": "2026-06-15T20:00:00",
        "online": true
      }
    ],
    "total": 1,
    "onlineCount": 1
  }
}
```

### 2.2 推送挑战

向指定用户的所有在线设备推送 3 选 1 挑战。

```
POST /api/auth/app/test-push/push
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "targetUserId": 1
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "targetUserId": 1,
    "results": [
      {
        "deviceId": "ANDROID_abc123_def456",
        "challengeId": "CHALLENGE_abc123_def456",
        "status": "pushed"
      }
    ],
    "totalPushed": 1,
    "totalFailed": 0
  }
}
```

### 2.3 轮询挑战结果

根据 challengeId 查询挑战的最终状态。

```
GET /api/auth/app/test-push/result/{challengeId}
Authorization: Bearer <JWT_TOKEN>
```

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "challengeId": "CHALLENGE_abc123_def456",
    "deviceId": "ANDROID_abc123_def456",
    "deviceName": "Xiaomi 14",
    "status": "approved",
    "requestedAt": "2026-06-15T20:00:00",
    "respondedAt": "2026-06-15T20:00:05"
  }
}
```

#### 状态说明

| 状态 | 说明 |
|------|------|
| `pending` | 等待用户响应 |
| `approved` | 用户选择正确，挑战通过 |
| `rejected` | 用户选择错误，挑战拒绝 |
| `expired` | 挑战超时未响应 |
| `unknown` | 未找到该挑战记录 |

---

## 💻 前端实现

### 3.1 API 服务层

文件：`src/services/appPush.ts`

```typescript
import request from '@/utils/request'

// ==================== 类型定义 ====================

/** 设备信息 */
export interface DeviceInfo {
  deviceId: string
  deviceName: string
  deviceType: string
  cipherPref: string
  lastActiveAt: string
  online: boolean
}

/** 绑定设备列表响应 */
export interface BoundDevicesResponse {
  userId: number
  devices: DeviceInfo[]
  total: number
  onlineCount: number
}

/** 推送结果（单个设备） */
export interface PushResult {
  deviceId: string
  challengeId?: string
  status: 'pushed' | 'failed'
  error?: string
}

/** 推送响应 */
export interface PushResponse {
  targetUserId: number
  results: PushResult[]
  totalPushed: number
  totalFailed: number
}

/** 挑战结果 */
export interface ChallengeResultResponse {
  challengeId: string
  deviceId?: string
  deviceName?: string
  status: 'pending' | 'approved' | 'rejected' | 'expired' | 'unknown'
  requestedAt?: string
  respondedAt?: string
  message?: string
}

// ==================== API 方法 ====================

/** 获取当前用户的绑定设备列表（含在线状态） */
export function getBoundDevices() {
  return request.get<BoundDevicesResponse>('/auth/app/test-push/devices')
}

/** 向指定用户推送挑战 */
export function pushChallenge(targetUserId: number) {
  return request.post<PushResponse>('/auth/app/test-push/push', { targetUserId })
}

/** 轮询获取挑战结果 */
export function getChallengeResult(challengeId: string) {
  return request.get<ChallengeResultResponse>(`/auth/app/test-push/result/${challengeId}`)
}
```

### 3.2 页面组件

文件：`src/views/admin/AppPushTest.vue`

页面包含 4 个主要区域：

1. **设备列表卡片** - 展示绑定设备
2. **推送操作卡片** - 推送挑战按钮和轮询控制
3. **推送结果卡片** - 显示推送成功/失败详情
4. **挑战结果卡片** - 显示每个挑战的最终状态

#### 核心状态管理

```typescript
// 设备列表
const devices = ref<DeviceInfo[]>([])
const loading = ref(false)

// 推送状态
const pushing = ref(false)
const pushResults = ref<PushResponse | null>(null)

// 轮询状态
const polling = ref(false)
const challengeResults = ref<Map<string, ChallengeResultResponse>>(new Map())
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)
const pollingChallengeIds = ref<string[]>([])

// 计算属性
const onlineDevices = computed(() => devices.value.filter(d => d.online))
const offlineDevices = computed(() => devices.value.filter(d => !d.online))
```

### 3.3 路由配置

文件：`src/router/index.ts`

在 `/admin` 路由的 children 中添加：

```typescript
{
  path: 'app-push',
  name: 'AdminAppPush',
  component: () => import('@/views/admin/AppPushTest.vue'),
  meta: { title: 'App 推送测试' },
}
```

### 3.4 菜单配置

文件：`src/layouts/AdminLayout.vue`

在 `menuItems` 的 `if (userStore.userInfo?.isAdmin)` 块中添加：

```typescript
{
  key: 'app-push',
  label: 'App 推送测试',
  icon: Cellphone,
  path: '/admin/app-push',
}
```

同时需要在 import 中添加 `Cellphone`：

```typescript
import { 
  // ... 其他图标
  Cellphone
} from '@element-plus/icons-vue'
```

---

## 🔧 核心逻辑详解

### 4.1 设备列表加载

```typescript
const loadDevices = async () => {
  loading.value = true
  try {
    const data = await getBoundDevices()
    devices.value = data.devices || []
    ElMessage.success(`已加载 ${data.total} 个设备（${data.onlineCount} 个在线）`)
  } catch (error: any) {
    console.error('加载设备列表失败:', error)
    ElMessage.error(error.response?.data?.message || '加载设备列表失败')
  } finally {
    loading.value = false
  }
}
```

### 4.2 推送挑战

```typescript
const handlePushChallenge = async () => {
  if (onlineDevices.value.length === 0) {
    ElMessage.warning('没有在线设备可推送')
    return
  }

  pushing.value = true
  pushResults.value = null
  challengeResults.value = new Map()

  try {
    const data = await pushChallenge(0) // 0 表示推送给当前用户
    pushResults.value = data

    // 收集推送成功的 challengeId
    const challengeIds: string[] = []
    for (const result of data.results) {
      if (result.status === 'pushed' && result.challengeId) {
        challengeIds.push(result.challengeId)
      }
    }

    ElMessage.success(`推送完成: ${data.totalPushed} 成功, ${data.totalFailed} 失败`)

    // 开始轮询
    if (challengeIds.length > 0) {
      startPolling(challengeIds)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '推送挑战失败')
  } finally {
    pushing.value = false
  }
}
```

### 4.3 轮询机制

轮询使用 `setInterval` 实现，每 2 秒查询一次所有挑战的状态。

```typescript
// 启动轮询
const startPolling = (challengeIds: string[]) => {
  stopPolling() // 先停止之前的轮询

  pollingChallengeIds.value = challengeIds
  polling.value = true

  // 立即查询一次
  pollResults()

  // 每 2 秒轮询一次
  pollingTimer.value = setInterval(() => {
    pollResults()
  }, 2000)
}

// 执行轮询查询
const pollResults = async () => {
  if (pollingChallengeIds.value.length === 0) {
    stopPolling()
    return
  }

  let allCompleted = true

  for (const challengeId of pollingChallengeIds.value) {
    try {
      const result = await getChallengeResult(challengeId)
      challengeResults.value.set(challengeId, result)

      if (result.status === 'pending') {
        allCompleted = false // 还有未完成的挑战
      } else {
        // 弹出通知
        ElNotification({
          title: `挑战结果 - ${result.deviceName || result.deviceId}`,
          message: `状态: ${getChallengeStatusTag(result.status).label}`,
          type: result.status === 'approved' ? 'success' : 
                result.status === 'rejected' ? 'warning' : 'info',
          duration: 5000
        })
      }
    } catch (error: any) {
      console.error(`查询挑战结果失败: ${challengeId}`, error)
      allCompleted = false
    }
  }

  // 所有挑战完成，停止轮询
  if (allCompleted) {
    stopPolling()
    ElMessage.success('所有挑战已完成')
  }
}

// 停止轮询
const stopPolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
  polling.value = false
  pollingChallengeIds.value = []
}
```

#### 轮询流程图

```
用户点击"推送挑战"
       │
       ▼
POST /push → 获取 challengeIds
       │
       ▼
启动 setInterval(2s)
       │
       ▼
┌─── GET /result/{challengeId}
│       │
│       ▼
│  检查 status
│       │
│   ┌───┴───┐
│   │       │
│ pending  其他(approved/rejected/expired)
│   │       │
│   │       ▼
│   │  弹出通知
│   │       │
│   └───┐   │
│       │   │
│   ┌───┘   │
│   ▼       ▼
│ 继续轮询  标记完成
│       │
│   ┌───┘
│   ▼
│ 所有完成?
│   │
│   是 → 停止轮询
│   否 → 继续
└───
```

---

## 🎨 UI 组件说明

### 设备列表卡片

使用 `el-card` 包裹，每个设备使用自定义卡片样式：

- **在线设备**：左边框绿色（`border-left: 4px solid #67C23A`）
- **离线设备**：左边框灰色（`border-left: 4px solid #909399`），透明度 0.7

设备信息展示：
- 设备图标（根据 deviceType 显示不同 emoji）
- 设备名称 + 在线/离线标签
- 设备 ID 和类型
- 加密偏好标签（AES-GCM / SM4-GCM）
- 最后活跃时间

### 推送操作卡片

- 推送按钮：主色大按钮，显示在线设备数量
- 停止轮询按钮：危险色，仅在轮询中显示
- 轮询状态提示：旋转图标 + 文字

### 推送结果卡片

每个设备的推送结果：
- ✅ 推送成功：显示 Challenge ID
- ❌ 推送失败：显示错误信息

### 挑战结果卡片

每个挑战的最终状态：
- 状态图标（颜色区分）
- 挑战 ID（截取前 8 位）
- 设备信息
- 状态标签
- 请求时间和响应时间

---

## ⚠️ 错误处理

| 场景 | 处理方式 |
|------|----------|
| 加载设备列表失败 | `ElMessage.error` 显示后端错误信息 |
| 没有在线设备 | `ElMessage.warning` 提示用户 |
| 推送挑战失败 | `ElMessage.error` 显示后端错误信息 |
| 轮询查询失败 | 控制台打印错误，继续轮询 |
| 所有挑战完成 | `ElMessage.success` 提示，停止轮询 |

---

## 📁 完整代码结构

```
src/
├── services/
│   └── appPush.ts              # API 服务层（类型定义 + 请求方法）
├── views/
│   └── admin/
│       └── AppPushTest.vue      # 推送测试页面组件
├── router/
│   └── index.ts                 # 路由配置（添加 /admin/app-push）
└── layouts/
    └── AdminLayout.vue          # 菜单配置（添加"App 推送测试"菜单项）
```

### 文件清单

| 文件 | 说明 | 新增/修改 |
|------|------|-----------|
| `src/services/appPush.ts` | API 类型定义和请求方法 | 新增 |
| `src/views/admin/AppPushTest.vue` | 推送测试页面 | 新增 |
| `src/router/index.ts` | 添加 `/admin/app-push` 路由 | 修改 |
| `src/layouts/AdminLayout.vue` | 添加菜单项和图标导入 | 修改 |
