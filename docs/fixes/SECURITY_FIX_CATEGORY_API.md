# SecurityConfig 修复 - 分类文章API公开访问

## 问题描述

前端调用以下三个分类文章列表API时返回403 Forbidden：
- `GET /api/posts/category/software-development`
- `GET /api/posts/category/ai`
- `GET /api/posts/category/cybersecurity`

## 原因分析

虽然这三个API在Controller层没有添加权限注解，但Spring Security的默认配置要求所有请求都需要认证（`.anyRequest().authenticated()`）。

在 `SecurityConfig.java` 中，只配置了 `/api/posts/public` 为公开接口，新增的分类API端点 `/api/posts/category/**` 没有被加入白名单，因此被Security拦截。

## 解决方案

在 `SecurityConfig.java` 的 `securityFilterChain` 方法中，添加分类文章API的公开访问权限：

```java
// 文章公开接口
.requestMatchers("/api/posts/public").permitAll()     // 公开文章列表
.requestMatchers("/api/posts/category/**").permitAll() // 分类文章列表（软件开发、AI、网络安全）
.requestMatchers("/api/posts/categories").authenticated()  // 分类列表需要登录
```

## 修改文件

- `src/main/java/com/leo/myblog/config/SecurityConfig.java`

## 验证

修复后，以下API可以无需登录直接访问：
- ✅ `GET /api/posts/category/software-development`
- ✅ `GET /api/posts/category/ai`
- ✅ `GET /api/posts/category/cybersecurity`

## 注意事项

1. **路径匹配规则**：使用 `/api/posts/category/**` 通配符匹配所有分类子路径
2. **顺序重要**：`.permitAll()` 规则必须在 `.anyRequest().authenticated()` 之前
3. **安全性**：这些API只返回已发布（status=1）的公开文章，不存在信息泄露风险
