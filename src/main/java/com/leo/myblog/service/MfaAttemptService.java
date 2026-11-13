package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Shared brute-force guard for TOTP and backup-code verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaAttemptService {

    private static final String FAILURE_PREFIX = "mfa:failures:";
    private static final String LOCK_PREFIX = "mfa:lock:";
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MINUTES = 5;
    private static final long LOCK_MINUTES = 15;

    private final StringRedisTemplate redisTemplate;
    private final SecurityMetricsService securityMetricsService;

    public void assertAllowed(Long userId) {
        String lockKey = LOCK_PREFIX + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            long remaining = remainingSeconds(lockKey);
            throw new com.leo.myblog.exception.BusinessException(
                    429, "验证失败次数过多，请 " + Math.max(remaining, 1) + " 秒后重试");
        }
    }

    public void recordFailure(Long userId) {
        String failureKey = FAILURE_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(failureKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failureKey, WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_FAILURES) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + userId, "locked", LOCK_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(failureKey);
            securityMetricsService.mfaLocked();
            log.warn("MFA 验证失败达到阈值并锁定: userId={}", userId);
        }
    }

    public void recordSuccess(Long userId) {
        redisTemplate.delete(FAILURE_PREFIX + userId);
        redisTemplate.delete(LOCK_PREFIX + userId);
    }

    private long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
