package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    
    /**
     * 将 Token 加入黑名单
     * @param userId 用户ID
     * @param token JWT Token
     * @param expirationSeconds Token 剩余有效期（秒）
     */
    public void addToBlacklist(Long userId, String token, long expirationSeconds) {
        String key = BLACKLIST_PREFIX + userId + ":" + token.hashCode();
        try {
            redisTemplate.opsForValue().set(key, "blacklisted", expirationSeconds, TimeUnit.SECONDS);
            log.info("Token已加入黑名单: userId={}, 过期时间={}秒", userId, expirationSeconds);
        } catch (Exception e) {
            log.error("加入Token黑名单失败: userId={}", userId, e);
        }
    }

    /**
     * 根据Token Hash直接将Token加入黑名单（用于无法获取完整Token的场景，如会话管理）
     * @param userId 用户ID
     * @param tokenHash Token的哈希值（Java hashCode字符串）
     * @param expirationSeconds Token 剩余有效期（秒）
     */
    public void addToBlacklistByHash(Long userId, String tokenHash, long expirationSeconds) {
        String key = BLACKLIST_PREFIX + userId + ":" + tokenHash;
        try {
            redisTemplate.opsForValue().set(key, "blacklisted", expirationSeconds, TimeUnit.SECONDS);
            log.info("Token已通过Hash加入黑名单: userId={}, tokenHash={}, 过期时间={}秒", userId, tokenHash, expirationSeconds);
        } catch (Exception e) {
            log.error("通过Hash加入Token黑名单失败: userId={}, tokenHash={}", userId, tokenHash, e);
        }
    }
    
    /**
     * 检查 Token 是否在黑名单中
     * @param userId 用户ID
     * @param token JWT Token
     * @return true-在黑名单中，false-不在黑名单中
     */
    public boolean isBlacklisted(Long userId, String token) {
        String key = BLACKLIST_PREFIX + userId + ":" + token.hashCode();
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查Token黑名单失败: userId={}", userId, e);
            return false;
        }
    }
    
    /**
     * 使指定用户的所有 Token 失效
     * @param userId 用户ID
     */
    public void invalidateAllTokens(Long userId) {
        // 注意：这里只是逻辑上的"所有Token"，实际上我们使用 userId 作为 Key 的一部分
        // 如果需要更精确的控制，可以存储用户当前有效的 token 列表
        String pattern = BLACKLIST_PREFIX + userId + ":*";
        try {
            redisTemplate.delete(
                redisTemplate.keys(pattern) != null ? 
                redisTemplate.keys(pattern) : 
                java.util.Collections.emptySet()
            );
            log.info("用户 {} 的所有Token已失效", userId);
        } catch (Exception e) {
            log.error("使用户Token失效失败: userId={}", userId, e);
        }
    }
}
