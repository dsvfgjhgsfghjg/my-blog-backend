package com.leo.myblog.service;

import com.leo.myblog.dto.response.PendingChallengeResponse;
import com.leo.myblog.entity.AuthHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待处理挑战的统一查询服务
 * <p>
 * 两类挑战此前各有一套存储与查询方式，App 端也因此没法用一次请求拿到全部待办：
 * <ul>
 *   <li><b>两步登录</b>：Redis Hash（{@code app:auth:login_challenge:}）+ 待推送集合，
 *       由 {@link LoginService} 维护；</li>
 *   <li><b>敏感操作</b>：{@code t_auth_history} 中 status='pending' 且在时效窗口内的记录，
 *       由 {@link AppAuthService} 维护。</li>
 * </ul>
 * 本服务只做聚合与排序，不改动任何一侧的存储。
 * <p>
 * 注意：本服务同时依赖 {@code LoginService} 与 {@code AppAuthService}，
 * 而 {@code AppAuthService} 并不反向依赖 {@code LoginService}（那样会形成循环依赖），
 * 所以聚合放在这里，而不是塞进任何一侧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingChallengeService {

    private final AppAuthService appAuthService;
    private final LoginService loginService;

    /**
     * 查询用户的全部待处理挑战（登录 + 敏感操作），按过期时间倒序
     * <p>
     * 返回结构里<b>不含 correctNumber</b>（见 {@link PendingChallengeResponse} 的类注释）。
     *
     * @param userId 用户 ID
     * @return 待处理挑战列表；无则返回空列表
     */
    public List<PendingChallengeResponse> getPendingChallenges(Long userId) {
        List<PendingChallengeResponse> challenges = new ArrayList<>();

        // 1. 敏感操作挑战：数据库是权威数据源
        for (AuthHistory history : appAuthService.getPendingChallengeRecords(userId)) {
            if (history.getChallengeId() == null) {
                continue;
            }
            challenges.add(new PendingChallengeResponse(
                    history.getChallengeId(),
                    PendingChallengeResponse.PURPOSE_SENSITIVE_OPERATION,
                    history.getNumbers(),
                    AppAuthService.challengeExpiresAtSeconds(history.getRequestedAt())));
        }

        // 2. 两步登录挑战：Redis 是权威数据源，集合里可能残留已过期/已应答的成员，
        //    getUserPendingLoginChallengeIds 已按结果状态过滤，这里再挡一次详情缺失
        for (String challengeId : loginService.getUserPendingLoginChallengeIds(userId)) {
            LoginService.LoginChallenge detail = loginService.getLoginChallengeDetail(challengeId);
            if (detail == null) {
                continue;
            }
            challenges.add(new PendingChallengeResponse(
                    detail.getChallengeId(),
                    PendingChallengeResponse.PURPOSE_LOGIN,
                    detail.getNumbers(),
                    detail.getExpiresAt()));
        }

        // 按过期时间倒序（同一时刻生成的挑战 TTL 相同，等价于「按发起时间倒序」）
        challenges.sort(Comparator.comparingLong(
                (PendingChallengeResponse c) -> c.getExpiresAt() != null ? c.getExpiresAt() : 0L).reversed());

        log.debug("查询待处理挑战: userId={}, count={}", userId, challenges.size());
        return challenges;
    }
}
