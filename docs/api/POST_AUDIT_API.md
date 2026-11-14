# 文章审核 API 文档

## 概述

文章审核系统负责把作者提交的文章送交审计员复核，决定其「通过并发布」还是「拒绝并退回草稿」。

**基础路径**: `/api/posts/audit`

**权限要求**:

- 「待审核列表」「审计（通过/拒绝）」两个接口需要**管理员权限**（`@RequireAdmin`），并在服务层**额外校验 `audit_posts` 能力**（超级管理员直通）；「审计」还额外要求 TOTP 双因素认证（`@RequireTotp`）
- 「查审计记录」「重新提交审核」**不需要管理员权限**，只要求登录：前者限**作者本人或管理员**，后者限**文章作者本人**
- TOTP 的通过方式见下文「TOTP 双因素认证」

---

## API 端点

### 1. 获取待审核文章列表

**GET** `/api/posts/audit/pending`

**描述**: 分页获取所有处于「待审核」状态的文章。数据源是审计记录表（`t_post_audit.audit_status = 0`），再按文章 ID 回查文章列表，返回的是**不含正文**的列表项。

**排序**: 按**提交审核时间倒序**（`t_post_audit.create_time DESC`），最新提交的排在最前面。实现上是先对审计记录分页，取出本页的文章 ID 后回查文章，再**按审计记录的顺序**映射输出——所以翻页时前后页的先后关系是连贯的，不会出现同一页内乱序。

**权限**: `@RequireAdmin`（管理员）+ `audit_posts` 能力（超管直通）

**请求参数**:

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页数量，**无上限校验**（后端未做限制，建议前端自行控制在 20 以内） |

**请求示例**:

```
GET /api/posts/audit/pending?pageNum=1&pageSize=20
Authorization: Bearer YOUR_ADMIN_TOKEN
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
        "userId": "100",
        "authorUsername": "leo",
        "authorNickname": "博主",
        "categoryId": "1",
        "categoryName": null,
        "title": "Spring Boot 最佳实践",
        "slug": "spring-boot-best-practice",
        "summary": "这是文章摘要...",
        "wordCount": 1500,
        "coverImage": "https://example.com/cover.jpg",
        "status": 3,
        "isTop": false,
        "viewCount": "0",
        "tags": null,
        "createTime": "2026-09-01T10:00:00Z",
        "updateTime": "2026-09-01T10:00:00Z",
        "publishTime": null
      }
    ],
    "total": "1",
    "size": "20",
    "current": "1"
  }
}
```

> **`categoryName` 与 `tags` 恒为 `null`**：该接口复用的转换器只做 `BeanUtils.copyProperties(post, response)`，并**没有**回填分类名与标签列表（与 `/api/posts/public` 等前台接口不同）。前端若需要在审核列表里展示分类/标签，需另行查询，或推动后端补齐。
>
> `viewCount` 是**字符串**（`PostListItemResponse.viewCount` 声明为 String）；`id` / `userId` / `categoryId` 也是字符串（Long → String，见下文「关于 Long 类型」）。
>
> `status` 这里预期恒为 `3`（待审核）——待审核列表只按审计记录筛选，不再叠加文章状态条件。

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 403 | 登录信息已经失效，请重新登录 | Token 无效（由 JWT 过滤器直接写出，HTTP 状态码也是 403） |
| 401 | 认证失败 | Token 解析过程抛异常（HTTP 状态码 401） |
| 500 | 权限不足，需要管理员权限 | 已登录但不是管理员（`AdminAspect`） |
| 500 | 权限不足，需要 [审计文章] 能力 | 是管理员、但既非超级管理员也没有 `audit_posts` 能力（`AbilityValidator`） |
| 500 | 用户不存在 | Token 里的用户在库中查不到 |

> 注意：业务异常的 `code` 默认是 **500**（`BusinessException` 的默认码），**HTTP 状态码仍是 200**。前端判断成败请看 `code`，不要看 HTTP 状态。

---

### 2. 审计文章（通过 / 拒绝）

**POST** `/api/posts/audit/{postId}`

**描述**: 对一篇文章给出审计结论。通过则文章立即变为「已发布」并写入发布时间；拒绝则文章退回「草稿」。**无论通过还是拒绝，都会给作者发一条站内通知**（见「审核结果通知」）。

**权限**: `@RequireAdmin`（管理员）+ `audit_posts` 能力（超管直通）+ `@RequireTotp(module = "security.totp.post_management")`

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**请求体**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| auditStatus | Integer | **是** | 审计结果：`1`-通过，`2`-拒绝。为空会触发参数校验失败 |
| auditComment | String | 否 | 审计意见 / 拒绝原因。拒绝时建议填写，会原样进入作者收到的通知正文 |

**请求示例**:

```
POST /api/posts/audit/1234567890123456789
Authorization: Bearer YOUR_ADMIN_TOKEN
X-TOTP-Code: 123456
Content-Type: application/json
```

```json
{
  "auditStatus": 1,
  "auditComment": "内容合规，予以发布"
}
```

**响应示例（通过）**:

```json
{
  "code": 200,
  "message": "文章已通过",
  "data": null
}
```

**响应示例（拒绝）**:

```json
{
  "code": 200,
  "message": "文章已拒绝",
  "data": null
}
```

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 401 | 此操作需要双因素认证，请提供 TOTP 验证码 | 系统开关与用户设置都要求 TOTP、但既无有效会话也没带 `X-TOTP-Code` |
| 401 | TOTP 验证码错误或已过期 | 提供的 `X-TOTP-Code` 既不是有效 TOTP 码也不是有效备份码 |
| 500 | 权限不足，需要管理员权限 | 不是管理员 |
| 500 | 权限不足，需要 [审计文章] 能力 | 管理员但缺 `audit_posts` 能力 |
| 500 | 文章不存在或已被删除 | 该文章**没有审计记录**、且按 postId 也查不到文章 |
| 500 | 创建审计记录失败 | 自动补建审计记录后仍查不到（极端情况） |
| 500 | 该文章已审计，无法重复审计 | 审计记录的 `audit_status` 已经**不是 0**（已通过或已拒绝） |
| 500 | 审计结果只能是1(通过)或2(拒绝) | `auditStatus` 传了 `1`/`2` 以外的值（例如 `0`、`3`、`9`） |
| 500 | 文章不存在 | 有审计记录，但文章已被（物理）删除 |
| 400 | 请求参数无效 | 请求体缺少 `auditStatus`（`@NotNull`），或请求体不是合法 JSON |

**校验顺序（重要）**: 后端依次执行 —— 能力校验 → 查审计记录（没有则**自动补建**）→ 检查是否已审计 → 校验 `auditStatus` 是否为 1/2 → 更新审计记录与文章 → 发通知。

因此对一篇**已审计过**的文章同时传一个非法 `auditStatus`，返回的是「该文章已审计，无法重复审计」，而不是「审计结果只能是1(通过)或2(拒绝)」。

---

### 3. 获取文章的审计记录

**GET** `/api/posts/audit/{postId}/record`

**描述**: 查询某篇文章的审计记录。审计记录只有**文章作者本人或管理员**能查。

**权限**: 仅需登录。后端会取当前用户判断是否管理员：作者本人 **或** 管理员放行，其他人拒绝。

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**请求示例**:

```
GET /api/posts/audit/1234567890123456789/record
Authorization: Bearer YOUR_TOKEN
```

**响应示例（已审计）**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": "9876543210987654321",
    "postId": "1234567890123456789",
    "auditorId": "1",
    "auditorNickname": "管理员",
    "auditStatus": 2,
    "auditComment": "涉及未公开的内部信息，请修改后重新提交",
    "auditTime": "2026-09-02T15:30:00Z",
    "createTime": "2026-09-01T10:00:00Z"
  }
}
```

**响应示例（待审核 / 还未产生审计结论）**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": "9876543210987654321",
    "postId": "1234567890123456789",
    "auditorId": null,
    "auditorNickname": null,
    "auditStatus": 0,
    "auditComment": null,
    "auditTime": null,
    "createTime": "2026-09-01T10:00:00Z"
  }
}
```

**响应示例（该文章完全没有审计记录）**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

> **`data: null` 是一个正常返回，不是错误**：历史文章可能尚未建立审计记录（正常创建流程会自动建，见「审计记录的自动补建」），此时服务层直接 `return null`，控制器仍包一层 `Result.success`。前端必须区分「`code == 200` 且 `data == null`」与「`code != 200`」两种情况，不要用 `data` 的真假判断请求成败。

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 500 | 文章不存在 | postId 对应的文章查不到 |
| 500 | 无权查看该文章的审计记录 | 当前用户既不是文章作者，也不是管理员 |

---

### 4. 重新提交审核

**POST** `/api/posts/audit/{postId}/resubmit`

**描述**: 作者在被拒绝后修改文章，重新把它送回审核队列。会把审计记录**重置**为待审核（清空审计员、审计意见、审计时间），并把文章状态改回待审核。

**权限**: 仅需登录，**且必须是文章作者本人**。管理员如果不是作者，同样会被拒绝——这个接口没有管理员旁路。

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**请求示例**:

```
POST /api/posts/audit/1234567890123456789/resubmit
Authorization: Bearer YOUR_TOKEN
```

**响应示例**:

```json
{
  "code": 200,
  "message": "已重新提交审核",
  "data": null
}
```

**失败响应**:

| code | message | 触发条件 |
|------|---------|----------|
| 500 | 文章不存在 | postId 对应的文章查不到 |
| 500 | 只有文章作者可以重新提交审核 | 当前用户不是文章作者（**管理员也不例外**） |
| 500 | 文章已在待审核状态，无需重复提交 | 审计记录 `audit_status == 0` |
| 500 | 文章已通过审核，无需重新提交 | 审计记录 `audit_status == 1` |

> 只有当审计记录为 `2`（已拒绝）时才会真正重置。审计记录**不存在**时会先补建一条待审核记录，然后照常把文章置为待审核（不会报「已在待审核状态」）。

---

## 数据模型

### PostAuditResponse

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 审计记录ID（**字符串**，雪花 ID 超出 JS 安全整数范围） |
| postId | String | 文章ID（**字符串**） |
| auditorId | String | 审计员ID（**字符串**）；未审计时为 `null` |
| auditorNickname | String | 审计员昵称；未审计或审计员已被删除时为 `null` |
| auditStatus | Integer | 审计状态，取值见「审计状态」 |
| auditComment | String | 审计意见 / 拒绝原因；未填写时为 `null` |
| auditTime | LocalDateTime | 审计时间；未审计时为 `null` |
| createTime | LocalDateTime | 审计记录创建时间 |

> 该 DTO **没有** `updateTime` 字段——数据库里 `t_post_audit.update_time` 会更新，但接口不下发。

### PostListItemResponse

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 文章ID（**字符串**） |
| userId | String | 作者ID（**字符串**） |
| authorUsername | String | 作者用户名 |
| authorNickname | String | 作者昵称 |
| categoryId | String | 分类ID（**字符串**） |
| categoryName | String | 分类名称。**本接口恒为 `null`**（转换器未回填） |
| title | String | 文章标题 |
| slug | String | SEO 友好 URL 标识 |
| summary | String | 文章摘要 |
| wordCount | Integer | 字数统计 |
| coverImage | String | 封面图片URL |
| status | Integer | 文章状态：0-草稿，1-已发布，2-私密，3-待审核 |
| isTop | Boolean | 是否置顶 |
| viewCount | String | 阅读量（**字符串**） |
| tags | Array | 标签列表 `[{id, name, color}]`。**本接口恒为 `null`**（转换器未回填） |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |
| publishTime | LocalDateTime | 发布时间；未发布时为 `null` |

### 分页结构（PageResult）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| records | Array | 数据列表 |
| total | String | 总记录数（**字符串**） |
| size | String | 每页大小（**字符串**） |
| current | String | 当前页码（**字符串**） |

分页结构**没有 `pages` 字段**，总页数需由对接方自行用 `ceil(total / size)` 计算。

### 关于 Long 类型

后端注册了全局的 `Long → String` 序列化器（`JacksonConfig`），因此**所有 `Long` 类型的字段在 JSON 里都是字符串**，包括 `postId`、`auditorId`、`userId`、`categoryId`、`viewCount`。

但 `Integer` 不受影响：`auditStatus`、`status`、`wordCount` 仍是**数字**。

`LocalDateTime` 序列化为 UTC ISO-8601 字符串（如 `"2026-09-01T10:00:00Z"`），带 `Z` 后缀。

### 关于 null 字段

本模块的响应 DTO **未**标注 `@JsonInclude(NON_NULL)`，全局 ObjectMapper 也没有配置该项，因此**为 `null` 的字段会保留成 `"xxx": null`**，不会整个键消失。取值时请做 `??` 兜底，但不必担心键不存在。

---

## 审计状态（auditStatus）与文章状态（status）的联动

### 审计状态 auditStatus

| 取值 | 含义 |
|------|------|
| 0 | 待审核 |
| 1 | 已通过 |
| 2 | 已拒绝 |

`auditStatus` **只有这三个取值**，不存在「已撤回」「已下架」等状态。

### 文章状态 status

| 取值 | 含义 |
|------|------|
| 0 | 草稿 |
| 1 | 已发布 |
| 2 | 私密 |
| 3 | 待审核 |

### 联动关系

| 事件 | `t_post_audit.audit_status` | `t_post.status` | 其它副作用 |
|------|------------------------------|------------------|------------|
| 创建文章 | `0`（自动创建） | `3` 待审核 | — |
| 审计通过 | `1` | `1` 已发布 | 写入 `publish_time = now()` |
| 审计拒绝 | `2` | `0` 草稿 | 只改 `status` 与 `update_time` |
| 重新提交 | `0`（并清空 `auditor_id` / `audit_comment` / `audit_time`） | `3` 待审核 | — |

> 判断「是否已发布」请以 `status` 为准，不要用 `publishTime != null`：拒绝分支不会去动 `publish_time`，这个字段只适合当发布时间的展示值。

---

## 业务规则

### 1. 审计记录的自动补建

正常情况下，文章在**创建时**就会自动生成一条待审核的审计记录（`PostServiceImpl.createPost` → `PostAuditService.createAuditRecord`）。

但历史数据可能没有审计记录。为此，**审计接口**（`POST /api/posts/audit/{postId}`）在发现审计记录不存在时，会先校验文章是否存在，然后**自动补建**一条 `audit_status = 0` 的记录，再继续走正常的审计流程。

- 文章存在 → 静默补建，审计照常成功
- 文章不存在 → 返回「文章不存在或已被删除」，**不会**凭空建出一条指向不存在文章的记录

### 2. 已审计过不能重复审计

审计记录一旦落到 `1` 或 `2`，就不能再审计：

```
该文章已审计，无法重复审计
```

作者想再次进入审核流程，只能走「重新提交审核」（`resubmit`），由后端把记录重置回 `0`。

### 3. 审核结果通知

审计通过 / 拒绝都会调用 `NotificationService.notify` 给**作者**（`post.userId`）发一条站内通知：

| 场景 | type | title | content | link |
|------|------|-------|---------|------|
| 通过 | `POST_AUDIT_PASSED` | 文章审核通过 | `《{title}》已通过审核并发布。` | `/admin/articles` |
| 拒绝 | `POST_AUDIT_REJECTED` | 文章审核未通过 | `《{title}》未通过审核。原因：{auditComment}`；`auditComment` 为空时追加「（审核员未填写原因）」 | `/admin/articles` |

通知与审计**在同一事务**里写入：

- 审计回滚 → 通知一并回滚，不会留下一条「你的文章已通过」的假消息
- 通知写入失败（例如数据库异常）→ **整个审计操作失败并回滚**，审计员会看到报错，需要重试

作者可能为空的极端情况（`post.userId == null`）下，通知会被静默跳过，审计本身仍然成功。

通知的查询接口见 `NOTIFICATION_API.md`。

---

## 安全特性

### 1. 权限控制

两道关卡，缺一不可：

```java
@RequireAdmin                                   // ① 必须是管理员（AdminAspect）
@PostMapping("/{postId}")
public Result<Void> auditPost(...) {
    postAuditService.auditPost(userId, postId, request);   // ② 内部再校验 audit_posts 能力
}
```

- `@RequireAdmin` 只检查 `t_user.is_admin`
- 服务层的 `abilityValidator.validateAbility(auditorId, "audit_posts")` 要求：是管理员 **且**（是超级管理员 **或** 拥有 `audit_posts` 能力）

因此「是管理员但没有 `audit_posts` 能力」的账号会在第二关被拦下。

### 2. TOTP 双因素认证

只有「审计文章」（`POST /api/posts/audit/{postId}`）标注了 `@RequireTotp(module = "security.totp.post_management")`。是否真的要求验证码，取决于两个条件，**任一不满足则直接放行**：

- 系统配置中该模块的 TOTP 开关（配置键 `security.totp.post_management`）被关闭。**注意：配置项在库中不存在时默认按「启用」处理**（安全优先）
- 当前用户自身未启用 TOTP

两个条件都满足时，需要满足下列**任一**方式才会放行：

1. 已有有效的验证会话（例如刚调用过 `/api/auth/totp/verify` 验证成功）；命中时响应头会返回 `X-TOTP-Session: valid`
2. 在请求头中携带本次验证码 `X-TOTP-Code: 123456`（也可用备份码），验证通过后后端会创建会话，并在响应头返回 `X-TOTP-Session: valid` 与 `X-TOTP-Session-Expires`（毫秒时间戳，会话约 5 分钟）

否则接口返回 401：

```json
{
  "code": 401,
  "message": "此操作需要双因素认证，请提供 TOTP 验证码",
  "data": null
}
```

### 3. 审计接口自身也会被记录

「待审核列表」与「审计文章」两个接口带有 `@RequireAdmin` / `@RequireTotp`，被审计切面覆盖，因此**调用它们本身会产生审计记录**：

| 接口 | operationType | module | 成功时等级 |
|------|---------------|--------|------------|
| `GET /pending` | `QUERY` | `POST` | `INFO` |
| `POST /{postId}` | `AUDIT` | `POST` | `NOTICE` |

「查审计记录」与「重新提交审核」两个接口没有任何注解，**不会**产生审计记录。

---

## 使用示例

### cURL 示例

#### 1. 查看待审核列表

```bash
curl -X GET "http://localhost:8080/api/posts/audit/pending?pageNum=1&pageSize=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

#### 2. 通过一篇文章

```bash
curl -X POST "http://localhost:8080/api/posts/audit/1234567890123456789" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456" \
  -H "Content-Type: application/json" \
  -d '{"auditStatus": 1, "auditComment": "内容合规，予以发布"}'
```

#### 3. 拒绝一篇文章并说明原因

```bash
curl -X POST "http://localhost:8080/api/posts/audit/1234567890123456789" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "X-TOTP-Code: 123456" \
  -H "Content-Type: application/json" \
  -d '{"auditStatus": 2, "auditComment": "涉及未公开的内部信息，请修改后重新提交"}'
```

#### 4. 作者查看自己文章的审计记录

```bash
curl -X GET "http://localhost:8080/api/posts/audit/1234567890123456789/record" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 5. 作者修改后重新提交

```bash
curl -X POST "http://localhost:8080/api/posts/audit/1234567890123456789/resubmit" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### JavaScript 示例

```javascript
// 待审核列表
async function getPendingPosts(pageNum = 1, pageSize = 20) {
  const response = await fetch(
    `/api/posts/audit/pending?pageNum=${pageNum}&pageSize=${pageSize}`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  const res = await response.json();
  if (res.code !== 200) throw new Error(res.message);
  return res.data;   // { records, total, size, current }，总数是字符串
}

// 审计：passed = true 通过，false 拒绝
async function auditPost(postId, passed, comment) {
  const response = await fetch(`/api/posts/audit/${postId}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-TOTP-Code': totpCode,          // 系统与用户都要求 TOTP 时必带
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ auditStatus: passed ? 1 : 2, auditComment: comment })
  });
  return await response.json();
}

// 查审计记录（注意 data 可能为 null：该文章还没有审计记录）
async function getAuditRecord(postId) {
  const response = await fetch(`/api/posts/audit/${postId}/record`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const res = await response.json();
  if (res.code !== 200) throw new Error(res.message);
  if (res.data === null) return null;      // 无审计记录
  return res.data;                          // { auditStatus, auditComment, auditorNickname, ... }
}

// 重新提交审核
async function resubmitForAudit(postId) {
  const response = await fetch(`/api/posts/audit/${postId}/resubmit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
}
```

### 审计状态的中文映射

```javascript
const AUDIT_STATUS = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已拒绝', type: 'danger' }
};

function auditStatusLabel(status) {
  return AUDIT_STATUS[status]?.label ?? '未知';
}
```

---

## 注意事项

1. **错误码一律是 500，不是 4xx**:
   业务异常（权限不足、状态冲突、文章不存在）走的是 `BusinessException` 默认码 `500`，且 HTTP 状态码仍是 200。只有 JWT 过滤器/Spring Security 直接拒绝时才是真正的 401 / 403。**前端必须用 `code` 判断成败**，不能依赖 HTTP 状态码。

2. **`GET /{postId}/record` 的 `data: null` 是正常返回**:
   表示该文章还没有审计记录，不是「查不到接口」也不是「无权限」。请与 `code != 200` 分开处理。

3. **待审核列表的 `categoryName` / `tags` 恒为 `null`**:
   该接口用的转换器不回填这两项。需要展示分类名/标签时请另调接口。

4. **待审核列表的 `total` 可能大于 `records.length`**:
   `total` 统计的是**审计记录**条数，而列表内容是按文章 ID 回查的结果。若某条审计记录指向的文章已被软删除（`is_deleted = true`）或物理删除，它会贡献 `total` 却不出现在 `records` 里（后端按文章 ID 取不到就跳过，不会补 `null` 占位）。极端情况下某一页可能看起来「条数不足」。

5. **待审核列表按「提交审核时间」倒序，不是「更新时间」**:
   排序字段是 `t_post_audit.create_time`，而不是文章的 `updateTime`。作者重新编辑一篇文章并不会把它顶到列表最前面（除非重新提交审核会重置记录，但重置的是审计状态，`create_time` 仍是最初建记录的时间）。前端展示时不要把它当「最近活跃」用。

6. **`pageSize` 没有上限校验**:
   审计日志接口在 `pageSize > 100` 时会报错，但本接口不会 —— 传多大就查多大。建议前端自行限制。

7. **判断「是否已发布」请用 `status`，不要用 `publishTime`**:
   拒绝分支只改 `status` 与 `update_time`，不动 `publish_time`。虽然 `t_post_audit` 的记录一旦审计过就不能重复审计（`resubmit` 也只能从「已拒绝」重来），实际走不到「已发布过又被拒绝」的路径，但以 `status` 为准总是更稳。

8. **重复审计与参数校验的顺序**:
   对已审计的文章传非法 `auditStatus`，返回的是「该文章已审计」而非「审计结果只能是1(通过)或2(拒绝)」。

9. **通知失败会让审计失败**:
   审核结果通知与审计在同一事务内。若通知写库失败，审计会整体回滚并报错，审计员需要重试。这不是「通知是尽力而为」的设计。

10. **`resubmit` 对管理员不友好**:
    该接口只认文章作者，管理员无法代替作者重新提交。若运营上需要管理员代操作，需另行改造。

11. **`@RequireTotp` 被两个切面同时拦截（潜在不确定性）**:
    项目中 `TotpAspect` 与 `VerificationAspect` 对同一个 `@RequireTotp` 切点都声明了 `@Around`，且都未标注 `@Order`。两者语义不同：前者只认 TOTP 会话/`X-TOTP-Code`；后者额外支持 `X-Verify-Method: challenge_push` + `X-Challenge-Id`（App 推送验证），并在用户「未启用 TOTP 但绑定了设备」时也要求验证。由于两个切面会**依次**执行、顺序未定义，实际生效的是两者的**交集**：走 App 推送验证的用户可能被另一个切面以「请提供 TOTP 验证码」拦下。
    对接建议：**审计接口统一走 `X-TOTP-Code`（或先建立验证会话）**，不要依赖 `X-Verify-Method: challenge_push` 来完成文章审核。

12. **前端对接检查清单**:
    - 请求带 `Authorization: Bearer <token>`；审计接口另带 `X-TOTP-Code`
    - 用 `code === 200` 判成败
    - 分页的 `total` / `size` / `current` 参与计算前先 `Number()`
    - `id` / `postId` / `auditorId` / `userId` 是字符串，不要当数字比较（`123` 与 `"123"` 不相等）
    - `auditStatus` 与 `status` 是数字

---

**版本**: 1.0
**最后更新**: 2026-09-27
