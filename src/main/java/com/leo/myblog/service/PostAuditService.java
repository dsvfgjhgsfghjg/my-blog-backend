package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostAuditResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.PostAudit;
import com.leo.myblog.entity.User;
import com.leo.myblog.enums.NotificationType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final com.leo.myblog.util.PostResponseConverter postResponseConverter;
    private final NotificationService notificationService;

    /**
     * 创建文章时自动创建审计记录
     * @param postId 文章ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void createAuditRecord(Long postId) {
        // 验证文章是否存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在，无法创建审计记录");
        }
        
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
     * @param auditorId 审计员ID
     * @param postId 文章ID
     * @param request 审计请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditPost(Long auditorId, Long postId, AuditPostRequest request) {
        // 1. 验证能力：必须拥有 audit_posts 能力
        abilityValidator.validateAbility(auditorId, "audit_posts");
        
        // 2. 查询审计记录
        LambdaQueryWrapper<PostAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAudit::getPostId, postId);
        PostAudit audit = postAuditMapper.selectOne(wrapper);
        
        // 3. 如果审计记录不存在，自动创建（兼容历史文章）
        if (audit == null) {
            log.warn("文章审计记录不存在，尝试自动创建: postId={}", postId);
            
            // 先验证文章是否存在
            Post post = postMapper.selectById(postId);
            if (post == null) {
                log.error("尝试审计不存在的文章: postId={}, auditorId={}", postId, auditorId);
                throw new BusinessException("文章不存在或已被删除");
            }
            
            log.info("为文章自动创建审计记录: postId={}, title={}, status={}", 
                    postId, post.getTitle(), post.getStatus());
            createAuditRecord(postId);
            // 重新查询
            audit = postAuditMapper.selectOne(wrapper);
            if (audit == null) {
                throw new BusinessException("创建审计记录失败");
            }
        }
        
        // 4. 检查是否已审计
        if (audit.getAuditStatus() != 0) {
            throw new BusinessException("该文章已审计，无法重复审计");
        }
        
        // 5. 验证审计结果
        if (request.getAuditStatus() != 1 && request.getAuditStatus() != 2) {
            throw new BusinessException("审计结果只能是1(通过)或2(拒绝)");
        }
        
        // 6. 更新审计记录
        audit.setAuditorId(auditorId);
        audit.setAuditStatus(request.getAuditStatus());
        audit.setAuditComment(request.getAuditComment());
        audit.setAuditTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        postAuditMapper.updateById(audit);
        
        // 7. 更新文章状态
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        if (request.getAuditStatus() == 1) {
            // 通过：设置为已发布
            post.setStatus(1);
            post.setPublishTime(LocalDateTime.now());
            log.info("文章审计通过: auditorId={}, postId={}", auditorId, postId);
        } else {
            // 拒绝：返回草稿状态
            post.setStatus(0);
            log.info("文章审计拒绝: auditorId={}, postId={}, reason={}", 
                    auditorId, postId, request.getAuditComment());
        }
        post.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(post);

        // 8. 通知作者审核结果
        //    以前这里什么都不做，作者提交后只能自己反复刷新列表去猜结果，
        //    被拒绝时连 audit_comment 里写的原因都看不到。
        //    通知与本次审计同事务（NotificationService 用默认传播行为），
        //    审计回滚了就不会留下一条「已通过」的假消息。
        notifyAuthor(post, request);
    }

    /**
     * 把审核结果通知给作者
     * <p>
     * 通知写失败时**不吞异常**：让整个审计事务回滚，审计员看到明确报错后重试。
     * 理由是这个功能的上一版就是被注释掉的 —— 通知静默失败等于回到「作者什么都不知道」，
     * 而那时审核结果已经落库、作者再也不会被通知到，比这次操作直接失败更糟。
     * 通知与审核同库同事务，写不进去基本意味着库本身有问题。
     */
    private void notifyAuthor(Post post, AuditPostRequest request) {
        boolean passed = request.getAuditStatus() == 1;

        String title = passed ? "文章审核通过" : "文章审核未通过";
        String content;
        if (passed) {
            content = "《" + post.getTitle() + "》已通过审核并发布。";
        } else {
            String comment = request.getAuditComment();
            content = "《" + post.getTitle() + "》未通过审核。"
                    + (comment == null || comment.isBlank() ? "（审核员未填写原因）" : "原因：" + comment);
        }

        notificationService.notify(
                post.getUserId(),
                passed ? NotificationType.POST_AUDIT_PASSED : NotificationType.POST_AUDIT_REJECTED,
                title,
                content,
                "/admin/articles");
    }

    /**
     * 获取待审核文章列表（仅审计员可见）
     * @param auditorId 审计员ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<PostListItemResponse> getPendingPosts(Long auditorId, Integer pageNum, Integer pageSize) {
        // 1. 验证能力：必须拥有 audit_posts 能力
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
            // 返回空的分页结果
            Page<PostListItemResponse> emptyPage = new Page<>(pageNum, pageSize, 0);
            return PageResult.of(emptyPage);
        }
        
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.in(Post::getId, postIds);
        List<Post> posts = postMapper.selectList(postWrapper);

        // 4. 转换。用批量版本，分类与标签各只查一次 —— 逐篇调单篇版会是 N+1。
        //    convertAll 保持入参顺序，所以下面按下标与原 posts 对齐即可。
        List<PostListItemResponse> converted = postResponseConverter.convertAll(posts);
        Map<Long, PostListItemResponse> responseById = new HashMap<>();
        for (int i = 0; i < posts.size(); i++) {
            responseById.put(posts.get(i).getId(), converted.get(i));
        }

        // 5. 按**审计记录的顺序**（create_time 倒序）输出，而不是按 selectList 的返回顺序。
        //    IN (...) 查询不保证返回顺序，直接用 posts 的遍历顺序会让分页结果的先后跟
        //    排序规则对不上 —— 同一页里最早提交的可能排在最前面，翻页时看起来像乱序。
        //    另外被逻辑删除的文章不会出现在 posts 里，所以按 postIds 取、取不到就跳过。
        List<PostListItemResponse> responseList = postIds.stream()
                .map(responseById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 5. 构建分页结果
        Page<PostListItemResponse> responsePage = new Page<>(pageNum, pageSize, auditPage.getTotal());
        responsePage.setRecords(responseList);
        
        return PageResult.of(responsePage);
    }
    
    /**
     * 获取文章的审计记录
     * @param userId 当前用户ID
     * @param isAdmin 是否为管理员
     * @param postId 文章ID
     * @return 审计记录
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
    
    /**
     * 重新提交审核（作者撤回后重新提交）
     * 用于已拒绝的文章重新进入审核流程
     * @param userId 用户ID（必须是文章作者）
     * @param postId 文章ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmitForAudit(Long userId, Long postId) {
        // 1. 验证文章是否存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 验证权限：只有作者可以重新提交
        if (!userId.equals(post.getUserId())) {
            throw new BusinessException("只有文章作者可以重新提交审核");
        }
        
        // 3. 查询审计记录
        LambdaQueryWrapper<PostAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAudit::getPostId, postId);
        PostAudit audit = postAuditMapper.selectOne(wrapper);
        
        // 4. 如果审计记录不存在，创建新的
        if (audit == null) {
            log.info("文章无审计记录，创建新记录: postId={}", postId);
            createAuditRecord(postId);
            audit = postAuditMapper.selectOne(wrapper);
        } else {
            // 5. 检查是否可以重新提交
            if (audit.getAuditStatus() == 0) {
                throw new BusinessException("文章已在待审核状态，无需重复提交");
            }
            if (audit.getAuditStatus() == 1) {
                throw new BusinessException("文章已通过审核，无需重新提交");
            }
            
            // 6. 重置审计记录（仅允许已拒绝的文章重新提交）
            log.info("重置审计记录: postId={}, previousStatus={}", postId, audit.getAuditStatus());
            audit.setAuditorId(null);
            audit.setAuditStatus(0); // 待审核
            audit.setAuditComment(null);
            audit.setAuditTime(null);
            audit.setUpdateTime(LocalDateTime.now());
            postAuditMapper.updateById(audit);
        }
        
        // 7. 更新文章状态为待审核
        post.setStatus(3); // 待审核
        post.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(post);
        
        log.info("文章重新提交审核: userId={}, postId={}", userId, postId);
    }
}
