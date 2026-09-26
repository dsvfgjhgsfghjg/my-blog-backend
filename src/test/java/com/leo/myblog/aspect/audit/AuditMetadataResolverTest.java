package com.leo.myblog.aspect.audit;

import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.AuditModule;
import com.leo.myblog.controller.AuditLogController;
import com.leo.myblog.controller.SecurityAdminController;
import com.leo.myblog.controller.UserController;
import com.leo.myblog.dto.request.UpdateSystemConfigRequest;
import com.leo.myblog.enums.AuditLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 描述生成与操作对象提取测试
 * <p>
 * 这些用例依赖**编译期保留了形参名**（`-parameters`，Spring Boot 插件默认开启）。
 * 若哪天该开关被关掉，本类会失败 —— 那是有意的：形参名一旦变成 arg0，
 * 描述模板与对象提取会整体失效。
 */
public class AuditMetadataResolverTest {

    private static final long OPERATOR_ID = 1L;
    private static final long TARGET_ID = 123L;

    /** 复刻 AspectJ MethodSignature.getParameterNames() 的行为 */
    private static String[] paramNames(Method method) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        String[] names = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            names[i] = params[i].getName();
        }
        return names;
    }

    private static AuditMetadataResolver.AuditMetadata resolve(Method method, Object... args) {
        return AuditMetadataResolver.resolve(method, args, paramNames(method),
                method.getParameterAnnotations(), method.getDeclaringClass(), "POST", "/api/x");
    }

    @Test
    @DisplayName("形参名确实被保留（否则模板与对象提取会整体失效）")
    void parameterNamesAreRetained() throws Exception {
        Method banIp = SecurityAdminController.class.getMethod(
                "banIp", Long.class, String.class, String.class, long.class);
        String[] names = paramNames(banIp);
        assertEquals("userId", names[0], "编译未保留形参名？请确认 -parameters 生效");
        assertEquals("ipAddress", names[1]);
        assertEquals("durationHours", names[3]);
    }

    @Test
    @DisplayName("封禁 IP：描述模板渲染出 IP 与时长（用户反馈的那个方法）")
    void banIpDescription() throws Exception {
        Method banIp = SecurityAdminController.class.getMethod(
                "banIp", Long.class, String.class, String.class, long.class);
        AuditMetadataResolver.AuditMetadata meta =
                resolve(banIp, OPERATOR_ID, "203.0.113.7", "测试封禁", 24L);

        assertEquals("封禁 IP 203.0.113.7，时长 24 小时", meta.description());
        assertEquals(AuditModule.SECURITY, meta.module());
        assertEquals(AuditAction.BAN_IP, meta.action());
        assertEquals(AuditLevel.CRITICAL, meta.baseLevel(), "封禁 IP 属于关键级");
    }

    @Test
    @DisplayName("改角色：描述带上新角色；等级是关键")
    void updateUserRoleDescription() throws Exception {
        Method method = UserController.class.getMethod(
                "updateUserRole", Long.class, Long.class, Boolean.class);
        AuditMetadataResolver.AuditMetadata meta = resolve(method, OPERATOR_ID, TARGET_ID, true);

        assertEquals("修改用户 123 的角色（管理员：true）", meta.description());
        assertEquals(AuditAction.CHANGE_ROLE, meta.action());
        assertEquals(AuditLevel.CRITICAL, meta.baseLevel());
    }

    @Test
    @DisplayName("清理过期日志：描述带上保留天数")
    void deleteExpiredLogsDescription() throws Exception {
        Method method = AuditLogController.class.getMethod("deleteExpiredLogs", int.class);
        AuditMetadataResolver.AuditMetadata meta = resolve(method, 30);

        assertEquals("清理 30 天前的审计日志", meta.description());
        assertEquals(AuditAction.CLEANUP, meta.action());
        assertEquals(AuditLevel.WARNING, meta.baseLevel());
    }

    @Test
    @DisplayName("操作对象不能取错：resetPassword 要取 targetUserId，不是操作者 userId")
    void objectExtractionPrefersTargetOverOperator() throws Exception {
        Method method = UserController.class.getMethod(
                "resetPassword", Long.class, Long.class, com.leo.myblog.dto.request.AdminResetPasswordRequest.class);
        AuditMetadataResolver.AuditMetadata meta = resolve(
                method, OPERATOR_ID, TARGET_ID, new com.leo.myblog.dto.request.AdminResetPasswordRequest());

        // 走通用拼装（该方法没有显式描述模板）
        assertEquals("重置密码 123", meta.description(),
                "取到了操作者 userId=1 而不是目标 123，说明没按 @RequestAttribute 排除");
        assertEquals(AuditLevel.CRITICAL, meta.baseLevel());
    }

    @Test
    @DisplayName("DTO 里的 configKey 能被识别为操作对象")
    void objectFromRequestBodyDto() throws Exception {
        Method method = com.leo.myblog.controller.SystemConfigController.class.getMethod(
                "updateConfig", Long.class, UpdateSystemConfigRequest.class);
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        request.setConfigKey("site.title");
        request.setConfigValue("我的博客");

        AuditMetadataResolver.AuditMetadata meta = resolve(method, OPERATOR_ID, request);

        assertEquals("修改配置 site.title", meta.description());
        assertEquals(AuditModule.SYSTEM, meta.module());
        assertEquals(AuditLevel.WARNING, meta.baseLevel(),
                "改配置与重置配置同级（警告）：配置里有 TOTP 开关之类安全项");
    }

    @Test
    @DisplayName("推导路径的模块不再一律落到 SYSTEM")
    void modulesAreSpecific() throws Exception {
        Method banIp = SecurityAdminController.class.getMethod(
                "banIp", Long.class, String.class, String.class, long.class);
        assertEquals(AuditModule.SECURITY, resolve(banIp, OPERATOR_ID, "1.1.1.1", "r", 1L).module());

        Method sayings = com.leo.myblog.controller.SayingController.class.getMethod(
                "addSaying", String.class, Long.class, com.leo.myblog.dto.request.AddSayingRequest.class);
        assertEquals(AuditModule.SAYING,
                resolve(sayings, "cs", OPERATOR_ID, new com.leo.myblog.dto.request.AddSayingRequest()).module(),
                "名言管理不该显示成「系统设置」");
    }

    @Test
    @DisplayName("硬性约束：任何描述都不得是「XX模块的OTHER操作」")
    void noMoreMeaninglessDescription() throws Exception {
        // 用户反馈的原话就是这个文案。用真实控制器方法全量扫一遍。
        String[] controllers = {"UserController", "TagController", "CommentController",
                "PostController", "PostAdminController", "PostAuditController", "AudioController",
                "BloggerInfoController", "SayingController", "AdminBugReportController",
                "AdminDashboardController", "AuditLogController", "SystemConfigController",
                "SecurityAdminController"};

        for (String simpleName : controllers) {
            Class<?> controller = Class.forName("com.leo.myblog.controller." + simpleName);
            for (Method method : controller.getDeclaredMethods()) {
                Annotation[][] anns = method.getParameterAnnotations();
                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    args[i] = defaultFor(types[i]);
                }
                AuditMetadataResolver.AuditMetadata meta = AuditMetadataResolver.resolve(
                        method, args, paramNames(method), anns, controller, "POST", "/api/x");

                assertFalse(meta.description() != null && meta.description().contains("模块的OTHER操作"),
                        controller.getSimpleName() + "#" + method.getName()
                                + " 仍在产出「XX模块的OTHER操作」：" + meta.description());
                assertTrue(meta.description() != null && !meta.description().isBlank(),
                        controller.getSimpleName() + "#" + method.getName() + " 描述为空");
            }
        }
    }

    private Object defaultFor(Class<?> type) {
        if (type == long.class || type == int.class) {
            return 0;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == Long.class) {
            return 1L;
        }
        if (type == Integer.class) {
            return 1;
        }
        if (type == String.class) {
            return "x";
        }
        if (type == Boolean.class) {
            return true;
        }
        return null;
    }
}
