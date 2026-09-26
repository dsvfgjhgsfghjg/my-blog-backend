package com.leo.myblog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录速率限制服务
 * 安全修复V3: 防止暴力破解
 * 渐进式锁定策略：
 * - 前5次失败：锁定3分钟
 * - 再3次失败（6-8次）：锁定10分钟
 * - 9次及以上：每次失败锁定30分钟
 * 
 * v2.0: 新增登录失败IP封禁功能
 * - 当IP的登录失败次数超过系统配置阈值时，自动封禁IP
 * - IP封禁时长可通过系统配置调整
 */
@Slf4j
@Service
public class LoginRateLimitService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SystemConfigService systemConfigService;
    private final IpBanService ipBanService;
    
    // 第一阶段：前5次失败
    private static final int FIRST_STAGE_MAX_ATTEMPTS = 5;
    private static final long FIRST_STAGE_LOCK_MINUTES = 3;
    
    // 第二阶段：6-8次失败
    private static final int SECOND_STAGE_MAX_ATTEMPTS = 8;
    private static final long SECOND_STAGE_LOCK_MINUTES = 10;
    
    // 第三阶段：9次及以上
    private static final long THIRD_STAGE_LOCK_MINUTES = 30;
    
    // IP级失败计数键前缀（独立于 username:ip）
    private static final String IP_FAILURES_KEY = "login:ip_failures:";
    private static final String IP_BANNED_KEY = "security:banned:";

    // 登录挑战（认证器推送）限流：独立于密码失败计数
    private static final String CHALLENGE_RATE_KEY = "login:challenge_rate:";
    private static final int MAX_CHALLENGES_PER_WINDOW = 5;
    private static final long CHALLENGE_RATE_WINDOW_SECONDS = 60;
    
    public LoginRateLimitService(RedisTemplate<String, String> redisTemplate,
                                  SystemConfigService systemConfigService,
                                  IpBanService ipBanService) {
        this.redisTemplate = redisTemplate;
        this.systemConfigService = systemConfigService;
        this.ipBanService = ipBanService;
    }
    
    /**
     * 检查是否允许登录尝试
     * @param username 用户名
     * @param ip IP地址
     * @return true-允许登录，false-已被锁定
     */
    public boolean isLoginAllowed(String username, String ip) {
        String lockKey = "login:lock:" + username + ":" + ip;
        
        // 检查是否被锁定
        Boolean isLocked = redisTemplate.hasKey(lockKey);
        if (Boolean.TRUE.equals(isLocked)) {
            log.warn("登录尝试被拒绝: username={}, ip={}, 原因: 账户已锁定", username, ip);
            return false;
        }
        
        return true;
    }
    
    /**
     * 记录登录失败
     * @param username 用户名
     * @param ip IP地址
     */
    public void recordLoginFailure(String username, String ip) {
        String counterKey = "login:failures:" + username + ":" + ip;
        String lockKey = "login:lock:" + username + ":" + ip;
        
        // 增加失败计数
        Long attempts = redisTemplate.opsForValue().increment(counterKey);
        
        if (attempts == null) {
            return;
        }
        
        // 设置过期时间（首次创建时，设置为1小时，足够覆盖所有锁定阶段）
        if (attempts == 1) {
            redisTemplate.expire(counterKey, 1, TimeUnit.HOURS);
        }
        
        // 根据失败次数确定是否需要锁定以及锁定时间
        long lockMinutes = 0;
        String stage = "";
        
        if (attempts == FIRST_STAGE_MAX_ATTEMPTS) {
            // 第5次失败后，锁定3分钟
            lockMinutes = FIRST_STAGE_LOCK_MINUTES;
            stage = "第一阶段";
            log.warn("登录失败达到阈值（第一阶段）: username={}, ip={}, 失败次数={}/{}, 将锁定{}分钟", 
                    username, ip, attempts, FIRST_STAGE_MAX_ATTEMPTS, lockMinutes);
        } else if (attempts == SECOND_STAGE_MAX_ATTEMPTS) {
            // 第8次失败后（5+3），锁定10分钟
            lockMinutes = SECOND_STAGE_LOCK_MINUTES;
            stage = "第二阶段";
            log.warn("登录失败达到阈值（第二阶段）: username={}, ip={}, 失败次数={}/{}, 将锁定{}分钟", 
                    username, ip, attempts, SECOND_STAGE_MAX_ATTEMPTS, lockMinutes);
        } else if (attempts > SECOND_STAGE_MAX_ATTEMPTS) {
            // 第9次及以后每次失败，锁定30分钟
            lockMinutes = THIRD_STAGE_LOCK_MINUTES;
            stage = "第三阶段";
            log.warn("登录失败（第三阶段）: username={}, ip={}, 失败次数={}, 将锁定{}分钟", 
                    username, ip, attempts, lockMinutes);
        } else {
            // 未达到锁定阈值，仅记录日志
            log.info("登录失败: username={}, ip={}, 失败次数={}/{}", 
                    username, ip, attempts, FIRST_STAGE_MAX_ATTEMPTS);
            return; // 不执行锁定
        }
        
        // 执行锁定
        redisTemplate.opsForValue().set(lockKey, "locked", lockMinutes, TimeUnit.MINUTES);
        log.warn("账户已锁定: username={}, ip={}, 失败次数={}, 锁定阶段={}, 锁定时间={}分钟", 
                username, ip, attempts, stage, lockMinutes);
        
        // ======== IP级封禁检查 ========
        // 尝试记录IP级失败计数（独立于username，防止攻击者换用户名绕过）
        recordIpLevelFailure(ip);
    }
    
    /**
     * 记录IP级别的登录失败计数
     * 当IP的总失败次数超过系统配置阈值时，自动封禁该IP
     * @param ip IP地址
     */
    private void recordIpLevelFailure(String ip) {
        try {
            // 检查配置是否启用IP封禁
            boolean banIpEnabled = "true".equalsIgnoreCase(
                    systemConfigService.getConfigValue("security.login.ban_ip_enabled", "false"));
            if (!banIpEnabled) {
                return; // IP封禁未启用
            }
            
            // 获取配置值
            int banThreshold;
            long banDurationHours;
            try {
                banThreshold = Integer.parseInt(
                        systemConfigService.getConfigValue("security.login.ban_ip_threshold", "20"));
                banDurationHours = Long.parseLong(
                        systemConfigService.getConfigValue("security.login.ban_ip_duration_hours", "24"));
            } catch (NumberFormatException e) {
                log.warn("IP封禁配置值格式错误，使用默认值: {}", e.getMessage());
                banThreshold = 20;
                banDurationHours = 24;
            }
            
            // IP级失败计数（独立于用户名）
            String ipFailKey = IP_FAILURES_KEY + ip;
            Long ipAttempts = redisTemplate.opsForValue().increment(ipFailKey);
            
            if (ipAttempts == null) {
                return;
            }
            
            // 首次创建时设置过期时间（24小时）
            if (ipAttempts == 1) {
                redisTemplate.expire(ipFailKey, banDurationHours, TimeUnit.HOURS);
            }
            
            log.debug("IP登录失败计数: ip={}, 总失败次数={}, 阈值={}", ip, ipAttempts, banThreshold);
            
            // 达到阈值，封禁IP
            if (ipAttempts >= banThreshold) {
                log.warn("IP登录失败次数超过阈值，自动封禁: ip={}, 失败次数={}, 阈值={}, 封禁时长={}小时",
                        ip, ipAttempts, banThreshold, banDurationHours);
                
                ipBanService.banIp(ip, 
                        "登录失败次数过多（" + ipAttempts + "次，阈值" + banThreshold + "次）", 
                        ipAttempts.intValue(), 
                        banDurationHours);
                
                // 清除IP失败计数，避免重复触发
                redisTemplate.delete(ipFailKey);
            }
        } catch (Exception e) {
            log.error("记录IP级登录失败计数异常", e);
        }
    }

    /**
     * 记录登录成功（清除失败计数）
     * @param username 用户名
     * @param ip IP地址
     */
    public void recordLoginSuccess(String username, String ip) {
        String counterKey = "login:failures:" + username + ":" + ip;
        String lockKey = "login:lock:" + username + ":" + ip;
        
        // 清除失败计数和锁定状态
        redisTemplate.delete(counterKey);
        redisTemplate.delete(lockKey);
        
        log.debug("登录成功，清除失败计数: username={}, ip={}", username, ip);
    }
    
    /**
     * 获取剩余锁定时间（秒）
     * @param username 用户名
     * @param ip IP地址
     * @return 剩余秒数，0表示未锁定
     */
    public long getRemainingLockTime(String username, String ip) {
        String lockKey = "login:lock:" + username + ":" + ip;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
    
    /**
     * 获取当前失败次数
     * @param username 用户名
     * @param ip IP地址
     * @return 失败次数，0表示无记录
     */
    public long getFailureCount(String username, String ip) {
        String counterKey = "login:failures:" + username + ":" + ip;
        String count = redisTemplate.opsForValue().get(counterKey);
        return count != null ? Long.parseLong(count) : 0;
    }

    // ==================== 登录挑战限流（认证器推送） ====================

    /**
     * 检查是否允许发起登录挑战
     * <p>
     * 与密码失败计数<b>完全独立</b>：挑战是正常用户会反复点的操作（重试、切模式），
     * 复用密码失败计数会把正常用户直接锁死。
     * <p>
     * 目的：压制推送轰炸 / MFA 疲劳攻击（攻击者反复触发推送骚扰用户）。
     *
     * @return true-允许，false-超出频率
     */
    public boolean isChallengeAllowed(String username, String ip) {
        String counterKey = CHALLENGE_RATE_KEY + username + ":" + ip;
        String count = redisTemplate.opsForValue().get(counterKey);
        long current = count != null ? Long.parseLong(count) : 0;
        return current < MAX_CHALLENGES_PER_WINDOW;
    }

    /**
     * 记录一次登录挑战请求
     */
    public void recordChallengeRequest(String username, String ip) {
        String counterKey = CHALLENGE_RATE_KEY + username + ":" + ip;
        Long current = redisTemplate.opsForValue().increment(counterKey);
        if (current != null && current == 1) {
            redisTemplate.expire(counterKey, CHALLENGE_RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        log.debug("记录登录挑战请求: username={}, ip={}, count={}", username, ip, current);
    }

    /**
     * 获取挑战限流窗口的剩余时间（秒）
     */
    public long getChallengeRemainingWindow(String username, String ip) {
        String counterKey = CHALLENGE_RATE_KEY + username + ":" + ip;
        Long ttl = redisTemplate.getExpire(counterKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
