package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.response.NotificationResponse;
import com.leo.myblog.entity.Notification;
import com.leo.myblog.enums.NotificationType;
import com.leo.myblog.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务
 * <p>
 * 谁会产生通知：文章审核结果（见 {@code PostAuditService.auditPost}）、
 * 管理员重置密码（见 {@code UserService.resetPassword}）。
 * <p>
 * 事务语义：{@link #notify} 用默认传播行为，即**加入调用方的事务**。
 * 这是有意为之 —— 通知必须与触发它的事件同生共死：
 * <ul>
 *   <li>若审核回滚了，不该留下一条「你的文章已通过」；</li>
 *   <li>若通知写不进去，说明库本身有问题，此时让整个操作失败比
 *       「审核成功但作者永远收不到通知」更诚实 —— 后者正是这个功能
 *       之前被注释掉之后留下的状态。</li>
 * </ul>
 * 因此这里**不吞异常**，调用方也不应该吞。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * 单页最多返回多少条，避免前端传个超大 pageSize 把库拖垮
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationMapper notificationMapper;

    /**
     * 发一条站内通知
     *
     * @param userId  接收者用户ID
     * @param type    通知类型
     * @param title   标题（数据库非空，必传）
     * @param content 正文，可空
     * @param link    前端站内跳转路径，可空
     */
    @Transactional(rollbackFor = Exception.class)
    public void notify(Long userId, NotificationType type, String title, String content, String link) {
        if (userId == null) {
            // 没有接收者就没法通知。历史文章的作者可能为空，这种情况静默跳过，
            // 但不能让上游操作失败。
            log.warn("通知接收者为空，跳过: type={}, title={}", type, title);
            return;
        }
        if (title == null || title.isBlank()) {
            log.warn("通知标题为空，跳过: userId={}, type={}", userId, type);
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.getValue());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setLink(link);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());

        notificationMapper.insert(notification);

        log.info("站内通知已发送: userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * 分页查询某用户的通知（时间倒序）
     *
     * @param userId     用户ID
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页数量
     * @param unreadOnly 是否只返回未读
     */
    public PageResult<NotificationResponse> list(Long userId, Integer pageNum, Integer pageSize, Boolean unreadOnly) {
        int current = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Notification::getIsRead, false);
        }
        wrapper.orderByDesc(Notification::getCreateTime);

        Page<Notification> page = notificationMapper.selectPage(new Page<>(current, size), wrapper);

        List<NotificationResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Page<NotificationResponse> responsePage = new Page<>(current, size, page.getTotal());
        responsePage.setRecords(records);

        return PageResult.of(responsePage);
    }

    /**
     * 未读数（用于菜单角标）
     * <p>
     * <b>返回 int 而不是 long 是有意的</b>：{@code JacksonConfig} 全局注册了 Long → String 的
     * 序列化器（防 JS 精度丢失），返回 Long 会被序列化成 {@code "0"} 这种字符串。
     * 而 JS 里 {@code "0"} 是**真值**，前端 {@code if (unread) showBadge()} 会永远显示角标。
     * int 不走那个序列化器，下发的是真正的数字。
     */
    public int unreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
               .eq(Notification::getIsRead, false);
        return Math.toIntExact(notificationMapper.selectCount(wrapper));
    }

    /**
     * 标记单条通知为已读
     * <p>
     * 条件里带上 userId：否则任何人拿到 ID 都能改别人的已读状态。
     *
     * @return 是否真的改动了（false 表示该通知不存在或不属于该用户）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long userId, Long notificationId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, notificationId)
               .eq(Notification::getUserId, userId)
               .eq(Notification::getIsRead, false)
               .set(Notification::getIsRead, true);

        return notificationMapper.update(null, wrapper) > 0;
    }

    /**
     * 全部标记为已读
     *
     * @return 本次被标记的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
               .eq(Notification::getIsRead, false)
               .set(Notification::getIsRead, true);

        return notificationMapper.update(null, wrapper);
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(String.valueOf(notification.getId()));
        response.setType(notification.getType());
        response.setTitle(notification.getTitle());
        response.setContent(notification.getContent());
        response.setLink(notification.getLink());
        response.setIsRead(notification.getIsRead());
        response.setCreateTime(notification.getCreateTime());
        return response;
    }
}
