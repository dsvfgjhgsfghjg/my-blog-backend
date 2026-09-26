package com.leo.myblog.aspect.audit;

import com.leo.myblog.common.AuditAction;

import java.util.List;
import java.util.Set;

/**
 * 由方法名推导审计操作类型
 * <p>
 * 替代原先埋在切面里的 {@code contains} 顺序链，修掉三类缺陷：
 * <ol>
 *   <li><b>顺序错误</b>：原先 {@code update} 判断早于 {@code role}，导致 {@code updateUserRole}
 *       被判成 UPDATE；{@code setSuperAdmin}/{@code setAbilities}/{@code banIp}/{@code unbanIp}/
 *       {@code migrateContent}/{@code auditPost} 与全部 {@code get*} 都无匹配 → OTHER。</li>
 *   <li><b>只读判断缺失</b>：13 个 {@code get*} 方法全落 OTHER。见下方顺序说明。</li>
 *   <li><b>兜底不可读</b>：OTHER 会生成「XX模块的OTHER操作」。现在 OTHER 只在规则表全不命中时出现，
 *       且描述侧会用 HTTP 方法 + URI 兜底。</li>
 * </ol>
 * <b>顺序即契约</b>：规则自下而上按「只读 → 多词精确语义 → 身份 → 会话/文件 → 通用动词」排列，
 * 修改顺序前请先看 {@code AuditActionResolverTest} 里的回归用例。
 */
public final class AuditActionResolver {

    private AuditActionResolver() {
    }

    /** 只读前缀。放在最前面：写动词（create/update/delete/…）不会以这些开头，
     *  而 getBannedIps / getAuditLogs / getCreatedPosts 本来就是查询。 */
    private static final List<String> READ_PREFIXES = List.of("get", "list", "query", "search", "count");

    /** 中段出现的只读词（方法名不以只读前缀开头时，放在写动词之后判断） */
    private static final Set<String> READ_MARKERS = Set.of("stats", "detail", "info");

    public static String resolve(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return AuditAction.OTHER;
        }
        String m = methodName.toLowerCase();

        // ---- 1. 只读（必须最先，否则 getBannedIps 会被下面的 ban 抢走） ----
        for (String prefix : READ_PREFIXES) {
            if (m.startsWith(prefix)) {
                return AuditAction.QUERY;
            }
        }

        // ---- 2. 多词精确语义（必须先于 update/delete 等通用动词） ----
        if (m.contains("reset") && m.contains("password")) {
            return AuditAction.RESET_PASSWORD;
        }
        if (m.contains("change") && m.contains("password")) {
            return AuditAction.CHANGE_PASSWORD;
        }
        if (m.contains("superadmin")) {
            return AuditAction.SET_SUPER_ADMIN;
        }
        if (m.contains("role")) {
            return AuditAction.CHANGE_ROLE;
        }
        // 必须带写动词，否则 getUserAbilities / getAllAbilityTypes 会被误判为改权限
        if (m.contains("abilit") && (m.contains("set") || m.contains("grant") || m.contains("update"))) {
            return AuditAction.CHANGE_ABILITY;
        }
        // unban 必须先于 ban
        if (m.contains("unban")) {
            return AuditAction.UNBAN_IP;
        }
        if (m.contains("ban")) {
            return AuditAction.BAN_IP;
        }
        if (m.contains("config") && m.contains("reset")) {
            return AuditAction.RESET_CONFIG;
        }
        if (m.contains("config") && (m.contains("update") || m.contains("batch") || m.contains("set"))) {
            return AuditAction.UPDATE_CONFIG;
        }
        // expired 清理必须先于 delete
        if (m.contains("expired") && (m.contains("delete") || m.contains("clean"))) {
            return AuditAction.CLEANUP;
        }
        // 用 startsWith：否则会抢走 AuditLogController.getAuditLogs（虽然它已被只读规则截走，
        // 但保持这条语义边界，避免将来规则调整时意外命中）
        if (m.startsWith("audit")) {
            return AuditAction.AUDIT;
        }
        if (m.contains("migrate")) {
            return AuditAction.MIGRATE;
        }

        // ---- 3. 身份 ----
        if (m.contains("login")) {
            return AuditAction.LOGIN;
        }
        if (m.contains("logout")) {
            return AuditAction.LOGOUT;
        }
        if (m.contains("register")) {
            return AuditAction.REGISTER;
        }
        // 两步登录的 step2-password：提交密码即登录尝试（reset/change password 已在上面截走）
        if (m.contains("password")) {
            return AuditAction.LOGIN;
        }

        // ---- 4. 会话 / 文件 ----
        if (m.contains("revoke") && m.contains("session")) {
            return AuditAction.REVOKE_SESSION;
        }
        if (m.contains("revoke")) {
            return AuditAction.REVOKE;
        }
        if (m.contains("upload")) {
            return AuditAction.UPLOAD;
        }
        if (m.contains("export")) {
            return AuditAction.EXPORT;
        }

        // ---- 5. 通用动词 ----
        if (m.contains("delete") || m.contains("remove")) {
            return AuditAction.DELETE;
        }
        if (m.contains("toggle") || m.contains("enable") || m.contains("disable")) {
            return AuditAction.TOGGLE_STATUS;
        }
        if (m.contains("reset")) {
            return AuditAction.RESET_CONFIG;
        }
        if (m.contains("create") || m.contains("add") || m.contains("save")) {
            return AuditAction.CREATE;
        }
        if (m.contains("update") || m.contains("edit") || m.contains("modify")) {
            return AuditAction.UPDATE;
        }

        // ---- 6. 中段只读词（放在写动词之后，避免 getCreatedXxx 类命名误判） ----
        for (String marker : READ_MARKERS) {
            if (m.contains(marker)) {
                return AuditAction.QUERY;
            }
        }

        return AuditAction.OTHER;
    }
}
