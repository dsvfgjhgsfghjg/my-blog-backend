package com.leo.myblog.websocket;

import com.leo.myblog.service.AdminDashboardService;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.CryptoService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.util.AbilityValidator;
import com.leo.myblog.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 挑战应答路由与登录挑战推送消息的单元测试
 * <p>
 * 覆盖两个此前让认证器登录链路完全跑不通的缺陷：
 * 1. 应答一律被送到敏感操作验证器（命名空间不匹配），登录挑战永远验不过；
 * 2. 推送给 App 的数字是硬编码占位的 0，且消息里可能混入正确答案。
 * <p>
 * 这两个方法都不依赖 Redis / WebSocket 传输，因此用 Mockito 直接断言即可，
 * 无需启动 Spring 上下文。
 */
class ChallengeRoutingTest {

    private LoginService loginService;
    private AppAuthService appAuthService;
    private AuthWebSocketHandler handler;

    private static final Long USER_ID = 42L;
    private static final String LOGIN_CHALLENGE_ID = "login-challenge-001";
    private static final String OP_CHALLENGE_ID = "sensitive-op-challenge-001";

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        appAuthService = mock(AppAuthService.class);

        handler = new AuthWebSocketHandler(
                mock(WebSocketSessionManager.class),
                appAuthService,
                mock(CryptoService.class),
                mock(JwtUtil.class),
                new ObjectMapper(),
                mock(AdminDashboardService.class),
                mock(UserCacheService.class),
                mock(AbilityValidator.class),
                loginService
        );
    }

    // ==================== 命名空间路由 ====================

    @Test
    @DisplayName("登录挑战的应答应交给 LoginService 验证，且不写 auth_history")
    void loginChallengeRoutedToLoginService() {
        LoginService.LoginChallenge detail =
                new LoginService.LoginChallenge(LOGIN_CHALLENGE_ID, USER_ID, new int[]{12, 45, 78}, 45, 0L);
        when(loginService.getLoginChallengeDetail(LOGIN_CHALLENGE_ID)).thenReturn(detail);
        when(loginService.verifyLoginChallenge(LOGIN_CHALLENGE_ID, USER_ID, 45))
                .thenReturn(LoginService.LoginChallengeResult.approved());

        AuthWebSocketHandler.ChallengeOutcome outcome =
                handler.resolveChallenge(USER_ID, LOGIN_CHALLENGE_ID, 45);

        assertTrue(outcome.isLoginChallenge(), "应被识别为登录挑战");
        assertTrue(outcome.isApproved());

        // 关键：不能再走到敏感操作验证器上
        verify(appAuthService, never()).verifyChallenge(anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("敏感操作挑战的应答仍走 AppAuthService，不受登录链路影响")
    void sensitiveOperationChallengeStillRoutedToAppAuthService() {
        // 登录命名空间查不到 → 回落到敏感操作
        when(loginService.getLoginChallengeDetail(OP_CHALLENGE_ID)).thenReturn(null);
        when(appAuthService.verifyChallenge(eq(OP_CHALLENGE_ID), eq(30), eq(USER_ID)))
                .thenReturn(AppAuthService.ChallengeResult.approved());

        AuthWebSocketHandler.ChallengeOutcome outcome =
                handler.resolveChallenge(USER_ID, OP_CHALLENGE_ID, 30);

        assertFalse(outcome.isLoginChallenge(), "不应被识别为登录挑战");
        assertTrue(outcome.isApproved());

        verify(loginService, never()).verifyLoginChallenge(anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("登录挑战选错数字时应返回失败而非通过")
    void wrongNumberOnLoginChallengeFails() {
        LoginService.LoginChallenge detail =
                new LoginService.LoginChallenge(LOGIN_CHALLENGE_ID, USER_ID, new int[]{12, 45, 78}, 45, 0L);
        when(loginService.getLoginChallengeDetail(LOGIN_CHALLENGE_ID)).thenReturn(detail);
        when(loginService.verifyLoginChallenge(LOGIN_CHALLENGE_ID, USER_ID, 12))
                .thenReturn(LoginService.LoginChallengeResult.failed("数字选择错误"));

        AuthWebSocketHandler.ChallengeOutcome outcome =
                handler.resolveChallenge(USER_ID, LOGIN_CHALLENGE_ID, 12);

        assertTrue(outcome.isLoginChallenge());
        assertFalse(outcome.isApproved());
        assertFalse(outcome.isExpired());
        assertEquals("数字选择错误", outcome.getMessage());
    }

    // ==================== 推送消息内容 ====================

    @Test
    @DisplayName("推送的登录挑战必须带真实数字，不能是占位的 0")
    void pushedLoginChallengeCarriesRealNumbers() {
        LoginService.LoginChallenge challenge =
                new LoginService.LoginChallenge("cid", USER_ID, new int[]{12, 45, 78}, 45, 1735689600L);

        String msg = handler.buildLoginChallengeMessage(challenge);

        assertTrue(msg.contains("\"numbers\":[12,45,78]"),
                "推送消息必须包含真实的 3 个候选数字，实际为: " + msg);
        assertTrue(msg.contains("\"challengeId\":\"cid\""));
    }

    @Test
    @DisplayName("推送消息绝不能包含正确答案（否则数字匹配退化为盲猜）")
    void pushedLoginChallengeMustNotLeakCorrectNumber() {
        // 正确答案 78 同时也是候选之一，用它来检测是否被单独泄漏
        LoginService.LoginChallenge challenge =
                new LoginService.LoginChallenge("cid", USER_ID, new int[]{12, 45, 78}, 78, 1735689600L);

        String msg = handler.buildLoginChallengeMessage(challenge);

        assertFalse(msg.contains("correctNumber"), "推送消息不得包含 correctNumber 字段: " + msg);
        assertEquals(1, countOccurrences(msg, "78"),
                "正确答案只能作为候选数字出现一次，不得额外下发: " + msg);
    }

    @Test
    @DisplayName("expiresAt 必须是 Unix 秒（毫秒会让 App 一打开就显示已过期）")
    void pushedChallengeExpiresAtIsUnixSeconds() {
        long unixSeconds = 1735689600L;
        LoginService.LoginChallenge challenge =
                new LoginService.LoginChallenge("cid", USER_ID, new int[]{1, 2, 3}, 1, unixSeconds);

        String msg = handler.buildLoginChallengeMessage(challenge);

        assertTrue(msg.contains("\"expiresAt\":" + unixSeconds),
                "expiresAt 应为 Unix 秒，实际为: " + msg);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
