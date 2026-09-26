package com.leo.myblog.aspect.audit;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.controller.AdminBugReportController;
import com.leo.myblog.controller.AdminDashboardController;
import com.leo.myblog.controller.AudioController;
import com.leo.myblog.controller.AuditLogController;
import com.leo.myblog.controller.BloggerInfoController;
import com.leo.myblog.controller.CommentController;
import com.leo.myblog.controller.PostAdminController;
import com.leo.myblog.controller.PostAuditController;
import com.leo.myblog.controller.PostController;
import com.leo.myblog.controller.SayingController;
import com.leo.myblog.controller.SecurityAdminController;
import com.leo.myblog.controller.SystemConfigController;
import com.leo.myblog.controller.TagController;
import com.leo.myblog.controller.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作类型推导的回归测试
 * <p>
 * 改造前 36 个 {@code @RequireAdmin} 方法里有 14 个落 OTHER（39%），描述随之退化成
 * 「XX模块的OTHER操作」—— 这正是用户反馈"看不懂"的根源。本测试把「零 OTHER」钉死。
 */
public class AuditActionResolverTest {

    /** 审计面上的全部控制器 */
    private static final List<Class<?>> AUDITED_CONTROLLERS = List.of(
            UserController.class, TagController.class, CommentController.class,
            PostController.class, PostAdminController.class, PostAuditController.class,
            AudioController.class, BloggerInfoController.class, SayingController.class,
            AdminBugReportController.class, AdminDashboardController.class,
            AuditLogController.class, SystemConfigController.class, SecurityAdminController.class);

    @Test
    @DisplayName("所有 @RequireAdmin 方法都不得推导成 OTHER")
    void noOtherForAnnotatedAdminMethods() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> controller : AUDITED_CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getAnnotation(RequireAdmin.class) == null) {
                    continue;
                }
                String action = AuditActionResolver.resolve(method.getName());
                if (AuditAction.OTHER.equals(action)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "以下被审计的方法仍推导为 OTHER，会产出「XX模块的OTHER操作」：" + offenders);
    }

    @Test
    @DisplayName("顺序缺陷回归：这几个曾经被判错的方法")
    void orderingRegressions() {
        // update 曾抢在 role 之前，把改角色判成普通 UPDATE
        assertEquals(AuditAction.CHANGE_ROLE, AuditActionResolver.resolve("updateUserRole"));
        // 曾无匹配 → OTHER
        assertEquals(AuditAction.SET_SUPER_ADMIN, AuditActionResolver.resolve("setSuperAdmin"));
        assertEquals(AuditAction.CHANGE_ABILITY, AuditActionResolver.resolve("setAbilities"));
        assertEquals(AuditAction.BAN_IP, AuditActionResolver.resolve("banIp"));
        assertEquals(AuditAction.MIGRATE, AuditActionResolver.resolve("migrateContent"));
        assertEquals(AuditAction.AUDIT, AuditActionResolver.resolve("auditPost"));
    }

    @Test
    @DisplayName("unban 不得被 ban 抢走")
    void unbanBeatsBan() {
        assertEquals(AuditAction.UNBAN_IP, AuditActionResolver.resolve("unbanIp"));
        assertEquals(AuditAction.BAN_IP, AuditActionResolver.resolve("banIp"));
    }

    @Test
    @DisplayName("只读方法不得被写动词抢走（getBannedIps / getUserAbilities / getAuditLogs）")
    void readOnlyWinsOverWriteVerbs() {
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getBannedIps"),
                "getBannedIps 含 'ban'，必须靠只读前缀先截住");
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getUserAbilities"),
                "getUserAbilities 含 'abilit'，不得被判成改权限");
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getAllAbilityTypes"));
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getAuditLogs"));
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getAuditLogStats"));
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getPendingPosts"));
        assertEquals(AuditAction.QUERY, AuditActionResolver.resolve("getDashboard"));
    }

    @Test
    @DisplayName("清理过期日志是 CLEANUP，不是 DELETE")
    void cleanupBeatsDelete() {
        assertEquals(AuditAction.CLEANUP, AuditActionResolver.resolve("deleteExpiredLogs"));
    }

    @Test
    @DisplayName("密码相关：重置/修改/登录三者的区分")
    void passwordVariants() {
        assertEquals(AuditAction.RESET_PASSWORD, AuditActionResolver.resolve("resetPassword"));
        assertEquals(AuditAction.CHANGE_PASSWORD, AuditActionResolver.resolve("changePassword"));
        // 两步登录提交密码 = 登录尝试，不能与重置/修改混为一谈
        assertEquals(AuditAction.LOGIN, AuditActionResolver.resolve("step2Password"));
    }

    @Test
    @DisplayName("系统配置的写操作用独立动作")
    void configVariants() {
        assertEquals(AuditAction.UPDATE_CONFIG, AuditActionResolver.resolve("updateConfig"));
        assertEquals(AuditAction.UPDATE_CONFIG, AuditActionResolver.resolve("batchUpdateConfigs"));
        // resetConfig 不是重置密码
        assertEquals(AuditAction.RESET_CONFIG, AuditActionResolver.resolve("resetConfig"));
    }

    @Test
    @DisplayName("会话与身份动作")
    void sessionAndIdentity() {
        assertEquals(AuditAction.REVOKE_SESSION, AuditActionResolver.resolve("revokeSession"));
        assertEquals(AuditAction.REVOKE_SESSION, AuditActionResolver.resolve("revokeOtherSessions"));
        assertEquals(AuditAction.LOGIN, AuditActionResolver.resolve("login"));
        assertEquals(AuditAction.LOGOUT, AuditActionResolver.resolve("logout"));
        assertEquals(AuditAction.REGISTER, AuditActionResolver.resolve("register"));
    }

    @Test
    @DisplayName("通用动词与边界")
    void genericVerbs() {
        assertEquals(AuditAction.DELETE, AuditActionResolver.resolve("deleteTag"));
        assertEquals(AuditAction.CREATE, AuditActionResolver.resolve("createTag"));
        assertEquals(AuditAction.UPDATE, AuditActionResolver.resolve("updateTag"));
        assertEquals(AuditAction.UPLOAD, AuditActionResolver.resolve("uploadAudio"));
        assertEquals(AuditAction.TOGGLE_STATUS, AuditActionResolver.resolve("toggleUserStatus"));

        assertEquals(AuditAction.OTHER, AuditActionResolver.resolve(null));
        assertEquals(AuditAction.OTHER, AuditActionResolver.resolve(""));
        assertEquals(AuditAction.OTHER, AuditActionResolver.resolve("frobnicate"));
    }

    @Test
    @DisplayName("每个动作都有中文标签，不会把英文枚举直接暴露给用户")
    void everyActionHasLabel() {
        for (String action : List.of(AuditAction.LOGIN, AuditAction.LOGOUT, AuditAction.REGISTER,
                AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE, AuditAction.QUERY,
                AuditAction.UPLOAD, AuditAction.AUDIT, AuditAction.RESET_PASSWORD,
                AuditAction.CHANGE_PASSWORD, AuditAction.CHANGE_ROLE, AuditAction.SET_SUPER_ADMIN,
                AuditAction.CHANGE_ABILITY, AuditAction.TOGGLE_STATUS, AuditAction.REVOKE,
                AuditAction.REVOKE_SESSION, AuditAction.BAN_IP, AuditAction.UNBAN_IP,
                AuditAction.UPDATE_CONFIG, AuditAction.RESET_CONFIG, AuditAction.MIGRATE,
                AuditAction.CLEANUP, AuditAction.EXPORT, AuditAction.OTHER)) {
            String label = AuditAction.label(action);
            assertNotEquals(action, label, "动作 " + action + " 缺少中文标签（会直接显示英文枚举值）");
        }
    }
}
