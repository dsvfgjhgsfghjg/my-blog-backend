# 文章图片上传 API 文档

## 📋 背景

文章编辑器中，用户按下 `Ctrl+V` 粘贴剪贴板中的图片时，前端需要：

1. 检测剪贴板内容是否为图片
2. 将图片文件上传到后端
3. 后端返回可公开访问的图片 URL
4. 前端将 Markdown 图片语法 `![alt](url)` 插入编辑器

目前后端 **缺少图片上传接口**，需要后端开发支持。

---

## 🎯 需求概述

| 需求 | 说明 |
|------|------|
| **接口** | `POST /api/images/upload` |
| **用途** | 上传文章正文中的图片（剪贴板粘贴 或 文件选择） |
| **认证** | 需要登录（管理员/作者） |
| **存储** | 建议使用已存在的 MinIO 对象存储（与头像、音频封面一致） |
| **访问控制** | 上传后的图片应**公开可访问**（直接通过 URL 展示在文章中） |
| **限制** | 单文件最大 10MB，支持格式：jpg/png/gif/webp/svg |

---

## 📡 接口定义

### POST /api/images/upload

#### 请求头

| 头 | 值 | 必填 | 说明 |
|----|-----|------|------|
| `Content-Type` | `multipart/form-data` | 是 | 文件上传必须 |
| `Cookie` | `SESSION=...` | 是 | 登录凭证（HttpOnly Cookie） |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
|--------|------|------|------|------|
| `file` | body | `multipart/form-data` | 是 | 图片文件（二进制） |

#### 成功响应（200）

```json
{
  "code": 0,
  "message": "上传成功",
  "data": {
    "url": "https://your-minio-domain.com/images/2026/06/abc123def.jpg",
    "filename": "abc123def.jpg",
    "originalName": "screenshot.png",
    "size": 102400,
    "width": 1920,
    "height": 1080,
    "mimeType": "image/png"
  }
}
```

#### 错误响应

```json
// 文件太大（> 10MB）
{
  "code": 400,
  "message": "图片大小不能超过10MB",
  "data": null
}

// 格式不支持
{
  "code": 400,
  "message": "不支持的图片格式，仅支持 jpg/png/gif/webp/svg",
  "data": null
}

// 未登录
{
  "code": 401,
  "message": "未登录",
  "data": null
}
```

---

## 🔧 后端实现建议

### 1. 存储路径规则

```
/images/{yyyy}/{MM}/{uuid}.{ext}
```

示例：
- `/images/2026/06/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg`

### 2. 返回 URL 格式

返回**可直接在 `<img>` 标签中使用的完整 URL**，建议通过 MinIO 预签名或 Nginx 反向代理提供公开访问：

```
https://your-domain.com/images/2026/06/a1b2c3d4.jpg
```

> ⚠️ 注意：不要返回 MinIO 内部路径（如 `bucket-name/xxx`），前端需要的是 HTTP 可访问的 URL。

### 3. 安全校验

| 校验项 | 说明 |
|--------|------|
| 登录认证 | 需要管理员/作者权限 |
| 文件类型 | 严格校验 MIME Type，禁止上传非图片文件 |
| 文件大小 | 单文件 ≤ 10MB |
| XSS 防护 | 返回的 URL 本身无风险，无需额外过滤 |

### 4. 后端参考代码（音频上传已有示例）

项目中已有 `POST /api/audio/upload` 和 `POST /api/audio/cover/upload` 的文件上传实现，可以参考其 MinIO 存储逻辑。不同点：

- 图片上传不需要 title/artist 等元数据
- 图片上传后需要返回 URL（而非 ID）
- 图片需要公开访问（音频是流式播放）

### 5. 建议使用已存在的统一响应格式

```java
public class Result<T> {
    private int code;      // 0=成功
    private String message;
    private T data;
}
```

---

## 📝 前端对接方式

**在 `src/services/post.ts` 中新增函数：**

```typescript
// 上传文章图片
export function uploadArticleImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ImageUploadResponse>('/images/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
```

> ⚠️ 注意：`request.ts` 默认的 `Content-Type` 是 `application/json`，上传文件时必须覆盖为 `multipart/form-data`。

**响应类型定义：**

```typescript
export interface ImageUploadResponse {
  url: string          // 可直接使用的图片 URL
  filename: string     // 存储文件名
  originalName: string // 原始文件名
  size: number         // 文件大小（字节）
  width?: number       // 图片宽度（可选）
  height?: number      // 图片高度（可选）
  mimeType: string     // MIME 类型
}
```

---

## ✅ 验收标准

1. ✅ 前端 `POST /api/images/upload` 上传图片文件，返回 200 + 图片 URL
2. ✅ 返回的 URL 可在浏览器中直接打开显示图片
3. ✅ 不支持的文件格式返回 400 错误
4. ✅ 超过 10MB 返回 400 错误
5. ✅ 未登录返回 401
6. ✅ 图片文件通过 MinIO 存储，访问路径公开可用

---

## 📤 操作流程

1. **将此文档提供给后端开发人员**
2. 后端完成后，将**接口 URL 和响应示例**更新回本文档
3. 前端根据最终确认的 API 实现 `Ctrl+V` 粘贴上传功能
