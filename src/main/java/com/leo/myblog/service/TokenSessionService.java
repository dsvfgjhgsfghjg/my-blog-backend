package com.leo.myblog.service;

import com.leo.myblog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Token Session管理服务
 * 用于跟踪和管理用户的活跃Token会话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenSessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    
    // Redis Key前缀
    private static final String USER_SESSIONS_PREFIX = "user:sessions:";
    private static final String TOKEN_SESSION_PREFIX = "token:session:";
    
    // Session过期时间（与JWT Token一致，默认24小时 = 86400秒）
    private static final long SESSION_TIMEOUT_SECONDS = 24 * 60 * 60;
    
    /**
     * 记录Token会话
     * @param userId 用户ID
     * @param token JWT Token
     * @param ipAddress IP地址
     * @param userAgent User-Agent
     * @param deviceInfo 设备信息描述
     */
    public void recordSession(Long userId, String token, String ipAddress, 
                              String userAgent, String deviceInfo) {
        try {
            // 1. 提取Token标识（使用hashCode避免存储完整Token）
            String tokenHash = String.valueOf(token.hashCode());
            
            // 2. 计算过期时间（使用固定的24小时）
            long expirationSeconds = SESSION_TIMEOUT_SECONDS;
            
            // 3. 存储Token到用户的会话列表
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("tokenHash", tokenHash);
            sessionData.put("ipAddress", ipAddress);
            sessionData.put("userAgent", userAgent);
            sessionData.put("deviceInfo", deviceInfo);
            sessionData.put("loginTime", LocalDateTime.now().toString());
            sessionData.put("expireTime", LocalDateTime.now().plusSeconds(expirationSeconds).toString());
            
            redisTemplate.opsForHash().put(userSessionsKey, tokenHash, sessionData);
            redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // 4. 存储反向索引：Token Hash -> UserId（用于快速查找）
            String tokenSessionKey = TOKEN_SESSION_PREFIX + tokenHash;
            redisTemplate.opsForValue().set(tokenSessionKey, userId, expirationSeconds, TimeUnit.SECONDS);
            
            log.debug("记录Token会话: userId={}, tokenHash={}, ip={}", userId, tokenHash, ipAddress);
        } catch (Exception e) {
            log.error("记录Token会话失败: userId={}", userId, e);
        }
    }
    
    /**
     * 获取用户的所有活跃会话
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<Map<String, Object>> getUserSessions(Long userId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            Map<Object, Object> sessions = redisTemplate.opsForHash().entries(userSessionsKey);
            
            if (sessions == null || sessions.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : sessions.entrySet()) {
                Map<String, Object> sessionData = (Map<String, Object>) entry.getValue();
                
                // 添加sessionId
                sessionData.put("sessionId", entry.getKey());
                
                result.add(sessionData);
            }
            
            // 按登录时间排序（最新的在前）
            result.sort((a, b) -> {
                String timeA = (String) a.get("loginTime");
                String timeB = (String) b.get("loginTime");
                return timeB.compareTo(timeA);
            });
            
            log.debug("获取用户会话: userId={}, count={}", userId, result.size());
            return result;
        } catch (Exception e) {
            log.error("获取用户会话失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取指定会话的数据（不删除会话）
     * @param userId 用户ID
     * @param sessionId 会话ID（Token Hash）
     * @return 会话数据（包含deviceInfo、userAgent等），不存在时返回null
     */
    public Map<String, Object> getSessionData(Long userId, String sessionId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            Object sessionDataObj = redisTemplate.opsForHash().get(userSessionsKey, sessionId);
            if (sessionDataObj instanceof Map) {
                Map<String, Object> sessionData = new HashMap<>((Map<String, Object>) sessionDataObj);
                sessionData.put("sessionId", sessionId);
                return sessionData;
            }
        } catch (Exception e) {
            log.warn("获取会话数据失败: userId={}, sessionId={}", userId, sessionId, e);
        }
        return null;
    }
    
    /**
     * 下线指定会话（使Token失效）
     * @param userId 用户ID
     * @param sessionId 会话ID（Token Hash）
     * @return true=成功，false=失败
     */
    public boolean revokeSession(Long userId, String sessionId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            
            // 1. 获取会话数据（包含过期时间），用于计算黑名单保留时间
            Object sessionDataObj = redisTemplate.opsForHash().get(userSessionsKey, sessionId);
            long remainingSeconds = 0;
            if (sessionDataObj instanceof Map) {
                Map<String, Object> sessionData = (Map<String, Object>) sessionDataObj;
                String expireTimeStr = (String) sessionData.get("expireTime");
                if (expireTimeStr != null) {
                    try {
                        LocalDateTime expireTime = LocalDateTime.parse(expireTimeStr);
                        remainingSeconds = java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds();
                        remainingSeconds = Math.max(remainingSeconds, 0);
                    } catch (Exception e) {
                        log.warn("解析会话过期时间失败: sessionId={}", sessionId);
                        remainingSeconds = SESSION_TIMEOUT_SECONDS; // 默认使用完整超时时间
                    }
                }
            } else {
                remainingSeconds = SESSION_TIMEOUT_SECONDS; // 无法获取时使用完整超时时间
            }
            
            // 2. 将Token Hash加入黑名单（必须在删除前获取sessionData）
            tokenBlacklistService.addToBlacklistByHash(userId, sessionId, remainingSeconds);
            
            // 3. 从用户会话列表中删除
            redisTemplate.opsForHash().delete(userSessionsKey, sessionId);
            
            // 4. 删除反向索引
            String tokenSessionKey = TOKEN_SESSION_PREFIX + sessionId;
            redisTemplate.delete(tokenSessionKey);
            
            log.info("会话已下线并加入黑名单: userId={}, sessionId={}, 黑名单保留={}秒", userId, sessionId, remainingSeconds);
            return true;
        } catch (Exception e) {
            log.error("下线会话失败: userId={}, sessionId={}", userId, sessionId, e);
            return false;
        }
    }
    
    /**
     * 下线所有其他会话（保留当前会话）
     * @param userId 用户ID
     * @param currentTokenHash 当前Token的Hash
     * @return 下线的会话数量
     */
    public int revokeOtherSessions(Long userId, String currentTokenHash) {
        return revokeSessions(userId, currentTokenHash);
    }

    /**
     * 下线该用户的**全部**会话
     * <p>
     * 用途：重置密码 / 用户自己改密码之后。这两件事的动机通常就是「账号可能不安全」，
     * 此时把登录态全清掉才有意义 —— 否则改了密码，攻击者手上的 token 照样能用。
     * <p>
     * 注意这只是 Redis 侧的登录态。**refresh token 不在这里**，它存在
     * {@code oauth2_authorizations} 表里，必须一并作废，否则拿 refresh 换一个新的
     * access token 就绕过去了。编排见 {@link SessionRevocationService#revokeAll}。
     *
     * @param userId 用户ID
     * @return 下线的会话数量
     */
    public int revokeAllSessions(Long userId) {
        return revokeSessions(userId, null);
    }

    /**
     * 下线会话的公共实现
     *
     * @param userId         用户ID
     * @param exceptTokenHash 要保留的 Token Hash；传 null 表示全部下线
     * @return 下线的会话数量
     */
    private int revokeSessions(Long userId, String exceptTokenHash) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            Map<Object, Object> sessions = redisTemplate.opsForHash().entries(userSessionsKey);

            if (sessions == null || sessions.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (Map.Entry<Object, Object> entry : sessions.entrySet()) {
                Object sessionId = entry.getKey();
                // 跳过要保留的会话。这里必须判空：revokeAllSessions 传的就是 null，
                // 原实现写成 currentTokenHash.equals(...) 会直接 NPE。
                if (exceptTokenHash != null && exceptTokenHash.equals(sessionId.toString())) {
                    continue;
                }

                // 计算剩余过期时间用于黑名单保留
                long remainingSeconds = 0;
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> sessionData = (Map<String, Object>) entry.getValue();
                    String expireTimeStr = (String) sessionData.get("expireTime");
                    if (expireTimeStr != null) {
                        try {
                            LocalDateTime expireTime = LocalDateTime.parse(expireTimeStr);
                            remainingSeconds = java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds();
                            remainingSeconds = Math.max(remainingSeconds, 0);
                        } catch (Exception e) {
                            log.warn("解析会话过期时间失败: sessionId={}", sessionId);
                            remainingSeconds = SESSION_TIMEOUT_SECONDS;
                        }
                    }
                } else {
                    remainingSeconds = SESSION_TIMEOUT_SECONDS;
                }

                // 将Token Hash加入黑名单
                tokenBlacklistService.addToBlacklistByHash(userId, sessionId.toString(), remainingSeconds);

                // 下线会话
                redisTemplate.opsForHash().delete(userSessionsKey, sessionId);
                String tokenSessionKey = TOKEN_SESSION_PREFIX + sessionId;
                redisTemplate.delete(tokenSessionKey);
                count++;
            }

            log.info("下线会话: userId={}, count={}, 保留当前会话={}, 已加入黑名单",
                    userId, count, exceptTokenHash != null);
            return count;
        } catch (Exception e) {
            log.error("下线会话失败: userId={}", userId, e);
            return 0;
        }
    }
    
    /**
     * 检查Token是否仍然活跃
     * @param tokenHash Token Hash
     * @return true=活跃，false=已下线或过期
     */
    public boolean isSessionActive(String tokenHash) {
        try {
            String tokenSessionKey = TOKEN_SESSION_PREFIX + tokenHash;
            return Boolean.TRUE.equals(redisTemplate.hasKey(tokenSessionKey));
        } catch (Exception e) {
            log.error("检查会话状态失败: tokenHash={}", tokenHash, e);
            return false;
        }
    }
    
    /**
     * 清理过期的会话
     * （由定时任务调用）
     */
    public void cleanupExpiredSessions() {
        // Redis会自动过期，无需手动清理
        log.debug("会话过期清理完成（Redis自动处理）");
    }
}
