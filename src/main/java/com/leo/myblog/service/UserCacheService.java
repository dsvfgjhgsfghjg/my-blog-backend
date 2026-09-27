package com.leo.myblog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.entity.User;
import com.leo.myblog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
     * 批量获取用户信息（先查缓存，缓存未命中的再一次性查库）
     * <p>
     * 与 {@link #getUserById(Long)} 的语义保持一致：返回的是 userId -> User 的映射，
     * 查不到的用户不会出现在映射里。入参里的 null 会被忽略、重复的 id 会去重，
     * 因此调用方不需要自己先去重。
     *
     * @param userIds 用户ID集合
     * @return userId -> User，永不为 null
     */
    public Map<Long, User> getUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = ids.stream()
                .map(id -> USER_CACHE_PREFIX + id)
                .collect(Collectors.toList());

        Map<Long, User> result = new HashMap<>(ids.size());
        try {
            // 1. 一次 multiGet 把命中的缓存全部取回（missing 位置上会是 null）
            List<Object> cachedValues = redisTemplate.opsForValue().multiGet(keys);

            List<Long> missedIds = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                Object cachedValue = (cachedValues != null && i < cachedValues.size()) ? cachedValues.get(i) : null;
                if (cachedValue == null) {
                    missedIds.add(ids.get(i));
                    continue;
                }
                User cachedUser;
                if (cachedValue instanceof User) {
                    cachedUser = (User) cachedValue;
                } else {
                    String json = objectMapper.writeValueAsString(cachedValue);
                    cachedUser = objectMapper.readValue(json, User.class);
                }
                result.put(ids.get(i), cachedUser);
            }

            // 2. 未命中的批量查库，并回填缓存
            if (!missedIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(missedIds);
                if (users != null) {
                    for (User user : users) {
                        result.put(user.getId(), user);
                        try {
                            redisTemplate.opsForValue().set(USER_CACHE_PREFIX + user.getId(), user,
                                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                        } catch (Exception e) {
                            // 回填失败不影响本次返回
                            log.error("用户信息回填缓存失败: userId={}", user.getId(), e);
                        }
                    }
                }
                log.debug("批量获取用户信息: 命中={}, 回源={}", ids.size() - missedIds.size(), missedIds.size());
            }

            return result;
        } catch (Exception e) {
            log.error("批量获取用户信息失败，降级为直接查库: userIds={}", ids, e);
            // 降级：与 getUserById 一样直接查库，只是改成批量
            List<User> users = userMapper.selectBatchIds(ids);
            if (users == null) {
                return Collections.emptyMap();
            }
            Map<Long, User> fallback = new HashMap<>(users.size());
            for (User user : users) {
                fallback.put(user.getId(), user);
            }
            return fallback;
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
