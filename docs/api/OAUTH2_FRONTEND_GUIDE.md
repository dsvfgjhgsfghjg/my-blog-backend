# OAuth 2.0 认证系统 — 前端对接文档

> **版本:** v1.0  
> **更新日期:** 2026-06-10  
> **后端开发者:** Leo  

---

## 📋 概述

OAuth 2.0 认证系统是在原有登录体系基础上新增的 **认证 API 2.0**，与现有 JWT 认证共存。前端可以使用以下两种方式登录：

| 方式 | 说明 | 接入难度 |
|---|---|---|
| **密码模式** | `grant_type=password` — 替代原有登录接口，配合验证码 | ⭐ 推荐 |
| **第三方 OAuth** | GitHub / Google 账号快捷登录 | ⭐⭐ |
| **刷新 Token** | `grant_type=refresh_token` — 无感续期 | ⭐ 推荐 |

> **基准 URL:** `https://你的域名/api/auth2`  
> （本地开发：`http://localhost:8080/api/auth2`）

---

## 🚀 快速开始

### 场景 A：密码登录（推荐替代原 /api/auth/login）

```
POST /api/auth2/token
```

**请求体：**
```json
{
    "grant_type": "password",
    "username": "leoadmin",
    "password": "your_password",
    "captcha_nonce_str": "xxx",       // 旋转验证码标识
    "captcha_angle": 123.45,           // 旋转验证码角度
    "captcha_trajectory": [            // 鼠标轨迹（可选）
        {"x": 100, "y": 200, "t": 50},
        {"x": 150, "y": 180, "t": 100}
    ],
    "captcha_total_time": 2500         // 验证耗时（毫秒）
}
```

**响应：**
```json
{
    "code": 200,
    "msg": "Token 颁发成功",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiJ9...",
        "token_type": "Bearer",
        "expires_in": 3600,
        "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
        "refresh_token_expires_in": 604800,
        "scope": "read write",
        "user_id": 1,
        "nickname": "Leo",
        "avatar": "https://..."
    }
}
```

**TypeScript 示例：**
```typescript
// src/services/auth.ts
interface PasswordLoginParams {
  username: string
  password: string
  captchaNonceStr: string
  captchaAngle: number
  captchaTrajectory?: Array<{ x: number; y: number; t: number }>
  captchaTotalTime?: number
}

interface TokenResponse {
  access_token: string
  token_type: string
  expires_in: number
  refresh_token: string
  refresh_token_expires_in: number
  scope?: string
  user_id: number
  nickname: string
  avatar: string
}

export async function loginWithPassword(params: PasswordLoginParams) {
  const res = await fetch('/api/auth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      grant_type: 'password',
      username: params.username,
      password: params.password,
      captcha_nonce_str: params.captchaNonceStr,
      captcha_angle: params.captchaAngle,
      captcha_trajectory: params.captchaTrajectory,
      captcha_total_time: params.captchaTotalTime,
    }),
  })
  return res.json() as Promise<ApiResponse<TokenResponse>>
}
```

---

### 场景 B：刷新 Token（Token 过期前调用）

```
POST /api/auth2/token/refresh
```

**请求体：**
```json
{
    "grant_type": "refresh_token",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

> 💡 实际上可以直接调用 `/api/auth2/token/refresh` 端点，无需传 grant_type，后端会自动处理。

**TypeScript 示例：**
```typescript
export async function refreshAccessToken(refreshToken: string) {
  const res = await fetch('/api/auth2/token/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: refreshToken }),
  })
  return res.json() as Promise<ApiResponse<TokenResponse>>
}
```

**前端建议：** 在 axios 响应拦截器中自动刷新 Token：

```typescript
// src/utils/axios.ts
import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

// 响应拦截器 — 自动刷新 Token
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
      error.config._retry = true
      const refreshToken = localStorage.getItem('refresh_token')
      if (refreshToken) {
        const res = await axios.post('/api/auth2/token/refresh', {
          refresh_token: refreshToken,
        })
        const { access_token, refresh_token } = res.data.data
        localStorage.setItem('access_token', access_token)
        localStorage.setItem('refresh_token', refresh_token)
        error.config.headers.Authorization = `Bearer ${access_token}`
        return http(error.config)
      }
    }
    return Promise.reject(error)
  }
)
```

---

### 场景 C：撤销 Token（登出时调用）

```
POST /api/auth2/token/revoke
```

**请求体：**
```json
{
    "access_token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**响应：**
```json
{
    "code": 200,
    "msg": "Token 已撤销"
}
```

**TypeScript 示例：**
```typescript
export async function logout(accessToken: string) {
  const res = await fetch('/api/auth2/token/revoke', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ access_token: accessToken }),
  })
  if (res.ok) {
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user_info')
    // 跳转到登录页
    window.location.href = '/login'
  }
}
```

---

### 场景 D：第三方 OAuth 登录（GitHub/Google）

#### 第一步：获取第三方授权 URL

```
POST /api/auth2/oauth/login
```

**请求体：**
```json
{
    "provider": "github",
    "redirect_uri": "http://localhost:3000/auth/callback"
}
```

**响应：**
```json
{
    "code": 200,
    "msg": "获取授权URL成功",
    "data": {
        "authorize_url": "https://github.com/login/oauth/authorize?client_id=xxx&redirect_uri=...&scope=user:email&state=http://localhost:3000/auth/callback"
    }
}
```

#### 第二步：前端跳转到第三方授权页

```typescript
export async function oauthLogin(provider: 'github' | 'google') {
  const res = await fetch('/api/auth2/oauth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider,
      redirect_uri: window.location.origin + '/auth/callback',
    }),
  })
  const data = await res.json()
  // 跳转到第三方授权页
  window.location.href = data.data.authorize_url
}
```

#### 第三步：处理回调（后端已完成，前端只需接收结果）

用户在 GitHub/Google 授权后，会重定向到后端的回调地址，后端处理完会重定向到前端页面。如果后端配置了前端回调地址，流程是全自动的。

---

### 场景 E：获取当前用户信息

```
GET /api/auth2/user
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**响应：**
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "user_id": 1,
        "username": "leoadmin",
        "nickname": "Leo",
        "avatar": "https://...",
        "email": "admin@example.com"
    }
}
```

---

### 场景 F：查询 Token 信息

```
GET /api/auth2/token/info
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**响应：**
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "user_id": 1,
        "username": "leoadmin",
        "expires_in": 3599,
        "active": true
    }
}
```

---

## 🔐 安全注意事项

1. **Token 存储：** 建议将 `access_token` 和 `refresh_token` 存储在 `localStorage` 中，并在每次请求的 `Authorization` 头中携带
2. **自动刷新：** 在 axios 拦截器中实现 401 自动刷新，提升用户体验
3. **登出清理：** 调用 `/api/auth2/token/revoke` 后清除本地存储的 Token
4. **验证码要求：** `password` 模式登录必须携带旋转验证码参数（`captcha_nonce_str` + `captcha_angle`）

---

## 📊 与旧版登录接口对比

| 功能 | 旧版 `/api/auth/login` | 新版 `/api/auth2/token` |
|---|---|---|
| 登录 | ✅ | ✅（推荐） |
| 刷新 Token | ❌ 需重新登录 | ✅ 自动刷新 |
| Token 撤销 | ❌ | ✅ |
| 第三方登录 | ❌ | ✅（GitHub/Google） |
| Token 信息查询 | ❌ | ✅ |
| 返回用户信息 | ❌ 仅返回 token | ✅ 返回 user_id/nickname/avatar |

---

## 🧩 端点汇总

| 方法 | 端点 | 是否需要认证 | 说明 |
|---|---|---|---|
| `POST` | `/api/auth2/token` | ❌ 公开 | 颁发 Token（password/authorization_code/client_credentials） |
| `POST` | `/api/auth2/token/refresh` | ❌ 公开 | 刷新 Token |
| `POST` | `/api/auth2/token/revoke` | ❌ 公开 | 撤销 Token |
| `GET` | `/api/auth2/token/info` | ✅ Bearer Token | 查询 Token 信息 |
| `POST` | `/api/auth2/authorize` | ✅ Bearer Token | 创建授权码 |
| `GET` | `/api/auth2/user` | ✅ Bearer Token | 获取当前用户信息 |
| `POST` | `/api/auth2/oauth/login` | ❌ 公开 | 获取第三方登录跳转 URL |
| `POST` | `/api/auth2/oauth/callback` | ❌ 公开 | 第三方登录回调（后端处理） |

---

## 📝 响应格式说明

所有接口统一返回格式：

```typescript
interface ApiResponse<T> {
  code: number    // 200=成功, 400=参数错误, 401=未授权, 500=服务器错误
  msg: string     // 提示信息
  data: T         // 实际数据
}
```
