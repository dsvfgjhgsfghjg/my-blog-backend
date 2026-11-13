package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.service.SystemConfigService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.service.TotpSessionService;
import com.leo.myblog.service.UserService;
import com.leo.myblog.service.MfaAttemptService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * TOTP 双因素认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/totp")
@RequiredArgsConstructor
public class TotpController {
    
    private final TotpService totpService;
    private final UserService userService;
    private final TotpSessionService totpSessionService;
    private final SystemConfigService systemConfigService;
    private final MfaAttemptService mfaAttemptService;
    
    /**
     * 生成 TOTP 密钥和二维码
     */
    @PostMapping("/generate")
    public Result<TotpGenerateResponse> generateTotp(@RequestAttribute Long userId) {
        log.info("生成 TOTP 密钥: userId={}", userId);
        
        TotpService.TotpConfig config = totpService.generateTotpSecret(userId);
        
        TotpGenerateResponse response = new TotpGenerateResponse();
        response.setQrCodeUrl(config.getQrCodeUrl());
        response.setSecret(config.getSecret());
        response.setBackupCodes(config.getBackupCodes());
        
        return Result.success(response);
    }
    
    /**
     * 验证并启用 TOTP
     */
    @PostMapping("/enable")
    public Result<Void> enableTotp(@RequestAttribute Long userId,
                                    @RequestBody TotpEnableRequest request) {
        log.info("启用 TOTP: userId={}", userId);
        
        mfaAttemptService.assertAllowed(userId);
        boolean success = totpService.enableTotp(userId, request.getCode());
        
        if (success) {
            mfaAttemptService.recordSuccess(userId);
            return Result.success();
        } else {
            mfaAttemptService.recordFailure(userId);
            return Result.error(400, "验证码错误，请重试");
        }
    }
    
    /**
     * 禁用 TOTP（需要密码验证）
     */
    @PostMapping("/disable")
    public Result<Void> disableTotp(@RequestAttribute Long userId,
                                     @RequestBody TotpDisableRequest request) {
        log.info("禁用 TOTP: userId={}", userId);
        
        // 验证管理员密码
        if (!userService.verifyPassword(userId, request.getPassword())) {
            return Result.error(401, "密码错误");
        }
        
        totpService.disableTotp(userId);
        
        return Result.success();
    }
    
    /**
     * 检查 TOTP 状态
     */
    @GetMapping("/status")
    public Result<TotpStatusResponse> getTotpStatus(@RequestAttribute Long userId) {
        boolean enabled = totpService.isTotpEnabled(userId);
        int remainingCodes = totpService.getRemainingBackupCodes(userId);
        
        TotpStatusResponse response = new TotpStatusResponse();
        response.setEnabled(enabled);
        response.setRemainingBackupCodes(remainingCodes);
        
        return Result.success(response);
    }
    
    /**
     * 检查当前请求是否需要 TOTP 验证
     * 前端可在发起敏感操作前调用此端点，避免不必要的弹窗
     * @param userId 用户ID（从请求属性获取）
     * @param moduleKey 模块配置键（可选），如 security.totp.system_config
     * @return 是否需要 TOTP 验证及相关状态
     */
    @GetMapping("/check-required")
    public Result<TotpRequiredResponse> checkTotpRequired(
            @RequestAttribute Long userId,
            @RequestParam(required = false) String moduleKey) {
        
        boolean userHasTotp = totpService.isTotpEnabled(userId);
        boolean moduleRequired = moduleKey == null || systemConfigService.isTotpEnabledForModule(moduleKey);
        boolean sessionValid = totpSessionService.isSessionValid(userId);
        
        // 需要 TOTP 的条件：用户已启用 + 模块要求 + 无有效会话
        boolean needTotp = userHasTotp && moduleRequired && !sessionValid;
        
        log.debug("检查 TOTP 需求: userId={}, moduleKey={}, userHasTotp={}, moduleRequired={}, sessionValid={}, needTotp={}",
                userId, moduleKey, userHasTotp, moduleRequired, sessionValid, needTotp);
        
        TotpRequiredResponse response = new TotpRequiredResponse();
        response.setNeedTotp(needTotp);
        response.setUserHasTotp(userHasTotp);
        response.setSessionValid(sessionValid);
        
        return Result.success(response);
    }
    
    /**
     * 验证 TOTP 码（用于敏感操作前的二次验证）
     * 验证成功后会自动创建 TOTP 会话，后续敏感操作无需再次输入验证码
     */
    @PostMapping("/verify")
    public Result<Boolean> verifyTotp(@RequestAttribute Long userId,
                                       @RequestBody TotpVerifyRequest request) {
        // 先检查用户是否已启用 TOTP
        if (!totpService.isTotpEnabled(userId)) {
            log.warn("用户未启用 TOTP 却尝试验证: userId={}", userId);
            return Result.error(400, "您尚未启用双因素认证，请先到安全设置中绑定 TOTP");
        }
        
        mfaAttemptService.assertAllowed(userId);
        boolean isValid;
        
        // 判断是 TOTP 码还是备份码
        if (request.getCode().length() == 6) {
            // 6 位数字：TOTP 验证码
            isValid = totpService.verifyTotpCode(userId, request.getCode());
        } else if (request.getCode().length() == 8) {
            // 8 位数字：备份码
            isValid = totpService.verifyBackupCode(userId, request.getCode());
        } else {
            return Result.error(400, "无效的验证码格式");
        }
        
        if (isValid) {
            mfaAttemptService.recordSuccess(userId);
            // 验证成功后创建 TOTP 会话，后续带 TOTP Session 的请求无需重复验证
            totpSessionService.createSession(userId);
            log.info("TOTP 验证成功并创建会话: userId={}", userId);
        } else {
            mfaAttemptService.recordFailure(userId);
            log.warn("TOTP 验证码错误: userId={}, codeLength={}", userId, request.getCode().length());
        }
        
        return Result.success(isValid);
    }
    
    // ==================== DTO Classes ====================
    
    @Data
    public static class TotpGenerateResponse {
        private String qrCodeUrl;      // 二维码 URL
        private String secret;         // 密钥（手动输入用）
        private String[] backupCodes;  // 备份码
    }
    
    @Data
    public static class TotpEnableRequest {
        private String code;  // 6 位验证码
    }
    
    @Data
    public static class TotpDisableRequest {
        private String password;  // 管理员密码
    }
    
    @Data
    public static class TotpStatusResponse {
        private Boolean enabled;           // 是否启用
        private Integer remainingBackupCodes;  // 剩余备份码数量
    }
    
    @Data
    public static class TotpVerifyRequest {
        private String code;  // 6 位 TOTP 码或 8 位备份码
    }
    
    @Data
    public static class TotpRequiredResponse {
        private Boolean needTotp;          // 是否需要弹出 TOTP 验证
        private Boolean userHasTotp;       // 用户是否已启用 TOTP
        private Boolean sessionValid;      // 当前会话是否已有有效 TOTP 会话
    }
}
