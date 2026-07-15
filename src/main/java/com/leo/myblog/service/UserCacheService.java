package com.leo.myblog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.entity.User;
import com.leo.myblog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    
    private static final String USER_CACHE_PREFIX = "user:cache:";
    private static final long CACHE_EXPIRE_HOURS = 24; // 缓存24小时
    
    /**
     * 获取用户信息（先查缓存，缓存未命中再查数据库）
     */
    public User getUserById(Long userId) {
        String key = USER_CACHE_PREFIX + userId;
        
        try {
            // 1. 先从缓存中获取
            Object cachedValue = redisTemplate.opsForValue().get(key);
            if (cachedValue != null) {
                User cachedUser;
                // 如果已经是User类型，直接返回
                if (cachedValue instanceof User) {
                    cachedUser = (User) cachedValue;
                } else {
                    // 如果是LinkedHashMap等其他类型，通过JSON转换
                    String json = objectMapper.writeValueAsString(cachedValue);
                    cachedUser = objectMapper.readValue(json, User.class);
                }
                log.debug("从缓存获取用户信息: userId={}", userId);
                return cachedUser;
            }
            
            // 2. 缓存未命中，从数据库查询
            User user = userMapper.selectById(userId);
            if (user != null) {
                // 3. 存入缓存
                redisTemplate.opsForValue().set(key, user, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                log.debug("用户信息已缓存: userId={}", userId);
            }
            
            return user;
        } catch (JsonProcessingException e) {
            log.error("JSON序列化/反序列化失败: userId={}", userId, e);
            // JSON异常时降级到数据库查询
            return userMapper.selectById(userId);
        } catch (Exception e) {
            log.error("获取用户信息失败: userId={}", userId, e);
            // 缓存异常时降级到数据库查询
            return userMapper.selectById(userId);
        }
    }
    
    /**
     * 清除用户缓存
     */
    public void clearUserCache(Long userId) {
        String key = USER_CACHE_PREFIX + userId;
        try {
            redisTemplate.delete(key);
            log.debug("用户缓存已清除: userId={}", userId);
        } catch (Exception e) {
            log.error("清除用户缓存失败: userId={}", userId, e);
        }
    }
    
    /**
     * 更新用户缓存
     */
    public void updateUserCache(User user) {
        String key = USER_CACHE_PREFIX + user.getId();
        try {
            redisTemplate.opsForValue().set(key, user, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("用户缓存已更新: userId={}", user.getId());
        } catch (Exception e) {
            log.error("更新用户缓存失败: userId={}", user.getId(), e);
        }
    }
    
    /**
     * 检查用户是否为管理员（带缓存）
     */
    public boolean isAdmin(Long userId) {
        User user = getUserById(userId);
        return user != null && Boolean.TRUE.equals(user.getIsAdmin());
    }
}
