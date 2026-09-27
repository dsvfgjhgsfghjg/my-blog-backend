package com.leo.myblog.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.entity.AuthHistory;
import com.leo.myblog.service.AdminDashboardService;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.CryptoService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.util.AbilityValidator;
import com.leo.myblog.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备上线补推的单元测试
 * <p>
 * 回归目标：原本只有登录挑战会被补推（来源是 Redis 的待推送集合），敏感操作挑战
 * 完全依赖「发起时设备正好在线」，断线即丢失 —— 而手机在兜里时断线是常态。
 * 补推必须同时覆盖两类挑战。
 * <p>
 * 这里直接调包级可见的 {@code pushPendingChallenges}，并让 DH 会话为空，
 * 于是消息以明文路径发出，可以断言内容（无需真的做 DH 握手）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingChallengeRePushTest {

    private static final Long USER_ID = 1001L;
    private static final String DEVICE_ID = "dev-a";
    private static final long LOGIN_EXPIRES_AT = 1735689600L;

    @Mock
    private WebSocketSessionManager sessionManager;
    @Mock
    private AppAuthService appAuthService;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AdminDashboardService adminDashboardService;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private AbilityValidator abilityValidator;
    @Mock
    private LoginService loginService;

    private AuthWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthWebSocketHandler(
                sessionManager, appAuthService, cryptoService, jwtUtil, new ObjectMapper(),
                adminDashboardService, userCacheService, abilityValidator, loginService);

        when(sessionManager.getUserId(DEVICE_ID)).thenReturn(USER_ID);
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of());
        when(appAuthService.getPendingChallengeRecords(USER_ID)).thenReturn(List.of());
    }

    private WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-1");
        return session;
    }

    private AuthHistory sensitiveRecord(String challengeId, LocalDateTime requestedAt) {
        AuthHistory history = new AuthHistory();
        history.setUserId(USER_ID);
        history.setChallengeId(challengeId);
        history.setStatus("pending");
        history.setNumbers(new int[]{10, 20, 30});
        history.setCorrectNumber(30);
        history.setRequestedAt(requestedAt);
        return history;
    }

    @Test
    @DisplayName("补推同时覆盖登录挑战与敏感操作挑战")
    void rePushCoversBothChallengeKinds() throws Exception {
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of("login-1"));
        when(loginService.getLoginChallengeDetail("login-1")).thenReturn(
                new LoginService.LoginChallenge("login-1", USER_ID, new int[]{1, 2, 3}, 2, LOGIN_EXPIRES_AT));
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", LocalDateTime.now().minusSeconds(5))));

        WebSocketSession session = session();
        handler.pushPendingChallenges(session, DEVICE_ID);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(captor.capture());

        String all = captor.getAllValues().stream().map(TextMessage::getPayload)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(all.contains("\"challengeId\":\"login-1\""), "登录挑战必须被补推: " + all);
        assertTrue(all.contains("\"challengeId\":\"op-1\""), "敏感操作挑战必须被补推: " + all);
        assertTrue(all.contains("\"type\":\"challenge\""), all);
        // 补推的消息同样不能泄漏正确答案
        assertFalse(all.contains("correctNumber"), "补推消息不得包含正确答案: " + all);
    }

    @Test
    @DisplayName("敏感操作挑战补推的 expiresAt 由 requested_at 换算为 Unix 秒")
    void sensitiveRePushCarriesUnixSecondsExpiry() throws Exception {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 27, 5, 33, 14);
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", requestedAt)));

        WebSocketSession session = session();
        handler.pushPendingChallenges(session, DEVICE_ID);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(captor.capture());

        long expected = requestedAt.toEpochSecond(ZoneOffset.UTC) + AppAuthService.CHALLENGE_TIMEOUT_SECONDS;
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("\"expiresAt\":" + expected),
                "expiresAt 应为 requestedAt(UTC) + 60 的 Unix 秒，实际: " + payload);
        assertTrue(payload.contains("\"numbers\":[10,20,30]"), payload);
    }

    @Test
    @DisplayName("一条推送失败不影响其余挑战的补推")
    void oneFailedPushDoesNotBlockOthers() throws Exception {
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of("login-1"));
        when(loginService.getLoginChallengeDetail("login-1")).thenReturn(
                new LoginService.LoginChallenge("login-1", USER_ID, new int[]{1, 2, 3}, 2, LOGIN_EXPIRES_AT));
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", LocalDateTime.now())));

        WebSocketSession session = session();
        // 第一条（登录挑战）发送失败，第二条（敏感操作挑战）必须照发
        doThrow(new IOException("broken pipe")).doNothing().when(session).sendMessage(any(TextMessage.class));

        handler.pushPendingChallenges(session, DEVICE_ID);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"challengeId\":\"op-1\""),
                "前一条失败不应导致后续挑战漏推: " + captor.getValue().getPayload());
    }

    @Test
    @DisplayName("两类都没有时不发送任何消息")
    void nothingToPushSendsNothing() throws Exception {
        WebSocketSession session = session();

        handler.pushPendingChallenges(session, DEVICE_ID);

        verify(session, times(0)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("会话未绑定设备时直接返回，不做任何查询")
    void unknownDeviceDoesNothing() throws Exception {
        when(sessionManager.getUserId("dev-x")).thenReturn(null);
        WebSocketSession session = session();

        handler.pushPendingChallenges(session, "dev-x");

        verify(session, times(0)).sendMessage(any(TextMessage.class));
    }
}
