# 站内通知 API 文档

## 概述

站内通知用于把「与某个用户直接相关、但用户不在场」的事件告诉他本人。当前有两类事件会产生通知：

- **文章审核结果**：通过 / 拒绝（见 `POST_AUDIT_API.md`）
- **密码被管理员重置**

采用站内信而非邮件：项目没有引入 `spring-boot-starter-mail`，也没有任何 SMTP 配置，邮件会变成一个「部署时必须配好、配错就报错」的外部依赖。

**基础路径**: `/api/notifications`

**权限要求**:

- 所有接口都**只要求登录**，**不需要管理员权限**（没有 `@RequireAdmin`），也**不要求 TOTP**
- 所有接口的用户身份**一律取自当前登录态**（鉴权过滤器写入的 `userId`），**不接受前端传入 `userId`**——因此不存在「查别人的通知」这种用法，也就没有越权面

---

## API 端点

### 1. 分页查询我的通知

**GET** `/api/notifications`

**描述**: 分页查询当前登录用户收到的通知，按 `createTime` **倒序**（最新在前）。

**权限**: 仅需登录

**请求参数**:

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码。`< 1` 或为空时按 `1` 处理（**不报错**） |
| pageSize | Integer | 否 | 20 | 每页数量。`< 1` 或为空时按 `20` 处理；**大于 100 会被静默截断为 100**（不报错） |
| unreadOnly | Boolean | 否 | - | 传 `true` 只返回未读；传 `false` 或不传返回全部 |

**请求示例**:

```
GET /api/notifications?pageNum=1&pageSize=20&unreadOnly=true
Authorization: Bearer YOUR_TOKEN
```

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": "1234567890123456789",
        "type": "POST_AUDIT_REJECTED",
        "title": "文章审核未通过",
        "content": "《Spring Boot 最佳实践》未通过审核。原因：涉及未公开的内部信息，请修改后重新提交",
        "link": "/admin/articles",
        "isRead": false,
        "createTime": "2026-09-02T15:30:00Z"
      },
      {
        "id": "1234567890123456788",
        "type": "PASSWORD_RESET",
        "title": "密码已被重置",
        "content": "你的账号密码已被管理员重置。如果这不是预期的操作，请立即联系管理员。",
        "link": null,
        "isRead": true,
        "createTime": "2026-09-01T09:12:00Z"
      }
    ],
    "total": "2",
    "size": "20",
    "current": "1"
  }
}
```

**空结果**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [],
    "total": "0",
    "size": "20",
    "current": "1"
  }
}
```

> `records` 恒为数组（无数据时是 `[]`，不是 `null`）。
> `total` / `size` / `current` 都是**字符串**，且**没有 `pages` 字段**，总页数需自行用 `ceil(total / size)` 计算。
> `size` 是**后端钳制后**的值——传 `pageSize=500` 时返回的 `size` 是 `"100"`。

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 403 | 登录信息已经失效，请重新登录 | Token 无效（JWT 过滤器直接写出，HTTP 状态码同为 403） |
| 401 | 认证失败 | Token 解析过程抛异常（HTTP 状态码 401） |
| 400 | 参数类型错误 | `pageNum` / `pageSize` 传了非数字（如 `pageNum=abc`），或 `unreadOnly` 传了无法转成布尔的值 |

> 本接口**没有**业务异常分支：不存在的资源不会报错，未登录以外的情况都返回成功。

---

### 2. 获取我的未读数

**GET** `/api/notifications/unread-count`

**描述**: 返回当前用户的未读通知条数，用于菜单角标。

**权限**: 仅需登录

**请求示例**:

```
GET /api/notifications/unread-count
Authorization: Bearer YOUR_TOKEN
```

**响应示例（有未读）**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 3
}
```

**响应示例（没有未读）**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 0
}
```

> **`data` 是数字，不是字符串**：注意与分页结构里的 `total` / `size` / `current`（那三个是字符串）区分。后端特意把这里的返回类型定为 `Integer` 而不是 `Long`，就是为了绕开全局 `Long → String` 序列化规则——因为未读数经常被写成 `if (data) ...` 之类的真值判断，而字符串 `"0"` 在 JS 里是**真值**，会让「没有未读」被误判成「有未读」。这里直接当数字用即可。

**失败响应**: 与「分页查询」相同（仅 401 / 403 两种登录态失败）。

---

### 3. 标记单条通知为已读

**POST** `/api/notifications/{notificationId}/read`

**描述**: 把当前用户名下的一条通知标记为已读。

**幂等**：**无论是否真的产生了变更，都返回成功**。具体来说，下列三种情况都会返回 `code: 200`：

1. 该通知确实是你的、且未读 → 正常置为已读
2. 该通知你已经读过 → 什么都不改，返回成功（重复点击不会报错）
3. 该通知**不存在**，或者**属于别人** → 什么都不改，返回成功

第 3 种是刻意的：标记操作在 SQL 条件里带上了 `user_id = 当前用户`，因此别人的通知**改不动**；同时接口不区分「不存在」与「不属于你」，**不泄露某个通知 ID 是否存在**。

**权限**: 仅需登录

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| notificationId | Long | 是 | 通知ID（**必须来自本用户的通知列表**，不能伪造） |

**请求示例**:

```
POST /api/notifications/1234567890123456789/read
Authorization: Bearer YOUR_TOKEN
```

**响应示例**:

```json
{
  "code": 200,
  "message": "已标记为已读",
  "data": null
}
```

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 403 | 登录信息已经失效，请重新登录 | Token 无效 |
| 401 | 认证失败 | Token 解析异常 |
| 400 | 参数类型错误 | `notificationId` 不是数字 |

> 注意 message 是「**已标记为已读**」，而**不是**「操作成功」——即使实际没改动任何行（已读 / 不属于你），message 也一样。**不要用 message 判断是否真的改动了数据**。前端若需要精确同步状态，请本地直接把该条 `isRead` 置为 `true`（或重新拉取列表）。

---

### 4. 全部标记为已读

**POST** `/api/notifications/read-all`

**描述**: 把当前用户名下**所有未读**通知一次性标记为已读。

**权限**: 仅需登录

**请求示例**:

```
POST /api/notifications/read-all
Authorization: Bearer YOUR_TOKEN
```

**响应示例（标记了 5 条）**:

```json
{
  "code": 200,
  "message": "已全部标记为已读",
  "data": 5
}
```

**响应示例（本来就没有未读）**:

```json
{
  "code": 200,
  "message": "已全部标记为已读",
  "data": 0
}
```

> **这里的 `data` 是数字**（`Integer`，不受 Long → String 影响），语义是「本次被标记的条数」。已读的通知不会被计入，所以重复调用第二次会得到 `0`。注意这与「未读数接口」返回的「当前未读总数」含义不同：那个是**当前状态**，这个是**本次变更量**。

**失败响应**: 仅 401 / 403 两种登录态失败（无参数，不会产生 400）。

---

## 数据模型

### NotificationResponse

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 通知ID（**字符串**，雪花 ID 超出 JS 安全整数范围，用 String 下发避免截断） |
| type | String | 通知类型，取值见「通知类型」。存储的是英文枚举名，**中文文案由前端自己映射** |
| title | String | 标题（列表页直接展示）。数据库非空 |
| content | String | 正文；无内容时为 `null` |
| link | String | 站内跳转路径（如 `/admin/articles`）；无跳转时为 `null` |
| isRead | Boolean | 是否已读 |
| createTime | LocalDateTime | 创建时间 |

> **响应里没有 `userId` 字段**，接口也从不接收 `userId` —— 你只能读到自己的通知。
>
> 本模块的响应 DTO **未**标注 `@JsonInclude(NON_NULL)`，全局 ObjectMapper 也没有配置该项，因此**为 `null` 的字段会保留成 `"xxx": null`**（例如 `"link": null`），不会整个键消失。

### 分页结构（PageResult）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| records | Array | 数据列表，无数据时为 `[]` |
| total | String | 总记录数（**字符串**） |
| size | String | 每页大小（**字符串**，为后端钳制后的值，最大 100） |
| current | String | 当前页码（**字符串**） |

### 关于 Long 类型

后端注册了全局的 `Long → String` 序列化器（`JacksonConfig`），因此**所有 `Long` 类型的返回值在 JSON 里都是字符串**。落到本模块就是：

| 接口 | `data` 的实际 JSON 类型 |
|------|--------------------------|
| `GET /api/notifications` | 对象（`records[].id` 是字符串） |
| `GET /api/notifications/unread-count` | **数字**，如 `3`（返回类型是 `Integer`，刻意避开 `Long → String`） |
| `POST /api/notifications/{id}/read` | `null` |
| `POST /api/notifications/read-all` | **数字**，如 `5`（同样是 `Integer`） |

> 本模块受 `Long → String` 影响的只有 `records[].id`：它是雪花 ID，超出 JS 安全整数范围，必须用字符串下发才不会丢精度。两个计数接口的 `data` 都是 `Integer`，是**数字**。

### 通知类型（type）

共 **4** 个取值，这是一份封闭清单，后端只会产出下列值（同时会随枚举演进，前端请对未知值做兜底）：

| 取值 | 中文含义 | 当前触发时机 |
|------|----------|--------------|
| `POST_AUDIT_PASSED` | 文章审核通过 | 审计员通过文章（`link` = `/admin/articles`） |
| `POST_AUDIT_REJECTED` | 文章审核未通过 | 审计员拒绝文章，正文含拒绝原因（`link` = `/admin/articles`） |
| `PASSWORD_RESET` | 密码已被重置 | 管理员重置该用户密码（`link` 为 `null`） |
| `SYSTEM` | 系统通知 | **预留，当前没有任何代码会产生它** |

建议的映射与兜底写法：

```javascript
const NOTIFICATION_TYPE = {
  POST_AUDIT_PASSED:   { label: '文章审核通过',   icon: 'success' },
  POST_AUDIT_REJECTED: { label: '文章审核未通过', icon: 'warning' },
  PASSWORD_RESET:      { label: '密码已被重置',   icon: 'danger'  },
  SYSTEM:              { label: '系统通知',       icon: 'info'    }
};

function typeLabel(type) {
  // 后端新增类型而前端尚未同步时，退化显示原始英文串，不要显示 undefined
  return NOTIFICATION_TYPE[type]?.label ?? type;
}
```

---

## 通知的产生时机

通知全部由后端**在业务事务内**写入，前端无需（也无法）手动创建通知。

### 1. 文章审核结果

见 `POST_AUDIT_API.md`「审核结果通知」。要点：

- 接收者是**文章作者**（`t_post.user_id`）
- 通过 / 拒绝各一条，`link` 均指向 `/admin/articles`
- 拒绝时 `content` 会带上审计意见 `auditComment`；审计员没填时正文是「…未通过审核。（审核员未填写原因）」
- **通知与审计同事务**：审计回滚，通知一并回滚；通知写入失败，审计也会失败回滚（不是「尽力而为」）
- 作者 ID 为空的历史数据会**静默跳过**发通知，审计本身仍然成功

### 2. 密码被重置

管理员重置某用户密码时（`UserService.resetPassword`）会给**被重置的用户**发一条：

| 字段 | 值 |
|------|-----|
| type | `PASSWORD_RESET` |
| title | `密码已被重置` |
| content | `你的账号密码已被管理员重置。如果这不是预期的操作，请立即联系管理员。` |
| link | `null` |

> **通知正文里刻意不含新密码**：把明文凭据写进 `t_notification` 表等于制造新的泄露面。新密码由管理员通过其它渠道转达。

---

## 安全特性

### 1. 不接受前端传入的 `userId`

所有接口的 `userId` 都来自 `@RequestAttribute Long userId`（由 `JwtAuthenticationFilter` 从 Token 解析后写入 request 属性）。

```java
@GetMapping
public Result<PageResult<NotificationResponse>> list(
        @RequestAttribute Long userId,
        @RequestParam(required = false, defaultValue = "1") Integer pageNum,
        @RequestParam(required = false, defaultValue = "20") Integer pageSize,
        @RequestParam(required = false) Boolean unreadOnly) {
    // userId 由鉴权过滤器写入，前端传什么都没用
}
```

因此：

- **不需要 `@RequireAdmin`**：每个登录用户只看得到自己的数据，没有越权可能
- 即使前端在 query 里塞 `?userId=999`，也会被**完全忽略**
- 响应里也不返回 `userId`

### 2. 幂等的标记已读

`markRead` 走的是带条件的批量更新：

```
UPDATE t_notification SET is_read = true
WHERE id = ? AND user_id = ? AND is_read = false
```

条件里的 `user_id = 当前用户` 保证**别人改不动你的通知**（即便拿到 ID）；`is_read = false` 让重复点击不产生副作用。更新影响行数为 0 时接口**不报错**，因为报错会泄露「这个 ID 是否存在 / 是否属于你」。

### 3. 与触发事件同事务

`NotificationService.notify` 使用**默认事务传播行为**，即加入调用方的事务。这是有意为之：

- 若审核回滚了，不该留下一条「你的文章已通过」
- 若通知写不进去，让整个操作失败，比「审核成功但作者永远收不到通知」更诚实

因此**不存在**「先返回成功、通知稍后补发」的最终一致性语义。

### 4. 不产生审计日志

通知接口没有任何 `@RequireAdmin` / `@AuditLog` / `@RequireTotp` 注解，也不在审计切面的显式方法清单里，因此**调用通知接口不会写入 `t_audit_log`**。轮询未读数不会污染审计日志。

---

## 使用示例

### cURL 示例

#### 1. 查询未读通知

```bash
curl -X GET "http://localhost:8080/api/notifications?pageNum=1&pageSize=10&unreadOnly=true" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 2. 查询未读数

```bash
curl -X GET "http://localhost:8080/api/notifications/unread-count" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 3. 标记单条已读

```bash
curl -X POST "http://localhost:8080/api/notifications/1234567890123456789/read" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 4. 全部标记已读

```bash
curl -X POST "http://localhost:8080/api/notifications/read-all" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### JavaScript 示例

```javascript
const authHeaders = () => ({ 'Authorization': `Bearer ${token}` });

// 分页查询（unreadOnly 可选）
async function listNotifications({ pageNum = 1, pageSize = 20, unreadOnly } = {}) {
  const params = new URLSearchParams({ pageNum, pageSize });
  if (unreadOnly === true) params.set('unreadOnly', 'true');

  const res = await (await fetch(`/api/notifications?${params}`, {
    headers: authHeaders()
  })).json();

  if (res.code !== 200) throw new Error(res.message);
  return res.data;   // { records, total, size, current }
}

// 未读数：data 是数字（Integer），可直接参与比较
async function getUnreadCount() {
  const res = await (await fetch('/api/notifications/unread-count', {
    headers: authHeaders()
  })).json();

  return res.code === 200 ? res.data : 0;
}

// 标记单条已读（幂等：失败只可能是登录态问题）
async function markRead(notificationId) {
  const res = await (await fetch(`/api/notifications/${notificationId}/read`, {
    method: 'POST',
    headers: authHeaders()
  })).json();
  return res.code === 200;
}

// 全部已读，返回本次标记的条数（数字）
async function markAllRead() {
  const res = await (await fetch('/api/notifications/read-all', {
    method: 'POST',
    headers: authHeaders()
  })).json();
  return res.code === 200 ? res.data : 0;
}

// 点击通知：先标记已读，再按 link 跳转（link 可能为 null）
async function openNotification(item, router) {
  if (!item.isRead) {
    await markRead(item.id);
    item.isRead = true;              // 本地同步，避免为一次点击重拉整个列表
    refreshUnreadBadge();
  }
  if (item.link) router.push(item.link);
}

// 菜单角标：轮询未读数
async function refreshUnreadBadge() {
  const count = await getUnreadCount();
  badge.value = count > 0 ? (count > 99 ? '99+' : String(count)) : '';
}
```

---

## 注意事项

1. **未读数曾被设计成 `Long`，那会让 `if (data)` 永远为真**:
   全局 `JacksonConfig` 会把 `Long` 序列化成**字符串**，而字符串 `"0"` 在 JS 里是**真值** —— 若未读数以 `Long` 下发，`if (data)` / `data ? ... : ...` 这类真值判断会把「没有未读」误判成「有未读」，角标永远亮着。
   现在 `unread-count` 与 `read-all` 都返回 `Integer`，`data` 是**数字**，直接当数字用即可，不需要 `Number()` 转换，也不必再写 `data > 0` 来兜底。

2. **`unread-count` 与 `read-all` 的 `data` 类型一致**:
   两者都是 `Integer`（数字），语义分别是「当前未读总数」与「本次被标记的条数」。后者在重复调用时会变成 `0`，因为它只统计**本次真正发生变更**的行，不会把已读的也算进去。

3. **标记已读永远返回成功**:
   已读、不存在、不属于你，都返回 `{"code":200,"message":"已标记为已读"}`。这是防信息泄露的**刻意设计**，不要指望用它来判断「这条通知是否存在」。前端请直接本地置 `isRead = true`。

4. **`total` / `size` / `current` 是字符串**:
   参与算术或 `>` / `<` 比较前先 `Number()`。字符串与数字比较在 JS 里会隐式转换，容易出现意料之外的结果（例如 `"20" > 5` 为真，但 `"20" < "5"` 也为真）。

5. **`pageSize` 超限是静默截断，不是报错**:
   最大 100，超出部分被砍掉而不是抛异常（这与审计日志接口的行为**不同**，那边会直接报错）。如果前端发现「传了 500 却只回来 100 条」，请以返回的 `size` 为准来算总页数。

6. **`pageNum` / `pageSize` 非法值不报错**:
   `pageNum=0`、`pageSize=-1` 都会被当成默认值（1 / 20）处理；但 `pageNum=abc` 这类**类型错误**会返回 400「参数类型错误」。

7. **`link` 需要前端自行判断**:
   目前只有文章审核类通知带 `link`（`/admin/articles`），且 `PASSWORD_RESET` 与未来可能的 `SYSTEM` 都是 `null`。跳转前请判空，并且 `link` 是**站内路径**，应交给前端路由（`router.push`），不要用 `window.location.href` 直接跳（会整页刷新）。

8. **`type` 是英文枚举名，中文由前端映射**:
   后端只下发 `POST_AUDIT_PASSED` 这类英文串。后端**新增类型时不会同步前端**，请对未知 `type` 做兜底显示（直接显示原始串比显示 `undefined` 好）。

9. **`SYSTEM` 类型当前没有任何生产者**:
   枚举里预留了 `SYSTEM`，但代码中没有任何地方产生该类型的通知。前端做好映射即可，不要期待它出现。

10. **通知正文可能包含文章标题与审核意见**:
    `content` 会把文章标题和审计员的 `auditComment` 原样拼进去。渲染时请按**纯文本**处理（Vue 用 `{{ }}` 而非 `v-html`），避免审核意见中的内容被当作 HTML 执行。

11. **未读数没有推送**:
    通知只在「下一次拉取」时可见，没有 WebSocket / SSE 推送。角标需要前端定时轮询 `/unread-count`（该接口不写审计日志，轮询成本可控）。

12. **前端对接检查清单**:
    - 请求带 `Authorization: Bearer <token>`，**不要**传 `userId`
    - 用 `code === 200` 判成败
    - 未读数与「全部已读」返回的 `data` 是数字，直接用；分页的 `total` / `size` / `current` 是字符串，先 `Number()`
    - `id` 是字符串，作为 key 或参数传递时保持字符串即可（后端能解析字符串形式的雪花 ID）
    - 标记已读后本地同步 `isRead`，不要依赖接口返回值判断

---

## 数据库

通知表为 `t_notification`（建表脚本 `V38__create_notification_table.sql`）：

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT | 主键（雪花算法） |
| user_id | BIGINT | 接收者ID，外键指向 `t_user(id)` |
| type | VARCHAR(32) | 通知类型，取值由应用层枚举保证 |
| title | VARCHAR(200) | 标题，非空 |
| content | TEXT | 正文，可空 |
| link | VARCHAR(500) | 站内跳转路径，可空 |
| is_read | BOOLEAN | 是否已读，默认 `FALSE` |
| create_time | TIMESTAMP | 创建时间，默认 `CURRENT_TIMESTAMP` |

索引：`idx_notification_user_read_time (user_id, is_read, create_time DESC)`，同时支撑「某用户的通知按时间倒序」与「某用户的未读数」两种查询。

---

**版本**: 1.0
**最后更新**: 2026-09-27
