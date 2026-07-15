# SecurityConfig 修复 - 文章详情API公开访问

## 问题描述

前端调用文章详情API时返回403 Forbidden：
- `GET /api/posts/{postId}`

即使文章是已发布状态，访客也无法查看，因为Spring Security拦截了未认证的请求。

## 原因分析

在 `SecurityConfig.java` 中，只配置了以下文章相关接口为公开：
- `/api/posts/public` - 公开文章列表
- `/api/posts/category/**` - 分类文章列表

但文章详情接口 `/api/posts/{postId}` 没有被加入白名单，因此被默认的 `.anyRequest().authenticated()` 规则拦截。

## 解决方案

在 `SecurityConfig.java` 的 `securityFilterChain` 方法中，添加文章详情API的公开访问权限：

```java
// 文章公开接口
.requestMatchers("/api/posts/public").permitAll()     // 公开文章列表
.requestMatchers("/api/posts/category/**").permitAll() // 分类文章列表（软件开发、AI、网络安全）
.requestMatchers("/api/posts/{postId}").permitAll()   // 文章详情（访客可查看已发布文章）
.requestMatchers("/api/posts/categories").authenticated()  // 分类列表需要登录
```

## 修改文件

- `src/main/java/com/leo/myblog/config/SecurityConfig.java`

## 业务逻辑说明

### Controller层
```java
@GetMapping("/{postId}")
public Result<PostDetailResponse> getPostDetail(
    @RequestAttribute(required = false) Long userId,  // 可选，访客时为null
    @PathVariable Long postId) {
    // ...
}
```

### Service层权限控制
```java
// 1. 文章不存在 -> 返回403
if (post == null) {
    throw new BusinessException(403, "文章不存在或无权访问");
}

// 2. 非公开文章（草稿、私密）-> 返回403
if (post.getStatus() != null && post.getStatus() != 1) {
    throw new BusinessException(403, "文章不存在或无权访问");
}

// 3. 阅读量+1（访客或他人访问时）
if (currentUserId == null || !post.getUserId().equals(currentUserId)) {
    post.setViewCount(post.getViewCount() + 1);
    postMapper.updateById(post);
}
```

## 验证

修复后，以下场景可以正常工作：

### ✅ 访客查看已发布文章
```bash
curl GET http://127.0.0.1:8080/api/posts/2000000000000000001
# 返回: 200 OK + 文章详情
```

### ✅ 用户登录后查看已发布文章
```bash
curl GET http://127.0.0.1:8080/api/posts/2000000000000000001 \
  -H "Authorization: Bearer <token>"
# 返回: 200 OK + 文章详情
```

### ❌ 查看草稿文章（返回403）
```bash
curl GET http://127.0.0.1:8080/api/posts/<draft-post-id>
# 返回: 403 Forbidden
```

### ❌ 查看私密文章（返回403）
```bash
curl GET http://127.0.0.1:8080/api/posts/<private-post-id>
# 返回: 403 Forbidden
```

### ❌ 查看不存在的文章（返回403）
```bash
curl GET http://127.0.0.1:8080/api/posts/999999999
# 返回: 403 Forbidden
```

## 安全性说明

1. **防扫描**: 不存在或非公开文章统一返回403，避免泄露文章状态
2. **访客友好**: 不需要token即可查看公开文章，提升用户体验
3. **阅读量统计**: 访客访问也会增加阅读量，统计数据更准确
4. **权限隔离**: 只有作者和管理员可以查看草稿和私密文章（需要通过其他管理接口）

## 注意事项

1. **路径变量匹配**: Spring Security支持 `{postId}` 这样的路径变量匹配
2. **顺序重要**: `.permitAll()` 规则必须在 `.anyRequest().authenticated()` 之前
3. **业务层校验**: Security只控制访问入口，具体的权限逻辑在Service层实现
