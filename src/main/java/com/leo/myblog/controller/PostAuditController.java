package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostAuditResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.User;
import com.leo.myblog.service.PostAuditService;
import com.leo.myblog.service.UserCacheService;
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
    private final UserCacheService userCacheService;
    
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
     * 审计文章（通过/拒绝，需要 TOTP 双因素认证）
     */
    @RequireTotp(operation = "文章审核", module = "security.totp.post_management")
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
                                                      @PathVariable Long postId) {
        log.debug("获取文章审计记录: userId={}, postId={}", userId, postId);
        
        // 查询当前用户信息，判断是否为管理员
        User currentUser = userCacheService.getUserById(userId);
        Boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        PostAuditResponse record = postAuditService.getAuditRecord(userId, isAdmin, postId);
        return Result.success(record);
    }
    
    /**
     * 重新提交审核（作者撤回后重新提交）
     * 用于已拒绝的文章重新进入审核流程
     */
    @PostMapping("/{postId}/resubmit")
    public Result<Void> resubmitForAudit(@RequestAttribute Long userId,
                                          @PathVariable Long postId) {
        log.info("重新提交审核: userId={}, postId={}", userId, postId);
        postAuditService.resubmitForAudit(userId, postId);
        return Result.success("已重新提交审核", null);
    }
}
