package com.leo.myblog.aspect.audit;

import com.leo.myblog.common.AuditAction;
import com.leo.myblog.enums.AuditLevel;
import com.leo.myblog.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 审计等级判定
 * <p>
 * 分两步：<b>基础等级</b>由操作类型决定（单一事实来源，不在各处手写），
 * <b>失败升级</b>再按结果调整。
 * <p>
 * 归类原则一句话：够拿到账号 → CRITICAL；能毁数据或关防护 → WARNING；改东西 → NOTICE；只看 → INFO。
 * <p>
 * 注意：失败一定至少升到 WARNING，所以「401/403 越权尝试要升到 WARNING」这条子规则被自动涵盖，
 * 无需单列。
 */
public final class AuditLevelResolver {

    private AuditLevelResolver() {
    }

    /** 只读类：不改变任何状态 */
    private static final Set<String> INFO_ACTIONS = Set.of(
            AuditAction.QUERY, AuditAction.EXPORT, AuditAction.LOGOUT);

    /** 普通写操作 */
    private static final Set<String> NOTICE_ACTIONS = Set.of(
            AuditAction.LOGIN, AuditAction.REGISTER, AuditAction.CREATE,
            AuditAction.UPDATE, AuditAction.UPLOAD, AuditAction.AUDIT);

    /** 破坏性或影响可用性、安全边界 */
    private static final Set<String> WARNING_ACTIONS = Set.of(
            AuditAction.DELETE, AuditAction.TOGGLE_STATUS, AuditAction.REVOKE,
            AuditAction.REVOKE_SESSION, AuditAction.UNBAN_IP, AuditAction.MIGRATE,
            AuditAction.CLEANUP, AuditAction.RESET_CONFIG,
            // 改配置与重置配置风险相当：配置里包含 TOTP 开关之类的安全项，
            // 改错一个键就等于关掉一层防护。不能靠"未登记回退 NOTICE"碰巧得到记录级。
            AuditAction.UPDATE_CONFIG);

    /** 权限/身份的授予或剥夺、凭据重置、封禁 */
    private static final Set<String> CRITICAL_ACTIONS = Set.of(
            AuditAction.RESET_PASSWORD, AuditAction.CHANGE_PASSWORD, AuditAction.CHANGE_ROLE,
            AuditAction.CHANGE_ABILITY, AuditAction.SET_SUPER_ADMIN, AuditAction.BAN_IP);

    private static final Map<String, AuditLevel> BASE_LEVELS = buildBaseLevels();

    private static Map<String, AuditLevel> buildBaseLevels() {
        java.util.HashMap<String, AuditLevel> map = new java.util.HashMap<>();
        INFO_ACTIONS.forEach(a -> map.put(a, AuditLevel.INFO));
        NOTICE_ACTIONS.forEach(a -> map.put(a, AuditLevel.NOTICE));
        WARNING_ACTIONS.forEach(a -> map.put(a, AuditLevel.WARNING));
        CRITICAL_ACTIONS.forEach(a -> map.put(a, AuditLevel.CRITICAL));
        return Map.copyOf(map);
    }

    /**
     * 操作类型的基础等级（成功时即为最终等级）
     * <p>
     * 未登记的动作（含 NEW 出来的）回退 NOTICE —— 比回退 INFO 安全：
     * 新写的操作默认会被当成"有副作用"而不是"只是看了看"。
     */
    public static AuditLevel baseOf(String action) {
        // 必须先挡 null：BASE_LEVELS 是不可变 Map，get(null) 会抛 NPE
        if (action == null || action.isBlank()) {
            return AuditLevel.NOTICE;
        }
        AuditLevel level = BASE_LEVELS.get(action);
        return level != null ? level : AuditLevel.NOTICE;
    }

    /**
     * 结合执行结果给出最终等级
     *
     * @param base    基础等级
     * @param success 是否成功
     * @param error   抛出的异常，可为 null
     */
    public static AuditLevel effective(AuditLevel base, boolean success, Throwable error) {
        if (base == null) {
            base = AuditLevel.NOTICE;
        }
        if (success) {
            return base;
        }

        // 失败：先升一级，且至少 WARNING
        AuditLevel level = base.escalate().max(AuditLevel.WARNING);

        // 非业务异常（系统故障、NPE、SQL 异常…）再升到 ERROR
        if (error != null && !(error instanceof BusinessException)) {
            level = level.max(AuditLevel.ERROR);
        }
        // 业务异常说明是预期内的拒绝（校验失败、权限不足等），到 WARNING/ERROR 为止

        return level;
    }
}
