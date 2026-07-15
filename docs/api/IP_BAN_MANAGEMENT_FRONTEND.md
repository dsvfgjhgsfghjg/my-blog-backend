# IP封禁管理 API 对接指南

## 概述

提供IP封禁管理的完整功能，包括查看被封禁IP列表、手动封禁/解封IP、以及登录失败自动封禁IP的配置。

- **基础路径**: `/api/admin/security`
- **认证方式**: JWT Token（需管理员权限）
- **权限要求**: 
  - 查看类操作：需要 `@RequireAdmin`（管理员权限）
  - 写入类操作：需要 `@RequireAdmin` + `@RequireTotp`（管理员 + TOTP双因素认证）

---

## API 接口

### 1. 获取被封禁IP列表

```
GET /api/admin/security/banned-ips
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 2054000000000000111,
      "ipAddress": "192.168.1.100",
      "reason": "登录失败次数过多（23次，阈值20次）",
      "banUntil": "2026-06-11T22:00:00",
      "triggerCount": 23,
      "createTime": "2026-06-10T22:00:00"
    },
    {
      "id": 2054000000000000112,
      "ipAddress": "10.0.0.5",
      "reason": "管理员手动封禁",
      "banUntil": "2026-06-11T22:30:00",
      "triggerCount": 0,
      "createTime": "2026-06-10T22:30:00"
    }
  ]
}
```

#### IpBan 数据结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 记录ID（雪花ID） |
| ipAddress | string | 被封禁的IP地址 |
| reason | string | 封禁原因（自动封禁会注明失败次数和阈值） |
| banUntil | string (datetime) | 封禁到期时间（ISO格式） |
| triggerCount | number | 触发次数（自动封禁时为失败次数，手动封禁为0） |
| createTime | string (datetime) | 封禁创建时间（ISO格式） |

---

### 2. 手动封禁IP

```
POST /api/admin/security/ban-ip?ipAddress={ip}&reason={reason}&durationHours={hours}
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |
| X-TOTP-Code | {totp_code}（需要TOTP验证） |

#### 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| ipAddress | string | 是 | - | 要封禁的IP地址 |
| reason | string | 否 | "管理员手动封禁" | 封禁原因 |
| durationHours | number | 否 | 24 | 封禁时长（小时） |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

#### 错误响应示例

```json
{
  "code": 400,
  "message": "TOTP验证失败",
  "data": null
}
```

---

### 3. 解除IP封禁

```
DELETE /api/admin/security/unban-ip/{ipAddress}
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |
| X-TOTP-Code | {totp_code}（需要TOTP验证） |

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| ipAddress | string | 要解封的IP地址 |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 4. 获取频繁违规IP统计

```
GET /api/admin/security/frequent-violators?minCount={minCount}
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |

#### 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| minCount | number | 否 | 5 | 最小触发次数，用于筛选频繁违规的IP |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "ip_address": "192.168.1.100",
      "event_count": 23
    },
    {
      "ip_address": "10.0.0.5",
      "event_count": 8
    }
  ]
}
```

---

### 5. 获取威胁等级统计

```
GET /api/admin/security/threat-stats
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "threat_level": "CRITICAL",
      "count": 3
    },
    {
      "threat_level": "HIGH",
      "count": 12
    },
    {
      "threat_level": "MEDIUM",
      "count": 45
    },
    {
      "threat_level": "LOW",
      "count": 120
    }
  ]
}
```

---

### 6. 清理过期数据

```
POST /api/admin/security/cleanup
```

#### 请求头

| 字段 | 值 |
|------|-----|
| Authorization | Bearer {token} |

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "eventsDeleted": 100,
    "bansDeleted": 5
  }
}
```

#### 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| eventsDeleted | number | 已清理的过期安全事件数（保留90天） |
| bansDeleted | number | 已清理的过期IP封禁记录数 |

---

## 登录失败自动封禁IP配置（系统配置）

在系统设置中增加以下三个配置项，用于控制登录失败自动封禁IP的行为：

### 配置项列表

| 配置Key | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| security.login.ban_ip_enabled | BOOLEAN | false | 是否启用登录失败IP封禁功能 |
| security.login.ban_ip_threshold | NUMBER | 20 | 触发IP封禁的登录失败次数（IP级别总次数） |
| security.login.ban_ip_duration_hours | NUMBER | 24 | IP封禁时长（小时） |

### 配置说明

1. **ban_ip_enabled**：控制开关，设为 `true` 后才会记录IP级失败计数并自动封禁
2. **ban_ip_threshold**：比如设为20，意味着同一个IP在24小时内累计20次登录失败（不限用户名）就会触发封禁
3. **ban_ip_duration_hours**：封禁时长，比如设为24表示封禁24小时后自动解封

### 使用系统配置 API

参考系统配置管理接口进行读取和修改（通常在 `/api/admin/system-config` 或类似路径）。

---

## 前端对接建议

### 页面设计建议

建议在系统设置中增加 **"IP封禁管理"** 页面，包含以下区域：

1. **IP封禁列表**：展示 `banned-ips` 接口返回的数据
   - 表格列：IP地址、封禁原因、封禁到期时间（显示倒计时）、触发次数、操作按钮
   - 操作按钮："解封"（需TOTP验证）

2. **手动封禁表单**：
   - IP地址输入框
   - 封禁原因输入框（可选，默认"管理员手动封禁"）
   - 封禁时长输入框（可选，默认24小时）
   - "封禁"按钮（需TOTP验证）

3. **自动封禁配置区**：
   - 启用/禁用开关（对应 `ban_ip_enabled`）
   - 触发阈值输入框（对应 `ban_ip_threshold`）
   - 封禁时长输入框（对应 `ban_ip_duration_hours`）
   - "保存配置"按钮

4. **统计数据区**（可选）：
   - 频繁违规IP列表（`frequent-violators`）
   - 威胁等级分布（`threat-stats`）
   - "清理过期数据"按钮（`cleanup`）

### TOTP验证处理

对于封禁和解封操作，需要在请求头中携带 `X-TOTP-Code`：

```javascript
// 示例：封禁IP时弹出TOTP验证框
async function handleBanIp(ipAddress, reason, durationHours) {
  // 1. 先弹出TOTP验证对话框
  const totpCode = await showTotpDialog();
  if (!totpCode) return;

  // 2. 发起请求
  const response = await fetch(`/api/admin/security/ban-ip?ipAddress=${ipAddress}&reason=${encodeURIComponent(reason)}&durationHours=${durationHours}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-TOTP-Code': totpCode
    }
  });

  if (response.ok) {
    // 3. 刷新列表
    await loadBannedIps();
  }
}
```

### 倒计时显示示例

```javascript
function getRemainingTime(banUntil) {
  const now = new Date();
  const end = new Date(banUntil);
  const diffMs = end - now;

  if (diffMs <= 0) return '已过期';

  const hours = Math.floor(diffMs / (1000 * 60 * 60));
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

  if (hours > 0) {
    return `剩余 ${hours} 小时 ${minutes} 分钟`;
  }
  return `剩余 ${minutes} 分钟`;
}
```
