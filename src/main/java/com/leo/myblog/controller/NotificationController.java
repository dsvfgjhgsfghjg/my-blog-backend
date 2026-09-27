package com.leo.myblog.controller;

import com.leo.myblog.common.PageResult;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.NotificationResponse;
import com.leo.myblog.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知控制器
 * <p>
 * 全部是「当前登录用户自己的」数据，用户ID 一律取自 {@code @RequestAttribute Long userId}
 * （由鉴权过滤器写入），**不接受前端传入的 userId** —— 否则任何人都能读别人的通知。
 * 因此这几个接口也不需要 {@code @RequireAdmin}。
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询我的通知
     *
     * @param unreadOnly 传 true 则只返回未读
     */
    @GetMapping
    public Result<PageResult<NotificationResponse>> list(
            @RequestAttribute Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Boolean unreadOnly) {
        log.debug("查询站内通知: userId={}, pageNum={}, pageSize={}, unreadOnly={}",
                userId, pageNum, pageSize, unreadOnly);
        return Result.success(notificationService.list(userId, pageNum, pageSize, unreadOnly));
    }

    /**
     * 我的未读数（菜单角标）
     * <p>
     * 用 Integer 而非 Long：全局 Jackson 配置会把 Long 序列化成字符串，{"0"} 在 JS 里是真值，
     * 角标会永远亮着。详见 {@code NotificationService#unreadCount}。
     */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(@RequestAttribute Long userId) {
        return Result.success(notificationService.unreadCount(userId));
    }

    /**
     * 标记单条为已读
     * <p>
     * 幂等：重复点击、或那条本就不属于我，都返回成功 —— 既避免重复点击报错，
     * 也不从响应里泄露「某个通知 ID 是否存在」。
     */
    @PostMapping("/{notificationId}/read")
    public Result<Void> markRead(@RequestAttribute Long userId,
                                 @PathVariable Long notificationId) {
        boolean changed = notificationService.markRead(userId, notificationId);
        if (!changed) {
            log.debug("标记已读未产生变更（可能已读或不属于该用户）: userId={}, notificationId={}",
                    userId, notificationId);
        }
        return Result.success("已标记为已读", null);
    }

    /**
     * 全部标记为已读
     */
    @PostMapping("/read-all")
    public Result<Integer> markAllRead(@RequestAttribute Long userId) {
        int changed = notificationService.markAllRead(userId);
        log.info("全部标记已读: userId={}, 条数={}", userId, changed);
        return Result.success("已全部标记为已读", changed);
    }
}
