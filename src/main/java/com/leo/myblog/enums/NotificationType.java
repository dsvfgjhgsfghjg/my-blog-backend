package com.leo.myblog.enums;

/**
 * 站内通知类型
 * <p>
 * 存储值用大写英文（与 {@link AuditLevel} 等枚举一致），前端负责显示中文与图标。
 * 新增类型时同步更新前端通知列表的映射，否则前端会退化成显示原始英文串。
 */
public enum NotificationType {

    /** 文章审核通过，已发布 */
    POST_AUDIT_PASSED("文章审核通过"),

    /** 文章审核被拒绝，退回草稿，附审核意见 */
    POST_AUDIT_REJECTED("文章审核未通过"),

    /** 管理员重置了该用户的密码 */
    PASSWORD_RESET("密码已被重置"),

    /** 其他系统通知（预留） */
    SYSTEM("系统通知");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getValue() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
