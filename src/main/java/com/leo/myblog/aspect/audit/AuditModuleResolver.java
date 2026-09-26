package com.leo.myblog.aspect.audit;

import com.leo.myblog.common.AuditModule;

import java.util.Map;

/**
 * 由控制器类推导审计模块
 * <p>
 * 替代原先的 {@code className.contains("User")} 式模糊匹配 —— 那种写法有两个毛病：
 * 「UserSession」「UserPreference」全靠碰巧；而 {@code SayingController}、{@code SecurityAdminController}、
 * {@code AdminBugReportController}、{@code AdminDashboardController} 之类一律落到 SYSTEM，
 * 于是「名言管理」和「系统设置」在日志里长得一模一样。
 * <p>
 * 这里改成**显式映射表**，兜底用类名转换（绝不返回 SYSTEM），保证任何新控制器都能得到一个
 * 说得清来源的模块值。
 */
public final class AuditModuleResolver {

    private AuditModuleResolver() {
    }

    private static final Map<String, String> CLASS_MODULE = Map.ofEntries(
            // 用户
            Map.entry("UserController", AuditModule.USER),
            Map.entry("UserSessionController", AuditModule.USER),
            Map.entry("UserPreferenceController", AuditModule.USER),
            // 文章
            Map.entry("PostController", AuditModule.POST),
            Map.entry("PostAdminController", AuditModule.POST),
            Map.entry("PostAuditController", AuditModule.POST),
            // 内容
            Map.entry("CommentController", AuditModule.COMMENT),
            Map.entry("TagController", AuditModule.TAG),
            Map.entry("CategoryController", AuditModule.CATEGORY),
            Map.entry("AudioController", AuditModule.AUDIO),
            Map.entry("BloggerInfoController", AuditModule.BLOGGER),
            Map.entry("SayingController", AuditModule.SAYING),
            // 反馈
            Map.entry("AdminBugReportController", AuditModule.BUG_REPORT),
            Map.entry("BugReportController", AuditModule.BUG_REPORT),
            // 系统
            Map.entry("SystemConfigController", AuditModule.SYSTEM),
            Map.entry("AuditLogController", AuditModule.SYSTEM),
            Map.entry("AdminDashboardController", AuditModule.SYSTEM),
            // 安全
            Map.entry("SecurityAdminController", AuditModule.SECURITY),
            Map.entry("TotpController", AuditModule.SECURITY),
            // 认证（原先这些全落 SYSTEM）
            Map.entry("LoginController", AuditModule.AUTH),
            Map.entry("OAuth2Controller", AuditModule.AUTH),
            Map.entry("AppAuthController", AuditModule.AUTH),
            Map.entry("AppUserController", AuditModule.AUTH),
            Map.entry("VerificationController", AuditModule.AUTH),
            Map.entry("SliderCaptchaController", AuditModule.AUTH),
            // 其他
            Map.entry("ImageController", AuditModule.SYSTEM),
            Map.entry("AppPushTestController", AuditModule.AUTH));

    /**
     * 推导模块
     *
     * @param targetClass 目标控制器类，可为 null
     * @return {@link AuditModule} 中的某个值；无法识别时用类名转换的结果（如 {@code FOO_BAR}），不返回 SYSTEM
     */
    public static String resolve(Class<?> targetClass) {
        if (targetClass == null) {
            return AuditModule.SYSTEM;
        }
        String simpleName = targetClass.getSimpleName();
        String mapped = CLASS_MODULE.get(simpleName);
        if (mapped != null) {
            return mapped;
        }

        // 兜底：XxxYyyController -> XXX_YYY。比硬编码 SYSTEM 诚实 ——
        // 至少能看出这条日志来自哪个类，而不是伪装成「系统设置」。
        String base = simpleName.endsWith("Controller")
                ? simpleName.substring(0, simpleName.length() - "Controller".length())
                : simpleName;
        if (base.isEmpty()) {
            return AuditModule.SYSTEM;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }
}
