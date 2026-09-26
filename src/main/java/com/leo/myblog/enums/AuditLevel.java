package com.leo.myblog.enums;

/**
 * 审计日志等级
 * <p>
 * 存储值用大写英文（与项目其他枚举一致），前端负责显示中文。
 * <b>声明顺序即严重度顺序</b>，{@link #escalate()} 与 {@link #isAtLeast} 依赖 ordinal。
 * <p>
 * 分工：够拿到账号 → CRITICAL；能毁数据或关防护 → WARNING；改东西 → NOTICE；只看 → INFO。
 * ERROR 不单独映射，只由「失败升级」产生。
 */
public enum AuditLevel {

    /** 信息：只读查询、统计、导出、登出 */
    INFO("信息"),

    /** 记录：普通业务写操作（创建/更新/上传/审核/登录成功） */
    NOTICE("记录"),

    /** 警告：破坏性或影响可用性、安全边界（删除、状态切换、解封、批量清理），以及失败兜底 */
    WARNING("警告"),

    /** 错误：非预期系统异常，或由 WARNING 升级而来 */
    ERROR("错误"),

    /** 关键：权限/身份的授予或剥夺、凭据重置、封禁 */
    CRITICAL("关键");

    private final String description;

    AuditLevel(String description) {
        this.description = description;
    }

    public String getValue() {
        return name();
    }

    public String getDescription() {
        return description;
    }

    /** 升一级（CRITICAL 封顶） */
    public AuditLevel escalate() {
        return values()[Math.min(ordinal() + 1, CRITICAL.ordinal())];
    }

    /** 取两者中更严重的一个 */
    public AuditLevel max(AuditLevel other) {
        if (other == null) {
            return this;
        }
        return ordinal() >= other.ordinal() ? this : other;
    }

    public boolean isAtLeast(AuditLevel other) {
        return other != null && ordinal() >= other.ordinal();
    }

    /** 宽松解析：无法识别或为空时回退到 NOTICE，绝不抛异常打断审计 */
    public static AuditLevel from(String name) {
        if (name == null || name.isBlank()) {
            return NOTICE;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOTICE;
        }
    }

    /**
     * 严格判断是否是合法等级值
     * <p>
     * 用于「按等级筛选」这类场景：那里不能用宽松解析 —— 传 {@code WARN} 这种拼错的值会被
     * 静默当成 NOTICE，于是查出一堆 NOTICE 行，看起来像筛选生效了，实际是错的。
     */
    public static boolean isValid(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            valueOf(name.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
