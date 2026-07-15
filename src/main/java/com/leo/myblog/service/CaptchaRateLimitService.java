package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 验证码速率限制服务
 * 防止恶意频繁请求验证码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaRateLimitService {
    
    private final StringRedisTemplate redisTemplate;
    
    /**
     * Redis key 前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:captcha:";
    
    /**
     * 每分钟最大请求次数
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    
    /**
     * 时间窗口（秒）
     */
    private static final long TIME_WINDOW = 60;
    
    /**
     * 检查是否允许请求
     * 
     * @param clientIp 客户端 IP
     * @return true-允许，false-拒绝
     */
    public boolean isRequestAllowed(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        
        // 获取当前计数
        String countStr = redisTemplate.opsForValue().get(key);
        
        if (countStr == null) {
            // 第一次请求，设置计数为 1，过期时间 60 秒
            redisTemplate.opsForValue().set(key, "1", TIME_WINDOW, TimeUnit.SECONDS);
            log.debug("验证码请求计数初始化: IP={}, count=1", clientIp);
            return true;
        }
        
        int count = Integer.parseInt(countStr);
        
        if (count >= MAX_REQUESTS_PER_MINUTE) {
            // 超过限制
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            log.warn("验证码请求频率超限: IP={}, count={}/{}, 剩余{}秒解锁", 
                    clientIp, count, MAX_REQUESTS_PER_MINUTE, ttl);
            return false;
        }
        
        // 增加计数
        redisTemplate.opsForValue().increment(key);
        log.debug("验证码请求计数: IP={}, count={}/{}", clientIp, count + 1, MAX_REQUESTS_PER_MINUTE);
        return true;
    }
    
    /**
     * 获取剩余可用次数
     * 
     * @param clientIp 客户端 IP
     * @return 剩余次数
     */
    public int getRemainingRequests(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        String countStr = redisTemplate.opsForValue().get(key);
        
        if (countStr == null) {
            return MAX_REQUESTS_PER_MINUTE;
        }
        
        int count = Integer.parseInt(countStr);
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - count);
    }
    
    /**
     * 获取解锁剩余时间（秒）
     * 
     * @param clientIp 客户端 IP
     * @return 剩余秒数，0 表示未锁定
     */
    public long getUnlockTime(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
