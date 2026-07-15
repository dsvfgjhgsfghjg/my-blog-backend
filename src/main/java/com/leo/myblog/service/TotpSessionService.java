package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * TOTP 会话管理服务
 * 用于管理双因素认证的会话状态，避免频繁验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpSessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis Key 前缀
    private static final String TOTP_SESSION_PREFIX = "totp:session:";
    
    // TOTP 会话有效期：5 分钟
    private static final long TOTP_SESSION_TIMEOUT_MINUTES = 5;
    
    /**
     * 创建 TOTP 会话（验证成功后调用）
     * @param userId 用户ID
     */
    public void createSession(Long userId) {
        String key = TOTP_SESSION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "valid", TOTP_SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        log.debug("创建 TOTP 会话: userId={}, 有效期={}分钟", userId, TOTP_SESSION_TIMEOUT_MINUTES);
    }
    
    /**
     * 验证 TOTP 会话是否有效
     * @param userId 用户ID
     * @return true=会话有效，无需再次验证；false=需要重新验证
     */
    public boolean isSessionValid(Long userId) {
        String key = TOTP_SESSION_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    
    /**
     * 清除 TOTP 会话
     * @param userId 用户ID
     */
    public void clearSession(Long userId) {
        String key = TOTP_SESSION_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("清除 TOTP 会话: userId={}", userId);
    }
    
    /**
     * 刷新 TOTP 会话（延长有效期）
     * @param userId 用户ID
     */
    public void refreshSession(Long userId) {
        if (isSessionValid(userId)) {
            createSession(userId);
            log.debug("刷新 TOTP 会话: userId={}", userId);
        }
    }
}
