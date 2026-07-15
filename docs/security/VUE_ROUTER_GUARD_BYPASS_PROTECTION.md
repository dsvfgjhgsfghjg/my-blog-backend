# Vue Router守卫绕过防护完整方案

## 📋 问题概述

攻击者可以使用各种工具（如vuecrack）绕过前端Vue Router的导航守卫，直接访问受保护的页面或API。前端路由守卫只是用户体验层面的保护，不能作为唯一的安全防线。

## 🛡️ 多层防护架构

```
┌─────────────────────────────────────────┐
│         第1层：前端路由守卫              │
│   (用户体验，可被绕过)                   │
├─────────────────────────────────────────┤
│         第2层：HTTP拦截器                │
│   (自动添加Token，处理401/403)          │
├─────────────────────────────────────────┤
│         第3层：JWT认证过滤器             │
│   (验证Token有效性和黑名单检查)          │
├─────────────────────────────────────────┤
│         第4层：Spring Security           │
│   (URL级别的访问控制)                    │
├─────────────────────────────────────────┤
│         第5层：方法级权限控制            │
│   (@PreAuthorize细粒度权限)              │
├─────────────────────────────────────────┤
│         第6层：业务逻辑验证              │
│   (数据所有权、状态检查等)               │
└─────────────────────────────────────────┘
```

## ✅ 当前项目已实现的安全措施

### 1. JWT认证机制 ✓
- **位置**: `JwtAuthenticationFilter.java`
- **功能**: 
  - 从请求头提取Bearer Token
  - 验证Token签名和有效期
  - 检查Token黑名单
  - 将用户信息存入SecurityContext

### 2. Spring Security配置 ✓
- **位置**: `SecurityConfig.java`
- **功能**:
  - 无状态会话管理（STATELESS）
  - URL级别的访问控制
  - CORS跨域配置
  - CSRF禁用（JWT不需要）

### 3. Token黑名单机制 ✓
- **位置**: `TokenBlacklistService`
- **功能**: 登出时将Token加入Redis黑名单

### 4. BCrypt密码加密 ✓
- **位置**: `SecurityConfig.passwordEncoder()`
- **功能**: 用户密码安全存储

## 🔧 需要增强的安全措施

### 增强1: 方法级权限控制（@PreAuthorize）

#### 1.1 启用方法级安全

修改 `SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)  // 添加这行
@RequiredArgsConstructor
public class SecurityConfig {
    // ... 现有代码
}
```

#### 1.2 在Controller中使用权限注解

示例：文章管理Controller

```java
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    
    private final PostService postService;
    
    /**
     * 创建文章 - 仅管理员
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")  // 仅管理员可创建
    public ResponseEntity<Result<Post>> createPost(@RequestBody PostDTO postDTO) {
        // 业务逻辑
    }
    
    /**
     * 更新文章 - 作者或管理员
     */
    @PutMapping("/{postId}")
    @PreAuthorize("@postService.isOwner(#postId, authentication.principal) or hasRole('ADMIN')")
    public ResponseEntity<Result<Post>> updatePost(
            @PathVariable Long postId,
            @RequestBody PostDTO postDTO) {
        // 业务逻辑
    }
    
    /**
     * 删除文章 - 作者或管理员
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize("@postService.isOwner(#postId, authentication.principal) or hasRole('ADMIN')")
    public ResponseEntity<Result<Void>> deletePost(@PathVariable Long postId) {
        // 业务逻辑
    }
    
    /**
     * 获取所有文章（包括草稿）- 仅管理员
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Page<Post>>> getAllPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 业务逻辑
    }
}
```

#### 1.3 在Service中实现权限检查方法

```java
@Service
@RequiredArgsConstructor
public class PostService {
    
    private final PostMapper postMapper;
    
    /**
     * 检查用户是否是文章所有者
     * 用于@PreAuthorize表达式
     */
    public boolean isOwner(Long postId, Long userId) {
        if (userId == null || postId == null) {
            return false;
        }
        
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return false;
        }
        
        return post.getAuthorId().equals(userId);
    }
}
```

### 增强2: 自定义权限评估器

#### 2.1 创建角色枚举

```java
package com.leo.myblog.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {
    USER("普通用户"),
    ADMIN("管理员"),
    SUPER_ADMIN("超级管理员");
    
    private final String description;
    
    UserRole(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

#### 2.2 在User实体中添加角色字段

```java
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private String username;
    private String email;
    private String password;
    
    // 添加角色字段
    private String role;  // USER, ADMIN, SUPER_ADMIN
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 2.3 创建自定义权限评估器

```java
package com.leo.myblog.security;

import com.leo.myblog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 自定义权限评估器
 * 用于@PreAuthorize表达式
 */
@Component("permissionEvaluator")
@RequiredArgsConstructor
public class CustomPermissionEvaluator {
    
    private final UserService userService;
    
    /**
     * 检查用户是否有指定角色
     */
    public boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return false;
        }
        
        Long userId = (Long) authentication.getPrincipal();
        String userRole = userService.getUserRole(userId);
        
        return role.equals(userRole);
    }
    
    /**
     * 检查用户是否是管理员
     */
    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ADMIN") || 
               hasRole(authentication, "SUPER_ADMIN");
    }
    
    /**
     * 检查用户是否有权操作目标资源
     */
    public boolean canOperate(Authentication authentication, Long targetUserId) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return false;
        }
        
        Long currentUserId = (Long) authentication.getPrincipal();
        
        // 用户可以操作自己的资源
        if (currentUserId.equals(targetUserId)) {
            return true;
        }
        
        // 管理员可以操作所有资源
        return isAdmin(authentication);
    }
}
```

#### 2.4 在Controller中使用自定义权限评估器

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    /**
     * 更新用户信息 - 仅本人或管理员
     */
    @PutMapping("/{userId}")
    @PreAuthorize("@permissionEvaluator.canOperate(authentication, #userId)")
    public ResponseEntity<Result<User>> updateUser(
            @PathVariable Long userId,
            @RequestBody UserUpdateDTO updateDTO) {
        // 业务逻辑
    }
    
    /**
     * 删除用户 - 仅管理员
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("@permissionEvaluator.isAdmin(authentication)")
    public ResponseEntity<Result<Void>> deleteUser(@PathVariable Long userId) {
        // 业务逻辑
    }
    
    /**
     * 获取用户列表 - 仅管理员
     */
    @GetMapping("/admin/list")
    @PreAuthorize("@permissionEvaluator.isAdmin(authentication)")
    public ResponseEntity<Result<Page<User>>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 业务逻辑
    }
}
```

### 增强3: API速率限制（防止暴力破解）

#### 3.1 添加依赖

在 `build.gradle` 中添加：

```gradle
dependencies {
    // Bucket4j 速率限制
    implementation 'com.bucket4j:bucket4j-core:8.7.0'
}
```

#### 3.2 创建速率限制服务

```java
package com.leo.myblog.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API速率限制服务
 */
@Slf4j
@Service
public class RateLimitService {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建限流桶
     * @param key 限流键（如IP地址、用户ID）
     * @param capacity 容量
     * @param refillTokens 补充令牌数
     * @param refillDuration 补充时间间隔
     */
    private Bucket getBucket(String key, long capacity, long refillTokens, Duration refillDuration) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, refillDuration));
            return Bucket.builder().addLimit(limit).build();
        });
    }
    
    /**
     * 检查是否允许请求
     */
    public boolean tryConsume(String key, long capacity, long refillTokens, Duration refillDuration) {
        Bucket bucket = getBucket(key, capacity, refillTokens, refillDuration);
        boolean allowed = bucket.tryConsume(1);
        
        if (!allowed) {
            log.warn("速率限制: key={}, 请求被拒绝", key);
        }
        
        return allowed;
    }
    
    /**
     * 登录限流：每IP每分钟最多5次尝试
     */
    public boolean tryConsumeLogin(String ip) {
        return tryConsume("login:" + ip, 5, 5, Duration.ofMinutes(1));
    }
    
    /**
     * API通用限流：每IP每秒最多20次请求
     */
    public boolean tryConsumeApi(String ip) {
        return tryConsume("api:" + ip, 20, 20, Duration.ofSeconds(1));
    }
    
    /**
     * 清理过期的桶（定时任务调用）
     */
    public void cleanup() {
        // 简单实现：清空所有桶
        // 生产环境应该使用更智能的过期策略
        buckets.clear();
    }
}
```

#### 3.3 创建速率限制过滤器

```java
package com.leo.myblog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API速率限制过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final RateLimitService rateLimitService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();
        
        // 对登录接口进行严格限流
        if (uri.contains("/api/users/login")) {
            if (!rateLimitService.tryConsumeLogin(clientIp)) {
                response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                );
                return;
            }
        }
        // 对其他API进行通用限流
        else if (uri.startsWith("/api/")) {
            if (!rateLimitService.tryConsumeApi(clientIp)) {
                response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                );
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
```

#### 3.4 注册速率限制过滤器

在 `SecurityConfig.java` 中添加：

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;  // 添加这行
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ... 现有配置
            )
            // 添加速率限制过滤器（在JWT过滤器之前）
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    // ... 其他配置
}
```

### 增强4: 请求签名验证（防止重放攻击）

#### 4.1 创建请求签名工具类

```java
package com.leo.myblog.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 请求签名工具类
 * 用于防止请求篡改和重放攻击
 */
@Slf4j
@Component
public class RequestSignatureUtil {
    
    @Value("${app.signature.secret}")
    private String signatureSecret;
    
    @Value("${app.signature.timestamp-window:300}")
    private long timestampWindow; // 时间戳窗口（秒），默认5分钟
    
    /**
     * 生成请求签名
     * @param params 请求参数（已排序）
     * @param timestamp 时间戳
     * @return 签名字符串
     */
    public String generateSignature(SortedMap<String, String> params, long timestamp) {
        StringBuilder sb = new StringBuilder();
        
        // 按字母顺序拼接参数
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        
        // 添加时间戳
        sb.append("timestamp=").append(timestamp);
        
        // 添加密钥
        sb.append("&secret=").append(signatureSecret);
        
        // 使用HMAC-SHA256生成签名
        return hmacSHA256(sb.toString(), signatureSecret);
    }
    
    /**
     * 验证请求签名
     * @param params 请求参数
     * @param timestamp 时间戳
     * @param signature 客户端传来的签名
     * @return 是否有效
     */
    public boolean verifySignature(SortedMap<String, String> params, 
                                   long timestamp, 
                                   String signature) {
        // 验证时间戳是否在有效期内
        long currentTime = System.currentTimeMillis() / 1000;
        if (Math.abs(currentTime - timestamp) > timestampWindow) {
            log.warn("签名时间戳过期: timestamp={}, currentTime={}", timestamp, currentTime);
            return false;
        }
        
        // 生成服务器端签名
        String serverSignature = generateSignature(params, timestamp);
        
        // 比较签名
        boolean valid = serverSignature.equals(signature);
        if (!valid) {
            log.warn("签名验证失败: expected={}, actual={}", serverSignature, signature);
        }
        
        return valid;
    }
    
    /**
     * HMAC-SHA256加密
     */
    private String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC-SHA256加密失败", e);
            throw new RuntimeException("签名生成失败", e);
        }
    }
}
```

#### 4.2 在application.yml中添加配置

```yaml
app:
  signature:
    secret: your-secret-key-change-in-production  # 生产环境必须修改
    timestamp-window: 300  # 时间戳窗口5分钟
```

### 增强5: 敏感操作日志记录

#### 5.1 创建操作日志注解

```java
package com.leo.myblog.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    
    /**
     * 操作类型
     */
    String operation();
    
    /**
     * 操作描述
     */
    String description() default "";
    
    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;
    
    /**
     * 是否记录响应结果
     */
    boolean logResult() default false;
}
```

#### 5.2 创建操作日志切面

```java
package com.leo.myblog.aspect;

import com.leo.myblog.annotation.OperationLog;
import com.leo.myblog.entity.OperationLogEntity;
import com.leo.myblog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    
    private final OperationLogService operationLogService;
    
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取当前用户
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = principal instanceof Long ? (Long) principal : null;
        
        // 执行方法
        Object result = null;
        Exception exception = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            // 记录日志
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            OperationLogEntity logEntity = new OperationLogEntity();
            logEntity.setUserId(userId);
            logEntity.setOperation(operationLog.operation());
            logEntity.setDescription(operationLog.description());
            logEntity.setMethod(((MethodSignature) joinPoint.getSignature()).getMethod().getName());
            logEntity.setUrl(request != null ? request.getRequestURI() : null);
            logEntity.setIp(request != null ? getClientIp(request) : null);
            logEntity.setDuration(duration);
            logEntity.setStatus(exception == null ? "SUCCESS" : "FAILED");
            logEntity.setErrorMsg(exception != null ? exception.getMessage() : null);
            logEntity.setCreatedAt(LocalDateTime.now());
            
            // 异步保存日志
            operationLogService.saveLogAsync(logEntity);
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

#### 5.3 在Controller中使用

```java
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    
    /**
     * 创建文章
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(
        operation = "CREATE_POST",
        description = "创建文章",
        logParams = true
    )
    public ResponseEntity<Result<Post>> createPost(@RequestBody PostDTO postDTO) {
        // 业务逻辑
    }
    
    /**
     * 删除文章
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize("@postService.isOwner(#postId, authentication.principal) or hasRole('ADMIN')")
    @OperationLog(
        operation = "DELETE_POST",
        description = "删除文章",
        logParams = true
    )
    public ResponseEntity<Result<Void>> deletePost(@PathVariable Long postId) {
        // 业务逻辑
    }
}
```

## 📝 前端防护最佳实践

### 1. Token安全存储

```javascript
// stores/auth.js
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null,
    user: null,
    tokenExpiry: null
  }),
  
  actions: {
    setToken(token, expiresIn = 3600) {
      this.token = token
      // 计算过期时间
      this.tokenExpiry = Date.now() + expiresIn * 1000
      
      // 存储在内存中，而不是localStorage（更安全）
      // 如果需要使用localStorage，至少加密存储
      sessionStorage.setItem('token', token)
      sessionStorage.setItem('tokenExpiry', this.tokenExpiry.toString())
    },
    
    isTokenValid() {
      if (!this.token || !this.tokenExpiry) {
        return false
      }
      // 提前5分钟认为token过期
      return Date.now() < (this.tokenExpiry - 5 * 60 * 1000)
    },
    
    async validateToken() {
      if (!this.isTokenValid()) {
        this.logout()
        return false
      }
      
      try {
        const response = await axios.get('/api/auth/validate', {
          headers: { Authorization: `Bearer ${this.token}` }
        })
        return response.data.valid
      } catch (error) {
        this.logout()
        return false
      }
    }
  }
})
```

### 2. 路由守卫增强

```javascript
// router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/dashboard',
      component: Dashboard,
      meta: { 
        requiresAuth: true, 
        roles: ['USER', 'ADMIN'],
        validateToken: true  // 标记需要验证token
      }
    },
    {
      path: '/admin',
      component: AdminPanel,
      meta: { 
        requiresAuth: true, 
        roles: ['ADMIN'],
        validateToken: true
      }
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  
  if (to.meta.requiresAuth) {
    // 检查token是否存在且未过期
    if (!authStore.token || !authStore.isTokenValid()) {
      authStore.logout()
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }
    
    // 如果需要验证token，调用后端API验证
    if (to.meta.validateToken) {
      const isValid = await authStore.validateToken()
      if (!isValid) {
        next({
          path: '/login',
          query: { redirect: to.fullPath }
        })
        return
      }
    }
    
    // 角色权限检查
    if (to.meta.roles && !to.meta.roles.includes(authStore.userRole)) {
      next('/403')
      return
    }
  }
  
  next()
})

export default router
```

### 3. Axios拦截器增强

```javascript
// utils/axios.js
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

// 请求拦截器
instance.interceptors.request.use(
  async config => {
    const authStore = useAuthStore()
    
    if (authStore.token) {
      // 如果token即将过期，先刷新
      if (!authStore.isTokenValid() && !isRefreshing) {
        isRefreshing = true
        
        try {
          const response = await axios.post('/api/auth/refresh', {
            refreshToken: authStore.refreshToken
          })
          
          authStore.setToken(response.data.token, response.data.expiresIn)
          config.headers.Authorization = `Bearer ${response.data.token}`
          processQueue(null, response.data.token)
        } catch (error) {
          authStore.logout()
          processQueue(error, null)
          router.push('/login')
          return Promise.reject(error)
        } finally {
          isRefreshing = false
        }
      } else if (isRefreshing) {
        // 等待刷新完成
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          config.headers.Authorization = `Bearer ${token}`
          return config
        })
      } else {
        config.headers.Authorization = `Bearer ${authStore.token}`
      }
    }
    
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
instance.interceptors.response.use(
  response => response,
  error => {
    const authStore = useAuthStore()
    
    if (error.response?.status === 401) {
      // Token无效或过期
      authStore.logout()
      router.push('/login')
    } else if (error.response?.status === 403) {
      // 权限不足
      router.push('/403')
    } else if (error.response?.status === 429) {
      // 请求过于频繁
      console.warn('请求过于频繁，请稍后再试')
    }
    
    return Promise.reject(error)
  }
)

export default instance
```

## 🔍 安全审计清单

### 前端安全检查
- [ ] 所有敏感路由都有beforeEach守卫
- [ ] Token不在localStorage明文存储
- [ ] Token有合理的过期时间
- [ ] 实现了Token自动刷新机制
- [ ] 401/403错误有适当处理
- [ ] 敏感数据不在前端硬编码

### 后端安全检查
- [ ] 所有API都有JWT认证保护
- [ ] 敏感操作有@PreAuthorize权限控制
- [ ] 实现了Token黑名单机制
- [ ] 有API速率限制
- [ ] 敏感操作有日志记录
- [ ] 密码使用BCrypt加密
- [ ] CORS配置正确
- [ ] SQL注入防护（使用预编译语句）
- [ ] XSS防护（输出转义）

### 运维安全检查
- [ ] JWT密钥足够强（至少32字符）
- [ ] 数据库密码不使用默认值
- [ ] HTTPS已配置
- [ ] 防火墙只开放必要端口
- [ ] 定期更新依赖包
- [ ] 有备份策略
- [ ] 有监控告警

## 🚀 实施建议

### 优先级P0（立即实施）
1. ✅ 启用方法级权限控制（@PreAuthorize）
2. ✅ 在所有敏感API上添加权限注解
3. ✅ 实现Token刷新机制

### 优先级P1（本周内实施）
4. ✅ 添加API速率限制
5. ✅ 实现操作日志记录
6. ✅ 前端Token存储优化

### 优先级P2（本月内实施）
7. ⏳ 实现请求签名验证
8. ⏳ 添加异常行为检测
9. ⏳ 完善监控告警

## 📚 参考资料

- [Spring Security官方文档](https://spring.io/projects/spring-security)
- [JWT最佳实践](https://jwt.io/introduction)
- [OWASP安全指南](https://owasp.org/www-project-top-ten/)
- [Vue Router导航守卫](https://router.vuejs.org/guide/advanced/navigation-guards.html)

---

**记住：安全是一个持续的过程，不是一次性的任务。定期审查和更新你的安全措施！**
