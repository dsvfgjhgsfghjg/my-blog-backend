package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.leo.myblog.entity.OAuth2Authorization;
import com.leo.myblog.mapper.OAuth2AuthorizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 「把某个用户的所有登录态清掉」的编排服务
 * <p>
 * 用在重置密码与用户自助改密码之后 —— 这两件事的动机通常就是「账号可能不安全」，
 * 此时必须让已有的登录态全部失效，否则密码改了、攻击者手上的 token 还能用。
 * <p>
 * 为什么要单独一个类：要清的东西分属两个存储，而能操作它们的两个服务之间存在依赖方向
 * 相反的关系 —— {@code OAuth2TokenService} 依赖 {@code UserService}，
 * 所以 {@code UserService} 不能再反向依赖 {@code OAuth2TokenService}（Spring Boot 默认
 * 禁止循环引用）。这里只依赖 Mapper 与 {@code TokenSessionService}，方向是单向的。
 * <p>
 * 覆盖的两块：
 * <ol>
 *   <li><b>refresh token</b>（{@code oauth2_authorizations} 表）—— 这一块最容易漏。
 *       只吊销 access token 的话，攻击者拿手里的 refresh token 换一个新的就绕过去了。
 *       刷新接口 {@code handleRefreshTokenGrant} 会校验 {@code is_revoked = false}，
 *       所以把这些行置为已撤销确实能掐断刷新。</li>
 *   <li><b>Redis 登录态</b>（{@code TokenSessionService}）—— 会话哈希 + 黑名单。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final TokenSessionService tokenSessionService;
    private final OAuth2AuthorizationMapper oAuth2AuthorizationMapper;

    /**
     * 吊销该用户的全部登录态（access token 与 refresh token 一并作废）
     *
     * @param userId 用户ID
     * @param reason 触发原因，仅用于日志（如 "重置密码" / "用户改密码"）
     * @return 被下线的 Redis 会话数
     */
    @Transactional(rollbackFor = Exception.class)
    public int revokeAll(Long userId, String reason) {
        if (userId == null) {
            log.warn("吊销登录态时 userId 为空，跳过: reason={}", reason);
            return 0;
        }

        // 1) 作废 OAuth2 授权记录 —— 这一步负责 refresh token
        LambdaUpdateWrapper<OAuth2Authorization> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(OAuth2Authorization::getUserId, userId)
               .eq(OAuth2Authorization::getIsRevoked, false)
               .set(OAuth2Authorization::getIsRevoked, true)
               .set(OAuth2Authorization::getRevokedAt, LocalDateTime.now());
        int revokedAuthorizations = oAuth2AuthorizationMapper.update(null, wrapper);

        // 2) 清 Redis 会话并加入黑名单
        int revokedSessions = tokenSessionService.revokeAllSessions(userId);

        log.info("已吊销用户全部登录态: userId={}, reason={}, 作废授权记录={}, 下线会话={}",
                userId, reason, revokedAuthorizations, revokedSessions);
        return revokedSessions;
    }
}
