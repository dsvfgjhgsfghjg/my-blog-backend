package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.CreateCommentRequest;
import com.leo.myblog.dto.request.GetCommentListRequest;
import com.leo.myblog.dto.response.CommentResponse;
import com.leo.myblog.service.CommentRateLimitService;
import com.leo.myblog.service.CommentService;
import com.leo.myblog.util.AbilityValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {
    
    private final CommentService commentService;
    private final AbilityValidator abilityValidator;
    private final CommentRateLimitService commentRateLimitService;
    
    /**
     * 发表评论
     * 需要登录
     * 安全修复V5: 添加速率限制，每分钟最多10条评论
     */
    @PostMapping
    public Result<CommentResponse> createComment(@RequestAttribute Long userId,
                                                  @Valid @RequestBody CreateCommentRequest request) {
        // 检查评论速率限制
        if (!commentRateLimitService.canComment(userId)) {
            long unlockTime = commentRateLimitService.getUnlockTime(userId);
            return Result.error(429, String.format("评论过于频繁，请%d秒后再试", unlockTime));
        }
        
        log.info("用户 {} 发表评论: postId={}", userId, request.getPostId());
        CommentResponse response = commentService.createComment(userId, request);
        return Result.success("评论成功", response);
    }
    
    /**
     * 获取文章的评论列表（分页）
     * 公开接口，无需登录
     */
    @GetMapping("/post")
    public Result<Page<CommentResponse>> getCommentList(@RequestAttribute(required = false) Long userId,
                                                         @Valid GetCommentListRequest request) {
        log.debug("查询文章评论: postId={}, pageNum={}, pageSize={}, userId={}", 
                request.getPostId(), request.getPageNum(), request.getPageSize(), userId);
        Page<CommentResponse> page = commentService.getCommentList(userId, request);
        return Result.success(page);
    }
    
    /**
     * 删除评论（仅评论作者或拥有delete_comments能力的管理员）
     */
    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(@RequestAttribute Long userId,
                                       @PathVariable Long commentId,
                                       @RequestAttribute(required = false) Boolean isAdmin) {
        log.info("删除评论: commentId={}, userId={}, isAdmin={}", commentId, userId, isAdmin);
        boolean admin = isAdmin != null && isAdmin;
        
        // 如果是管理员，需要验证是否有 delete_comments 能力
        if (admin) {
            abilityValidator.validateAbility(userId, "delete_comments");
        }
        
        commentService.deleteComment(userId, commentId, admin);
        return Result.success("评论已删除", null);
    }
    
    /**
     * 隐藏/显示评论（仅拥有edit_comments能力的管理员）
     */
    @RequireAdmin
    @PutMapping("/{commentId}/status")
    public Result<Void> toggleCommentStatus(@RequestAttribute Long userId,
                                             @PathVariable Long commentId,
                                             @RequestParam(required = true) Boolean visible) {
        log.info("管理员更新评论状态: commentId={}, visible={}", commentId, visible);
        
        // 验证 visible 不为 null
        if (visible == null) {
            return Result.error(400, "visible参数不能为空");
        }
        
        // 验证能力：需要 edit_comments 能力
        abilityValidator.validateAbility(userId, "edit_comments");
        
        // 转换为 status：true -> 1（显示），false -> 0（隐藏）
        Integer status = visible ? 1 : 0;
        
        commentService.toggleCommentStatus(commentId, status);
        return Result.success(visible ? "评论已显示" : "评论已隐藏", null);
    }
    
    /**
     * 获取用户的评论列表
     * 需要登录，只能查看自己的评论
     */
    @GetMapping("/my")
    public Result<Page<CommentResponse>> getMyComments(@RequestAttribute Long userId,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "20") Integer pageSize) {
        log.debug("查询用户评论: userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        Page<CommentResponse> page = commentService.getUserComments(userId, pageNum, pageSize);
        return Result.success(page);
    }
    
    /**
     * 点赞评论
     * 需要登录
     */
    @PostMapping("/{commentId}/like")
    public Result<Void> likeComment(@RequestAttribute Long userId,
                                     @PathVariable Long commentId) {
        log.info("用户 {} 点赞评论: commentId={}", userId, commentId);
        commentService.likeComment(userId, commentId);
        return Result.success("点赞成功", null);
    }
    
    /**
     * 取消点赞评论
     * 需要登录
     */
    @DeleteMapping("/{commentId}/like")
    public Result<Void> unlikeComment(@RequestAttribute Long userId,
                                       @PathVariable Long commentId) {
        log.info("用户 {} 取消点赞评论: commentId={}", userId, commentId);
        commentService.unlikeComment(userId, commentId);
        return Result.success("取消点赞成功", null);
    }
}
