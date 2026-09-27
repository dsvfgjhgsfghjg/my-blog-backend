package com.leo.myblog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.response.PendingChallengeResponse;
import com.leo.myblog.entity.AuthHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 待处理挑战聚合查询的单元测试
 * <p>
 * 覆盖两件事：
 * 1. 两类挑战（两步登录 + 敏感操作）能被一次查询合并返回，并按时间倒序；
 * 2. <b>响应里绝不能出现 correctNumber</b> —— App 只该拿到 3 个候选数字，
 *    正确答案由发起操作的那个浏览器展示。数字匹配的安全性建立在
 *    「攻击者无法把数字转达给受害者」之上，下发答案会让它退化成 1/3 概率的盲猜。
 * <p>
 * 纯 Mockito，不依赖 Redis / PostgreSQL。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingChallengeServiceTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private AppAuthService appAuthService;
    @Mock
    private LoginService loginService;

    @InjectMocks
    private PendingChallengeService pendingChallengeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(loginService.getUserPendingLoginChallengeIds(anyLong())).thenReturn(List.of());
        when(appAuthService.getPendingChallengeRecords(anyLong())).thenReturn(List.of());
    }

    private AuthHistory sensitiveRecord(String challengeId, LocalDateTime requestedAt) {
        AuthHistory history = new AuthHistory();
        history.setUserId(USER_ID);
        history.setChallengeId(challengeId);
        history.setStatus("pending");
        history.setNumbers(new int[]{10, 20, 30});
        // 正确答案就藏在候选里，用来检测是否被单独泄漏
        history.setCorrectNumber(30);
        history.setRequestedAt(requestedAt);
        return history;
    }

    @Test
    @DisplayName("登录与敏感操作两类挑战合并返回，按过期时间倒序")
    void mergesBothKindsAndSortsByExpiryDesc() {
        // 敏感操作挑战：发起于 30 秒前（先过期）
        LocalDateTime sensitiveRequestedAt = LocalDateTime.now().minusSeconds(30);
        long sensitiveExpiresAt = AppAuthService.challengeExpiresAtSeconds(sensitiveRequestedAt);
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", sensitiveRequestedAt)));
        // 登录挑战：比它晚 30 秒过期（后过期）→ 应排在前
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of("login-1"));
        when(loginService.getLoginChallengeDetail("login-1")).thenReturn(
                new LoginService.LoginChallenge("login-1", USER_ID, new int[]{1, 2, 3}, 2, sensitiveExpiresAt + 30));

        List<PendingChallengeResponse> result = pendingChallengeService.getPendingChallenges(USER_ID);

        assertEquals(2, result.size());
        assertEquals("login", result.get(0).getPurpose(), "较晚过期的登录挑战应排在前");
        assertEquals("login-1", result.get(0).getChallengeId());
        assertEquals("sensitive_operation", result.get(1).getPurpose());
        assertEquals("op-1", result.get(1).getChallengeId());
        assertTrue(result.get(0).getExpiresAt() > result.get(1).getExpiresAt());
    }

    @Test
    @DisplayName("只有敏感操作挑战时也能返回（设备不在线场景的主要来源）")
    void sensitiveOnlyIsReturned() {
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", LocalDateTime.now())));

        List<PendingChallengeResponse> result = pendingChallengeService.getPendingChallenges(USER_ID);

        assertEquals(1, result.size());
        assertEquals(PendingChallengeResponse.PURPOSE_SENSITIVE_OPERATION, result.get(0).getPurpose());
        assertEquals(List.of(10, 20, 30), toList(result.get(0).getNumbers()));
    }

    @Test
    @DisplayName("敏感操作挑战的 expiresAt 由 requested_at 推算为 Unix 秒（+60s）")
    void sensitiveChallengeExpiresAtDerivedFromRequestedAt() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 27, 5, 33, 14);
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", requestedAt)));

        List<PendingChallengeResponse> result = pendingChallengeService.getPendingChallenges(USER_ID);

        long expected = requestedAt.toEpochSecond(ZoneOffset.UTC) + AppAuthService.CHALLENGE_TIMEOUT_SECONDS;
        assertEquals(expected, result.get(0).getExpiresAt());
    }

    @Test
    @DisplayName("登录挑战详情缺失（已过期/已应答）时跳过，不返回半截数据")
    void missingLoginChallengeDetailIsSkipped() {
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of("gone"));
        when(loginService.getLoginChallengeDetail("gone")).thenReturn(null);

        assertTrue(pendingChallengeService.getPendingChallenges(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("★ 响应序列化后绝不能含 correctNumber（App 只能拿 3 个候选）")
    void responseMustNeverLeakCorrectNumber() throws Exception {
        when(appAuthService.getPendingChallengeRecords(USER_ID))
                .thenReturn(List.of(sensitiveRecord("op-1", LocalDateTime.now())));
        when(loginService.getUserPendingLoginChallengeIds(USER_ID)).thenReturn(List.of("login-1"));
        when(loginService.getLoginChallengeDetail("login-1")).thenReturn(
                new LoginService.LoginChallenge("login-1", USER_ID, new int[]{1, 2, 3}, 2, 0L));

        String json = objectMapper.writeValueAsString(
                pendingChallengeService.getPendingChallenges(USER_ID));

        assertFalse(json.contains("correctNumber"), "响应不得包含 correctNumber 字段: " + json);
        assertFalse(json.contains("correct_number"), "响应不得包含 correct_number 字段: " + json);
        assertFalse(json.contains("\"correct\""), "响应不得包含 correct 字段: " + json);
        // 候选数字与目的仍然必须下发
        assertTrue(json.contains("\"numbers\""), json);
        assertTrue(json.contains("\"purpose\""), json);
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new java.util.ArrayList<>();
        for (int v : values) {
            list.add(v);
        }
        return list;
    }
}
