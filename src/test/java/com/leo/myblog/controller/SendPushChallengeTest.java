package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.SendPushVerifyResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.SystemConfigService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.service.TotpSessionService;
import com.leo.myblog.websocket.AuthWebSocketHandler;
import com.leo.myblog.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 敏感操作「发送推送挑战」的单元测试
 * <p>
 * 回归目标：原实现只在**有在线 WebSocket 会话**时才生成挑战，没有设备就
 * {@code Result.error(400, "没有在线设备可推送")}；而且对每台在线设备各调一次
 * {@code pushChallengeToUser}，后者每次都重新 {@code generateChallenge} ——
 * N 台设备得到 N 个数字不同的挑战，页面只展示第一个的数字、轮询也只认第一个。
 * <p>
 * 手机在兜里时 socket 大概率已断，所以「没有在线设备」是常态而非边缘情况：
 * 挑战必须无条件生成并落库，推送只是尽力而为。
 * <p>
 * 纯 Mockito，不需要 Redis / PostgreSQL / WebSocket 传输。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendPushChallengeTest {

    private static final Long USER_ID = 1001L;
    private static final String CHALLENGE_ID = "challenge-abc";
    private static final int[] NUMBERS = {12, 45, 78};
    private static final int CORRECT_NUMBER = 45;
    private static final long EXPIRES_AT = 1735689600L;

    @Mock
    private LoginService loginService;
    @Mock
    private TotpService totpService;
    @Mock
    private TotpSessionService totpSessionService;
    @Mock
    private AppAuthService appAuthService;
    @Mock
    private WebSocketSessionManager webSocketSessionManager;
    @Mock
    private AuthWebSocketHandler authWebSocketHandler;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private VerificationController controller;

    @BeforeEach
    void setUp() {
        when(appAuthService.hasBoundDevice(USER_ID)).thenReturn(true);
        when(appAuthService.generateChallenge(USER_ID)).thenReturn(challengeInfo());
    }

    private AppAuthService.ChallengeInfo challengeInfo() {
        return new AppAuthService.ChallengeInfo(CHALLENGE_ID, NUMBERS, CORRECT_NUMBER, EXPIRES_AT);
    }

    private AppDevice device(String deviceId, String deviceName) {
        AppDevice device = new AppDevice();
        device.setDeviceId(deviceId);
        device.setDeviceName(deviceName);
        return device;
    }

    @Test
    @DisplayName("没有在线设备时不再报错：仍生成并落库挑战，状态为 queued")
    void noOnlineDeviceStillCreatesChallenge() throws Exception {
        when(webSocketSessionManager.getUserSessions(USER_ID)).thenReturn(Map.of());

        Result<SendPushVerifyResponse> result = controller.sendPushChallenge(USER_ID, Map.of());

        assertEquals(200, result.getCode(), "无在线设备不应是错误响应");
        SendPushVerifyResponse data = result.getData();
        assertNotNull(data);
        assertEquals("queued", data.getStatus());
        assertTrue(data.getDeviceNames().isEmpty(), "queued 时 deviceNames 应为空数组");
        assertEquals(CHALLENGE_ID, data.getChallengeId());
        assertEquals(EXPIRES_AT, data.getExpiresAt());
        // correctNumber 必须回给浏览器：数字匹配靠页面展示它
        assertEquals(CORRECT_NUMBER, data.getCorrectNumber());

        // 挑战必须无条件生成并落库，否则设备稍后上线时无从补推
        verify(appAuthService, times(1)).generateChallenge(USER_ID);
        ArgumentCaptor<int[]> numbersCaptor = ArgumentCaptor.forClass(int[].class);
        verify(appAuthService, times(1)).recordChallenge(
                eq(USER_ID), eq(CHALLENGE_ID), numbersCaptor.capture(),
                eq(CORRECT_NUMBER), isNull(), isNull());
        assertEquals(List.of(12, 45, 78), toList(numbersCaptor.getValue()));

        verify(authWebSocketHandler, never()).pushChallenge(any(), any());
    }

    @Test
    @DisplayName("多台在线设备只生成一个挑战，所有设备收到同一个 challengeId 与同一组数字")
    void multipleDevicesShareOneChallenge() throws Exception {
        WebSocketSession sessionA = org.mockito.Mockito.mock(WebSocketSession.class);
        WebSocketSession sessionB = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, WebSocketSession> sessions = new HashMap<>();
        sessions.put("dev-a", sessionA);
        sessions.put("dev-b", sessionB);
        when(webSocketSessionManager.getUserSessions(USER_ID)).thenReturn(sessions);
        when(appAuthService.getUserDevices(USER_ID))
                .thenReturn(List.of(device("dev-a", "手机A"), device("dev-b", "平板B")));

        Result<SendPushVerifyResponse> result = controller.sendPushChallenge(USER_ID, Map.of());

        // 关键：只生成一次。改前是每台设备各生成一次，数字互不相同
        verify(appAuthService, times(1)).generateChallenge(USER_ID);
        verify(appAuthService, times(1)).recordChallenge(
                anyLong(), anyString(), any(int[].class), any(Integer.class), isNull(), isNull());

        ArgumentCaptor<AppAuthService.ChallengeInfo> challengeCaptor =
                ArgumentCaptor.forClass(AppAuthService.ChallengeInfo.class);
        verify(authWebSocketHandler, times(2)).pushChallenge(any(), challengeCaptor.capture());

        Set<String> pushedIds = challengeCaptor.getAllValues().stream()
                .map(AppAuthService.ChallengeInfo::getChallengeId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(CHALLENGE_ID), pushedIds, "所有设备必须收到同一个 challengeId");

        Set<String> pushedNumbers = challengeCaptor.getAllValues().stream()
                .map(c -> java.util.Arrays.toString(c.getNumbers()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("[12, 45, 78]"), pushedNumbers, "所有设备必须收到同一组候选数字");

        SendPushVerifyResponse data = result.getData();
        assertEquals("pushed", data.getStatus());
        // HashMap 迭代顺序不保证，按集合比较
        assertEquals(Set.of("手机A", "平板B"), Set.copyOf(data.getDeviceNames()));
    }

    @Test
    @DisplayName("推送全部失败时回落为 queued，而不是报错")
    void allPushesFailingFallsBackToQueued() throws Exception {
        WebSocketSession session = org.mockito.Mockito.mock(WebSocketSession.class);
        when(webSocketSessionManager.getUserSessions(USER_ID)).thenReturn(Map.of("dev-a", session));
        when(appAuthService.getUserDevices(USER_ID)).thenReturn(List.of(device("dev-a", "手机A")));
        doThrow(new IOException("connection closed"))
                .when(authWebSocketHandler).pushChallenge(any(), any());

        Result<SendPushVerifyResponse> result = controller.sendPushChallenge(USER_ID, Map.of());

        assertEquals(200, result.getCode());
        assertEquals("queued", result.getData().getStatus());
        assertEquals(CHALLENGE_ID, result.getData().getChallengeId());
    }

    @Test
    @DisplayName("没有绑定设备时才报错（这是真正的不可用状态）")
    void noBoundDeviceIsStillAnError() {
        when(appAuthService.hasBoundDevice(USER_ID)).thenReturn(false);

        Result<SendPushVerifyResponse> result = controller.sendPushChallenge(USER_ID, Map.of());

        assertEquals(400, result.getCode());
        verify(appAuthService, never()).generateChallenge(anyLong());
    }

    @Test
    @DisplayName("生成新挑战前先作废旧挑战（单活跃挑战）")
    void previousChallengesAreInvalidated() {
        when(webSocketSessionManager.getUserSessions(USER_ID)).thenReturn(Map.of());

        controller.sendPushChallenge(USER_ID, Map.of());

        verify(appAuthService, times(1)).invalidateUserChallenges(USER_ID);
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>();
        for (int v : values) {
            list.add(v);
        }
        return list;
    }
}
