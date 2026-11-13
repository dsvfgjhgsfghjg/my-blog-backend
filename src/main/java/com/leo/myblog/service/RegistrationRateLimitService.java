package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RegistrationRateLimitService {

    private static final String PREFIX = "rate:register:";
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MINUTES = 10;

    private final StringRedisTemplate redisTemplate;
    private final SecurityMetricsService securityMetricsService;

    public void checkAndRecord(String clientIp) {
        String key = PREFIX + clientIp;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            securityMetricsService.rateLimited("registration");
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            throw new com.leo.myblog.exception.BusinessException(
                    429, "注册尝试过于频繁，请 " + Math.max(ttl != null ? ttl : 1, 1) + " 秒后重试");
        }
    }
}
