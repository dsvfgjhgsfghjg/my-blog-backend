package com.leo.myblog.aspect;

import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.service.TotpSessionService;
import com.leo.myblog.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 双因素验证切面（扩展 TOTP 支持多方式验证）
 * <p>
 * 拦截标注了 @RequireTotp 的方法，支持两种验证方式：
 * 1. TOTP：请求头 X-TOTP-Code
 * 2. 挑战推送：请求头 X-Verify-Method: challenge_push + X-Challenge-Id
 * <p>
 * 验证流程：
 * 1. 检查是否有有效的验证会话（TOTP session）
 * 2. 有会话 → 直接放行
 * 3. 无会话 → 检查请求头中的验证信息
 * 4. 验证成功 → 创建会话并放行
 * 5. 验证失败 → 返回 401
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class VerificationAspect {

    private final TotpService totpService;
    private final TotpSessionService totpSessionService;
    private final SystemConfigService systemConfigService;
    private final AppAuthService appAuthService;
    private final LoginService loginService;

    @Around("@annotation(com.leo.myblog.annotation.RequireTotp)")
    public Object checkVerification(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前用户ID
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或会话已过期");
        }

        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireTotp annotation = method.getAnnotation(RequireTotp.class);

        // 检查是否配置了模块开关
        String moduleKey = annotation.module();
        if (moduleKey != null && !moduleKey.trim().isEmpty()) {
            boolean requireTotp = systemConfigService.isTotpEnabledForModule(moduleKey);
            if (!requireTotp) {
                log.debug("模块 {} 的 TOTP 已禁用，跳过验证", moduleKey);
                return joinPoint.proceed();
            }
        }

        // 检查用户是否启用了 TOTP 或绑定过设备
        boolean totpEnabled = totpService.isTotpEnabled(userId);
        boolean hasBoundDevices = appAuthService.hasBoundDevice(userId);

        // 如果用户既没有 TOTP 也没有绑定设备，直接放行（无法验证）
        if (!totpEnabled && !hasBoundDevices) {
            log.debug("用户未启用任何双因素认证方式，跳过验证: userId={}", userId);
            return joinPoint.proceed();
        }

        // 获取 HttpServletRequest
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(500, "无法获取请求上下文");
        }
        HttpServletRequest request = attributes.getRequest();

        // 检查是否有有效的 TOTP 会话（统一会话）
        boolean sessionValid = totpSessionService.isSessionValid(userId);
        log.debug("验证会话检查: userId={}, sessionValid={}", userId, sessionValid);

        if (sessionValid) {
            log.debug("验证会话有效，跳过二次验证: userId={}", userId);
            try {
                attributes.getResponse().setHeader("X-TOTP-Session", "valid");
            } catch (Exception e) {
                log.warn("设置响应头失败", e);
            }
            return joinPoint.proceed();
        }

        // 检查验证方法：优先 X-Verify-Method 请求头，否则尝试 TOTP（向后兼容）
        String verifyMethod = request.getHeader("X-Verify-Method");

        if ("challenge_push".equals(verifyMethod)) {
            return handleChallengePushVerification(joinPoint, userId, request, attributes);
        } else {
            // 默认走 TOTP 验证
            return handleTotpVerification(joinPoint, userId, annotation, request, attributes);
        }
    }

    /**
     * 处理 TOTP 验证
     */
    private Object handleTotpVerification(ProceedingJoinPoint joinPoint, Long userId,
                                           RequireTotp annotation, HttpServletRequest request,
                                           ServletRequestAttributes attributes) throws Throwable {
        String totpCode = request.getHeader("X-TOTP-Code");

        if (totpCode == null || totpCode.trim().isEmpty()) {
            log.warn("敏感操作缺少验证码: userId={}, method={}",
                    userId, joinPoint.getSignature().getName());
            throw new BusinessException(401, "此操作需要双因素认证，请提供验证码");
        }

        log.debug("收到 TOTP 验证码: userId={}, codeLength={}", userId, totpCode.length());

        String code = totpCode.trim();
        boolean isValid = totpService.verifyTotpCode(userId, code);

        if (!isValid) {
            log.debug("TOTP 验证失败，尝试备份码验证: userId={}", userId);
            isValid = totpService.verifyBackupCode(userId, code);
        }

        if (!isValid) {
            log.warn("TOTP 验证码错误: userId={}, codeLength={}", userId, code.length());
            throw new BusinessException(401, "验证码错误或已过期");
        }

        // 验证成功，创建会话
        totpSessionService.createSession(userId);
        loginService.recordLastVerifyMethod(userId, "totp");

        log.info("TOTP 验证成功: userId={}, operation={}",
                userId, getOperationDescription(joinPoint));

        try {
            attributes.getResponse().setHeader("X-TOTP-Session", "valid");
            attributes.getResponse().setHeader("X-TOTP-Session-Expires",
                    String.valueOf(System.currentTimeMillis() + 5 * 60 * 1000));
        } catch (Exception e) {
            log.warn("设置响应头失败", e);
        }

        return joinPoint.proceed();
    }

    /**
     * 处理挑战推送验证
     */
    private Object handleChallengePushVerification(ProceedingJoinPoint joinPoint, Long userId,
                                                    HttpServletRequest request,
                                                    ServletRequestAttributes attributes) throws Throwable {
        String challengeId = request.getHeader("X-Challenge-Id");

        if (challengeId == null || challengeId.trim().isEmpty()) {
            log.warn("挑战推送验证缺少 Challenge-Id: userId={}", userId);
            throw new BusinessException(401, "此操作需要 App 验证，请先发送推送挑战");
        }

        log.debug("检查挑战推送验证结果: userId={}, challengeId={}", userId, challengeId);

        // 检查登录挑战结果
        com.leo.myblog.dto.response.LoginPollResponse pollResult =
                loginService.pollLoginResult(challengeId);

        if ("approved".equals(pollResult.getStatus())) {
            // 验证成功，创建会话
            totpSessionService.createSession(userId);
            loginService.recordLastVerifyMethod(userId, "challenge_push");

            log.info("挑战推送验证成功: userId={}, operation={}",
                    userId, getOperationDescription(joinPoint));

            try {
                attributes.getResponse().setHeader("X-TOTP-Session", "valid");
            } catch (Exception e) {
                log.warn("设置响应头失败", e);
            }

            return joinPoint.proceed();
        }

        if ("rejected".equals(pollResult.getStatus())) {
            throw new BusinessException(401, "App 验证已拒绝");
        }

        if ("expired".equals(pollResult.getStatus())) {
            throw new BusinessException(401, "验证挑战已过期，请重新发送");
        }

        // pending 状态
        throw new BusinessException(401, "请在 App 上完成验证");
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return (Long) attributes.getRequest().getAttribute("userId");
            }
        } catch (Exception e) {
            log.error("获取用户ID失败", e);
        }
        return null;
    }

    private String getOperationDescription(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireTotp annotation = method.getAnnotation(RequireTotp.class);
        return annotation != null ? annotation.operation() : method.getName();
    }
}