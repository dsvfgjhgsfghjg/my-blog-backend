# 2026-06-19 开发日报

> **项目:** MyBlog - App 双因素认证系统  
> **提交范围:** `ebad652` → `06a8f61`（共 14 个提交）  
> **核心主题:** App 设备持久化、推送挑战完善、前端集成指南

---

## 一、今日开发概览

今日共完成 **14 个提交**，涵盖后端功能增强、Bug 修复、数据库迁移、前端文档等多个方面。核心聚焦于 **App 双因素认证系统的设备持久化机制** 和 **推送挑战功能的完善**。

### 提交清单

| # | 提交 | 类型 | 描述 |
|---|------|------|------|
| 1 | `ebad652` | 🐛 fix | 修复推送挑战时 `targetUserId=0` 未转换为当前用户 ID |
| 2 | `a597a0c` | 🐛 fix | 修复推送挑战 JSON 类型错误 & Flyway 迁移版本号冲突 |
| 3 | `5f437af` | 🐛 fix | 修复 PostgreSQL JSON 列类型转换 & .gitignore 误忽略 config 包 |
| 4 | `b850454` | ✨ feat | 推送/轮询 API 返回 `numbers` 字段 & 新增 Vue3 前端处理指南 |
| 5 | `58f919a` | 🐛 fix | 推送新挑战时自动失效旧挑战，防止复用 |
| 6 | `6f40428` | 🐛 fix | 设备绑定幂等化 & 新增设备特征码绑定指南 |
| 7 | `5eb72cb` | ✨ feat | 新增批量解绑设备 API（清理旧重复设备） |
| 8 | `4ad4439` | 📝 docs | 新增 Vue3 前端设备管理指南（含完整 API） |
| 9 | `f5753f6` | 🐛 fix | 修复 DH 握手后自动推送挑战的 bug + 新增调试模式配置说明文档 |
| 10 | `491dfc9` | 🐛 fix | 添加数据库迁移脚本 - `t_auth_history` 表缺少 `correct_number` 列 |
| 11 | `2d5df4e` | 🐛 fix | 添加缺失的 `push_challenge_show_correct` 系统配置项 |
| 12 | `ec28a98` | ✨ feat | 实现 App 设备持久化设备码机制 |
| 13 | `334e411` | 🐛 fix | `BindPasswordRequest` 缺少 `deviceSecret` 字段导致编译失败 |
| 14 | `06a8f61` | 🐛 fix | `generateDeviceSecret` 不再预插入数据库记录，避免 `user_id NOT NULL` 约束冲突 |

---

## 二、核心功能开发

### 2.1 App 设备持久化设备码机制（Device Secret）

**背景：** 之前 App 每次登录都生成新的 `deviceId`，导致数据库产生大量重复设备记录，旧设备无法收到推送挑战。

**解决方案：** 引入服务端生成的持久化设备码（Device Secret）机制。

#### 新增 API

| 接口 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/api/auth/app/device-secret` | POST | 生成设备码（deviceId + deviceSecret） | ❌ 无需认证 |

#### 核心设计

- **deviceId**：格式 `svr_<UUID>`，服务端生成，不可伪造
- **deviceSecret**：32 字节随机密钥（64 位 hex），BCrypt 哈希存储
- **deviceSecretHint**：前4位 + ... + 后4位，用于 UI 展示
- **加密信道下发**：设备码通过 DH 协商的 AES-GCM 加密通道下发

#### 数据库变更

```sql
ALTER TABLE t_app_device 
ADD COLUMN device_secret VARCHAR(64) DEFAULT NULL,
ADD COLUMN device_secret_hint VARCHAR(20) DEFAULT NULL;
```

#### 涉及文件

| 文件 | 变更 |
|------|------|
| `AppAuthService.java` | 新增 `generateDeviceSecret()`、`verifyDeviceSecret()`、`isDeviceBound()` |
| `AppAuthController.java` | 新增 `POST /device-secret` 端点，`BindPasswordRequest` 增加 `deviceSecret` 字段 |
| `AuthWebSocketHandler.java` | WebSocket bind 消息增加 `deviceSecret` 验证 |
| `SecurityConfig.java` | 放行 `/api/auth/app/device-secret` 公开接口 |
| `AppDevice.java` | 新增 `deviceSecret`、`deviceSecretHint` 字段 |
| `sql/add_device_secret_to_app_device.sql` | 数据库迁移脚本 |

---

### 2.2 设备绑定幂等化

**背景：** 同一用户重复绑定同一设备会创建重复记录。

**解决方案：** 后端支持幂等绑定，同一用户 + 同一 deviceId 重复绑定时更新已有记录。

#### 幂等绑定规则

| 场景 | 后端行为 |
|------|----------|
| 同一用户 + 同一 deviceId + 首次绑定 | 创建设备记录，返回 Token |
| 同一用户 + 同一 deviceId + 重复绑定 | **更新设备名称和时间**，返回新 Token |
| 同一用户 + 新 deviceId | 创建新设备记录（上限 5 台） |
| 不同用户 + 已被绑定的 deviceId | **拒绝**：该设备已被其他账号绑定 |
| 同一用户已有 5 台设备 + 新 deviceId | **拒绝**：已达到最大绑定设备数 |

#### 新增批量解绑 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/app/devices/other` | DELETE | 解绑所有其他设备（保留指定设备） |
| `/api/auth/app/devices/all` | DELETE | 解绑所有设备（完全重置） |

---

### 2.3 推送挑战功能完善

#### 修复的问题

1. **`targetUserId=0` 未转换**：修复推送挑战时 `targetUserId=0` 未自动转换为当前用户 ID
2. **JSON 类型错误**：修复 `AuthHistory.numbers` 字段在 PostgreSQL 中的 JSON 列类型转换问题
3. **旧挑战复用**：推送新挑战时自动失效旧挑战，防止复用
4. **DH 握手后自动推送**：修复 DH 握手完成后自动推送挑战的 bug，改为由管理后台手动触发

#### 新增功能

- **推送/轮询 API 返回 `numbers` 字段**：前端可以直接看到推送的 3 个候选数字
- **调试模式配置**：新增 `push_challenge_show_correct` 系统配置项，调试模式下 API 返回正确答案
- **`correct_number` 字段**：`t_auth_history` 表新增 `correct_number` 列，记录正确答案

#### 系统配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `security.app_auth.enabled` | `true` | 是否启用 App 双因素认证 |
| `security.app_auth.challenge_timeout` | `60` | 挑战超时时间（秒） |
| `security.app_auth.preferred_cipher` | `AES-256-GCM` | 首选加密算法 |
| `security.app_auth.push_challenge_show_correct` | `false` | 推送挑战时显示正确答案（调试用） |

---

## 三、文档建设

### 新增文档

| 文档 | 说明 |
|------|------|
| `docs/api/APP_DEVICE_SECRET_GUIDE.md` | App 设备码（Device Secret）重构指南（205 行） |
| `docs/api/APP_DEVICE_FINGERPRINT_GUIDE.md` | App 设备特征码绑定指南（396 行，含完整 Kotlin 代码） |
| `docs/api/VUE3_DEVICE_MANAGEMENT_GUIDE.md` | Vue3 前端设备管理指南（729 行，含完整 Vue 组件） |
| `docs/api/VUE3_PUSH_CHALLENGE_GUIDE.md` | Vue3 前端推送挑战处理指南 v2.0（1152 行，含完整 Vue 组件） |
| `docs/features/APP_PUSH_SYSTEM_CONFIG_GUIDE.md` | App 推送挑战系统配置指南（74 行） |
| `docs/frontend/报错/2026-06-19 15:33 前端推送调试模式配置说明.md` | 前端推送调试模式配置说明 |
| `docs/frontend/报错/2026-06-19 15:47 前端推送报错.md` | 前端推送报错记录 |
| `docs/frontend/报错/2026-06-19 15:50 前端推送实现纠正指南.md` | 前端推送实现纠正指南 |

### 文档特点

- **VUE3_PUSH_CHALLENGE_GUIDE.md**（1152 行）：包含完整的 TypeScript 类型定义、Service 封装、Vue 组件（含 Element Plus UI），可直接复制使用
- **VUE3_DEVICE_MANAGEMENT_GUIDE.md**（729 行）：包含完整的设备管理 CRUD 操作、Composable 封装、Vue 组件
- **APP_DEVICE_FINGERPRINT_GUIDE.md**（396 行）：包含完整的 Kotlin 实现代码、持久化方案对比、测试用例

---

## 四、Bug 修复汇总

| Bug | 原因 | 修复 |
|-----|------|------|
| `targetUserId=0` 未转换 | 前端传 0 表示推给自己，后端未处理 | 在 `AppPushTestController.pushChallenge()` 中增加 0→当前用户 ID 转换 |
| 推送挑战 JSON 类型错误 | `AuthHistory.numbers` 字段在 PostgreSQL 中需要 JSON 类型 | 新增 `PostgreJsonTypeHandler` 自定义类型处理器 |
| Flyway 迁移版本号冲突 | 多个分支的迁移文件版本号重复 | 重构迁移文件版本号（V9-V31），清理重复历史 |
| .gitignore 误忽略 config 包 | `.gitignore` 中 `config/` 模式匹配了 `src/main/java/com/leo/myblog/config/` | 修复 `.gitignore` 规则 |
| 旧挑战可复用 | 推送新挑战时未失效旧挑战 | 新增 `invalidateUserChallenges()` 方法，推送前清理旧挑战 |
| DH 握手后自动推送挑战 | 握手完成后立即推送，不符合设计 | 移除自动推送逻辑，改为管理后台手动触发 |
| `BindPasswordRequest` 缺少 `deviceSecret` | 新增字段未添加到请求体类 | 补充 `deviceSecret` 字段 |
| `generateDeviceSecret` 预插入数据库 | 设备未绑定时 `user_id` 为 NULL，违反 NOT NULL 约束 | 改为只生成不插入，绑定时再创建记录 |

---

## 五、数据库变更

### 新增迁移脚本

| 文件 | 说明 |
|------|------|
| `sql/add_device_secret_to_app_device.sql` | `t_app_device` 表新增 `device_secret`、`device_secret_hint` 列 |
| `sql/add_correct_number_to_auth_history.sql` | `t_auth_history` 表新增 `correct_number` 列 |
| `sql/init_system_config.sql` | 新增 4 条 App 双因素认证系统配置项 |
| `V32__add_push_challenge_show_correct_config.sql` | Flyway 迁移：新增 `push_challenge_show_correct` 配置项 |

### Flyway 迁移重构

- 清理了重复的迁移历史（V9-V31 重新编号）
- 解决了多个分支合并导致的版本号冲突问题

---

## 六、安全增强

| 措施 | 说明 |
|------|------|
| 设备密钥验证 | WebSocket 连接时验证 `deviceSecret`，防止设备码伪造 |
| 旧连接关闭 | 同一 deviceId 的新连接强制关闭旧连接，防止多设备重用 |
| 挑战失效机制 | 推送新挑战前自动失效旧挑战，防止复用 |
| 绑定频率限制 | 5 次失败后锁定 5 分钟 |
| 挑战频率限制 | 每分钟最多生成 10 个挑战 |
| 设备数上限 | 每个用户最多绑定 5 台设备 |

---

## 七、文件变更统计

### 新增文件（15 个）

```
docs/api/APP_DEVICE_SECRET_GUIDE.md
docs/api/APP_DEVICE_FINGERPRINT_GUIDE.md
docs/api/VUE3_DEVICE_MANAGEMENT_GUIDE.md
docs/api/VUE3_PUSH_CHALLENGE_GUIDE.md
docs/features/APP_PUSH_SYSTEM_CONFIG_GUIDE.md
docs/frontend/报错/2026-06-19 15:33 前端推送调试模式配置说明.md
docs/frontend/报错/2026-06-19 15:47 前端推送报错.md
docs/frontend/报错/2026-06-19 15:50 前端推送实现纠正指南.md
sql/add_device_secret_to_app_device.sql
sql/add_correct_number_to_auth_history.sql
src/main/java/com/leo/myblog/config/PostgreJsonTypeHandler.java
src/main/resources/db/migration/V32__add_push_challenge_show_correct_config.sql
src/main/resources/db/migration/V10__add_phone_and_email_to_user.sql
src/main/resources/db/migration/V11__create_comment_table.sql
src/main/resources/db/migration/V12__add_super_admin_and_ability_constraints.sql
...（Flyway 迁移文件 V10-V31 重构）
```

### 修改文件（核心）

| 文件 | 变更内容 |
|------|----------|
| `AppAuthService.java` | 设备码生成/验证、幂等绑定、批量解绑、挑战失效 |
| `AppAuthController.java` | 新增 device-secret 端点、批量解绑端点、BindPasswordRequest 增加 deviceSecret |
| `AuthWebSocketHandler.java` | WebSocket bind 增加 deviceSecret 验证、旧连接关闭、挑战失效 |
| `AppPushTestController.java` | targetUserId=0 转换、调试模式、推送前失效旧挑战 |
| `SecurityConfig.java` | 放行 device-secret 公开接口 |
| `AppDevice.java` | 新增 deviceSecret、deviceSecretHint 字段 |
| `AuthHistory.java` | 新增 correctNumber 字段、JSON 类型处理器 |
| `AuthHistoryResponse.java` | 新增 correctNumber 字段 |
| `UserResponse.java` | 用户信息响应调整 |
| `UserService.java` | 用户服务调整 |
| `.gitignore` | 修复误忽略 config 包 |

---

## 八、技术债务与后续计划

### 待优化项

1. **App 端实现**：需要 Android App 端实现设备码持久化存储和 WebSocket 连接增强
2. **前端页面集成**：管理后台需要集成设备管理页面和推送测试页面
3. **国密 SM4 支持**：加密算法已支持 SM4-GCM，但需要 App 端实现
4. **WebSocket 重连风暴**：已有分析文档，需要前端实现指数退避重连策略

### 已知问题

- 无重大已知问题，所有已发现的 Bug 均已修复

---

## 九、总结

今日开发重点完成了 **App 双因素认证系统的设备持久化机制**，解决了设备重复注册、挑战复用、推送流程不完善等核心问题。同时产出了 **5 份高质量开发指南文档**（总计超过 2500 行），为 App 端开发和前端集成提供了完整的参考实现。

**关键里程碑：**
- ✅ 设备码（Device Secret）机制：服务端生成、BCrypt 存储、WebSocket 验证
- ✅ 设备绑定幂等化：同一设备重复绑定不创建重复记录
- ✅ 批量解绑 API：清理旧设备、完全重置
- ✅ 推送挑战完善：调试模式、旧挑战失效、numbers 返回
- ✅ 系统配置项：4 个 App 双因素认证配置项
- ✅ 数据库迁移：2 个 SQL 脚本 + Flyway 迁移重构
- ✅ 开发文档：5 份完整指南（2500+ 行）
