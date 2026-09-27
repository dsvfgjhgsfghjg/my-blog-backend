package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体
 * <p>
 * 用于把「文章审核结果」「密码被重置」这类与用户直接相关的事件告诉当事人。
 * 建表见 {@code V38__create_notification_table.sql}。
 */
@Data
@TableName("t_notification")
public class Notification {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 接收者用户ID
     */
    private Long userId;

    /**
     * 通知类型，取值见 {@link com.leo.myblog.enums.NotificationType}
     */
    private String type;

    /**
     * 标题（列表页直接显示）
     */
    private String title;

    /**
     * 正文，可空
     */
    private String content;

    /**
     * 站内跳转路径（如 {@code /admin/articles}），可空
     */
    private String link;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
