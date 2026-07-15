# Vue3 前端设备管理指南

> **版本:** v1.0  
> **更新日期:** 2026-06-19  
> **适用:** 管理后台 Vue3 前端  
> **关联文档:** [VUE3_PUSH_CHALLENGE_GUIDE.md](./VUE3_PUSH_CHALLENGE_GUIDE.md)、[APP_DEVICE_FINGERPRINT_GUIDE.md](./APP_DEVICE_FINGERPRINT_GUIDE.md)

---

## 概述

管理后台可以管理用户的 App 绑定设备，包括：查看设备列表（含在线状态）、单个解绑、批量清理旧设备、完全重置。

**所有接口基础路径：** `/api/auth/app`  
**认证方式：** `Authorization: Bearer <token>`

---

## API 接口

### 1. 获取设备列表

```
GET /api/auth/app/devices
```

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1923456789012345678,
      "userId": 1,
      "deviceId": "ANDROID_a3f8b2c1d4e5_5237ba0e",
      "deviceName": "Pixel 7 Pro",
      "deviceType": "ANDROID",
      "publicKey": null,
      "cipherPref": "AES-256-GCM",
      "lastActiveAt": "2026-06-19T10:30:00",
      "createdAt": "2026-06-18T08:00:00"
    }
  ]
}
```

> 返回数组按 `lastActiveAt` 降序排列（最近活跃的排前面）。

---

### 2. 获取设备列表（含在线状态）

```
GET /api/auth/app/test-push/devices
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "devices": [
      {
        "deviceId": "ANDROID_a3f8b2c1d4e5_5237ba0e",
        "deviceName": "Pixel 7 Pro",
        "deviceType": "ANDROID",
        "cipherPref": "AES-256-GCM",
        "lastActiveAt": "2026-06-19T10:30:00",
        "online": true
      }
    ],
    "total": 2,
    "onlineCount": 1
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `online` | boolean | 设备当前 WebSocket 是否在线 |
| `total` | number | 总设备数 |
| `onlineCount` | number | 在线设备数 |

---

### 3. 检查绑定状态

```
GET /api/auth/app/status
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "hasBoundDevice": true
  }
}
```

---

### 4. 解绑单个设备

```
DELETE /api/auth/app/devices/{deviceId}
```

**URL 参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `deviceId` | string | 要解绑的设备 ID |

**响应（成功）：**

```json
{
  "code": 200,
  "data": null,
  "message": "设备已解绑"
}
```

**响应（失败）：**

```json
{
  "code": 400,
  "message": "设备不存在"
}
```

---

### 5. 解绑所有其他设备（保留指定设备）

```
DELETE /api/auth/app/devices/other?keepDeviceId={deviceId}
```

**URL 参数：**

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `keepDeviceId` | 否 | string | 要保留的设备 ID，不传则全部解绑 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "removedCount": 3,
    "message": "已解绑 3 台设备"
  }
}
```

**使用场景：** 用户当前正在一台设备上操作，想清理所有旧的/重复的设备记录，只保留当前这台。

---

### 6. 解绑所有设备（完全重置）

```
DELETE /api/auth/app/devices/all
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "removedCount": 5,
    "message": "已解绑全部 5 台设备"
  }
}
```

**使用场景：** 用户想完全重置设备绑定，之后 App 需重新绑定。

---

### 7. 生成配对码

```
POST /api/auth/app/generate-pair-code
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "pairCode": "837291",
    "expiresIn": 300
  }
}
```

| 字段 | 说明 |
|------|------|
| `pairCode` | 6 位数字配对码 |
| `expiresIn` | 有效期（秒），默认 300 秒（5 分钟） |

---

### 8. 获取认证历史

```
GET /api/auth/app/history?page=1&size=20&status=approved
```

**URL 参数：**

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | 否 | 1 | 页码 |
| `size` | 否 | 20 | 每页条数 |
| `status` | 否 | - | 筛选状态：`pending`/`approved`/`rejected`/`expired` |

**响应：**

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "challengeId": "a1b2c3d4e5f67890",
        "deviceId": "ANDROID_a3f8b2c1d4e5_5237ba0e",
        "deviceName": "Pixel 7 Pro",
        "numbers": [42, 88, 7],
        "status": "approved",
        "requestedAt": "2026-06-19T10:30:00",
        "respondedAt": "2026-06-19T10:30:25"
      }
    ],
    "total": 45,
    "page": 1,
    "size": 20,
    "pages": 3
  }
}
```

---

## 错误码汇总

| HTTP 状态码 | 错误信息 | 触发场景 |
|-------------|---------|---------|
| 200 | - | 成功 |
| 400 | "设备不存在" | 解绑了不存在的设备 |
| 400 | "该设备已被其他账号绑定，请先解绑" | 绑定已被其他用户绑定的设备 |
| 400 | "已达到最大绑定设备数（5），请先解绑旧设备" | 用户设备数超过上限 |
| 400 | "用户名或密码错误" | 密码绑定认证失败 |
| 400 | "配对码已过期，请重新生成" | 使用过期的配对码 |
| 400 | "绑定尝试次数过多，请 X 秒后重试" | 频繁绑定失败被锁定 |

---

## Vue3 实现

### TypeScript 类型定义

```typescript
// types/appDevice.ts

/** 设备信息（基础） */
export interface AppDevice {
  id: number
  userId: number
  deviceId: string
  deviceName: string
  deviceType: 'ANDROID' | 'IOS' | 'OTHER'
  publicKey: string | null
  cipherPref: string
  lastActiveAt: string
  createdAt: string
}

/** 设备信息（含在线状态） */
export interface AppDeviceWithStatus {
  deviceId: string
  deviceName: string
  deviceType: string
  cipherPref: string
  lastActiveAt: string
  online: boolean
}

/** 设备列表响应（含在线状态） */
export interface DeviceListResponse {
  userId: number
  devices: AppDeviceWithStatus[]
  total: number
  onlineCount: number
}

/** 绑定状态 */
export interface BindStatus {
  hasBoundDevice: boolean
}

/** 批量解绑结果 */
export interface UnbindResult {
  removedCount: number
  message: string
}

/** 配对码信息 */
export interface PairCodeInfo {
  pairCode: string
  expiresIn: number
}

/** 认证历史记录 */
export interface AuthHistoryRecord {
  challengeId: string
  deviceId: string
  deviceName: string
  numbers: [number, number, number]
  status: 'pending' | 'approved' | 'rejected' | 'expired' | 'unknown'
  requestedAt: string
  respondedAt: string | null
}
```

### Service 封装

```typescript
// services/appDevice.ts

import request from '@/utils/request'
import type {
  AppDevice,
  AppDeviceWithStatus,
  DeviceListResponse,
  BindStatus,
  UnbindResult,
  PairCodeInfo,
} from '@/types/appDevice'

const BASE = '/api/auth/app'

/** 获取设备列表（基础信息） */
export function getDevices(): Promise<AppDevice[]> {
  return request.get(`${BASE}/devices`)
}

/** 获取设备列表（含在线状态） */
export function getDevicesWithStatus(): Promise<DeviceListResponse> {
  return request.get(`${BASE}/test-push/devices`)
}

/** 检查绑定状态 */
export function getBindStatus(): Promise<BindStatus> {
  return request.get(`${BASE}/status`)
}

/** 解绑单个设备 */
export function unbindDevice(deviceId: string): Promise<void> {
  return request.delete(`${BASE}/devices/${encodeURIComponent(deviceId)}`)
}

/** 解绑所有其他设备（保留指定设备） */
export function unbindOtherDevices(keepDeviceId?: string): Promise<UnbindResult> {
  const params = keepDeviceId ? { keepDeviceId } : {}
  return request.delete(`${BASE}/devices/other`, { params })
}

/** 解绑所有设备（完全重置） */
export function unbindAllDevices(): Promise<UnbindResult> {
  return request.delete(`${BASE}/devices/all`)
}

/** 生成配对码 */
export function generatePairCode(): Promise<PairCodeInfo> {
  return request.post(`${BASE}/generate-pair-code`)
}
```

### Composable

```typescript
// composables/useDeviceManagement.ts

import { ref, computed } from 'vue'
import type { AppDeviceWithStatus } from '@/types/appDevice'
import * as deviceService from '@/services/appDevice'

export function useDeviceManagement() {
  const devices = ref<AppDeviceWithStatus[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const totalDevices = computed(() => devices.value.length)
  const onlineDevices = computed(() => devices.value.filter(d => d.online).length)

  /** 刷新设备列表 */
  async function refreshDevices() {
    loading.value = true
    error.value = null
    try {
      const res = await deviceService.getDevicesWithStatus()
      devices.value = res.devices
    } catch (e: any) {
      error.value = e.message || '加载设备列表失败'
    } finally {
      loading.value = false
    }
  }

  /** 解绑单个设备 */
  async function unbind(deviceId: string) {
    try {
      await deviceService.unbindDevice(deviceId)
      // 从列表中移除
      devices.value = devices.value.filter(d => d.deviceId !== deviceId)
      return true
    } catch (e: any) {
      error.value = e.message || '解绑失败'
      return false
    }
  }

  /** 清理旧设备（保留指定设备） */
  async function cleanOldDevices(keepDeviceId: string) {
    try {
      const res = await deviceService.unbindOtherDevices(keepDeviceId)
      // 刷新列表
      await refreshDevices()
      return res.removedCount
    } catch (e: any) {
      error.value = e.message || '清理失败'
      return 0
    }
  }

  /** 完全重置（解绑所有设备） */
  async function resetAll() {
    try {
      const res = await deviceService.unbindAllDevices()
      devices.value = []
      return res.removedCount
    } catch (e: any) {
      error.value = e.message || '重置失败'
      return 0
    }
  }

  /** 生成配对码 */
  async function generatePairCode() {
    try {
      return await deviceService.generatePairCode()
    } catch (e: any) {
      error.value = e.message || '生成配对码失败'
      return null
    }
  }

  return {
    devices,
    loading,
    error,
    totalDevices,
    onlineDevices,
    refreshDevices,
    unbind,
    cleanOldDevices,
    resetAll,
    generatePairCode,
  }
}
```

### Vue 组件示例

```vue
<!-- components/DeviceManagement.vue -->
<template>
  <div class="device-management">
    <div class="header">
      <h3>设备管理</h3>
      <div class="actions">
        <el-button @click="refreshDevices" :loading="loading">
          刷新
        </el-button>
        <el-button type="danger" @click="handleCleanAll" :disabled="totalDevices <= 1">
          清理旧设备
        </el-button>
        <el-button type="danger" plain @click="handleResetAll" :disabled="totalDevices === 0">
          解绑所有设备
        </el-button>
      </div>
    </div>

    <!-- 设备列表 -->
    <el-table :data="devices" v-loading="loading" stripe>
      <el-table-column prop="deviceName" label="设备名称" />
      <el-table-column prop="deviceId" label="设备 ID" width="300">
        <template #default="{ row }">
          <el-tooltip :content="row.deviceId" placement="top">
            <span class="device-id">{{ row.deviceId }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="deviceType" label="类型" width="100" />
      <el-table-column prop="online" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.online ? 'success' : 'info'" size="small">
            {{ row.online ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastActiveAt" label="最后活跃" width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            title="确定要解绑此设备？"
            @confirm="handleUnbind(row.deviceId)"
          >
            <template #reference>
              <el-button type="danger" link size="small">解绑</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 统计信息 -->
    <div class="stats" v-if="totalDevices > 0">
      <span>共 {{ totalDevices }} 台设备，{{ onlineDevices }} 台在线</span>
    </div>

    <!-- 配对码 -->
    <div class="pair-code-section">
      <el-button @click="handleGeneratePairCode" :loading="pairCodeLoading">
        生成配对码
      </el-button>
      <div v-if="pairCode" class="pair-code-display">
        <span class="code">{{ pairCode.pairCode }}</span>
        <span class="expires">有效期 {{ pairCode.expiresIn }} 秒</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDeviceManagement } from '@/composables/useDeviceManagement'

const {
  devices,
  loading,
  totalDevices,
  onlineDevices,
  refreshDevices,
  unbind,
  cleanOldDevices,
  resetAll,
  generatePairCode,
} = useDeviceManagement()

const pairCode = ref<{ pairCode: string; expiresIn: number } | null>(null)
const pairCodeLoading = ref(false)

onMounted(() => {
  refreshDevices()
})

async function handleUnbind(deviceId: string) {
  const ok = await unbind(deviceId)
  if (ok) ElMessage.success('设备已解绑')
}

async function handleCleanAll() {
  // 保留第一个在线设备
  const onlineDevice = devices.value.find(d => d.online)
  if (!onlineDevice) {
    ElMessage.warning('没有在线设备可保留')
    return
  }

  try {
    await ElMessageBox.confirm(
      `将解绑所有设备，仅保留「${onlineDevice.deviceName}」`,
      '清理旧设备',
      { type: 'warning' }
    )
    const count = await cleanOldDevices(onlineDevice.deviceId)
    ElMessage.success(`已清理 ${count} 台旧设备`)
  } catch { /* 取消操作 */ }
}

async function handleResetAll() {
  try {
    await ElMessageBox.confirm(
      '将解绑所有设备，App 需要重新绑定',
      '完全重置',
      { type: 'warning' }
    )
    const count = await resetAll()
    ElMessage.success(`已解绑全部 ${count} 台设备`)
  } catch { /* 取消操作 */ }
}

async function handleGeneratePairCode() {
  pairCodeLoading.value = true
  try {
    pairCode.value = await generatePairCode()
  } finally {
    pairCodeLoading.value = false
  }
}
</script>

<style scoped>
.device-management {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.device-id {
  font-family: monospace;
  font-size: 12px;
  color: #666;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}

.stats {
  margin-top: 12px;
  color: #999;
  font-size: 14px;
}

.pair-code-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}

.pair-code-display {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.pair-code-display .code {
  font-size: 32px;
  font-weight: bold;
  font-family: monospace;
  letter-spacing: 8px;
  color: #409eff;
}

.pair-code-display .expires {
  color: #999;
  font-size: 14px;
}
</style>
```

---

## 数据流总览

```
Vue3 管理后台                              后端 API
     |                                      |
     |-- GET /devices --------------------->|  获取设备列表（基础）
     |<-- AppDevice[] ----------------------|
     |                                      |
     |-- GET /test-push/devices ----------->|  获取设备列表（含在线状态）
     |<-- { devices, total, onlineCount } --|
     |                                      |
     |-- GET /status ---------------------->|  检查是否有绑定设备
     |<-- { hasBoundDevice: bool } ---------|
     |                                      |
     |-- DELETE /devices/{id} ------------->|  解绑单个设备
     |<-- "设备已解绑" ---------------------|
     |                                      |
     |-- DELETE /devices/other?keep=X ----->|  解绑其他设备（保留 X）
     |<-- { removedCount, message } --------|
     |                                      |
     |-- DELETE /devices/all -------------->|  解绑所有设备
     |<-- { removedCount, message } --------|
     |                                      |
     |-- POST /generate-pair-code --------->|  生成配对码
     |<-- { pairCode, expiresIn } ----------|
     |                                      |
     |-- GET /history?page=1&size=20 ------>|  查询认证历史
     |<-- { records, total, pages } --------|
```

---

## 注意事项

1. **路由冲突：** `DELETE /devices/other` 和 `DELETE /devices/all` 与 `DELETE /devices/{deviceId}` 共享路径前缀。Spring 会优先匹配字面路径 `other`/`all`，不会将其当作 `{deviceId}` 变量。前端调用时无需特殊处理。

2. **设备数上限：** 每个用户最多绑定 5 台设备。超过上限时绑定接口返回 400 错误。前端应引导用户先清理旧设备。

3. **幂等绑定：** 同一用户使用同一 `deviceId` 重复绑定时，后端自动更新已有记录而非创建新记录。前端无需特殊处理。

4. **解绑后 App 需重新绑定：** 调用解绑 API 后，对应 App 的 WebSocket 连接将失效（`isDeviceBelongToUser` 返回 false）。App 需重新执行绑定流程。

5. **`encodeURIComponent`：** `deviceId` 可能包含特殊字符（如 `_`），在拼接 URL 路径时建议使用 `encodeURIComponent` 编码。
