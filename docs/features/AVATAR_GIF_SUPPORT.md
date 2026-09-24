# 头像 GIF 动图支持

## 📋 概述

博主头像与用户头像均支持 GIF 动图，体积上限统一为 **5MB**。

本次同时修复了三个此前就已存在的缺陷（详见[一并修复的既有 Bug](#-一并修复的既有-bug)），
其中「博主头像永久 404」在改动前就已经在生产环境发生。

---

## 🔍 背景：GIF 为什么播不出来

**后端头像链路本身不做任何转码** —— 上传原始字节、存 MinIO 原始字节、返回原始字节，
白名单与魔数校验也早已放行 GIF。所以问题不在编码，而在三处人为限制：

| 环节 | 改动前 | 后果 |
|------|--------|------|
| 博主头像读取 | `Content-Type` 硬编码为 `image/jpeg` | 字节是 GIF 却声明 JPEG，浏览器解码失败 → **破图** |
| 博主头像上传 | 前端白名单只有 jpeg/png/webp | 文件选择器与校验双重拒绝 GIF |
| 用户头像上传 | 前端限制 2MB | 动图常超过此值，被拒 |

根因在于读取链路只返回 `byte[]`，`BloggerInfoController` 无从得知实际格式，只能写死类型。

---

## 🛠 实现要点

### 1. Content-Type 按扩展名推导

新增 `util/ImageContentTypeUtils`，统一所有头像端点的类型推导（此前 `UserController`
有一份私有实现，博主头像则完全硬编码）。

| 扩展名 | Content-Type |
|--------|--------------|
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.png` | `image/png` |
| `.gif` | `image/gif` |
| `.webp` | `image/webp` |
| `.svg` | `image/svg+xml` |
| 其他 / 无扩展名 | `application/octet-stream` |

依据是 MinIO 对象路径（`avatars/{userId}/{uuid}.gif`）的扩展名 —— 该格式在上传时
已经过白名单与魔数双重校验，比读取对象元数据更廉价，也比嗅探字节更简单。

> **注意**：未知格式刻意回退 `application/octet-stream` 而非 `image/jpeg`。
> 生产入口设置了 `X-Content-Type-Options: nosniff`，浏览器必须按声明的类型解释响应，
> 谎报成 JPEG 的后果就是破图（`.svg` 此前正是这样被误标的）。

### 2. Content-Disposition 策略

| 类型 | 取值 |
|------|------|
| 位图（jpg/png/gif/webp） | `inline` |
| SVG | `attachment` |
| 未知格式 | `attachment` |

按 RFC 6266，`Content-Disposition` **只作用于顶层导航**，对 `<img>`、`fetch()` 这类
子资源请求会被浏览器忽略。因此它对「防止图片被内联渲染」并无实际作用 ——
真正的安全边界是 **Content-Type 白名单 + `nosniff`**。

保留 `attachment` 仅用于堵住「用户直接导航到该 URL」这一条路径，而唯一具备脚本能力的
格式是 SVG，故只对它生效。`filename` 只由白名单扩展名拼出，不含用户输入，无头注入面。

### 3. 格式与体积限制

| 位置 | 限制 |
|------|------|
| 前端 | `utils/imageUpload.ts` —— 白名单含 GIF/SVG，上限 5MB |
| 后端 | `AvatarUploadService` / `BloggerInfoService` —— 均为 5MB |
| Servlet | `application.yml` —— `max-file-size: 10MB` |

前端的校验仅用于快速反馈，真正生效的是后端白名单 + 魔数校验
（文本改名成 `.gif` 会被魔数分支拒绝）。

### 4. 列表页懒加载

GIF 头像可达 5MB，评论列表与用户管理表格的每一行都会触发下载与动画解码。

`el-avatar` **不透传 `$attrs`** —— 它内部只把 `src/alt/srcset/style/onError` 交给
`<img>`，其余属性会落到最外层 `<span>`，因此 `loading="lazy"` 传不进去。

为此新增 `components/LazyAvatar.vue`：用 `IntersectionObserver` 在元素进入视口
（含 200px 预加载余量）后才挂载 `el-avatar`，未进入视口时保留同尺寸占位以免布局跳动。
环境不支持 `IntersectionObserver` 时直接加载，不会让头像永久空白。

已应用于：`PostDetail.vue`（评论、回复）、`admin/UserManagement.vue`（用户列表）。
自己的头像（顶栏、个人中心）是单个元素，无需懒加载。

---

## 🐛 一并修复的既有 Bug

### 1. 博主头像永久 404（改动前已在生产发生）

**成因链**：

```
convertToResponse 把 avatar 字段投影成展示 URL「/api/blogger/avatar」
  → 前端 Object.assign(formData, data) 回填表单
  → 提交时 {...formData} 原样回传
  → updateBloggerInfo 无条件 info.setAvatar(request.getAvatar())
  → 数据库里的对象路径被覆盖成该伪 URL
  → validateObjectPath 拒绝以「/」开头的路径
  → getBloggerAvatar() 返回 null → 404
```

**修复**：`updateBloggerInfo` 不再写入 `avatar` —— 头像只由 `/api/blogger/avatar/upload`
变更；前端提交时也不再回传该字段（双保险）。

> ⚠️ **存量数据不会自动更正**。若线上已发生污染，需在后台**重新上传一次博主头像**。

### 2. 保存昵称会清空用户头像

`Profile.vue` 提交时无条件带上 `avatar: formData.value.avatar`，而该字段对应的
「头像URL」输入框默认为空串且页面加载时不回填。后端 `UserService.updateUser` 判
`request.getAvatar() != null`（空串非 null）后执行 `setAvatar("")` —— 于是「只改昵称」
也会把头像清空。

**修复**：仅在用户确实填写了头像 URL 时才提交该字段。

### 3. `.svg` 被误标为 `image/jpeg`

旧的 `getImageContentType` 默认分支返回 `image/jpeg`，而 `.svg` 不在任何已处理分支中。
在 `nosniff` 下表现为破图。已由新的工具类修正。

### 4. 非 MinIO 路径返回 500 而非 404

`AvatarUploadService.getAvatar()` 的 `validateObjectPath` 位于 `try` 块之外，
而 `validateAvatarUrl` 允许 `https://` 外链头像 —— 这类值会在校验时抛异常直穿到
controller，返回 500。已改为校验失败按「不存在」处理，落到 404。

---

## 💾 缓存行为

| 接口 | Cache-Control | 说明 |
|------|---------------|------|
| `/api/users/me/avatar` | `private, max-age=3600` | 前端 `useAvatar` 另加 `?t=` 时间戳 |
| `/api/users/avatar/{userId}` | `public, max-age=86400` | 语义为带权限的下载 |
| `/api/users/avatar/public/{userId}` | `public, max-age=86400` | 评论区/列表使用 |
| `/api/blogger/avatar` | `public, max-age=86400` | 管理页预览另加 `?t=` 时间戳 |

**已知限制**：`/avatar/public/{userId}` 的 URL 不含版本号，用户更换头像后，
评论区与用户列表最多 24 小时才会刷新（既有行为，非本次引入）。
如需即时生效，可在该 URL 上附加基于用户更新时间的版本参数。

---

## ✅ 验证方式

**单元测试**：`src/test/java/com/leo/myblog/util/ImageContentTypeUtilsTest.java`
覆盖各扩展名映射、大小写、带点的目录名、SVG 强制 attachment、未知格式回退等 11 个用例。

**接口验证**（上传 GIF 后）：

```bash
# 类型必须是 image/gif，而不是 image/jpeg
curl -sI http://127.0.0.1:8080/api/blogger/avatar | grep -i content-type

# 不信扩展名，看魔数：应为 GIF89a / GIF87a
curl -s http://127.0.0.1:8080/api/blogger/avatar | head -c 6 | od -c
```

**前端验证**：
1. 后台上传 GIF → 立即播放动画
2. 保存博主信息 → 刷新页面 → 头像仍在（验证 Bug 1）
3. 个人资料上传 GIF → 改昵称保存 → 头像不消失（验证 Bug 2）

> 本地跑 `./gradlew test` 需要先补 MinIO 占位环境变量，另需 Redis 与 PostgreSQL，
> 详见 `backend-test-env` 笔记。

---

## 📌 后续可选

- **列表页纯静态头像**：为 GIF 额外生成静态首帧供列表使用，需要引入图像处理库
- **响应式流**：`getAvatar()` 目前 `readAllBytes()` 全量进堆，高并发下可改为流式传输
- **nginx 部署注意**：仓库根 `nginx.conf` 的 `location /api/` 未设 `client_max_body_size`
  （默认 1MB），走 nginx 时 >1MB 会 413。生产当前用 Apache httpd（默认不限），不受影响
