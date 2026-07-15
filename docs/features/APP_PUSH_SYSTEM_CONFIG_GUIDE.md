# App 推送挑战系统配置指南

> **日期:** 2026-06-19  
> **关联:** App 推送挑战（Push Challenge）功能  
> **涉及页面:** 系统设置 → 安全配置

---

## 配置项列表

以下配置项已在 `t_system_config` 表中预置，属于 `SECURITY` 分类：

| 配置键 | 类型 | 默认值 | 说明 | 可编辑 |
|--------|------|--------|------|--------|
| `security.app_auth.enabled` | BOOLEAN | `true` | 是否启用 App 双因素认证 | ✅ |
| `security.app_auth.challenge_timeout` | NUMBER | `60` | App 认证挑战超时时间（秒） | ✅ |
| `security.app_auth.preferred_cipher` | STRING | `AES-256-GCM` | 首选加密算法（AES-256-GCM / SM4-GCM） | ✅ |
| `security.app_auth.push_challenge_show_correct` | BOOLEAN | `false` | 推送挑战时显示正确答案（调试用，生产环境应关闭） | ✅ |

---

## 配置项详情

### 1. `security.app_auth.push_challenge_show_correct`

- **类型:** BOOLEAN
- **默认值:** `false`
- **说明:** 推送挑战时显示正确答案（调试用，生产环境应关闭）
- **作用:**
  - `true`（调试模式）：API 只返回 `{deviceId, correctNumber, status}`，前端只显示一个绿色高亮的正确数字
  - `false`（正常模式，默认）：API 返回 `{deviceId, challengeId, numbers, expiresAt, status}`，前端显示 3 个候选数字

### 2. `security.app_auth.enabled`

- **类型:** BOOLEAN
- **默认值:** `true`
- **说明:** 是否启用 App 双因素认证

### 3. `security.app_auth.challenge_timeout`

- **类型:** NUMBER
- **默认值:** `60`
- **说明:** App 认证挑战超时时间（秒）

### 4. `security.app_auth.preferred_cipher`

- **类型:** STRING
- **默认值:** `AES-256-GCM`
- **说明:** 首选加密算法（AES-256-GCM / SM4-GCM）

---

## 前端展示位置

这些配置项在「系统设置 → 安全配置」页面的「其他安全配置」表格中自动展示：

- `push_challenge_show_correct` 为 BOOLEAN 类型，显示为 el-switch 开关
- `challenge_timeout` 为 NUMBER 类型，显示为数字输入框
- `preferred_cipher` 为 STRING 类型，显示为文本输入框
- `enabled` 为 BOOLEAN 类型，显示为 el-switch 开关

**无需修改 SystemSettings.vue 代码**，这些配置会自动出现在表格中。

---

## 数据库初始化 SQL

```sql
-- App 双因素认证配置
(2054000000000000221, 'security.app_auth.enabled', 'true', 'BOOLEAN', '是否启用 App 双因素认证', 'SECURITY', FALSE, TRUE, 18),
(2054000000000000222, 'security.app_auth.challenge_timeout', '60', 'NUMBER', 'App 认证挑战超时时间（秒）', 'SECURITY', FALSE, TRUE, 19),
(2054000000000000223, 'security.app_auth.preferred_cipher', 'AES-256-GCM', 'STRING', '首选加密算法（AES-256-GCM / SM4-GCM）', 'SECURITY', FALSE, TRUE, 20),
(2054000000000000224, 'security.app_auth.push_challenge_show_correct', 'false', 'BOOLEAN', '推送挑战时显示正确答案（调试用，生产环境应关闭）', 'SECURITY', FALSE, TRUE, 21)
```
