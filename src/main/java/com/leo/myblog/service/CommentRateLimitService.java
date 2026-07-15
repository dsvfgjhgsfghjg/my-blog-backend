package com.leo.myblog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 评论速率限制服务
 * 防止垃圾评论和恶意刷评
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentRateLimitService {
    
    private final StringRedisTemplate redisTemplate;
    
    /**
     * Redis key 前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:comment:";
    
    /**
     * 每分钟最大评论次数
     */
    private static final int MAX_COMMENTS_PER_MINUTE = 10;
    
    /**
     * 时间窗口（秒）
     */
    private static final long TIME_WINDOW = 60;
    
    /**
     * 检查是否允许发表评论
     * 
     * @param userId 用户ID
     * @return true-允许，false-拒绝
     */
    public boolean canComment(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        
        // 获取当前计数
        String countStr = redisTemplate.opsForValue().get(key);
        
        if (countStr == null) {
            // 第一次评论，设置计数为 1，过期时间 60 秒
            redisTemplate.opsForValue().set(key, "1", TIME_WINDOW, TimeUnit.SECONDS);
            log.debug("评论计数初始化: userId={}, count=1", userId);
            return true;
        }
        
        int count = Integer.parseInt(countStr);
        
        if (count >= MAX_COMMENTS_PER_MINUTE) {
            // 超过限制
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            log.warn("评论频率超限: userId={}, count={}/{}, 剩余{}秒解锁", 
                    userId, count, MAX_COMMENTS_PER_MINUTE, ttl);
            return false;
        }
        
        // 增加计数
        redisTemplate.opsForValue().increment(key);
        log.debug("评论计数: userId={}, count={}/{}", userId, count + 1, MAX_COMMENTS_PER_MINUTE);
        return true;
    }
    
    /**
     * 获取剩余可评论次数
     * 
     * @param userId 用户ID
     * @return 剩余次数
     */
    public int getRemainingComments(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        String countStr = redisTemplate.opsForValue().get(key);
        
        if (countStr == null) {
            return MAX_COMMENTS_PER_MINUTE;
        }
        
        int count = Integer.parseInt(countStr);
        return Math.max(0, MAX_COMMENTS_PER_MINUTE - count);
    }
    
    /**
     * 获取解锁剩余时间（秒）
     * 
     * @param userId 用户ID
     * @return 剩余秒数，0 表示未锁定
     */
    public long getUnlockTime(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
