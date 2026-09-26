# 审计日志系统

> 现状说明（最后核对：2026-09-26，对应后端提交 `3597f93`）。本文描述的是**当前实现**。
> 设备信息展示相关的历史文档见 [AUDIT_LOG_ENHANCEMENT.md](AUDIT_LOG_ENHANCEMENT.md)。

## 概述

审计日志回答四个问题：**谁**、在什么**时间**、对**什么对象**、做了**什么**，结果如何。

- 落库表：`t_audit_log`；查询入口：`/api/system/audit-logs`（`@RequireAdmin`，非管理员 403）
- 记录方式：AOP 切面自动记录，业务代码不感知
- 每条日志带一个**五级等级**（INFO/NOTICE/WARNING/ERROR/CRITICAL），管理员可以一眼看出
  「这条只是查了一下」还是「这条把某人的权限改了」

2026-09 的改造修掉了三处结构性缺陷，其中两处是正在发生的安全问题：

| 缺陷 | 改造前 | 现在 |
|---|---|---|
| 凭据明文入库 | 只判断**形参名**是否含 `password`，而登录方法的形参名是 `request`，判断永远不成立 → 密码/clientSecret/refreshToken 全部明文写入 `request_params` | `AuditParamSanitizer` 递归遍历对象树、按**字段名**打码（见 [脱敏](#脱敏安全修复)） |
| 失败被记成成功 | 只在 `catch` 里置 `success=false`，而控制器大量 `return Result.error(...)` 不抛异常 → 「TOTP 校验失败」记成 `success=true` | 检查返回值，`Result.code` 非 0/200 即判失败 |
| 描述读不懂 | 36 个 `@RequireAdmin` 方法里 **14 个**（39%）推导为 `OTHER`，文案是「SYSTEM模块的OTHER操作」 | 规则表重写，**OTHER 归零**（有单测钉死） |

## 处理链路

```
切点命中
  └─ 解析元数据（AuditMetadataResolver：注解优先，推导兜底）
       ├─ 操作类型 AuditActionResolver   ← 决定基础等级
       ├─ 模块     AuditModuleResolver
       ├─ 操作对象 AuditObjectExtractor
       └─ 描述     模板渲染 或 通用拼装
  └─ 脱敏请求参数（AuditParamSanitizer）
  └─ 执行目标方法
  └─ 判定结果（success / responseStatus / responseMessage）
  └─ 按结果升级等级（AuditLevelResolver）
  └─ 落库（落库本身失败只记 error 日志，不影响主业务）
```

**切面只做编排**（`aspect/AuditLogAspect.java`），推导逻辑全部抽在 `aspect/audit/` 下的 6 个
纯函数类里 —— 它们没有 Spring 依赖，可以直接单测，所以推导规则有回归保护。

| 类 | 职责 |
|---|---|
| `AuditActionResolver` | 方法名 → 操作类型（有序规则表） |
| `AuditModuleResolver` | 控制器类 → 模块（显式类名映射表） |
| `AuditObjectExtractor` | 从实参里提取「操作对象」 |
| `AuditMetadataResolver` | 编排上述三者 + 描述模板渲染，产出最终元数据 |
| `AuditLevelResolver` | 动作 → 基础等级；结合结果给出最终等级 |
| `AuditParamSanitizer` | 请求参数脱敏（递归打码、降采样、限量） |

## 五级等级

取值与存储见 `enums/AuditLevel.java`。**存储大写英文，中文由前端显示** ——
枚举自带 `getDescription()`（信息/记录/警告/错误/关键）备查，前端也维护一份显示映射。
枚举的声明顺序即严重度顺序，`escalate()`/`max()` 依赖 ordinal。

| 等级 | 中文 | 基础触发动作（成功时） | 归类原则 |
|---|---|---|---|
| `INFO` | 信息 | `QUERY` `EXPORT` `LOGOUT` | 只看，不改变任何状态 |
| `NOTICE` | 记录 | `LOGIN` `REGISTER` `CREATE` `UPDATE` `UPLOAD` `AUDIT` | 改东西 |
| `WARNING` | 警告 | `DELETE` `TOGGLE_STATUS` `REVOKE` `REVOKE_SESSION` `UNBAN_IP` `MIGRATE` `CLEANUP` `RESET_CONFIG` | 能毁数据或关防护 |
| `ERROR` | 错误 | 无基础映射 | 只由「失败升级」产生 |
| `CRITICAL` | 关键 | `RESET_PASSWORD` `CHANGE_PASSWORD` `CHANGE_ROLE` `CHANGE_ABILITY` `SET_SUPER_ADMIN` `BAN_IP` | 够拿到账号 |

**等级由操作类型单一推导，不写在注解里**。原因：Java 注解的 enum 默认值无法区分「没写」
和「显式写了 NOTICE」，一旦允许手写就会与动作语义打架。所以 `@AuditLog` 刻意**没有** `level` 属性。

### 失败升级

失败一定至少升到 `WARNING`（所以「401/403 越权尝试要升到警告」这条子规则被自动涵盖），
非业务异常（非 `BusinessException`，即系统故障、NPE、SQL 异常等）再升到 `ERROR`；
**`CRITICAL` 不回退** —— 权限变更失败同样是最高优先级。

| 基础等级 | 成功 | 失败（业务异常） | 失败（其他异常） |
|---|---|---|---|
| `INFO` | INFO | **WARNING** | **ERROR** |
| `NOTICE` | NOTICE | **WARNING** | **ERROR** |
| `WARNING` | WARNING | **ERROR** | **ERROR** |
| `CRITICAL` | CRITICAL | CRITICAL | CRITICAL |
| 未登记 / `OTHER` | NOTICE | WARNING | ERROR |

**未登记的动作回退 `NOTICE`**（而不是 `INFO`）：新写的操作默认被当成「有副作用」，
而不是「只是看了看」。目前只有 `OTHER` 走这条兜底 —— 新增动作时请显式登记到上面的集合里，
别指望兜底恰好得到期望的等级：`UPDATE_CONFIG` 一度就是这样，兜底给的 `NOTICE` 看似合理，
但配置里包含 TOTP 开关之类安全项，改配置与重置配置风险相当，应当是 `WARNING`。

## 操作类型（25 个）

取值是**封闭清单**（`common/AuditAction.java`），`AuditActionResolver` 只产出这里的值。
新增动作要同步三处：`AuditAction`、`AuditActionResolver`、前端 `AuditLogManagement.vue` 的 `ACTIONS`。

| 分类 | 取值 |
|---|---|
| 身份 | `LOGIN` `LOGOUT` `REGISTER` `CHANGE_PASSWORD` `RESET_PASSWORD` |
| 权限 | `CHANGE_ROLE` `SET_SUPER_ADMIN` `CHANGE_ABILITY` |
| 增删改查 | `CREATE` `UPDATE` `DELETE` `QUERY` `TOGGLE_STATUS` |
| 业务特有 | `UPLOAD` `AUDIT` `UPDATE_CONFIG` `RESET_CONFIG` `MIGRATE` `CLEANUP` `EXPORT` |
| 会话 | `REVOKE` `REVOKE_SESSION` |
| 安全 | `BAN_IP` `UNBAN_IP` |
| 兜底 | `OTHER` |

每个取值都有中文标签（`AuditAction.label()`），描述生成与前端筛选都用它。

### 推导：有序规则表

改造前是一串 `contains` 判断，顺序错了就出错（`update` 抢在 `role` 之前，把「改角色」
判成普通 `UPDATE`；13 个 `get*` 全部落 `OTHER`）。现在改为**有序规则表**，
**规则顺序即契约**，改动顺序前先看 `AuditActionResolverTest` 的回归用例：

1. **只读前缀**：`get` / `list` / `query` / `search` / `count` —— 必须最先。
   否则 `getBannedIps` 会被 `ban` 抢走、`getUserAbilities` 会被 `abilit` 抢走。
2. **多词精确语义**：`reset+password` → `RESET_PASSWORD`；`change+password` → `CHANGE_PASSWORD`；
   `superadmin` → `SET_SUPER_ADMIN`；`role` → `CHANGE_ROLE`；`abilit` **且带写动词**
   → `CHANGE_ABILITY`；`unban` **先于** `ban`；`config+reset` → `RESET_CONFIG`；
   `config+(update|batch|set)` → `UPDATE_CONFIG`；`expired+(delete|clean)` → `CLEANUP`
   **先于** `delete`；`audit*` 前缀 → `AUDIT`；`migrate` → `MIGRATE`。
3. **身份**：`login` / `logout` / `register` / 其他含 `password` 的（如两步登录的 `step2Password`）。
4. **会话与文件**：`revoke+session` → `REVOKE_SESSION`；`revoke` → `REVOKE`；`upload` → `UPLOAD`；`export` → `EXPORT`。
5. **通用动词**：`delete|remove` → `DELETE`；`toggle|enable|disable` → `TOGGLE_STATUS`；
   `reset` → `RESET_CONFIG`；`create|add|save` → `CREATE`；`update|edit|modify` → `UPDATE`。
6. **中段只读词**：`stats` / `detail` / `info` → `QUERY`（放在写动词之后，避免 `getCreatedXxx` 这类命名误判）。

## 模块（12 个）

`USER` `POST` `COMMENT` `TAG` `CATEGORY` `AUDIO` `BLOGGER` `SAYING` `BUG_REPORT` `SECURITY`
`SYSTEM` `AUTH`（同样有中文标签；新增模块要同步 `AuditModule`、`AuditModuleResolver`、前端 `MODULES`）。

`AuditModuleResolver` 用**显式类名映射表**，兜底策略是「类名去掉 `Controller` 后缀转大写下划线」
（`FooBarController` → `FOO_BAR`），**绝不返回 `SYSTEM`**。改造前靠 `className.contains("User")`
这类模糊匹配，导致名言管理、Bug 反馈、安全中心、登录认证一律显示成「系统设置」，
日志里根本分不出是谁在动什么。

## 描述生成

这是用户反馈的核心（原话：「操作描述说的啥 SYSTEM模块的OTHER操作」）。优先级：

1. `@AuditLog` 注解的 `description` 模板（模板渲染后）
2. **动作中文 + 操作对象** 自动拼装（如 `审核 45`、`重置密码 123`）
3. 兜底：**动作中文（HTTP 方法 URI）**（如 `其他操作（POST /api/x）`）

**绝不产出「XX模块的OTHER操作」** —— 有单测（`AuditMetadataResolverTest#noMoreMeaninglessDescription`）
拿 14 个真实控制器的全部方法扫一遍钉死这个约束。

真实例子：

| 方法 | 等级 | 描述 |
|---|---|---|
| `SecurityAdminController#banIp` | 关键 | 封禁 IP 203.0.113.7，时长 24 小时 |
| `UserController#setSuperAdmin` | 关键 | 设置超级管理员 123 |
| `UserController#updateUserRole` | 关键 | 修改用户 123 的角色（管理员：true） |
| `UserController#resetPassword` | 关键 | 重置密码 123 |
| `AuditLogController#deleteExpiredLogs` | 警告 | 清理 30 天前的审计日志 |
| `SystemConfigController#updateConfig` | 记录 | 修改配置 site.title |

### 描述模板

`@AuditLog(description = "...")` 支持三种占位符，取不到值时替换成空串（不打印 `null`）：

| 占位符 | 含义 | 示例 |
|---|---|---|
| `{形参名}` | 该形参的实参 | `{days}` → `30` |
| `{形参名.字段名}` | 该形参的一级 getter（先 `getXxx` 再 `isXxx`） | `{request.configKey}` → `site.title` |
| `{0}` `{1}` | 按位置取实参 | `{0}` |

模板依赖**编译期保留形参名**（`-parameters`，Spring Boot Gradle 插件默认开启）。
若哪天被关掉，形参名会变成 `arg0`，模板与对象提取会整体失效 —— `AuditMetadataResolverTest`
里有一条用例专门守住这个开关。

### 操作对象怎么取

`AuditObjectExtractor` **靠参数注解区分操作者与目标，而不是靠参数名**：

- 带 `@RequestAttribute` / `@RequestHeader` 的形参**永不**作为操作对象
  （操作者信息已有独立的 `user_id`/`username` 列）
- 所以 `resetPassword(@RequestAttribute userId, @PathVariable targetUserId)` 取到的是**目标用户**，
  而 `getUserAbilities(@PathVariable userId)` 取到的是**该用户自己** —— 只看参数名无法区分，看注解一目了然
- 优先级：注解的 `object()` 显式指定 → 白名单形参名（`targetUserId`/`postId`/`configKey`/`key`/…）
  → 第一个 `@PathVariable`/`@RequestParam` 标量 → `@RequestBody` DTO 的标识字段
  （`getId` → `getConfigKey` → `getKey` → `getTitle` → `getName` → `getUsername` → `getIpAddress` → 任何 `*Id`）
- 分页/开关类形参（`pageNum`/`pageSize`/`keyword`/`days`/`reason`/`status`/…）被排除，
  取不到对象时**省略对象后缀**，不打印「未知对象」

## `@AuditLog` 注解

`annotation/AuditLog.java`，属性：

| 属性 | 默认 | 说明 |
|---|---|---|
| `module()` | `""` | 模块值；留空按控制器类推导 |
| `action()` | `""` | 操作类型，**同时决定基础等级**；留空按方法名推导 |
| `description()` | `""` | 描述模板；留空走通用拼装 |
| `object()` | `""` | 指定哪个形参是操作对象；留空自动挑选 |
| `recordParams()` | `true` | 是否记录请求参数（脱敏始终执行） |
| `logOnSuccess()` | `true` | 成功时是否落库，给高频只读接口降噪；**失败一律记录** |

与无属性的 `@RequireAdmin` **并存、不扩展它**：那个管「能不能调」（鉴权），这个管「怎么记」（审计）。
合并会让权限与审计耦合，而且 `@RequireAdmin` 覆盖的方法里有十来个是纯查询，表达不了
「查询只记 `INFO`」；此外登录、改密码这类**非管理员**方法也要审计。

未加注解的方法走自动推导，两者产出的字段结构完全一致，因此可以**渐进覆盖**：
只给最关键的模块加注解，其余靠推导。

目前显式使用注解的只有 3 处（封禁 IP、改角色、清理过期日志），其余全靠推导。

## 切点覆盖范围

`AuditLogAspect` 的 `@Around` 表达式，四类：

1. `@RequireAdmin` —— 全部 36 个管理员接口
2. `@AuditLog` —— 显式声明审计元数据的方法
3. `@RequireTotp` —— 需要二次验证的敏感操作。它的语义天然就是「敏感」，正好补上原先的盲区：
   **改系统配置、删文章、改密码、启停 2FA 此前完全没有日志**
4. 认证/会话的关键方法（控制器未被注解覆盖时用 `execution` 兜住）：
   `LoginController.step2Password` / `step2Challenge`、
   `UserController.login` / `logout` / `register`、
   `OAuth2Controller.token` / `refreshToken` / `revokeToken`、
   `UserSessionController.revokeSession` / `revokeOtherSessions`

**刻意排除 `LoginController.pollResult`**：前端每 2 秒轮询一次，纳入后一次登录会灌进几百条日志，
把审计表变成噪声。

## 脱敏（安全修复）

改造前只判断**形参名**是否含 `password`，而 `login(@RequestBody LoginRequest request)`
的形参名是 `request`，判断永远不成立 ⇒ `request_params` 里躺着明文密码、`clientSecret`、
`refreshToken`。现在由 `AuditParamSanitizer` 递归遍历对象树、**按字段名**打码。

| 策略 | 字段（键名归一化后比较） | 处理 |
|---|---|---|
| 硬掩码 | `password` `passwd` `pwd` `oldPassword` `newPassword` `confirmPassword` `secret` `clientSecret` `secretKey` `privateKey` `credential(s)` `token` `accessToken` `refreshToken` `idToken` `authorization` `cookie` `totpCode` `backupCode` `captchaCode` `verifyCode` `smsCode` `authCode` `code` | 统一替换为 `***MASKED***`（刻意不带长度/前后缀，避免泄露 token 类型） |
| 部分掩码 | `phone` `mobile` `email` `idCard` `idNumber` `realName` `bankCard` | 保留首尾：`138****34`、`ab***@example.com` |
| 降采样 | `captchaTrajectory` `trajectory` `points` `path` `trace` | 数组 → `[N 点轨迹]`（否则一次登录灌进几千个坐标点） |
| 跳过 | 带 `@RequestAttribute`/`@RequestHeader` 的形参、`HttpServletRequest`/`HttpServletResponse` | 不写入参数（操作者已有独立列） |
| 限量 | 全量 | 深度 3、对象 50 键、数组 20 项、单值 256 字符、总量 8KB |

**键名归一化**是必须的：项目 Jackson 输出 snake_case（`client_secret`/`refresh_token`），
而掩码表按 camelCase 书写。去掉 `_` 和 `-` 后再比对，两种命名都能命中 ——
漏掉这一步，最该保护的 `refresh_token` 反而会被放过（这一点是单测抓出来的）。
`code` 用**精确匹配**而非 `contains`，避免误伤 `statusCode`/`detailCode`。

其他细节：

- 超限时整体换成合法的 `{"_truncated":true,"_originalLength":N}` JSON，**不把 JSON 砍成半截**
  （否则前端 `JSON.parse` 会炸）；序列化失败返回 `{"_error":"参数序列化失败"}`，
  与「真的没有参数」的 `{}` 区分开
- 同样的打码用于 `extra_data`（防止将来有人往里塞凭据）
- `extra_data` 里**不再重复整串 `userAgent`** —— `user_agent` 列已经有了，重复写只会让每行膨胀一倍

## 成功与响应码判定

- **`success`**：检查目标方法的返回值，`Result.code` 非 `0`/`200` 即判失败
  （`0` 与 `200` 都当成功，兼容两种历史约定）。抛异常也是失败
- **`responseStatus` 语义变了：现在是「业务返回码」，不再是 HTTP 状态码**。
  前端对应的列名已改为「响应码」
- `responseMessage`：此前只写库不返回，现在 `AuditLogResponse` 补上了该字段

失败时 `error_message` 记异常类名 + 消息；业务返回码非 0/200 时记 `业务返回码 400：xxx`。
两者都截断到 500 字符。

## 接口变化

除 `responseStatus` 的语义变更（前端已同步改列名）外，其余都是在原有接口上**追加**字段：

| 位置 | 变化 |
|---|---|
| `AuditLogResponse` | 新增 `level`（等级）、`responseMessage`（响应消息） |
| `GetAuditLogRequest` | 新增 `level` 筛选（`AuditLevel.from()` 宽松解析，非法值回退 `NOTICE`） |
| `AuditLogStatsResponse` | 新增 `levelStats`（`GROUP BY level`）；`moduleStats` 改为 `GROUP BY module` |

`moduleStats` 原先在 Java 里循环写死 6 个模块计数，导致 `BLOGGER`/`AUDIO`
**永远统计不到**，新增模块也必须回来改代码；现在按实际数据分组，来什么统计什么。

常用查询参数：`username`（模糊）、`operationType`、`module`、`level`、`success`、`startTime`/`endTime`
（ISO 格式，如 `2026-09-01T00:00:00`），`pageSize` 上限 100。
详见 [AUDIT_LOG_API.md](../api/AUDIT_LOG_API.md)。

## 数据迁移

| 版本 | 内容 |
|---|---|
| `V9__create_audit_log_table.sql` | 建表 + 7 个索引 |
| `V36__add_level_to_audit_log.sql` | 加 `level VARCHAR(16) NOT NULL DEFAULT 'NOTICE'`，并回填存量 |
| `V37__add_audit_log_level_indexes.sql` | `(level)` 与 `(level, create_time DESC)` 两个索引 |

回填用**一条 `CASE` 从原始列**（`operation_type`/`success`/`request_method`）推导，
并配 `IS DISTINCT FROM`。这样写是原始列的纯函数，**可安全重复执行**；
另一种看似更直观的写法（先设基础等级、再逐级升级的多条 `UPDATE`）则**不幂等** ——
重跑一次会把已经升级过的行再升一级（`INFO` + 失败本应是 `WARNING`，重跑变 `ERROR`）。

历史等级是**近似值**：历史数据没有 `QUERY` 这个动作值，只能按 `request_method = 'GET'`
近似判断只读。

`ADD COLUMN ... DEFAULT` 在 PostgreSQL 11+ 只改元数据、不重写表，是瞬时操作；
但回填会全表扫描。若 `t_audit_log` 超过约 500 万行，请把回填移出迁移、改在低峰执行
（该语句本身可重复执行）。

## 测试

`src/test/java/com/leo/myblog/aspect/audit/` 下 4 个测试类，本次改造新增 29 个用例
（全仓共 76 个）：

| 测试类 | 覆盖 |
|---|---|
| `AuditActionResolverTest` | 零 `OTHER`（扫全部 14 个控制器）、顺序缺陷回归、每个动作都有中文标签 |
| `AuditLevelResolverTest` | 基础等级映射、失败升级矩阵、未登记回退、枚举升级语义 |
| `AuditMetadataResolverTest` | 形参名保留、描述模板渲染、对象提取（目标 vs 操作者）、「描述不含 OTHER」硬约束 |
| `AuditParamSanitizerTest` | 用真实 `LoginRequest`/`OAuth2TokenRequest` 验证凭据打码、轨迹降采样、超限仍是合法 JSON |

注意：项目测试库是空的（`spring.flyway.enabled=false`，迁移由宿主上的 flyway 镜像执行），
所以这些测试都是**纯函数测试**，不依赖数据库 —— 这正是把推导逻辑抽成无 Spring 依赖的
类的收益。迁移本身在本地 PostgreSQL 实测过（加列/注释/索引、10 种组合的回填结果、重复执行恒定）。

## 运维与注意事项

- **查询上限**：`pageSize` 最大 100，超出抛业务异常
- **清理**：`DELETE /api/system/audit-logs/expired?days=30`（`days` 限 1–365），手动触发；
  **没有**定时任务自动清理审计日志（`SecurityCleanupTask` 清的是安全事件与过期封禁记录）
- **记录是同步的**：不是异步。审计落库失败只写 `error` 日志、不抛出，避免影响主业务；
  但「日志缺失」不会被静默吞掉（日志里有 `记录审计日志失败: action=…, module=…`）
- **隐私**：IP 与 User-Agent 属于个人信息，`request_params` 里可能残留业务数据，请按隐私法规保管

### 尚未实现

日志导出（`EXPORT` 动作是给别的接口用的，审计日志本身没有导出接口）、
定时清理、异常操作实时告警、归档到冷存储。

---

**最后核对**：2026-09-26（对应提交 `3597f93`）
