package com.leo.myblog.common;

import java.util.Map;

/**
 * 审计模块（对应 {@code t_audit_log.module}）
 * <p>
 * 相比改造前的 8 个取值，这里把原先被硬塞进 {@code SYSTEM} 的几个模块拆了出来 ——
 * 「名言管理」「Bug 反馈」「安全中心」「认证」各自独立，这正是日志"看不懂"的来源之一。
 * <p>
 * 这是一份**封闭清单**：{@code AuditModuleResolver} 只产出这里的值。新增模块时三处要同步：
 * 本类、{@code AuditModuleResolver} 的类名映射表、前端 AuditLogManagement.vue 的 MODULES 常量。
 */
public final class AuditModule {

    private AuditModule() {
    }

    public static final String USER = "USER";
    public static final String POST = "POST";
    public static final String COMMENT = "COMMENT";
    public static final String TAG = "TAG";
    public static final String CATEGORY = "CATEGORY";
    public static final String AUDIO = "AUDIO";
    public static final String BLOGGER = "BLOGGER";
    public static final String SAYING = "SAYING";
    public static final String BUG_REPORT = "BUG_REPORT";
    public static final String SECURITY = "SECURITY";
    public static final String SYSTEM = "SYSTEM";
    /** 登录/注册/令牌 → 认证模块（原先这些也落 SYSTEM） */
    public static final String AUTH = "AUTH";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(USER, "用户管理"),
            Map.entry(POST, "文章管理"),
            Map.entry(COMMENT, "评论管理"),
            Map.entry(TAG, "标签管理"),
            Map.entry(CATEGORY, "分类管理"),
            Map.entry(AUDIO, "音频管理"),
            Map.entry(BLOGGER, "博主信息"),
            Map.entry(SAYING, "名言管理"),
            Map.entry(BUG_REPORT, "Bug 反馈"),
            Map.entry(SECURITY, "安全中心"),
            Map.entry(SYSTEM, "系统设置"),
            Map.entry(AUTH, "登录认证"));

    /** 模块的中文标签；未知模块回退为原值 */
    public static String label(String module) {
        if (module == null) {
            return null;
        }
        return LABELS.getOrDefault(module, module);
    }
}
