# 头像 API 变更指南（前端对接）

## 变更摘要

新增了 `GET /api/users/me/avatar` 展示接口，并将上传接口返回值从 `/api/users/avatar/{userId}` 改为 `/api/users/me/avatar`。

---

## 完整接口列表

| 接口 | 方法 | 用途 | Content-Disposition | 认证 |
|------|------|------|---------------------|------|
| `/api/users/me/avatar` | POST | 上传头像 | — | 需要 |
| `/api/users/me/avatar` | GET | **展示**（`<img src>` 直接使用） | `inline` | 需要 |
| `/api/users/avatar/{userId}` | GET | **下载**（强制浏览器下载） | `attachment` | 需要 |
| `/api/users/avatar/public/{userId}` | GET | 公开头像（评论区等） | `attachment` | 不需要 |

---

## 变更对比

### 上传接口返回值

**旧**：
```json
{
  "code": 200,
  "message": "头像上传成功",
  "data": "/api/users/avatar/1234567890"
}
```

**新**：
```json
{
  "code": 200,
  "message": "头像上传成功",
  "data": "/api/users/me/avatar"
}
```

### 头像展示

**旧**：使用 `GET /api/users/avatar/{userId}`（会触发浏览器下载）

**新**：使用 `GET /api/users/me/avatar`（浏览器直接渲染图片）

---

## 前端调整指南

### 1. 头像 `<img>` 标签 — 直接使用新接口

```html
<!-- 推荐：直接作为 img 的 src -->
<img src="/api/users/me/avatar" alt="我的头像" />
```

> **注意**：`/api/users/me/avatar` 返回 `Content-Disposition: inline`，浏览器会直接渲染图片。
> 而 `/api/users/avatar/{userId}` 返回 `attachment`，浏览器会下载而不是渲染。

### 2. JavaScript 动态加载

```javascript
// ✅ 推荐：使用展示接口
const loadAvatar = async () => {
  const response = await fetch('/api/users/me/avatar', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (response.ok) {
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    avatarImg.src = url;
  }
};

// ❌ 不推荐：下载接口会返回 attachment，不适用于 <img> 展示
const loadAvatar = async () => {
  const response = await fetch(`/api/users/avatar/${userId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  // ...
};
```

### 3. Vue 3 组件示例

```vue
<template>
  <div class="avatar-section">
    <!-- 展示头像 -->
    <img 
      v-if="avatarUrl" 
      :src="avatarUrl" 
      alt="我的头像" 
      class="avatar"
    />
    <img v-else src="/default-avatar.png" alt="默认头像" class="avatar" />
    
    <!-- 上传按钮 -->
    <input type="file" @change="handleUpload" accept="image/*" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const avatarUrl = ref(null);
const token = localStorage.getItem('token');

// 加载头像
const loadAvatar = async () => {
  const response = await fetch('/api/users/me/avatar', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (response.ok) {
    const blob = await response.blob();
    avatarUrl.value = URL.createObjectURL(blob);
  }
};

// 上传头像
const handleUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch('/api/users/me/avatar', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },
    body: formData
  });
  
  const result = await response.json();
  if (result.code === 200) {
    // 上传成功后重新加载头像
    await loadAvatar();
  }
};

onMounted(loadAvatar);
</script>
```

### 4. 下载头像时使用下载接口

```javascript
// 下载头像（如导出/备份场景）
const downloadAvatar = async (userId) => {
  const response = await fetch(`/api/users/avatar/${userId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (response.ok) {
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `avatar-${userId}.jpg`;
    a.click();
    URL.revokeObjectURL(url);
  }
};
```

---

## 缓存策略

| 接口 | Cache-Control | 说明 |
|------|---------------|------|
| `GET /api/users/me/avatar` | `private, max-age=3600` | 私有缓存1小时，上传后需刷新 |
| `GET /api/users/avatar/{userId}` | `public, max-age=86400` | 公共缓存1天 |
| `GET /api/users/avatar/public/{userId}` | `public, max-age=86400` | 公共缓存1天 |

> **上传后刷新**：上传成功后调用 `loadAvatar()` 重新获取，浏览器会获取新的图片。

---

## 注意事项

1. **`/api/users/me/avatar` 需要登录认证**，未登录用户访问返回 401
2. **评论区等公开场景**使用 `/api/users/avatar/public/{userId}`（无需登录）
3. **默认头像**：`GET /api/users/me` 返回的 `avatar` 字段为 null 时，前端应显示默认头像
4. **上传后**：直接重新请求 `GET /api/users/me/avatar` 即可获取最新头像
