# 文章审计系统 - 实现指南

## 📋 概述

实现一个完整的文章审计系统，所有文章提交后都进入"待审核"状态，只有作者和拥有`audit_posts`能力的管理员可以查看和操作。

---

## ✅ 已完成的工作

### 1. 数据库层面

#### 1.1 创建审计表 ✅

**表名**: `t_post_audit`

**字段**:
- `id` - 主键ID
- `post_id` - 文章ID
- `auditor_id` - 审计员ID（NULL表示未审计）
- `audit_status` - 审计状态（0-待审核, 1-已通过, 2-已拒绝）
- `audit_comment` - 审计意见/拒绝原因
- `audit_time` - 审计时间
- `create_time` - 创建时间
- `update_time` - 更新时间

**索引**:
- `idx_post_audit_post_id` - 文章ID索引
- `idx_post_audit_auditor_id` - 审计员ID索引
- `idx_post_audit_status` - 审计状态索引
- `idx_post_audit_create_time` - 创建时间索引

#### 1.2 添加新能力 ✅

**能力名称**: `audit_posts`  
**描述**: 审计文章  
**ID**: `2054570236258279437`

#### 1.3 更新文章状态注释 ✅

```sql
COMMENT ON COLUMN t_post.status IS '状态：0-草稿, 1-已发布, 2-私密, 3-待审核';
```

---

## 🔧 需要实现的代码

### 第一步：更新Post实体类

**文件**: `src/main/java/com/leo/myblog/entity/Post.java`

**修改**: 更新status字段的注释

```java
/**
 * 状态：0-草稿, 1-已发布, 2-私密, 3-待审核
 */
private Integer status;
```

---

### 第二步：创建PostAuditMapper

**文件**: `src/main/java/com/leo/myblog/mapper/PostAuditMapper.java`

```java
package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.PostAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章审计Mapper
 */
@Mapper
public interface PostAuditMapper extends BaseMapper<PostAudit> {
}
```

---

### 第三步：创建审计相关DTO

#### 3.1 AuditPostRequest

**文件**: `src/main/java/com/leo/myblog/dto/request/AuditPostRequest.java`

```java
package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审计文章请求
 */
@Data
public class AuditPostRequest {
    
    /**
     * 审计结果：1-通过, 2-拒绝
     */
    @NotNull(message = "审计结果不能为空")
    private Integer auditStatus;
    
    /**
     * 审计意见/拒绝原因
     */
    private String auditComment;
}
```

#### 3.2 PostAuditResponse

**文件**: `src/main/java/com/leo/myblog/dto/response/PostAuditResponse.java`

```java
package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章审计响应
 */
@Data
public class PostAuditResponse {
    
    private String id;
    private String postId;
    private String auditorId;
    private String auditorNickname;
    private Integer auditStatus;
    private String auditComment;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}
```

---

### 第四步：创建PostAuditService

**文件**: `src/main/java/com/leo/myblog/service/PostAuditService.java`

```java
package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostAuditResponse;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.PostAudit;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.PostAuditMapper;
import com.leo.myblog.mapper.PostMapper;
import com.leo.myblog.util.AbilityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 文章审计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAuditService {
    
    private final PostAuditMapper postAuditMapper;
    private final PostMapper postMapper;
    private final UserCacheService userCacheService;
    private final AbilityValidator abilityValidator;
    
    /**
     * 创建文章时自动创建审计记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void createAuditRecord(Long postId) {
        PostAudit audit = new PostAudit();
        audit.setPostId(postId);
        audit.setAuditStatus(0); // 待审核
        audit.setCreateTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        postAuditMapper.insert(audit);
        
        log.info("创建文章审计记录: postId={}", postId);
    }
    
    /**
     * 审计文章（仅拥有audit_posts能力的管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditPost(Long auditorId, Long postId, AuditPostRequest request) {
        // 1. 验证能力
        abilityValidator.validateAbility(auditorId, "audit_posts");
        
        // 2. 查询审计记录
        LambdaQueryWrapper<PostAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAudit::getPostId, postId);
        PostAudit audit = postAuditMapper.selectOne(wrapper);
        
        if (audit == null) {
            throw new BusinessException("文章审计记录不存在");
        }
        
        // 3. 检查是否已审计
        if (audit.getAuditStatus() != 0) {
            throw new BusinessException("该文章已审计，无法重复审计");
        }
        
        // 4. 验证审计结果
        if (request.getAuditStatus() != 1 && request.getAuditStatus() != 2) {
            throw new BusinessException("审计结果只能是1(通过)或2(拒绝)");
        }
        
        // 5. 更新审计记录
        audit.setAuditorId(auditorId);
        audit.setAuditStatus(request.getAuditStatus());
        audit.setAuditComment(request.getAuditComment());
        audit.setAuditTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        postAuditMapper.updateById(audit);
        
        // 6. 更新文章状态
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        if (request.getAuditStatus() == 1) {
            // 通过：设置为已发布
            post.setStatus(1);
            post.setPublishTime(LocalDateTime.now());
        } else {
            // 拒绝：保持待审核状态或设置为草稿
            post.setStatus(0); // 返回草稿状态
        }
        post.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(post);
        
        log.info("文章审计完成: auditorId={}, postId={}, result={}", 
                auditorId, postId, request.getAuditStatus() == 1 ? "通过" : "拒绝");
    }
    
    /**
     * 获取待审核文章列表（仅审计员可见）
     */
    public PageResult<PostListItemResponse> getPendingPosts(Long auditorId, Integer pageNum, Integer pageSize) {
        // 1. 验证能力
        abilityValidator.validateAbility(auditorId, "audit_posts");
        
        // 2. 查询待审核的文章ID
        LambdaQueryWrapper<PostAudit> auditWrapper = new LambdaQueryWrapper<>();
        auditWrapper.eq(PostAudit::getAuditStatus, 0); // 待审核
        auditWrapper.orderByDesc(PostAudit::getCreateTime);
        
        Page<PostAudit> auditPage = new Page<>(pageNum, pageSize);
        postAuditMapper.selectPage(auditPage, auditWrapper);
        
        // 3. 获取文章详情
        List<Long> postIds = auditPage.getRecords().stream()
                .map(PostAudit::getPostId)
                .collect(Collectors.toList());
        
        if (postIds.isEmpty()) {
            return PageResult.empty();
        }
        
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.in(Post::getId, postIds);
        List<Post> posts = postMapper.selectList(postWrapper);
        
        // 4. 转换为响应对象
        // ... (转换逻辑)
        
        return PageResult.of(responsePage);
    }
    
    /**
     * 获取文章的审计记录
     */
    public PostAuditResponse getAuditRecord(Long userId, Boolean isAdmin, Long postId) {
        // 1. 权限检查：只有作者或管理员可以查看
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        if (!userId.equals(post.getUserId()) && !Boolean.TRUE.equals(isAdmin)) {
            throw new BusinessException("无权查看该文章的审计记录");
        }
        
        // 2. 查询审计记录
        LambdaQueryWrapper<PostAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAudit::getPostId, postId);
        PostAudit audit = postAuditMapper.selectOne(wrapper);
        
        if (audit == null) {
            return null;
        }
        
        // 3. 转换为响应对象
        PostAuditResponse response = new PostAuditResponse();
        BeanUtils.copyProperties(audit, response);
        response.setId(String.valueOf(audit.getId()));
        response.setPostId(String.valueOf(audit.getPostId()));
        
        if (audit.getAuditorId() != null) {
            response.setAuditorId(String.valueOf(audit.getAuditorId()));
            User auditor = userCacheService.getUserById(audit.getAuditorId());
            if (auditor != null) {
                response.setAuditorNickname(auditor.getNickname());
            }
        }
        
        return response;
    }
}
```

---

### 第五步：修改PostServiceImpl

**文件**: `src/main/java/com/leo/myblog/service/PostServiceImpl.java`

#### 5.1 注入PostAuditService

```java
private final PostAuditService postAuditService;
```

#### 5.2 修改createPost方法

在创建文章后，自动创建审计记录：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createPost(Long userId, CreatePostRequest request) {
    // ... 现有逻辑 ...
    
    // 保存文章
    postMapper.insert(post);
    
    // 创建审计记录
    postAuditService.createAuditRecord(post.getId());
    
    // ... 其他逻辑 ...
    
    return post.getId();
}
```

#### 5.3 修改getPublicPostList方法

只查询已通过审计的文章：

```java
// 添加审计状态过滤
LambdaQueryWrapper<PostAudit> auditWrapper = new LambdaQueryWrapper<>();
auditWrapper.eq(PostAudit::getAuditStatus, 1); // 已通过
List<Long> approvedPostIds = postAuditMapper.selectList(auditWrapper).stream()
        .map(PostAudit::getPostId)
        .collect(Collectors.toList());

if (approvedPostIds.isEmpty()) {
    return PageResult.empty();
}

postWrapper.in(Post::getId, approvedPostIds);
```

#### 5.4 修改getPostDetail方法

添加权限检查：

```java
// 如果文章是待审核状态，只有作者和管理员可以查看
if (post.getStatus() == 3) {
    if (userId == null || (!userId.equals(post.getUserId()) && !Boolean.TRUE.equals(isAdmin))) {
        throw new BusinessException("文章正在审核中，暂不可见");
    }
}
```

---

### 第六步：创建PostAuditController

**文件**: `src/main/java/com/leo/myblog/controller/PostAuditController.java`

```java
package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostAuditResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.service.PostAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文章审计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/posts/audit")
@RequiredArgsConstructor
public class PostAuditController {
    
    private final PostAuditService postAuditService;
    
    /**
     * 获取待审核文章列表（仅审计员）
     */
    @RequireAdmin
    @GetMapping("/pending")
    public Result<PageResult<PostListItemResponse>> getPendingPosts(
            @RequestAttribute Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.debug("获取待审核文章列表: userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        PageResult<PostListItemResponse> result = postAuditService.getPendingPosts(userId, pageNum, pageSize);
        return Result.success(result);
    }
    
    /**
     * 审计文章（通过/拒绝）
     */
    @RequireAdmin
    @PostMapping("/{postId}")
    public Result<Void> auditPost(@RequestAttribute Long userId,
                                   @PathVariable Long postId,
                                   @Valid @RequestBody AuditPostRequest request) {
        log.info("审计文章: userId={}, postId={}, result={}", userId, postId, request.getAuditStatus());
        postAuditService.auditPost(userId, postId, request);
        return Result.success(request.getAuditStatus() == 1 ? "文章已通过" : "文章已拒绝", null);
    }
    
    /**
     * 获取文章的审计记录
     */
    @GetMapping("/{postId}/record")
    public Result<PostAuditResponse> getAuditRecord(@RequestAttribute Long userId,
                                                      @RequestAttribute Boolean isAdmin,
                                                      @PathVariable Long postId) {
        log.debug("获取文章审计记录: userId={}, postId={}", userId, postId);
        PostAuditResponse record = postAuditService.getAuditRecord(userId, isAdmin, postId);
        return Result.success(record);
    }
}
```

---

### 第七步：更新AbilityValidator

**文件**: `src/main/java/com/leo/myblog/util/AbilityValidator.java`

添加新能力的描述：

```java
case "audit_posts" -> "审计文章";
```

---

## 📊 文章状态流转图

```
创建文章
   ↓
待审核 (status=3)
   ↓
   ├─→ [审计员通过] → 已发布 (status=1) → 公开可见
   └─→ [审计员拒绝] → 草稿 (status=0) → 仅作者可见
   
作者可以：
- 编辑待审核文章
- 删除待审核文章
- 重新提交（会创建新的审计记录）
```

---

## 🔐 权限控制矩阵

| 操作 | 作者 | 普通用户 | 审计员 | 超级管理员 |
|------|------|---------|--------|-----------|
| 创建文章 | ✅ | ❌ | ✅ | ✅ |
| 查看自己的待审核文章 | ✅ | ❌ | ✅ | ✅ |
| 查看他人的待审核文章 | ❌ | ❌ | ✅ | ✅ |
| 编辑待审核文章 | ✅ | ❌ | ❌ | ❌ |
| 删除待审核文章 | ✅ | ❌ | ❌ | ❌ |
| 审计文章 | ❌ | ❌ | ✅ | ✅ |
| 查看已发布文章 | ✅ | ✅ | ✅ | ✅ |

---

## 🧪 测试用例

### 测试1: 创建文章自动进入待审核

```bash
# 1. 创建文章
curl -X POST http://localhost:8080/api/posts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "测试文章",
    "content": "# 测试内容",
    "categoryId": 1,
    "tagIds": [1],
    "status": 1
  }'

# 2. 查询文章状态（应该是3-待审核）
curl http://localhost:8080/api/posts/{postId} \
  -H "Authorization: Bearer $TOKEN"
```

### 测试2: 审计员审计文章

```bash
# 1. 获取待审核列表
curl http://localhost:8080/api/posts/audit/pending \
  -H "Authorization: Bearer $AUDITOR_TOKEN"

# 2. 通过文章
curl -X POST http://localhost:8080/api/posts/audit/{postId} \
  -H "Authorization: Bearer $AUDITOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "auditStatus": 1,
    "auditComment": "内容符合要求"
  }'

# 3. 验证文章已发布
curl http://localhost:8080/api/posts/public \
  | jq '.data.records[] | select(.id == "{postId}")'
```

### 测试3: 非审计员尝试审计

```bash
curl -X POST http://localhost:8080/api/posts/audit/{postId} \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "auditStatus": 1
  }'

# 预期: 403 权限不足
```

---

## 📝 注意事项

1. **历史数据处理**: 运行迁移脚本会自动为已发布的文章创建"已通过"的审计记录
2. **性能优化**: 待审核文章列表可能需要分页和缓存
3. **通知机制**: 可以考虑在审计完成后通知作者
4. **审计日志**: 所有审计操作都会记录到`t_audit_log`表

---

## 🚀 后续优化

1. **批量审计**: 支持一次性审计多篇文章
2. **审计统计**: 统计每个审计员的审计数量和通过率
3. **自动通过**: 对于可信作者的文章可以设置自动通过
4. **申诉机制**: 作者可以对拒绝决定提出申诉

---

**状态**: 📝 实现指南已完成  
**下一步**: 按照本指南逐步实现代码  
**预计工作量**: 2-3小时
