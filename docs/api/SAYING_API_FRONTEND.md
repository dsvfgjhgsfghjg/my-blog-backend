# 名言名句 API — 前端对接文档

> **版本:** v1.0  
> **更新日期:** 2026-06-07  
> **后端开发者:** Leo  

---

## 📋 背景

博客首页需要展示名言名句，支持 5 个模块：
- **计算机** (`cs`)
- **设计** (`design`)
- **学习** (`study`)
- **科技** (`technology`)
- **思想** (`thought`)

每个模块包含约 100 条名言，前端可从任意模块随机获取一条展示。同时后台管理界面需要支持添加新的名言到指定模块。

---

## 🚀 快速开始

### 1. API 基础路径

```
生产环境: https://your-api-domain.com
开发环境: http://localhost:8080
```

所有请求路径均以 `/api` 开头。

### 2. 统一响应格式

所有接口返回格式一致：

```typescript
interface ApiResult<T> {
  code: number    // 200=成功，其他=失败
  message: string
  data: T
}
```

---

## 📡 公开接口（无需鉴权）

### 2.1 随机获取一条名言

从指定模块随机抽出一条名言。

```
GET /api/public/sayings/{module}/random
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 | 可选值 |
|------|------|------|------|--------|
| `module` | string | 是 | 模块名 | `cs`, `design`, `study`, `technology`, `thought` |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 7,
    "content": "我们创造工具，然后工具塑造我们。",
    "author": "马歇尔·麦克卢汉",
    "author_en": "Marshall McLuhan",
    "tags": ["媒介", "塑造"],
    "module": "technology"
  }
}
```

**错误示例（模块不存在）：**

```json
{
  "code": 500,
  "message": "无效的模块名: xxx，可选值: cs, design, study, technology, thought",
  "data": null
}
```

### 2.2 获取模块所有名言

```
GET /api/public/sayings/{module}
```

**路径参数：** 同上

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "content": "任何足够先进的科技，都与魔法无异。",
      "author": "亚瑟·克拉克",
      "author_en": "Arthur C. Clarke",
      "tags": ["未来", "魔法"],
      "module": "technology"
    },
    {
      "id": 2,
      "content": "预测未来的最好方式就是去创造它。",
      "author": "艾伦·凯",
      "author_en": "Alan Kay",
      "tags": ["创造", "未来"],
      "module": "technology"
    }
  ]
}
```

---

## 🔒 管理接口（需要管理员 + manage_sayings 能力）

### 3.1 添加名言

```
POST /api/admin/sayings/{module}
```

**请求头：**

| 头 | 值 |
|----|----|
| `Authorization` | `Bearer {jwt_token}` |

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `module` | string | 是 | 模块名，同上 |

**请求体：**

```json
{
  "content": "编程不是关于你知道什么，而是关于你能弄清楚什么。",
  "author": "克里斯·派恩",
  "author_en": "Chris Pine",
  "tags": ["学习", "探索"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 名言内容 |
| `author` | string | 是 | 作者名称 |
| `author_en` | string | 否 | 作者英文名（可选） |
| `tags` | string[] | 否 | 标签数组（可选） |

**响应示例：**

```json
{
  "code": 200,
  "message": "名言添加成功",
  "data": {
    "id": 101,
    "content": "编程不是关于你知道什么，而是关于你能弄清楚什么。",
    "author": "克里斯·派恩",
    "author_en": "Chris Pine",
    "tags": ["学习", "探索"],
    "module": "cs"
  }
}
```

**错误示例（无权限）：**

```json
{
  "code": 500,
  "message": "权限不足，需要 [管理名言库（添加/删除名言）] 能力",
  "data": null
}
```

---

## 💻 前端 TypeScript 示例

### 4.1 定义类型

```typescript
// src/types/saying.ts

/** 名言条目 */
export interface Saying {
  id: number
  content: string
  author: string
  author_en: string
  tags: string[]
  module: string
}

/** 添加名言请求 */
export interface AddSayingRequest {
  content: string
  author: string
  author_en?: string
  tags?: string[]
}
```

### 4.2 封装 API 调用

```typescript
// src/services/saying.ts

import request from '@/utils/request'
import type { Saying, AddSayingRequest } from '@/types/saying'

/** 模块列表常量 */
export const SAYING_MODULES = [
  { key: 'cs', label: '计算机' },
  { key: 'design', label: '设计' },
  { key: 'study', label: '学习' },
  { key: 'technology', label: '科技' },
  { key: 'thought', label: '思想' },
] as const

/** 模块名中文映射 */
export const SAYING_MODULE_NAMES: Record<string, string> = {
  cs: '计算机',
  design: '设计',
  study: '学习',
  technology: '科技',
  thought: '思想',
}

/**
 * 获取随机名言（公开）
 */
export function getRandomSaying(module: string) {
  return request.get<Saying>(`/public/sayings/${module}/random`)
}

/**
 * 获取模块所有名言（公开）
 */
export function getSayings(module: string) {
  return request.get<Saying[]>(`/public/sayings/${module}`)
}

/**
 * 添加名言（需要管理员权限 + manage_sayings 能力）
 */
export function addSaying(module: string, data: AddSayingRequest) {
  return request.post<Saying>(`/admin/sayings/${module}`, data)
}
```

### 4.3 首页展示使用示例

```vue
<!-- 在首页组件中 -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRandomSaying, SAYING_MODULE_NAMES } from '@/services/saying'
import type { Saying } from '@/types/saying'

const saying = ref<Saying | null>(null)
const loading = ref(false)

/** 加载名言 */
async function loadSaying(module: string) {
  loading.value = true
  try {
    const res = await getRandomSaying(module)
    saying.value = res.data
  } catch (e) {
    console.error('加载名言失败:', e)
  } finally {
    loading.value = false
  }
}

/** 点击切换名言 */
function refreshSaying() {
  // 随机选择一个模块
  const modules = ['cs', 'design', 'study', 'technology', 'thought']
  const randomModule = modules[Math.floor(Math.random() * modules.length)]
  loadSaying(randomModule)
}

onMounted(() => {
  refreshSaying()
})
</script>

<template>
  <div v-if="saying" class="saying-card">
    <blockquote class="saying-content">“{{ saying.content }}”</blockquote>
    <p class="saying-author">—— {{ saying.author }}</p>
    <p class="saying-module">📚 {{ SAYING_MODULE_NAMES[saying.module] || saying.module }}</p>
    <button @click="refreshSaying">↻ 换一句</button>
  </div>
</template>
```

---

## 🧪 测试用例

### 正常流程测试

```bash
# 1. 随机获取一条计算机名言
curl http://localhost:8080/api/public/sayings/cs/random

# 2. 获取设计模块所有名言
curl http://localhost:8080/api/public/sayings/design

# 3. 添加名言（需要登录）
curl -X POST http://localhost:8080/api/admin/sayings/cs \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"content":"测试名言","author":"测试作者","tags":["测试"]}'
```

### 异常流程测试

```bash
# 4. 无效模块名 → 返回 500
curl http://localhost:8080/api/public/sayings/invalid/random

# 5. 未登录访问管理接口 → 返回 401
curl -X POST http://localhost:8080/api/admin/sayings/cs \
  -H "Content-Type: application/json" \
  -d '{"content":"测试","author":"测试"}'
```

---

## 📝 注意事项

1. **模块名区分大小写**：必须使用小写，如 `cs` 而不是 `CS` 或 `Cs`
2. **数据持久化**：添加的名言会保存到 MinIO 对象存储，重启后端后不会丢失
3. **ID 自动分配**：添加名言时无需传 id，后端会自动分配 `maxId + 1`
4. **排序**：返回的数据按 id 升序排列
5. **审计日志**：添加名言操作会自动记录到审计日志，可在后台查看
6. **权限初始化**：管理员需要在后台"能力管理"中为对应管理员分配 `manage_sayings` 能力后才能使用添加接口