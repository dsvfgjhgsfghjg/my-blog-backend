package com.leo.myblog.aspect.audit;

import com.leo.myblog.common.AuditAction;
import com.leo.myblog.enums.AuditLevel;
import com.leo.myblog.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审计等级判定测试
 */
public class AuditLevelResolverTest {

    @Test
    @DisplayName("基础等级：只看 → 信息，改东西 → 记录，毁数据/关防护 → 警告，动账号权限 → 关键")
    void baseLevels() {
        Map<String, AuditLevel> expected = Map.ofEntries(
                Map.entry(AuditAction.QUERY, AuditLevel.INFO),
                Map.entry(AuditAction.EXPORT, AuditLevel.INFO),
                Map.entry(AuditAction.LOGOUT, AuditLevel.INFO),
                Map.entry(AuditAction.CREATE, AuditLevel.NOTICE),
                Map.entry(AuditAction.UPDATE, AuditLevel.NOTICE),
                Map.entry(AuditAction.LOGIN, AuditLevel.NOTICE),
                Map.entry(AuditAction.AUDIT, AuditLevel.NOTICE),
                Map.entry(AuditAction.DELETE, AuditLevel.WARNING),
                Map.entry(AuditAction.TOGGLE_STATUS, AuditLevel.WARNING),
                Map.entry(AuditAction.CLEANUP, AuditLevel.WARNING),
                Map.entry(AuditAction.UNBAN_IP, AuditLevel.WARNING),
                // 改配置与重置配置同级：配置里有 TOTP 开关之类安全项，改错就等于关防护
                Map.entry(AuditAction.UPDATE_CONFIG, AuditLevel.WARNING),
                Map.entry(AuditAction.RESET_CONFIG, AuditLevel.WARNING),
                Map.entry(AuditAction.RESET_PASSWORD, AuditLevel.CRITICAL),
                Map.entry(AuditAction.CHANGE_ROLE, AuditLevel.CRITICAL),
                Map.entry(AuditAction.SET_SUPER_ADMIN, AuditLevel.CRITICAL),
                Map.entry(AuditAction.BAN_IP, AuditLevel.CRITICAL));

        expected.forEach((action, level) ->
                assertEquals(level, AuditLevelResolver.baseOf(action), "动作 " + action + " 的基础等级不符"));
    }

    @Test
    @DisplayName("未登记的动作回退 NOTICE —— 新操作默认被当成有副作用，而不是只是看了看")
    void unknownActionFallsBackToNotice() {
        assertEquals(AuditLevel.NOTICE, AuditLevelResolver.baseOf(AuditAction.OTHER));
        assertEquals(AuditLevel.NOTICE, AuditLevelResolver.baseOf(null));
        assertEquals(AuditLevel.NOTICE, AuditLevelResolver.baseOf("SOME_NEW_ACTION"));
    }

    @Test
    @DisplayName("失败升级矩阵：任何失败至少警告，系统异常升到错误，关键不回退")
    void escalationMatrix() {
        BusinessException business = new BusinessException("业务拒绝");
        RuntimeException system = new RuntimeException("炸了");

        // 信息
        assertEquals(AuditLevel.INFO, eff(AuditLevel.INFO, true, null));
        assertEquals(AuditLevel.WARNING, eff(AuditLevel.INFO, false, business));
        assertEquals(AuditLevel.ERROR, eff(AuditLevel.INFO, false, system));

        // 记录
        assertEquals(AuditLevel.NOTICE, eff(AuditLevel.NOTICE, true, null));
        assertEquals(AuditLevel.WARNING, eff(AuditLevel.NOTICE, false, business));
        assertEquals(AuditLevel.ERROR, eff(AuditLevel.NOTICE, false, system));

        // 警告
        assertEquals(AuditLevel.WARNING, eff(AuditLevel.WARNING, true, null));
        assertEquals(AuditLevel.ERROR, eff(AuditLevel.WARNING, false, business));
        assertEquals(AuditLevel.ERROR, eff(AuditLevel.WARNING, false, system));

        // 关键：失败也不回退，权限变更失败同样是最高优先级
        assertEquals(AuditLevel.CRITICAL, eff(AuditLevel.CRITICAL, true, null));
        assertEquals(AuditLevel.CRITICAL, eff(AuditLevel.CRITICAL, false, business));
        assertEquals(AuditLevel.CRITICAL, eff(AuditLevel.CRITICAL, false, system));
    }

    @Test
    @DisplayName("枚举自身的升级与比较语义")
    void enumSemantics() {
        assertEquals(AuditLevel.NOTICE, AuditLevel.INFO.escalate());
        assertEquals(AuditLevel.CRITICAL, AuditLevel.CRITICAL.escalate(), "关键已封顶，不能再升");
        assertEquals(AuditLevel.WARNING, AuditLevel.NOTICE.max(AuditLevel.WARNING));
        assertEquals(AuditLevel.NOTICE, AuditLevel.NOTICE.max(AuditLevel.INFO));
        assertEquals(AuditLevel.CRITICAL, AuditLevel.CRITICAL.max(AuditLevel.INFO));

        assertEquals(AuditLevel.WARNING, AuditLevel.from("warning"));
        assertEquals(AuditLevel.NOTICE, AuditLevel.from(null));
        assertEquals(AuditLevel.NOTICE, AuditLevel.from("不存在的等级"));
        assertEquals(AuditLevel.CRITICAL, AuditLevel.from(" CRITICAL "));
    }

    @Test
    @DisplayName("严格校验：筛选用它，避免 WARN 这类拼错的值被静默当成 NOTICE")
    void strictValidation() {
        assertEquals(true, AuditLevel.isValid("WARNING"));
        assertEquals(true, AuditLevel.isValid("warning"));
        assertEquals(true, AuditLevel.isValid(" CRITICAL "));

        assertEquals(false, AuditLevel.isValid("WARN"), "缩写不是合法值");
        assertEquals(true, AuditLevel.isValid("INFO "), "首尾空格会被 trim，仍算合法");
        assertEquals(false, AuditLevel.isValid(""));
        assertEquals(false, AuditLevel.isValid(null));
        assertEquals(false, AuditLevel.isValid("严重"));
    }

    @Test
    @DisplayName("五个等级都有中文说明，前端可直接用")
    void everyLevelHasChineseLabel() {
        for (AuditLevel level : AuditLevel.values()) {
            assertEquals(true, level.getDescription() != null && !level.getDescription().isBlank(),
                    level + " 缺少中文说明");
        }
        assertEquals("信息", AuditLevel.INFO.getDescription());
        assertEquals("记录", AuditLevel.NOTICE.getDescription());
        assertEquals("警告", AuditLevel.WARNING.getDescription());
        assertEquals("错误", AuditLevel.ERROR.getDescription());
        assertEquals("关键", AuditLevel.CRITICAL.getDescription());
    }

    private AuditLevel eff(AuditLevel base, boolean success, Throwable error) {
        return AuditLevelResolver.effective(base, success, error);
    }
}
