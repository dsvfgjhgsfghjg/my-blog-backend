# App 侧主动拉取待处理挑战 —— 需求与对接文档

> 面向 App 开发者。后端改动**已经上线**，本文档说明 App 需要配合做什么。

## 一、为什么要改

挑战（3 选 1 数字匹配）此前**只有 WebSocket 一条投递路径**：

1. 实时推送 —— 要求设备此刻连着；
2. 服务端在设备上线时「补推」—— 仍然要求连接先建立起来。

而移动端 socket 被系统在后台收掉太常见了。于是出现了一个很别扭的结果：
**手机在兜里、App 在后台时，网页端发起验证会直接失败**（后端返回「没有在线设备可推送」，
挑战压根不生成），用户必须先把 App 亮到前台并等它连上才能继续。

「设备不在线」本来是常态，却成了一条死路。

## 二、后端已经做了什么（对 App 透明，无需配合）

- **挑战生成与推送解耦**：发起验证时**无条件**先生成一个挑战并落库，再尽力推给在线设备。
  没有在线设备时不再报错，而是返回 `status: "queued"`（已排队）。
- **补推覆盖两类挑战**：原先只有登录挑战会在设备上线时补推，现在敏感操作挑战也一并补推。
- **新增拉取接口**（本文档要对接的）。
- 顺带修掉一个**多设备缺陷**：以前对每台在线设备各生成一个挑战，N 台设备就有 N 组不同的数字，
  而页面只展示第一组的数字 → 用户在第二台设备上按页面数字选中**必然验不过**。
  现在生成恰好一次，所有设备收到同一个 `challengeId` 与同一组数字。

## 三、需要 App 做什么

**在 App 回到前台时，主动拉取一次待处理的挑战。**

就这一件事。原因是它补上了推送覆盖不到的那一段：

- 应用内的 `WebSocketService.consumePendingChallenge()` 那个本地缓冲区，
  只在「WebSocket 收到过、但 Activity 不在前台」时才有内容；
- **socket 断开、App 被系统杀掉重开时，它是空的** —— 拉取才补得上。

> 建议的落点：`MainActivity.onResume()` 里，紧挨现有的「消费后台期间缓存的挑战」之后。
> 那是天然的兜底位置，也便于对比「本地缓冲区」与「服务端」两个来源。

## 四、接口契约

```
GET /api/auth/app/pending-challenges
```

- **鉴权**：与 `/api/auth/app/*` 其它接口一致 —— `Authorization: Bearer <JWT>`。
- **入参**：无。
- **响应**：`Result<List<PendingChallengeResponse>>`，`data` 就是数组本身（不是分页对象）。

统一响应包裹（与其它接口一致）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [ /* 见下 */ ]
}
```

`data` 数组元素：

| 字段 | 类型 | 说明 |
|---|---|---|
| `challengeId` | String | 挑战 ID（32 位 hex）。应答时在加密的 `challenge_response` 里原样回传 |
| `purpose` | String | `"login"` = 两步登录；`"sensitive_operation"` = 敏感操作验证。**不读它也能正常工作** |
| `numbers` | int[3] | 3 个 0–99 的候选数字，展示给用户选 |
| `expiresAt` | long | **Unix 秒**（不是毫秒，App 侧需 `* 1000`） |

示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "challengeId": "0000000000000000000000000000abcd",
      "purpose": "sensitive_operation",
      "numbers": [12, 45, 78],
      "expiresAt": 1758971614
    },
    {
      "challengeId": "0000000000000000000000000000beef",
      "purpose": "login",
      "numbers": [3, 61, 99],
      "expiresAt": 1758971590
    }
  ]
}
```

**没有待处理挑战时** `data` 为 `[]`（不是 `null`）。按 `expiresAt` 倒序。

### ⚠️ 响应中绝不含正确答案

`data` 里**没有** `correctNumber`。这是硬约束，不是遗漏：

数字匹配模型的安全性建立在「**攻击者无法把数字转达给受害者**」之上 ——
正确答案只发给发起操作的浏览器，用户靠「浏览器上显示的数字」与「App 里 3 个候选」比对来完成验证。
**App 侧一旦拿到正确答案，这个模型就失效了。**

后端有一条测试专门断言该响应序列化后不含 `correctNumber` / `correct_number` 字段。

### 时效

两类挑战的有效期都是**自发起 60 秒**。过期的挑战不会出现在这个接口的返回里。
若 App 拉到的挑战已经接近 `expiresAt`，建议直接忽略而不是弹窗。

## 五、参考实现

以下是建议的改法（三段，都很小）。**代码仅供参考，请按 App 现有风格调整。**

### 1. 数据模型

字段与现有的 `ChallengeMessage` 刻意保持一致，便于直接转换、复用同一个弹窗；
只多一个 `purpose`：

```kotlin
/**
 * 待处理挑战 —— HTTP 主动拉取（GET /api/auth/app/pending-challenges）
 *
 * 与 WebSocket 推送的 ChallengeMessage 是同一份数据的两种投递方式，字段保持一致，
 * 好让调用方直接转成 ChallengeMessage 复用现有的挑战弹窗。
 *
 * ⚠️ 服务端只下发 3 个候选数字，绝不含正确答案。
 */
data class PendingChallenge(
    @SerializedName("challengeId")
    val challengeId: String,
    @SerializedName("purpose")
    val purpose: String? = null,
    @SerializedName("numbers")
    val numbers: List<Int>,
    @SerializedName("expiresAt")
    val expiresAt: Long
)
```

### 2. Retrofit 接口

加在 `ApiService` 里即可（`retrofit2.http.*` 已经在导入范围内）：

```kotlin
/**
 * 主动拉取待处理的挑战（3 选 1）
 *
 * 与 WebSocket 推送互补：socket 断开、App 被杀掉重开、或服务端补推没送达时，
 * 由 App 主动拉一次兜底 —— 这样「发起验证时设备不在线」就只是一个延迟，而不是一条死路。
 */
@GET("/api/auth/app/pending-challenges")
suspend fun getPendingChallenges(
    @Header("Authorization") authorization: String
): Response<ApiResponse<List<PendingChallenge>>>
```

### 3. 前台化时拉取

在 `MainActivity.onResume()` 里，**紧接在现有的「消费后台期间缓存的挑战」之后**：

```kotlin
// 消费后台期间缓存的挑战（Activity 回到前台时处理）
val pendingChallenge = WebSocketService.consumePendingChallenge()
if (pendingChallenge != null) {
    Log.d(TAG, "消费缓存的挑战: id=${pendingChallenge.challengeId}")
    showChallengeDialog(pendingChallenge)
}

// 再向服务端拉一次，作为上面那个本地缓冲区的兜底。
// 本地缓冲区只在「WebSocket 收到过、但 Activity 不在前台」时才有内容；
// socket 断开、App 被杀掉重开时它是空的 —— 拉取才补得上。
pullPendingChallenges()
```

```kotlin
/**
 * 主动拉取服务端待处理的挑战（前台化时调用）
 *
 * 失败静默：推送那条路仍在，不该因为一次拉取失败就弹错打扰用户。
 */
private fun pullPendingChallenges() {
    if (!isLoggedIn()) return
    val token = secureStorage.getToken() ?: return

    lifecycleScope.launch {
        try {
            val response = RetrofitClient.apiService.getPendingChallenges("Bearer $token")
            val pending = response.body()?.data
            if (!response.isSuccessful || pending.isNullOrEmpty()) return@launch

            Log.d(TAG, "拉取到 ${pending.size} 个待处理挑战")
            for (item in pending) {
                // 转成与推送相同的模型，复用同一个弹窗。
                // showChallengeDialog 内部已保证「已有弹窗时跳过」，所以不会叠出多个。
                showChallengeDialog(
                    ChallengeMessage(
                        type = "challenge",
                        challengeId = item.challengeId,
                        numbers = item.numbers,
                        expiresAt = item.expiresAt
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "拉取待处理挑战失败（推送通道仍在，忽略）", e)
        }
    }
}
```

## 六、注意事项

1. **WebSocket 那条路不要动。** 拉取是**补充**不是替代 —— 推送的实时性更好
   （用户守着手机时能立刻弹窗），拉取负责兜底。两条并存。
2. **`expiresAt` 是秒**，别忘 `* 1000`（`ChallengeDialogFragment` 现有的处理方式是对的）。
3. **重复弹窗**：`showChallengeDialog` 已有「已有弹窗则跳过」的守卫，连拉两次不会叠。
   服务端也只会返回 `status='pending'` 的挑战，已应答过的不会再次出现。
4. **不要在 App 里持久化拉取结果**，它是瞬时状态，60 秒后就失效了。
5. **兼容旧版本**：这个接口是新增的，老版本 App 不调它也能正常工作
   （只是少了「不在线时」的兜底）。后端对老版本没有任何破坏性改动。

## 七、如何验证

1. **关闭 App**（或让它在后台待久一点让 socket 断开）。
2. 在网页端做一次敏感操作（例如「系统设置」里保存），触发验证弹窗。
3. 网页上应该看到 **「已提交，打开 App 即可看到」**（而不是以前那句「没有在线设备可推送」）。
4. 打开 App → 应当自动弹出 3 选 1 挑战 → 按网页上显示的数字选中 → 网页那边验证通过。

第 4 步就是本次改动要达到的效果。

## 八、相关改动

后端同时补了一条**红线违规**（与本需求无关，但同一次改动里发现并修掉的）：
`GET /api/auth/app/history` 返回的 `AuthHistoryResponse` 原先带 `correctNumber`，
而该接口**不排除 pending 中的挑战** —— 意味着 App 能读到当前待处理挑战的正确答案。
已核实 App 侧零引用该字段，故直接摘除。**如果将来要用它做任何展示，请先联系后端。**

---

**文档版本**：1.0
**最后更新**：2026-09-27
