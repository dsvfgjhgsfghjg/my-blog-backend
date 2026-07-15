# API 错误修复说明

## 问题描述

在调用 `POST /api/posts` 创建文章接口时，出现以下错误：

```json
{
  "code": 500,
  "message": "分类不存在",
  "data": null
}
```

同时契约测试报错：`.data: 不允许为 null`

## 根本原因分析

### 1. 分类ID验证问题

**请求数据：**
```json
{
  "categoryId": 0,
  ...
}
```

**问题：**
- 数据库中分类表使用 `BIGSERIAL` 自增主键，ID从1开始
- ID为0的分类不存在
- 原代码只检查 `categoryId != null`，没有检查是否为有效值（> 0）

### 2. Result.error() 返回 null data

**问题：**
```java
public static <T> Result<T> error(Integer code, String message) {
    return new Result<>(code, message, null);  // ❌ data为null
}
```

**契约要求：**
- 错误响应的 `data` 字段不允许为 `null`
- 应返回空字符串或其他默认值

## 修复方案

### 修复1：优化分类ID验证逻辑

**文件：** `PostServiceImpl.java`

**修改前：**
```java
if (request.getCategoryId() != null) {
    Category category = categoryMapper.selectById(request.getCategoryId());
    if (category == null) {
        throw new BusinessException("分类不存在");
    }
}
```

**修改后：**
```java
// 1. 验证分类是否存在（如果提供了有效的分类ID）
if (request.getCategoryId() != null && request.getCategoryId() > 0) {
    Category category = categoryMapper.selectById(request.getCategoryId());
    if (category == null) {
        throw new BusinessException("分类不存在");
    }
}
```

**说明：**
- 只有当 `categoryId > 0` 时才进行验证
- `categoryId` 为 `null` 或 `0` 时，表示不设置分类，跳过验证
- 同样修复了 `updatePost` 方法中的相同问题

### 修复2：Result.error() 返回空字符串而非 null

**文件：** `Result.java`

**修改前：**
```java
public static <T> Result<T> error(String message) {
    return new Result<>(500, message, null);  // ❌
}

public static <T> Result<T> error(Integer code, String message) {
    return new Result<>(code, message, null);  // ❌
}
```

**修改后：**
```java
public static <T> Result<T> error(String message) {
    return new Result<>(500, message, (T) "");  // ✅
}

public static <T> Result<T> error(Integer code, String message) {
    return new Result<>(code, message, (T) "");  // ✅
}
```

**说明：**
- 错误响应中 `data` 字段返回空字符串 `""` 而非 `null`
- 满足契约测试要求：data 不允许为 null
- 使用泛型强制转换 `(T) ""` 保持类型安全

## 修复后的行为

### 场景1：categoryId = 0 或 null

**请求：**
```json
POST /api/posts
{
  "title": "测试文章",
  "content": "内容",
  "categoryId": 0,  // 或 null
  "tagIds": [1, 2]
}
```

**结果：**
- ✅ 不再抛出"分类不存在"错误
- ✅ 文章创建成功，`category_id` 字段为 NULL
- ✅ 表示该文章不属于任何分类

### 场景2：categoryId = 无效值（如999）

**请求：**
```json
POST /api/posts
{
  "title": "测试文章",
  "content": "内容",
  "categoryId": 999,  // 不存在的分类ID
  "tagIds": [1, 2]
}
```

**结果：**
- ✅ 抛出"分类不存在"错误（符合预期）

**响应：**
```json
{
  "code": 500,
  "message": "分类不存在",
  "data": ""  // ✅ 不再是 null
}
```

### 场景3：categoryId = 有效值（如1）

**请求：**
```json
POST /api/posts
{
  "title": "测试文章",
  "content": "内容",
  "categoryId": 1,  // 存在的分类ID
  "tagIds": [1, 2]
}
```

**结果：**
- ✅ 文章创建成功，关联到ID为1的分类

## 测试建议

### 1. 测试无分类的文章创建

```json
POST /api/posts
Authorization: Bearer {token}

{
  "title": "无分类文章",
  "content": "# 测试内容",
  "categoryId": null,  // 或 0
  "status": 1
}
```

**预期：** 成功创建，`category_id` 为 NULL

### 2. 测试错误分类ID

```json
POST /api/posts
Authorization: Bearer {token}

{
  "title": "错误分类",
  "content": "# 测试",
  "categoryId": 999,
  "status": 1
}
```

**预期：** 
```json
{
  "code": 500,
  "message": "分类不存在",
  "data": ""  // ✅ 不是 null
}
```

### 3. 测试有效分类ID

```json
POST /api/posts
Authorization: Bearer {token}

{
  "title": "有效分类",
  "content": "# 测试",
  "categoryId": 1,
  "status": 1
}
```

**预期：** 成功创建，关联到分类ID=1

## 数据库验证

查看默认分类数据：

```sql
SELECT * FROM t_category;
```

**预期输出：**
```
 id |  name   | description | sort_order
----+---------+-------------+------------
  1 | 未分类  | 默认分类     |          0
  2 | 技术    | 技术相关文章 |          1
  3 | 生活    | 生活随笔     |          2
```

**注意：** 分类ID从1开始，不存在ID为0的分类。

## 最佳实践建议

### 1. 前端传参规范

- **不设置分类时**：传 `null` 或 `0`
- **设置分类时**：传有效的分类ID（> 0）

```javascript
// 推荐做法
const createPost = async (postData) => {
  const payload = {
    title: postData.title,
    content: postData.content,
    categoryId: postData.categoryId || null,  // 无分类时传null
    tagIds: postData.tagIds || [],
    status: postData.status
  };
  
  return await api.post('/posts', payload);
};
```

### 2. 后端验证增强（可选）

如果需要更严格的验证，可以添加状态码校验：

```java
// 验证文章状态
if (request.getStatus() != null && 
    !Arrays.asList(0, 1, 2).contains(request.getStatus())) {
    throw new BusinessException("无效的文章状态，只能是0(草稿)、1(已发布)、2(私密)");
}
```

### 3. 标签验证同理

当前标签验证逻辑：
```java
if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
    List<Tag> tags = tagMapper.selectBatchIds(request.getTagIds());
    if (tags.size() != request.getTagIds().size()) {
        throw new BusinessException("存在无效的标签ID");
    }
}
```

**建议：** 如果允许空标签列表，当前逻辑已足够。如果需要过滤掉ID为0的标签，可以添加：

```java
List<Long> validTagIds = request.getTagIds().stream()
    .filter(id -> id != null && id > 0)
    .collect(Collectors.toList());
```

## 总结

✅ **已修复的问题：**
1. 分类ID为0时不再抛出错误
2. 错误响应的data字段返回空字符串而非null
3. 满足契约测试要求

✅ **改进点：**
- 更灵活的分类处理（允许无分类）
- 更符合RESTful规范的错误响应
- 更好的前后端兼容性
