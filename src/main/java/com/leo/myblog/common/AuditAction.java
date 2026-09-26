package com.leo.myblog.common;

import java.util.Map;

/**
 * 审计操作类型（对应 {@code t_audit_log.operation_type}）
 * <p>
 * 这是一份**封闭清单**：{@code AuditActionResolver} 只会产出这里的值，
 * 前端也按这份清单渲染筛选项与中文标签。新增动作时三处要同步：
 * 本类、{@code AuditActionResolver}、前端 AuditLogManagement.vue 的 ACTIONS 常量。
 */
public final class AuditAction {

    private AuditAction() {
    }

    // 身份
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTER = "REGISTER";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    // 权限
    public static final String CHANGE_ROLE = "CHANGE_ROLE";
    public static final String SET_SUPER_ADMIN = "SET_SUPER_ADMIN";
    public static final String CHANGE_ABILITY = "CHANGE_ABILITY";

    // 通用增删改查
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String QUERY = "QUERY";
    public static final String TOGGLE_STATUS = "TOGGLE_STATUS";

    // 业务特有
    public static final String UPLOAD = "UPLOAD";
    public static final String AUDIT = "AUDIT";
    public static final String UPDATE_CONFIG = "UPDATE_CONFIG";
    public static final String RESET_CONFIG = "RESET_CONFIG";
    public static final String MIGRATE = "MIGRATE";
    public static final String CLEANUP = "CLEANUP";
    public static final String EXPORT = "EXPORT";

    // 会话
    public static final String REVOKE = "REVOKE";
    public static final String REVOKE_SESSION = "REVOKE_SESSION";

    // 安全
    public static final String BAN_IP = "BAN_IP";
    public static final String UNBAN_IP = "UNBAN_IP";

    /** 兜底：规则表全不命中。描述生成时会用 HTTP 方法 + URI 兜底，不再输出「XX模块的OTHER操作」 */
    public static final String OTHER = "OTHER";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(LOGIN, "登录"),
            Map.entry(LOGOUT, "登出"),
            Map.entry(REGISTER, "注册"),
            Map.entry(CHANGE_PASSWORD, "修改密码"),
            Map.entry(RESET_PASSWORD, "重置密码"),
            Map.entry(CHANGE_ROLE, "修改角色"),
            Map.entry(SET_SUPER_ADMIN, "设置超级管理员"),
            Map.entry(CHANGE_ABILITY, "调整权限"),
            Map.entry(CREATE, "创建"),
            Map.entry(UPDATE, "修改"),
            Map.entry(DELETE, "删除"),
            Map.entry(QUERY, "查询"),
            Map.entry(TOGGLE_STATUS, "切换状态"),
            Map.entry(UPLOAD, "上传"),
            Map.entry(AUDIT, "审核"),
            Map.entry(UPDATE_CONFIG, "修改配置"),
            Map.entry(RESET_CONFIG, "重置配置"),
            Map.entry(MIGRATE, "数据迁移"),
            Map.entry(CLEANUP, "清理"),
            Map.entry(EXPORT, "导出"),
            Map.entry(REVOKE, "撤销"),
            Map.entry(REVOKE_SESSION, "下线会话"),
            Map.entry(BAN_IP, "封禁 IP"),
            Map.entry(UNBAN_IP, "解封 IP"),
            Map.entry(OTHER, "其他操作"));

    /** 动作的中文标签；未知动作回退为原值，便于发现漏登记的常量 */
    public static String label(String action) {
        if (action == null) {
            return LABELS.get(OTHER);
        }
        return LABELS.getOrDefault(action, action);
    }
}
