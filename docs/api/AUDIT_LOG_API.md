# 审计日志 API 文档

## 概述

审计日志系统用于记录所有敏感操作和系统事件。

**基础路径**: `/api/system/audit-logs`

**权限要求**:

- 所有接口都需要管理员权限（`@RequireAdmin` 注解保护）
- 「分页查询」「详情」「统计」三个 GET 接口**额外要求 TOTP 双因素认证**（`@RequireTotp`）；通过方式见下文「TOTP 双因素认证」
- 只有「清理过期日志」（`DELETE /expired`）不要求 TOTP

---

## API 端点

### 1. 分页查询审计日志

**GET** `/api/system/audit-logs`

**描述**: 分页查询审计日志，支持多种筛选条件。结果按 `createTime` 倒序（最新在前）。

**权限**: `@RequireAdmin` + `@RequireTotp`（TOTP 的实际要求与否见「TOTP 双因素认证」）

**请求参数**:

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页数量，**最大 100**，超过会报错 |
| username | String | 否 | - | 用户名（模糊搜索） |
| operationType | String | 否 | - | 操作类型，取值见「操作类型」（精确匹配） |
| module | String | 否 | - | 操作模块，取值见「操作模块」（精确匹配） |
| level | String | 否 | - | 日志等级，取值见「日志等级」（精确匹配） |
| success | Boolean | 否 | - | 是否成功 |
| startTime | String | 否 | - | 开始时间（ISO 格式：`yyyy-MM-ddTHH:mm:ss`） |
| endTime | String | 否 | - | 结束时间（ISO 格式：`yyyy-MM-ddTHH:mm:ss`） |

**参数校验行为**（对接方需注意：这些都是报错，不是静默忽略）:

- `pageSize > 100` → 报错「每页最多只能获取100条日志」
- `level` 传入非法值（例如拼错的 `WARN`）→ 报错「无效的日志等级: WARN」。等级值大小写不敏感（`info` 可用），但必须是 `INFO/NOTICE/WARNING/ERROR/CRITICAL` 之一
- `startTime` / `endTime` 不是合法 ISO 时间 → 报错「时间格式错误，请使用ISO格式：yyyy-MM-ddTHH:mm:ss」
- `operationType` / `module` **不做合法性校验**：传了清单外的值不会报错，只会查不到数据（结果为空）

**请求示例**:

```
GET /api/system/audit-logs?pageNum=1&pageSize=20&username=admin&operationType=LOGIN&module=AUTH&level=NOTICE&success=true&startTime=2026-05-01T00:00:00&endTime=2026-05-13T23:59:59
```

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1234567890,
        "userId": 1,
        "username": "admin",
        "operationType": "LOGIN",
        "module": "AUTH",
        "level": "NOTICE",
        "description": "登录",
        "ipAddress": "192.168.1.100",
        "deviceType": "PC",
        "osName": "Windows",
        "browserName": "Chrome",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
        "requestUrl": "/api/auth/login/step2/password",
        "requestMethod": "POST",
        "requestParams": "{\"username\":\"admin\",\"password\":\"***MASKED***\"}",
        "extraData": "{\"deviceType\":\"PC\",\"osName\":\"Windows\",\"browserName\":\"Chrome\"}",
        "responseStatus": 200,
        "responseMessage": "操作成功",
        "success": true,
        "executionTime": 45,
        "createTime": "2026-05-13T10:30:00"
      }
    ],
    "total": "150",
    "size": "20",
    "current": "1"
  }
}
```

> 注意分页结构：`total` / `size` / `current` 都是**字符串**（后端为避免 JavaScript 大整数精度丢失而序列化为字符串），且**没有 `pages` 字段**，总页数需由对接方自行用 `ceil(total / size)` 计算。`records` 中为 `null` 的字段会**整个键缺失**（后端使用了 `NON_NULL` 序列化策略），例如成功记录的 `errorMessage` 不会出现——取值时请做兜底判断。

---

### 2. 获取审计日志详情

**GET** `/api/system/audit-logs/{id}`

**描述**: 根据 ID 获取单条审计日志的详细信息。

**权限**: `@RequireAdmin` + `@RequireTotp`（TOTP 的实际要求与否见「TOTP 双因素认证」）

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 审计日志ID |

**请求示例**:

```
GET /api/system/audit-logs/1234567890
```

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1234567890,
    "userId": 1,
    "username": "admin",
    "operationType": "RESET_PASSWORD",
    "module": "USER",
    "level": "CRITICAL",
    "description": "重置密码 123",
    "ipAddress": "192.168.1.100",
    "deviceType": "PC",
    "osName": "Windows",
    "browserName": "Chrome",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
    "requestUrl": "/api/users/123/reset-password",
    "requestMethod": "PUT",
    "requestParams": "{\"newPassword\":\"***MASKED***\"}",
    "responseStatus": 200,
    "responseMessage": "操作成功",
    "success": true,
    "executionTime": 120,
    "createTime": "2026-05-13T10:30:00"
  }
}
```

**日志不存在时**:

```json
{
  "code": 500,
  "message": "审计日志不存在",
  "data": null
}
```

---

### 3. 获取审计日志统计信息

**GET** `/api/system/audit-logs/stats`

**描述**: 获取审计日志的整体统计，包括总数、今日数量、成功/失败数、各模块分布与各等级分布。

**权限**: `@RequireAdmin` + `@RequireTotp`（TOTP 的实际要求与否见「TOTP 双因素认证」）

**请求示例**:

```
GET /api/system/audit-logs/stats
```

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalCount": 1234,
    "todayCount": 56,
    "successCount": 1200,
    "failCount": 34,
    "moduleStats": {
      "USER": 500,
      "POST": 300,
      "COMMENT": 200,
      "AUTH": 120,
      "CATEGORY": 100,
      "TAG": 80,
      "SYSTEM": 54
    },
    "levelStats": {
      "INFO": 600,
      "NOTICE": 400,
      "WARNING": 150,
      "ERROR": 34,
      "CRITICAL": 50
    }
  }
}
```

> **`levelStats` 的缺键行为**：`moduleStats` 与 `levelStats` 都是对实际数据做分组统计得到的，**某个等级（或模块）一条都没有时，后端不会返回该键**。
> 例如库里从未出现 `ERROR` 记录时，响应里就没有 `"ERROR"` 这个键。
> 对接方展示时必须自己兜 0（例如 `levelStats[level] ?? 0`），不要假设 5 个等级键一定齐全。

---

### 4. 删除过期的审计日志

**DELETE** `/api/system/audit-logs/expired?days=30`

**描述**: 删除指定天数之前的审计日志，用于清理历史数据。该操作本身会被记入审计日志（操作类型 `CLEANUP`，模块 `SYSTEM`）。

**权限**: `@RequireAdmin`

**查询参数**:

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| days | Integer | 否 | 30 | 保留天数（1-365） |

**请求示例**:

```
DELETE /api/system/audit-logs/expired?days=30
```

**响应示例**:

```json
{
  "code": 200,
  "message": "已删除123条过期日志",
  "data": 123
}
```

**错误响应**:

```json
{
  "code": 500,
  "message": "保留天数必须在1-365之间",
  "data": null
}
```

---

## 数据模型

### AuditLogResponse

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 日志ID |
| userId | Long | 操作用户ID（匿名/取不到时可能缺该键） |
| username | String | 操作用户名（空值后端已替换为 `"N/A"`） |
| operationType | String | 操作类型，取值见「操作类型」 |
| module | String | 操作模块，取值见「操作模块」 |
| level | String | 日志等级，取值见「日志等级」 |
| description | String | 操作描述（中文，如「登录」「重置密码 123」） |
| ipAddress | String | 请求IP地址（空值替换为 `"N/A"`） |
| deviceType | String | 客户端类型（空值替换为 `"N/A"`） |
| osName | String | 操作系统名称（空值替换为 `"N/A"`） |
| browserName | String | 浏览器/客户端名称（空值替换为 `"N/A"`） |
| userAgent | String | 完整 User-Agent（空值替换为 `"N/A"`） |
| requestUrl | String | 请求URL（空值替换为 `"N/A"`） |
| requestMethod | String | 请求方法（空值替换为 `"N/A"`） |
| requestParams | String | 已脱敏的请求参数，JSON 字符串（可能缺该键） |
| extraData | String | 设备信息等额外数据，JSON 字符串（可能缺该键） |
| responseStatus | Integer | **业务返回码**（即 `Result.code`），不是 HTTP 状态码 |
| success | Boolean | 是否成功 |
| executionTime | Long | 执行时长（毫秒） |
| errorMessage | String | 错误信息，成功时缺该键 |
| responseMessage | String | 响应消息，最长 500 字符 |
| createTime | LocalDateTime | 创建时间 |

**关于 `deviceType` / `osName` / `browserName`**: 这三个字段**不是数据库列**，后端在返回前从 `extraData` 中解析得出。`extraData` 只在**登录类操作**上生成，因此其它操作的这三个字段恒为 `"N/A"`。

**关于 `responseStatus`**: 它记录的是**业务返回码**（控制器返回的 `Result.code`），语义如下：

| 场景 | responseStatus 取值 |
|------|---------------------|
| 正常返回 `Result` | 该 `Result` 的 `code`（为 null 时记 200） |
| 抛出业务异常 | 该异常的 `code`（默认为 500） |
| 抛出其它异常 | 500 |
| 返回值不是 `Result` | 200 |

这与 HTTP 状态码无关——HTTP 层面通常仍是 200。判断操作是否成功请直接用 `success` 字段。

**关于 `requestParams`（重要）**: 它是**已经脱敏**的 JSON 字符串，**对接方不要指望在这里拿到凭据原文**：

- 凭据类字段（`password`、`newPassword`、`secret`、`clientSecret`、`token`、`refreshToken`、`authorization`、`cookie`、`totpCode`、`captchaCode`、`code` 等）一律为 `"***MASKED***"`
- 手机号 / 邮箱 / 身份证 / 银行卡等为**部分掩码**，例如 `138****34`、`ab***@example.com`
- 验证码轨迹类字段降采样为 `"[N 点轨迹]"`
- 超长文本会被截断（带 `…`）；整体超过 8192 字符时，整个值换成 `{"_truncated":true,"_originalLength":N}`（仍是合法 JSON）
- 序列化失败时为 `{"_error":"参数序列化失败"}`

该字段与 `extraData` 都是 **JSON 字符串**（不是嵌套对象），前端如需结构化使用需自行 `JSON.parse`。

### 分页结构（PageResult）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| records | Array | 数据列表 |
| total | String | 总记录数（**字符串**） |
| size | String | 每页大小（**字符串**） |
| current | String | 当前页码（**字符串**） |

### 统计响应（AuditLogStatsResponse）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| totalCount | Long | 日志总数 |
| todayCount | Long | 今日日志数（当日 00:00:00 起） |
| successCount | Long | 成功条数 |
| failCount | Long | 失败条数 |
| moduleStats | Map\<String, Long\> | 各模块条数，键为模块值；无数据的模块缺键 |
| levelStats | Map\<String, Long\> | 各等级条数，键为等级值；无数据的等级缺键 |

### 操作类型（operationType）

共 **25** 个取值，这是一份封闭清单，后端只会产出下列值：

| 取值 | 中文标签 | 取值 | 中文标签 |
|------|----------|------|----------|
| LOGIN | 登录 | UPDATE_CONFIG | 修改配置 |
| LOGOUT | 登出 | RESET_CONFIG | 重置配置 |
| REGISTER | 注册 | MIGRATE | 数据迁移 |
| CHANGE_PASSWORD | 修改密码 | CLEANUP | 清理 |
| RESET_PASSWORD | 重置密码 | EXPORT | 导出 |
| CHANGE_ROLE | 修改角色 | REVOKE | 撤销 |
| SET_SUPER_ADMIN | 设置超级管理员 | REVOKE_SESSION | 下线会话 |
| CHANGE_ABILITY | 调整权限 | BAN_IP | 封禁 IP |
| CREATE | 创建 | UNBAN_IP | 解封 IP |
| UPDATE | 修改 | OTHER | 其他操作 |
| DELETE | 删除 | | |
| QUERY | 查询 | | |
| TOGGLE_STATUS | 切换状态 | | |
| UPLOAD | 上传 | | |
| AUDIT | 审核 | | |

按主题分组：

- **身份**: `LOGIN`、`LOGOUT`、`REGISTER`、`CHANGE_PASSWORD`、`RESET_PASSWORD`
- **权限**: `CHANGE_ROLE`、`SET_SUPER_ADMIN`、`CHANGE_ABILITY`
- **通用增删改查**: `CREATE`、`UPDATE`、`DELETE`、`QUERY`、`TOGGLE_STATUS`
- **业务特有**: `UPLOAD`、`AUDIT`、`UPDATE_CONFIG`、`RESET_CONFIG`、`MIGRATE`、`CLEANUP`、`EXPORT`
- **会话**: `REVOKE`、`REVOKE_SESSION`
- **安全**: `BAN_IP`、`UNBAN_IP`
- **兜底**: `OTHER`

### 操作模块（module）

共 **12** 个取值：

| 取值 | 中文标签 |
|------|----------|
| USER | 用户管理 |
| POST | 文章管理 |
| COMMENT | 评论管理 |
| TAG | 标签管理 |
| CATEGORY | 分类管理 |
| AUDIO | 音频管理 |
| BLOGGER | 博主信息 |
| SAYING | 名言管理 |
| BUG_REPORT | Bug 反馈 |
| SECURITY | 安全中心 |
| SYSTEM | 系统设置 |
| AUTH | 登录认证 |

> 登录 / 注册 / 令牌相关操作归入 `AUTH`，不再落在 `SYSTEM`。

### 日志等级（level）

共 **5** 个取值，严重度递增：

| 取值 | 中文标签 | 含义 |
|------|----------|------|
| INFO | 信息 | 只读查询、统计、导出、登出 |
| NOTICE | 记录 | 普通业务写操作（创建/修改/上传/审核/登录成功） |
| WARNING | 警告 | 破坏性或影响可用性、安全边界的操作，以及失败兜底 |
| ERROR | 错误 | 非预期系统异常，或由 WARNING 升级而来 |
| CRITICAL | 关键 | 权限/身份的授予或剥夺、凭据重置、封禁 |

**基础等级由操作类型决定**（成功时的最终等级）：

| 基础等级 | 操作类型 |
|----------|----------|
| INFO | QUERY、EXPORT、LOGOUT |
| NOTICE | LOGIN、REGISTER、CREATE、UPDATE、UPLOAD、AUDIT，以及所有未登记的动作（含 OTHER） |
| WARNING | DELETE、TOGGLE_STATUS、REVOKE、REVOKE_SESSION、UNBAN_IP、MIGRATE、CLEANUP、RESET_CONFIG、UPDATE_CONFIG |
| CRITICAL | RESET_PASSWORD、CHANGE_PASSWORD、CHANGE_ROLE、CHANGE_ABILITY、SET_SUPER_ADMIN、BAN_IP |
| ERROR | 无基础映射，**只由失败升级产生** |

**失败会升级**: 操作失败时先升一级，且至少为 `WARNING`；若抛出的是**非业务异常**（系统故障、空指针、SQL 异常等）再升到 `ERROR`；业务异常（校验失败、权限不足等）止步于 `WARNING`/`ERROR`。

**`CRITICAL` 不回退**: 关键等级不会被降级，即权限变更 / 凭据重置 / 封禁类操作**失败后仍是 `CRITICAL`**。

几个例子：

| 操作类型 | 结果 | 最终等级 |
|----------|------|----------|
| QUERY | 成功 | INFO |
| QUERY | 业务异常失败 | WARNING |
| QUERY | 系统异常失败 | ERROR |
| LOGIN | 失败 | WARNING |
| DELETE | 成功 | WARNING |
| DELETE | 业务异常失败 | ERROR |
| RESET_PASSWORD | 成功 | CRITICAL |
| RESET_PASSWORD | 失败（任意异常） | CRITICAL |

---

## 安全特性

### 1. 权限控制

所有审计日志接口都使用 `@RequireAdmin` 保护，只有管理员可以访问。查询 / 详情 / 统计三个 GET 接口还叠加了 `@RequireTotp`（何时真的校验验证码见下一节）：

```java
@RequireTotp(operation = "查看审计日志", module = "security.totp.audit_log")
@GetMapping
@RequireAdmin
public Result<PageResult<AuditLogResponse>> getAuditLogs(@Valid GetAuditLogRequest request) {
    // 需要管理员权限 + TOTP 会话
}
```

### 2. TOTP 双因素认证

查询 / 详情 / 统计三个 GET 接口标注了 `@RequireTotp(module = "security.totp.audit_log")`。是否真的要求验证码，取决于两个条件，**任一不满足则直接放行**：

- 系统配置中该模块的 TOTP 开关（`security.totp.audit_log`）被关闭
- 当前用户本身未启用 TOTP

两个条件都满足时，需要满足下列**任一**方式才会放行：

1. 已有有效的 TOTP 会话（例如刚调用过 `/api/auth/totp/verify` 验证成功，或本次请求带上验证码验证通过后会创建一个有时效的会话），响应头会返回 `X-TOTP-Session: valid`
2. 在请求头中携带本次的验证码 `X-TOTP-Code: 123456`（也可用备份码），验证通过后后端会创建会话

否则接口返回 401：

```json
{
  "code": 401,
  "message": "此操作需要双因素认证，请提供 TOTP 验证码",
  "data": null
}
```

验证码错误或过期时返回 401「TOTP 验证码错误或已过期」。

### 3. 数据脱敏

`requestParams` 与 `extraData` 落库前都会递归脱敏，凭据类字段一律替换为 `***MASKED***`，详见上文「关于 `requestParams`」。

### 4. 记录失败不影响主流程

审计日志的写入被包裹在异常处理中：即使审计写入失败，也不会影响业务接口本身返回结果（仅在服务端留下错误日志）。对接方无需为审计写入做额外容错。

### 5. 审计接口自身也会被记录

查询 / 详情 / 统计三个 GET 接口受审计切面覆盖，因此**调用它们会产生新的审计记录**（操作类型 `QUERY`，模块 `SYSTEM`，等级 `INFO`）。轮询统计接口会持续增加日志量，建议控制调用频率。

---

## 使用示例

### cURL 示例

> 下列 GET 请求都带了 `X-TOTP-Code`；若当前系统开关与用户设置并不要求 TOTP，去掉该请求头也能正常调用（见「TOTP 双因素认证」）。

#### 1. 查询最近的管理员登录日志

```bash
curl -X GET "http://localhost:8080/api/system/audit-logs?pageNum=1&pageSize=10&operationType=LOGIN&module=AUTH" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456"
```

#### 2. 查询关键等级的操作

```bash
curl -X GET "http://localhost:8080/api/system/audit-logs?level=CRITICAL&pageSize=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456"
```

#### 3. 查询今天的失败操作

```bash
curl -X GET "http://localhost:8080/api/system/audit-logs?success=false&startTime=2026-05-13T00:00:00&endTime=2026-05-13T23:59:59" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456"
```

#### 4. 获取统计信息

```bash
curl -X GET "http://localhost:8080/api/system/audit-logs/stats" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456"
```

#### 5. 删除90天前的日志

```bash
curl -X DELETE "http://localhost:8080/api/system/audit-logs/expired?days=90" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### JavaScript 示例

```javascript
// 查询审计日志
async function getAuditLogs(pageNum = 1, pageSize = 20, filters = {}) {
  const params = new URLSearchParams({
    pageNum,
    pageSize,
    ...filters   // 可传 operationType / module / level / success / username / startTime / endTime
  });

  const response = await fetch(`/api/system/audit-logs?${params}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-TOTP-Code': totpCode
    }
  });

  return await response.json();
}

// 获取统计信息
async function getAuditLogStats() {
  const response = await fetch('/api/system/audit-logs/stats', {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-TOTP-Code': totpCode
    }
  });

  return await response.json();
}

// 按等级展示分布（注意兜 0：某等级 0 条时后端不返回该键）
const LEVELS = [
  { value: 'INFO', label: '信息' },
  { value: 'NOTICE', label: '记录' },
  { value: 'WARNING', label: '警告' },
  { value: 'ERROR', label: '错误' },
  { value: 'CRITICAL', label: '关键' }
];

function renderLevelStats(levelStats = {}) {
  return LEVELS.map(item => ({
    ...item,
    count: levelStats[item.value] ?? 0
  }));
}

// 删除过期日志
async function deleteExpiredLogs(days = 30) {
  const response = await fetch(`/api/system/audit-logs/expired?days=${days}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  return await response.json();
}
```

---

## 注意事项

1. **性能考虑**:
   - 建议定期清理过期日志（如每月清理 90 天前的日志）
   - 查询时尽量使用筛选条件，避免全表扫描
   - 单次查询最多返回 100 条记录，超出会直接报错
   - 结果按创建时间倒序，深分页（大 `pageNum`）性能会下降

2. **字段取值兜底**:
   - 响应中为 `null` 的字段会整个键缺失，请用可选链或默认值处理
   - `levelStats` / `moduleStats` 缺键表示该等级/模块为 0 条
   - 分页的 `total` / `size` / `current` 是字符串，参与计算前需 `Number()` 转换

3. **存储考虑**:
   - 审计日志会持续增长，建议设置自动清理策略
   - 可以考虑将历史日志归档到冷存储

4. **隐私考虑**:
   - 请求参数中的敏感信息已自动脱敏，但请勿依赖它作为唯一的数据保护手段
   - IP 地址和 User-Agent 可能包含个人信息，请妥善保管

5. **权限验证**:
   - 非管理员访问审计日志接口会返回 403 错误
   - Token 过期或无效会返回 401 错误
   - 查询 / 详情 / 统计接口在系统开关与用户设置都要求 TOTP 时，缺少有效会话或 `X-TOTP-Code` 会被拒绝（401）

---

## 数据库索引

为了提高查询性能，已为以下字段创建索引：

- `user_id`: 按用户查询
- `username`: 按用户名查询
- `operation_type`: 按操作类型查询
- `module`: 按模块查询
- `success`: 按成功状态查询
- `create_time`: 按时间范围查询
- `(module, create_time)`: 复合索引，优化模块+时间查询
- `level`: 按日志等级筛选
- `(level, create_time DESC)`: 复合索引，优化等级+时间查询

---

**版本**: 2.0
**最后更新**: 2026-09-26
