# Vue3 前端推送挑战处理指南 v2.0

> **版本:** v2.0  
> **更新日期:** 2026-06-19  
> **适用:** 管理后台 Vue3 前端（AppPushTest 页面）  
> **关联文档:** [APP_PUSH_CHALLENGE_POPUP_GUIDE.md](./APP_PUSH_CHALLENGE_POPUP_GUIDE.md)（App 端弹窗实现）

---

## 📋 概述

管理后台可以主动向 App 用户推送「3 选 1 挑战」，推送后前端可以看到推送的数字，并通过轮询查看挑战结果。

**核心流程：**

```
管理后台 Vue3 前端                     后端 API                    App 设备
     |                                   |                          |
     |-- POST /push (targetUserId) ----->|                          |
     |<-- 返回结果 ----------------------|                          |
     |                                   |-- WebSocket 推送挑战 --->|
     |   显示数字 ✅                      |                          |-- 弹窗三选一
     |                                   |                          |
     |-- GET /result/{challengeId} ----->|<---- App 响应 ----------|
     |<-- 返回 status -------------------|                          |
     |   显示结果 ✅                      |                          |
```

---

## 🔌 API 接口

### 1. 推送挑战

```
POST /api/auth/app/test-push/push
Authorization: Bearer <token>
```

**请求体：**

```json
{
  "targetUserId": 1234567890123456789
}
```

> `targetUserId` 为 `0` 表示推送给当前操作者自己。

**响应有两种模式，由后端系统配置 `security.app_auth.push_challenge_show_correct` 控制：**

#### 模式 A：调试模式（配置为 true）

只返回 `correctNumber`（正确答案），不返回 `numbers`、`challengeId`、`expiresAt`：

```json
{
  "code": 200,
  "data": {
    "targetUserId": 1234567890123456789,
    "results": [
      {
        "deviceId": "ANDROID_17e9ea285d6bcca0_5237ba0e",
        "correctNumber": 88,
        "status": "pushed"
      }
    ],
    "totalPushed": 1,
    "totalFailed": 0
  }
}
```

#### 模式 B：正常模式（配置为 false，默认）

返回完整信息：

```json
{
  "code": 200,
  "data": {
    "targetUserId": 1234567890123456789,
    "results": [
      {
        "deviceId": "ANDROID_17e9ea285d6bcca0_5237ba0e",
        "challengeId": "a1b2c3d4e5f6789012345678abcdef01",
        "numbers": [42, 88, 7],
        "expiresAt": 1718784060,
        "status": "pushed"
      }
    ],
    "totalPushed": 1,
    "totalFailed": 0
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `correctNumber` | int | **调试模式专属**：正确答案（0-99），仅当系统配置开启时返回 |
| `challengeId` | String | **正常模式专属**：挑战唯一 ID，用于后续轮询 |
| `numbers` | int[3] | **正常模式专属**：3 个候选数字（0-99） |
| `expiresAt` | long | **正常模式专属**：过期时间（Unix 秒级时间戳） |
| `status` | String | `"pushed"` 推送成功 / `"failed"` 推送失败 |

> ⚠️ **调试模式和正常模式互斥**：API 要么返回 `correctNumber`，要么返回 `numbers`+`challengeId`+`expiresAt`，不会同时返回。

**响应（失败 - 无在线设备）：**

```json
{
  "code": 400,
  "message": "目标用户没有在线设备"
}
```

---

### 2. 轮询挑战结果

```
GET /api/auth/app/test-push/result/{challengeId}
Authorization: Bearer <token>
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "challengeId": "a1b2c3d4e5f6789012345678abcdef01",
    "deviceId": "ANDROID_17e9ea285d6bcca0_5237ba0e",
    "deviceName": "Pixel 7 Pro",
    "numbers": [42, 88, 7],
    "status": "approved",
    "requestedAt": "2026-06-19T10:30:00Z",
    "respondedAt": "2026-06-19T10:30:25Z"
  }
}
```

**状态枚举：**

| status | 含义 | 建议 UI 展示 |
|--------|------|-------------|
| `pending` | 等待 App 用户响应 | ⏳ 等待响应中... |
| `approved` | 用户选择了正确数字 | ✅ 验证通过 |
| `rejected` | 用户选择了错误数字 | ❌ 验证失败 |
| `expired` | 60 秒内未响应，已过期 | ⏰ 已过期 |
| `unknown` | 未找到该挑战记录 | ❓ 未知 |

---

## 💻 前端完整实现

### 文件 1：`services/appPush.ts` — API 服务层

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

/**
 * 推送结果（单个设备）
 *
 * 注意：调试模式和正常模式返回的字段不同：
 * - 调试模式（correctNumber 存在）：deviceId + correctNumber + status
 * - 正常模式（numbers 存在）：deviceId + challengeId + numbers + expiresAt + status
 */
export interface PushResult {
  deviceId: string
  status: 'pushed' | 'failed'
  error?: string

  // 调试模式字段（仅当系统配置 push_challenge_show_correct=true 时返回）
  correctNumber?: number

  // 正常模式字段（仅当系统配置 push_challenge_show_correct=false 时返回）
  challengeId?: string
  numbers?: [number, number, number]
  expiresAt?: number
}

/** 推送响应 */
export interface PushResponse {
  targetUserId: number
  results: PushResult[]
  totalPushed: number
  totalFailed: number
}

/** 挑战结果（轮询返回） */
export interface ChallengeResultResponse {
  challengeId: string
  deviceId?: string
  deviceName?: string
  numbers?: [number, number, number]
  status: 'pending' | 'approved' | 'rejected' | 'expired' | 'unknown'
  requestedAt?: string
  respondedAt?: string
  message?: string
}

// ==================== API 方法 ====================

/** 获取当前用户的绑定设备列表（含在线状态） */
export function getBoundDevices(): Promise<BoundDevicesResponse> {
  return request.get<any, BoundDevicesResponse>('/auth/app/test-push/devices')
}

/** 向指定用户推送挑战 */
export function pushChallenge(targetUserId: number): Promise<PushResponse> {
  return request.post<any, PushResponse>('/auth/app/test-push/push', { targetUserId })
}

/** 轮询获取挑战结果 */
export function getChallengeResult(challengeId: string): Promise<ChallengeResultResponse> {
  return request.get<any, ChallengeResultResponse>(`/auth/app/test-push/result/${challengeId}`)
}

/** 解绑单个设备 */
export function unbindDevice(deviceId: string): Promise<void> {
  return request.delete(`/auth/app/devices/${encodeURIComponent(deviceId)}`)
}

/** 解绑所有其他设备（保留指定设备） */
export function unbindOtherDevices(keepDeviceId?: string): Promise<{ removedCount: number; message: string }> {
  const params = keepDeviceId ? { keepDeviceId } : {}
  return request.delete('/auth/app/devices/other', { params })
}

/** 解绑所有设备（完全重置） */
export function unbindAllDevices(): Promise<{ removedCount: number; message: string }> {
  return request.delete('/auth/app/devices/all')
}
```

---

### 文件 2：`AppPushTest.vue` — 页面组件

```vue
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  Refresh,
  Promotion,
  Search,
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  QuestionFilled,
  Cellphone,
  WarnTriangleFilled
} from '@element-plus/icons-vue'
import {
  getBoundDevices,
  pushChallenge,
  getChallengeResult,
  unbindDevice,
  unbindOtherDevices,
  unbindAllDevices,
  type DeviceInfo,
  type PushResponse,
  type ChallengeResultResponse
} from '@/services/appPush'

// ==================== 状态 ====================

// 设备列表
const devices = ref<DeviceInfo[]>([])
const loading = ref(false)
const pushing = ref(false)
const polling = ref(false)

// 推送结果
const pushResults = ref<PushResponse | null>(null)
const challengeResults = ref<Map<string, ChallengeResultResponse>>(new Map())
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)
const pollingChallengeIds = ref<string[]>([])

// 计算在线设备数
const onlineDevices = computed(() => devices.value.filter(d => d.online))
const offlineDevices = computed(() => devices.value.filter(d => !d.online))

// ==================== 工具函数 ====================

const getDeviceIcon = (device: DeviceInfo) => {
  const type = device.deviceType?.toUpperCase() || ''
  if (type === 'ANDROID') return '🤖'
  if (type === 'IOS') return '🍎'
  if (type === 'HARMONY') return '📱'
  return '📟'
}

const getCipherPrefTag = (pref: string) => {
  const map: Record<string, { type: string; label: string }> = {
    'AES_GCM': { type: 'success', label: 'AES-GCM' },
    'SM4_GCM': { type: 'warning', label: 'SM4-GCM' }
  }
  return map[pref] || { type: 'info', label: pref || '未知' }
}

const formatDateTime = (dateTime?: string) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

const getChallengeStatusTag = (status: string) => {
  const map: Record<string, { type: string; label: string }> = {
    'pending': { type: 'warning', label: '⏳ 等待响应中...' },
    'approved': { type: 'success', label: '✅ 验证通过' },
    'rejected': { type: 'danger', label: '❌ 验证失败' },
    'expired': { type: 'info', label: '⏰ 已过期' },
    'unknown': { type: 'info', label: '❓ 未知' }
  }
  return map[status] || { type: 'info', label: status }
}

const getStatusColor = (status: string): string => {
  const colors: Record<string, string> = {
    pending: '#E6A23C', approved: '#67C23A',
    rejected: '#F56C6C', expired: '#909399', unknown: '#909399',
  }
  return colors[status] || '#909399'
}

const getChallengeStatusIcon = (status: string) => {
  const map: Record<string, any> = {
    'pending': Clock, 'approved': CircleCheckFilled,
    'rejected': CircleCloseFilled, 'expired': QuestionFilled, 'unknown': QuestionFilled
  }
  return map[status] || QuestionFilled
}

const formatExpiresAt = (expiresAt?: number): string => {
  if (!expiresAt) return '-'
  return new Date(expiresAt * 1000).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

// ==================== 设备管理 ====================

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

// ==================== 轮询逻辑 ====================

const stopPolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
  polling.value = false
  pollingChallengeIds.value = []
}

const MAX_POLL_COUNT = 70
let pollCount = 0

const startPolling = (challengeIds: string[]) => {
  stopPolling()
  pollingChallengeIds.value = challengeIds
  polling.value = true
  pollCount = 0
  pollResults()
  pollingTimer.value = setInterval(() => { pollResults() }, 1000)
}

const pollResults = async () => {
  if (pollingChallengeIds.value.length === 0) {
    stopPolling()
    return
  }

  pollCount++
  if (pollCount >= MAX_POLL_COUNT) {
    stopPolling()
    ElMessage.warning('轮询已超时，部分挑战可能未完成')
    return
  }

  let allCompleted = true

  for (const challengeId of pollingChallengeIds.value) {
    try {
      const result = await getChallengeResult(challengeId)
      challengeResults.value.set(challengeId, result)

      if (result.status === 'pending') {
        allCompleted = false
      } else {
        const deviceName = result.deviceName || result.deviceId || '未知设备'
        const statusLabel = getChallengeStatusTag(result.status).label
        ElNotification({
          title: `挑战结果 - ${deviceName}`,
          message: `状态: ${statusLabel}`,
          type: result.status === 'approved' ? 'success' : result.status === 'rejected' ? 'warning' : 'info',
          duration: 5000
        })
      }
    } catch (error: any) {
      console.error(`查询挑战结果失败: ${challengeId}`, error)
      allCompleted = false
    }
  }

  if (allCompleted) {
    stopPolling()
    ElMessage.success('所有挑战已完成')
  }
}

// ==================== 推送挑战 ====================

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

    // 收集推送成功的 challengeId（正常模式才有 challengeId）
    const challengeIds: string[] = []
    for (const result of data.results) {
      if (result.status === 'pushed' && result.challengeId) {
        challengeIds.push(result.challengeId)
      }
    }

    ElMessage.success(`推送完成: ${data.totalPushed} 成功, ${data.totalFailed} 失败`)

    // 如果有推送成功的挑战且是正常模式（有 challengeId），开始轮询结果
    if (challengeIds.length > 0) {
      startPolling(challengeIds)
    }
  } catch (error: any) {
    console.error('推送挑战失败:', error)
    ElMessage.error(error.response?.data?.message || '推送挑战失败')
  } finally {
    pushing.value = false
  }
}

// ==================== 解绑设备 ====================

const unbindingDeviceId = ref<string | null>(null)

const handleUnbindDevice = async (deviceId: string) => {
  unbindingDeviceId.value = deviceId
  try {
    await unbindDevice(deviceId)
    devices.value = devices.value.filter(d => d.deviceId !== deviceId)
    ElMessage.success('设备已解绑')
  } catch (error: any) {
    console.error('解绑设备失败:', error)
    ElMessage.error(error.response?.data?.message || '解绑设备失败')
  } finally {
    unbindingDeviceId.value = null
  }
}

const cleaningOld = ref(false)
const handleCleanOldDevices = async () => {
  const onlineDevice = devices.value.find(d => d.online)
  if (!onlineDevice) {
    ElMessage.warning('没有在线设备可保留')
    return
  }
  cleaningOld.value = true
  try {
    const res = await unbindOtherDevices(onlineDevice.deviceId)
    await loadDevices()
    ElMessage.success(`已清理 ${res.removedCount} 台旧设备`)
  } catch (error: any) {
    console.error('清理旧设备失败:', error)
    ElMessage.error(error.response?.data?.message || '清理旧设备失败')
  } finally {
    cleaningOld.value = false
  }
}

const resettingAll = ref(false)
const handleResetAllDevices = async () => {
  resettingAll.value = true
  try {
    const res = await unbindAllDevices()
    devices.value = []
    ElMessage.success(`已解绑全部 ${res.removedCount} 台设备`)
  } catch (error: any) {
    console.error('解绑所有设备失败:', error)
    ElMessage.error(error.response?.data?.message || '解绑所有设备失败')
  } finally {
    resettingAll.value = false
  }
}

// ==================== 初始化 ====================

onMounted(() => {
  loadDevices()
})
</script>

<template>
  <div class="app-push-test">
    <!-- ========== 设备列表 ========== -->
    <el-card class="device-card">
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon><Cellphone /></el-icon>
            绑定设备列表
          </span>
          <div class="header-actions">
            <el-tag v-if="onlineDevices.length > 0" type="success" effect="dark" size="small">
              {{ onlineDevices.length }} 个在线
            </el-tag>
            <el-tag v-if="offlineDevices.length > 0" type="info" effect="plain" size="small">
              {{ offlineDevices.length }} 个离线
            </el-tag>
            <el-button type="primary" :icon="Refresh" :loading="loading" size="small" @click="loadDevices">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="device-list">
        <el-empty v-if="!loading && devices.length === 0" description="暂无绑定设备" />

        <div v-for="device in devices" :key="device.deviceId"
             class="device-item"
             :class="{ 'device-online': device.online, 'device-offline': !device.online }">
          <div class="device-header">
            <div class="device-info">
              <span class="device-icon">{{ getDeviceIcon(device) }}</span>
              <div class="device-details">
                <div class="device-name">
                  {{ device.deviceName || '未知设备' }}
                  <el-tag :type="device.online ? 'success' : 'info'" size="small" effect="dark">
                    {{ device.online ? '在线' : '离线' }}
                  </el-tag>
                </div>
                <div class="device-meta">
                  <span class="meta-item">ID: {{ device.deviceId }}</span>
                  <span class="meta-item">类型: {{ device.deviceType || '未知' }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="device-details-row">
            <div class="detail-item">
              <span class="label">加密偏好</span>
              <el-tag :type="getCipherPrefTag(device.cipherPref).type as any" size="small" effect="plain">
                {{ getCipherPrefTag(device.cipherPref).label }}
              </el-tag>
            </div>
            <div class="detail-item">
              <span class="label">最后活跃</span>
              <span class="value">{{ formatDateTime(device.lastActiveAt) }}</span>
            </div>
            <div class="detail-item device-actions">
              <el-popconfirm title="确定要解绑此设备？" @confirm="handleUnbindDevice(device.deviceId)">
                <template #reference>
                  <el-button type="danger" link size="small"
                             :loading="unbindingDeviceId === device.deviceId"
                             :disabled="unbindingDeviceId !== null">
                    解绑
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </div>
      </div>

      <div v-if="devices.length > 0" class="device-batch-actions">
        <el-button type="danger" size="small"
                   :loading="cleaningOld"
                   :disabled="onlineDevices.length === 0 || cleaningOld || resettingAll"
                   @click="handleCleanOldDevices">
          清理旧设备
        </el-button>
        <el-button type="danger" size="small" plain
                   :loading="resettingAll"
                   :disabled="resettingAll || cleaningOld"
                   @click="handleResetAllDevices">
          解绑所有设备
        </el-button>
      </div>
    </el-card>

    <!-- ========== 推送操作 ========== -->
    <el-card class="push-card">
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon><Promotion /></el-icon>
            推送 3 选 1 挑战
          </span>
        </div>
      </template>

      <div class="push-section">
        <el-alert title="推送说明" type="info" :closable="false" show-icon style="margin-bottom: 20px">
          <template #default>
            <ul style="margin: 0; padding-left: 20px;">
              <li>向所有<strong>在线设备</strong>推送 3 选 1 挑战</li>
              <li>App 端会显示 3 个选项，用户需从中选择正确的一个</li>
              <li>系统会每 1 秒轮询一次挑战结果，直到所有挑战完成</li>
              <li>挑战状态：⏳ 等待响应中... → ✅ 验证通过 / ❌ 验证失败 / ⏰ 已过期</li>
            </ul>
          </template>
        </el-alert>

        <div class="push-actions">
          <el-button type="primary" size="large" :icon="Promotion"
                     :loading="pushing" :disabled="onlineDevices.length === 0"
                     @click="handlePushChallenge">
            推送挑战（{{ onlineDevices.length }} 个在线设备）
          </el-button>

          <el-button v-if="polling" type="danger" size="large" :icon="Refresh" @click="stopPolling">
            停止轮询
          </el-button>
        </div>

        <div v-if="polling" class="polling-status">
          <el-icon class="is-loading"><Refresh /></el-icon>
          <span>等待 App 用户响应...</span>
        </div>
      </div>
    </el-card>

    <!-- ========== 推送结果 ========== -->
    <el-card v-if="pushResults" class="result-card">
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon><Search /></el-icon>
            推送结果
          </span>
          <el-tag type="success" effect="dark" size="small">
            {{ pushResults.totalPushed }} 成功 / {{ pushResults.totalFailed }} 失败
          </el-tag>
        </div>
      </template>

      <div class="push-results">
        <div v-for="result in pushResults.results" :key="result.deviceId"
             class="push-result-item"
             :class="{ 'push-success': result.status === 'pushed', 'push-failed': result.status === 'failed' }">

          <div class="result-header">
            <div class="result-device">
              <el-icon v-if="result.status === 'pushed'" color="#67C23A"><CircleCheckFilled /></el-icon>
              <el-icon v-else color="#F56C6C"><CircleCloseFilled /></el-icon>
              <span class="device-id">{{ result.deviceId }}</span>
            </div>
            <el-tag :type="result.status === 'pushed' ? 'success' : 'danger'" size="small" effect="dark">
              {{ result.status === 'pushed' ? '推送成功' : '推送失败' }}
            </el-tag>
          </div>

          <!--
            数字展示逻辑（由后端系统配置 security.app_auth.push_challenge_show_correct 控制）：
            - correctNumber 存在 → 调试模式：只显示正确答案（绿色高亮）
            - numbers 存在 → 正常模式：显示 3 个候选数字
          -->
          <div v-if="result.status === 'pushed'" class="numbers-display">
            <!-- 调试模式：correctNumber 存在 -->
            <template v-if="result.correctNumber !== undefined">
              <p class="numbers-label">推送的数字（调试模式）：</p>
              <div class="number-badges">
                <el-tag size="large" type="success" class="number-tag correct-number">
                  {{ result.correctNumber }}
                </el-tag>
              </div>
              <p class="debug-hint">🛠️ 调试模式：这是正确答案</p>
            </template>
            <!-- 正常模式：numbers 存在 -->
            <template v-else-if="result.numbers">
              <p class="numbers-label">推送的数字：</p>
              <div class="number-badges">
                <el-tag v-for="(num, index) in result.numbers" :key="index"
                        size="large" type="info" class="number-tag">
                  {{ num }}
                </el-tag>
              </div>
              <p class="numbers-hint">App 用户需要从这 3 个数字中选择正确的一个</p>
            </template>
          </div>

          <!-- 正常模式才显示 challengeId 和 expiresAt -->
          <div v-if="result.status === 'pushed' && result.challengeId" class="result-detail">
            <span class="label">Challenge ID:</span>
            <code class="challenge-id">{{ result.challengeId }}</code>
          </div>
          <div v-if="result.status === 'pushed' && result.expiresAt" class="result-detail">
            <span class="label">过期时间:</span>
            <span class="value">{{ formatExpiresAt(result.expiresAt) }}</span>
          </div>

          <div v-if="result.status === 'failed' && result.error" class="result-error">
            <el-icon><WarnTriangleFilled /></el-icon>
            <span>{{ result.error }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ========== 挑战结果 ========== -->
    <el-card v-if="challengeResults.size > 0" class="challenge-card">
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon><Search /></el-icon>
            挑战结果
          </span>
          <el-tag v-if="polling" type="warning" effect="dark" size="small">轮询中...</el-tag>
        </div>
      </template>

      <div class="challenge-results">
        <div v-for="[challengeId, result] in challengeResults" :key="challengeId" class="challenge-result-item">
          <div class="challenge-header">
            <el-icon :size="24" :color="getStatusColor(result.status)">
              <component :is="getChallengeStatusIcon(result.status)" />
            </el-icon>
            <div class="challenge-info">
              <div class="challenge-title">挑战 {{ result.challengeId?.substring(0, 8) }}...</div>
              <div class="challenge-meta">
                <span v-if="result.deviceName">设备: {{ result.deviceName }}</span>
                <span v-if="result.deviceId">ID: {{ result.deviceId }}</span>
              </div>
            </div>
            <el-tag :type="getChallengeStatusTag(result.status).type as any" size="small" effect="dark">
              {{ getChallengeStatusTag(result.status).label }}
            </el-tag>
          </div>

          <div v-if="result.numbers" class="numbers-display">
            <p class="numbers-label">挑战数字：</p>
            <div class="number-badges">
              <el-tag v-for="(num, index) in result.numbers" :key="index"
                      size="large"
                      :type="result.status === 'approved' ? 'success' : result.status === 'rejected' ? 'danger' : 'info'"
                      class="number-tag">
                {{ num }}
              </el-tag>
            </div>
          </div>

          <div class="challenge-timeline">
            <div class="timeline-item">
              <span class="label">请求时间:</span>
              <span class="value">{{ formatDateTime(result.requestedAt) }}</span>
            </div>
            <div class="timeline-item">
              <span class="label">响应时间:</span>
              <span class="value">{{ formatDateTime(result.respondedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.app-push-test {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .title {
    font-size: 18px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

// 设备列表
.device-list {
  min-height: 100px;
}

.device-item {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  }

  &.device-online {
    border-left: 4px solid var(--el-color-success);
  }

  &.device-offline {
    border-left: 4px solid var(--el-color-info);
    opacity: 0.7;
  }
}

.device-header {
  margin-bottom: 12px;
}

.device-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.device-icon {
  font-size: 28px;
  line-height: 1;
}

.device-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.device-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  .meta-item {
    white-space: nowrap;
  }
}

.device-details-row {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);

  .detail-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;

    .label {
      color: var(--el-text-color-secondary);
    }

    .value {
      color: var(--el-text-color-primary);
    }
  }
}

// 推送操作
.push-section {
  .push-actions {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .polling-status {
    margin-top: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--el-color-warning);
    font-size: 14px;
  }
}

// 数字展示
.numbers-display {
  margin: 12px 0;
  padding: 12px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.numbers-label {
  margin: 0 0 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.number-badges {
  display: flex;
  gap: 12px;
  margin: 8px 0;
}

.number-tag {
  font-size: 20px;
  padding: 8px 20px;
  font-weight: bold;
}

.numbers-hint {
  margin: 8px 0 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

// 调试模式：正确答案样式
.number-tag.correct-number {
  font-size: 24px;
  padding: 12px 28px;
  font-weight: bold;
}

.debug-hint {
  color: #E6A23C;
  font-size: 12px;
  margin-top: 4px;
}

// 推送结果
.push-results {
  .push-result-item {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 12px 16px;
    margin-bottom: 8px;

    &.push-success {
      border-left: 4px solid var(--el-color-success);
    }

    &.push-failed {
      border-left: 4px solid var(--el-color-danger);
    }
  }

  .result-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .result-device {
    display: flex;
    align-items: center;
    gap: 8px;

    .device-id {
      font-size: 14px;
      font-weight: 500;
      font-family: monospace;
    }
  }

  .result-detail {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    margin-top: 4px;

    .label {
      color: var(--el-text-color-secondary);
    }

    .challenge-id {
      font-size: 12px;
      background-color: var(--el-fill-color);
      padding: 2px 8px;
      border-radius: 4px;
      font-family: monospace;
    }

    .value {
      color: var(--el-text-color-primary);
    }
  }

  .result-error {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--el-color-danger);
    font-size: 13px;
  }
}

// 挑战结果
.challenge-results {
  .challenge-result-item {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 12px;
  }

  .challenge-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  .challenge-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .challenge-title {
    font-size: 14px;
    font-weight: 600;
  }

  .challenge-meta {
    display: flex;
    gap: 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .challenge-timeline {
    display: flex;
    gap: 24px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);

    .timeline-item {
      display: flex;
      gap: 6px;
      font-size: 13px;

      .label {
        color: var(--el-text-color-secondary);
      }

      .value {
        color: var(--el-text-color-primary);
      }
    }
  }
}

// 移动端适配
@media (max-width: 768px) {
  .app-push-test {
    padding: 10px;
  }

  .device-details-row {
    flex-direction: column;
    gap: 8px;
  }

  .push-actions {
    flex-direction: column;

    .el-button {
      width: 100%;
    }
  }

  .challenge-timeline {
    flex-direction: column;
    gap: 8px;
  }

  .number-badges {
    justify-content: center;
  }
}
</style>

