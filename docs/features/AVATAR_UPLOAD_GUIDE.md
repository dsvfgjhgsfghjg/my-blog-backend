# 头像上传功能使用指南

## 功能概述

本系统实现了完整的头像上传、存储和获取功能，具有以下特性：

### 核心功能
1. **头像上传**：支持上传图片文件到MinIO对象存储
2. **安全校验**：文件类型白名单、魔数验证、文件大小限制
3. **隐私保护**：用户只能查看自己的头像，管理员可查看所有
4. **自动更新**：上传后自动更新数据库中的头像路径
5. **缓存优化**：使用Redis缓存用户信息，提高访问速度

### 支持格式
- JPEG (.jpg, .jpeg)
- PNG (.png)
- GIF (.gif)
- WebP (.webp)

### 限制条件
- 最大文件大小：5MB
- 必须进行身份认证（JWT Token）

---

## API接口说明

### 1. 上传头像

**接口地址**：`POST /api/users/me/avatar`

**请求方式**：multipart/form-data

**请求头**：
```
Authorization: Bearer <your_jwt_token>
Content-Type: multipart/form-data
```

**请求参数**：
- `file`: 图片文件（必填）

**响应示例**：
```json
{
  "code": 200,
  "message": "头像上传成功",
  "data": "/api/users/me/avatar"
}
```

**JSON格式的请求示例**（用于理解参数结构）：
```
POST /api/users/me/avatar
Headers:
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  
Body (form-data):
  file: [选择图片文件]
```

---

### 2. 展示头像（内联，用于 `<img>` 标签）

**接口地址**：`GET /api/users/me/avatar`

**请求方式**：GET

**请求头**：
```
Authorization: Bearer <your_jwt_token>
```

**说明**：
- 返回当前登录用户的头像，**浏览器直接渲染图片**
- `Content-Disposition: inline` — 可用于 `<img src>` 直接展示
- `Cache-Control: private, max-age=3600` — 私有缓存1小时
- 需要登录认证

**响应**：
- 成功：返回图片二进制数据（Content-Type: image/jpeg 等，Content-Disposition: inline）
- 失败：
  - 404 Not Found：头像不存在
  - 500 Internal Server Error：服务器错误

**使用方式**：
```html
<!-- 直接作为 img 标签的 src（需携带 Authorization） -->
<img src="/api/users/me/avatar" alt="我的头像" />
```

---

### 3. 下载头像（附件，强制浏览器下载）

**接口地址**：`GET /api/users/avatar/{userId}`

**请求方式**：GET

**请求头**：
```
Authorization: Bearer <your_jwt_token>
```

**路径参数**：
- `userId`: 目标用户ID

**说明**：
- `Content-Disposition: attachment` — **强制浏览器下载**，不会内联展示
- `Cache-Control: public, max-age=86400` — 公共缓存1天

**权限控制**：
- ✅ 用户可以查看自己的头像（token中的userId = 路径中的userId）
- ✅ 管理员可以查看任何用户的头像
- ❌ 普通用户不能查看他人头像（返回403）

**响应**：
- 成功：返回图片二进制数据（Content-Type: image/jpeg 等，Content-Disposition: attachment）
- 失败：
  - 403 Forbidden：无权查看
  - 404 Not Found：头像不存在
  - 500 Internal Server Error：服务器错误

**JSON格式的请求示例**：
```
GET /api/users/avatar/1234567890
Headers:
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

### 4. 公开获取头像（无需登录）

**接口地址**：`GET /api/users/avatar/public/{userId}`

**请求方式**：GET

**说明**：
- 无需认证，用于评论区等公开场景
- `Content-Disposition: attachment`

---

### 5. 获取当前用户信息（包含头像路径）

**接口地址**：`GET /api/users/me`

**请求方式**：GET

**请求头**：
```
Authorization: Bearer <your_jwt_token>
```

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1234567890,
    "username": "testuser",
    "nickname": "测试用户",
    "avatar": "avatars/1234567890/abc-def-123.jpg",
    "isAdmin": false,
    "createTime": "2024-01-01T12:00:00"
  }
}
```

**注意**：返回的`avatar`字段是MinIO对象路径（如 `avatars/1/xxx.jpg`），需通过 `/api/users/me/avatar` 接口获取实际图片。

---

## 使用流程

### 完整流程示例

#### 步骤1：登录获取Token
```
POST /api/users/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "123456"
}

响应：
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1234567890,
    "username": "testuser",
    ...
  }
}
```

#### 步骤2：上传头像
```
POST /api/users/me/avatar
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: multipart/form-data

file: avatar.jpg (选择本地图片文件)

响应：
{
  "code": 200,
  "message": "头像上传成功",
  "data": "/api/users/me/avatar"
}
```

#### 步骤3：展示头像（内联）
```
GET /api/users/me/avatar
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

响应：图片二进制数据（Content-Disposition: inline）
```

#### 步骤4：在HTML中显示头像
```html
<!-- 方式1：直接使用展示URL（推荐，最简单） -->
<img src="/api/users/me/avatar" 
     alt="用户头像"
     style="width: 100px; height: 100px; border-radius: 50%;" />

<!-- 方式2：通过JavaScript动态加载（适用于需要携带token的场景） -->
<script>
fetch('/api/users/me/avatar', {
  headers: {
    'Authorization': 'Bearer ' + token
  }
})
.then(response => response.blob())
.then(blob => {
  const url = URL.createObjectURL(blob);
  document.getElementById('avatar').src = url;
});
</script>
```

---

## 技术实现细节

### 1. 文件存储结构

MinIO中的文件路径格式：
```
avatars/{userId}/{uuid}.{extension}

示例：
avatars/1234567890/550e8400-e29b-41d4-a716-446655440000.jpg
```

### 2. 数据库存储

`t_user`表中的`avatar`字段存储的是MinIO对象路径（而非完整URL）：
```sql
UPDATE t_user SET avatar = 'avatars/1234567890/550e8400-e29b-41d4-a716-446655440000.jpg' 
WHERE id = 1234567890;
```

### 3. 安全校验流程

上传时的安全检查：
1. ✅ 验证文件不为空
2. ✅ 验证文件大小（≤ 5MB）
3. ✅ 验证文件扩展名（白名单）
4. ✅ 验证MIME类型（白名单）
5. ✅ 验证文件魔数签名（防止伪装文件）
6. ✅ 防止路径遍历攻击
7. ✅ 生成UUID文件名（防止冲突和注入）

### 4. 权限控制

头像获取接口的权限逻辑：
```java
// 伪代码
if (currentUserId == targetUserId || isAdmin) {
    // 允许访问
    return getAvatarFromMinio();
} else {
    // 拒绝访问
    return 403 Forbidden;
}
```

---

## 前端集成示例

### React示例

```jsx
import React, { useState } from 'react';

function AvatarUpload({ userId, token }) {
  const [avatarUrl, setAvatarUrl] = useState(null);
  
  // 上传头像
  const handleUpload = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('/api/users/me/avatar', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      },
      body: formData
    });
    
    const result = await response.json();
    if (result.code === 200) {
      // 重新加载头像
      loadAvatar();
    }
  };
  
  // 加载头像（展示接口，inline）
  const loadAvatar = async () => {
    const response = await fetch('/api/users/me/avatar', {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    if (response.ok) {
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      setAvatarUrl(url);
    }
  };
  
  return (
    <div>
      {avatarUrl && <img src={avatarUrl} alt="头像" />}
      <input 
        type="file" 
        accept="image/*"
        onChange={(e) => handleUpload(e.target.files[0])} 
      />
    </div>
  );
}
```

### Vue示例

```vue
<template>
  <div>
    <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
    <input type="file" @change="handleUpload" accept="image/*" />
  </div>
</template>

<script>
export default {
  data() {
    return {
      avatarUrl: null
    };
  },
  methods: {
    async handleUpload(event) {
      const file = event.target.files[0];
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await fetch('/api/users/me/avatar', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${this.token}`
        },
        body: formData
      });
      
      const result = await response.json();
      if (result.code === 200) {
        this.loadAvatar();
      }
    },
    
    // 加载头像（展示接口，inline）
    async loadAvatar() {
      const response = await fetch('/api/users/me/avatar', {
        headers: {
          'Authorization': `Bearer ${this.token}`
        }
      });
      
      if (response.ok) {
        const blob = await response.blob();
        this.avatarUrl = URL.createObjectURL(blob);
      }
    }
  }
};
</script>
```

---

## 常见问题

### Q1: 为什么上传后看不到头像？
A: 检查以下几点：
1. 确认上传成功（返回code=200）
2. 确认使用了正确的Token
3. 确认调用获取头像接口时传递了相同的userId
4. 检查浏览器控制台是否有CORS错误

### Q2: 如何设置默认头像？
A: 在`UserService.convertToUserResponse()`方法中已设置默认头像：
```java
if (response.getAvatar() == null || response.getAvatar().isEmpty()) {
    response.setAvatar("https://avatars.githubusercontent.com/u/67023703");
}
```

### Q3: 能否直接访问MinIO中的文件？
A: 不建议。应该通过 `/api/users/me/avatar` 接口访问，这样可以：
- 进行权限控制
- 记录访问日志
- 统一处理错误

### Q4: 如何更换头像？
A: 直接再次调用上传接口即可，新头像会覆盖旧头像的路径。旧的MinIO文件不会被自动删除（可以定期清理）。

### Q5: 支持的最大头像尺寸是多少？
A: 文件大小限制为5MB，建议上传前压缩图片以提升性能。

---

## 注意事项

1. **安全性**：
   - 所有接口都需要JWT认证
   - 文件上传有严格的安全校验
   - 头像访问有权限控制

2. **性能优化**：
   - 用户信息使用Redis缓存
   - 展示接口（`/me/avatar`）缓存1小时，下载接口（`/avatar/{userId}`）缓存1天
   - 建议使用CDN加速图片访问

3. **存储空间**：
   - 每次上传都会生成新文件
   - 建议定期清理旧头像文件
   - 可以配置MinIO生命周期策略

4. **兼容性**：
   - 支持主流图片格式
   - 自动检测Content-Type
   - 响应头包含缓存控制

---

## 后续优化建议

1. **图片压缩**：上传时自动压缩图片，减少存储空间
2. **缩略图生成**：生成不同尺寸的头像（小、中、大）
3. **旧文件清理**：上传新头像后自动删除旧文件
4. **CDN集成**：使用CDN加速头像访问
5. **水印功能**：可选添加水印保护版权
6. **批量上传**：支持一次上传多张图片供用户选择
