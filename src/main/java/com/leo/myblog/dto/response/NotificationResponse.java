package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知响应
 * <p>
 * id 用 String 下发，避免雪花 ID 超出 JavaScript 安全整数范围被前端截断
 * （与 {@code PostAuditResponse} 一致）。
 */
@Data
public class NotificationResponse {

    private String id;

    /**
     * 通知类型，取值见 {@link com.leo.myblog.enums.NotificationType}
     */
    private String type;

    private String title;

    private String content;

    private String link;

    private Boolean isRead;

    private LocalDateTime createTime;
}
