package com.leo.myblog.service;

import com.leo.myblog.exception.BusinessException;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * TOTP 双因素认证服务
 * 兼容 Microsoft Authenticator、Google Authenticator 等标准 TOTP 应用
 */
@Slf4j
@Service
public class TotpService {
    
    private final GoogleAuthenticator gAuth;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis Key 前缀
    private static final String TOTP_SECRET_PREFIX = "totp:secret:";
    private static final String TOTP_BACKUP_CODES_PREFIX = "totp:backup:";
    
    // 备份码数量
    private static final int BACKUP_CODE_COUNT = 10;
    
    // 备份码有效期：30天
    private static final long BACKUP_CODE_EXPIRE_DAYS = 30;
    
    public TotpService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 配置 GoogleAuthenticator，允许 ±2 个时间窗口（±60秒）的误差
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
            .setTimeStepSizeInMillis(30000)  // 30秒时间窗口
            .setWindowSize(2)                // 允许前后各2个窗口（共5个窗口）
            .build();
        this.gAuth = new GoogleAuthenticator(config);
    }
    
    /**
     * 为用户生成 TOTP 密钥和二维码
     * @param userId 用户ID
     * @return TOTP 配置信息（包含密钥和二维码URL）
     */
    public TotpConfig generateTotpSecret(Long userId) {
        // 1. 检查是否已启用 TOTP
        if (isTotpEnabled(userId)) {
            throw new BusinessException("TOTP 已启用，请先禁用后再重新生成");
        }
        
        // 2. 生成密钥
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        
        // 3. 生成备份码
        String[] backupCodes = generateBackupCodes();
        
        // 4. 保存到 Redis（临时存储，等待用户确认）
        String secretKey = TOTP_SECRET_PREFIX + userId;
        redisTemplate.opsForValue().set(secretKey, secret, 10, TimeUnit.MINUTES);
        
        String backupKey = TOTP_BACKUP_CODES_PREFIX + userId;
        redisTemplate.opsForValue().set(backupKey, String.join(",", backupCodes), 
                BACKUP_CODE_EXPIRE_DAYS, TimeUnit.DAYS);
        
        // 5. 生成二维码 URL（兼容 Microsoft Authenticator）
        String qrCodeUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
            "MyBlog",                    // issuer（网站名称）
            "user_" + userId,           // account（账户标识）
            key
        );
        
        log.info("生成 TOTP 密钥: userId={}", userId);
        
        return new TotpConfig(secret, qrCodeUrl, backupCodes);
    }
    
    /**
     * 验证并启用 TOTP
     * @param userId 用户ID
     * @param code 用户输入的 6 位验证码
     * @return 是否成功启用
     */
    public boolean enableTotp(Long userId, String code) {
        // 1. 获取临时密钥
        String secretKey = TOTP_SECRET_PREFIX + userId;
        String secret = (String) redisTemplate.opsForValue().get(secretKey);
        
        if (secret == null) {
            throw new BusinessException("TOTP 密钥已过期，请重新生成");
        }
        
        // 2. 验证验证码
        boolean isValid = gAuth.authorize(secret, Integer.parseInt(code));
        
        if (!isValid) {
            log.warn("TOTP 验证码验证失败: userId={}, code={}", userId, code);
            return false;
        }
        
        // 3. 永久保存密钥
        redisTemplate.opsForValue().set(secretKey, secret); // 无过期时间
        
        log.info("TOTP 已启用: userId={}", userId);
        return true;
    }
    
    /**
     * 验证 TOTP 验证码
     * @param userId 用户ID
     * @param code 6 位验证码
     * @return 是否有效
     */
    public boolean verifyTotpCode(Long userId, String code) {
        // 1. 检查是否启用了 TOTP
        if (!isTotpEnabled(userId)) {
            log.warn("用户未启用 TOTP: userId={}", userId);
            return false;
        }
        
        // 2. 获取密钥
        String secretKey = TOTP_SECRET_PREFIX + userId;
        String secret = (String) redisTemplate.opsForValue().get(secretKey);
        
        if (secret == null) {
            log.error("TOTP 密钥丢失: userId={}", userId);
            return false;
        }
        
        // 3. 验证验证码（允许 ±1 个时间窗口的误差，防止时钟不同步）
        boolean isValid = gAuth.authorize(secret, Integer.parseInt(code));
        
        if (isValid) {
            log.debug("TOTP 验证码验证成功: userId={}", userId);
        } else {
            log.warn("TOTP 验证码验证失败: userId={}, code={}", userId, code);
        }
        
        return isValid;
    }
    
    /**
     * 使用备份码验证
     * @param userId 用户ID
     * @param backupCode 备份码
     * @return 是否有效
     */
    public boolean verifyBackupCode(Long userId, String backupCode) {
        String backupKey = TOTP_BACKUP_CODES_PREFIX + userId;
        String codesStr = (String) redisTemplate.opsForValue().get(backupKey);
        
        if (codesStr == null || codesStr.isEmpty()) {
            log.warn("备份码不存在: userId={}", userId);
            return false;
        }
        
        String[] codes = codesStr.split(",");
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(backupCode)) {
                // 删除已使用的备份码
                codes[i] = "";
                String remainingCodes = String.join(",", codes).replaceAll("^,+|,+$", "").replaceAll(",,+", ",");
                
                if (remainingCodes.isEmpty()) {
                    redisTemplate.delete(backupKey);
                    log.warn("所有备份码已用完: userId={}", userId);
                } else {
                    redisTemplate.opsForValue().set(backupKey, remainingCodes, 
                            BACKUP_CODE_EXPIRE_DAYS, TimeUnit.DAYS);
                }
                
                log.info("备份码验证成功: userId={}", userId);
                return true;
            }
        }
        
        log.warn("备份码无效: userId={}, code={}", userId, backupCode);
        return false;
    }
    
    /**
     * 禁用 TOTP
     * @param userId 用户ID
     */
    public void disableTotp(Long userId) {
        String secretKey = TOTP_SECRET_PREFIX + userId;
        String backupKey = TOTP_BACKUP_CODES_PREFIX + userId;
        
        redisTemplate.delete(secretKey);
        redisTemplate.delete(backupKey);
        
        log.info("TOTP 已禁用: userId={}", userId);
    }
    
    /**
     * 检查用户是否启用了 TOTP
     */
    public boolean isTotpEnabled(Long userId) {
        String secretKey = TOTP_SECRET_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(secretKey));
    }
    
    /**
     * 获取剩余备份码数量
     */
    public int getRemainingBackupCodes(Long userId) {
        String backupKey = TOTP_BACKUP_CODES_PREFIX + userId;
        String codesStr = (String) redisTemplate.opsForValue().get(backupKey);
        
        if (codesStr == null || codesStr.isEmpty()) {
            return 0;
        }
        
        return codesStr.split(",").length;
    }
    
    /**
     * 生成随机备份码
     */
    private String[] generateBackupCodes() {
        String[] codes = new String[BACKUP_CODE_COUNT];
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            // 生成 8 位随机数字
            codes[i] = String.format("%08d", (int) (Math.random() * 100000000));
        }
        return codes;
    }
    
    /**
     * TOTP 配置信息
     */
    public static class TotpConfig {
        private final String secret;
        private final String qrCodeUrl;
        private final String[] backupCodes;
        
        public TotpConfig(String secret, String qrCodeUrl, String[] backupCodes) {
            this.secret = secret;
            this.qrCodeUrl = qrCodeUrl;
            this.backupCodes = backupCodes;
        }
        
        public String getSecret() {
            return secret;
        }
        
        public String getQrCodeUrl() {
            return qrCodeUrl;
        }
        
        public String[] getBackupCodes() {
            return backupCodes;
        }
    }
}
