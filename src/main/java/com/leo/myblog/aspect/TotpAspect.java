package com.leo.myblog.aspect;

import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.SystemConfigService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.service.TotpSessionService;
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
 * TOTP 双因素认证切面
 * 拦截标注了 @RequireTotp 的方法，验证 TOTP
 * 
 * 验证流程：
 * 1. 检查是否有有效的 TOTP 会话 → 有则直接放行
 * 2. 检查请求头 X-TOTP-Code → 有则验证验证码，成功后创建会话并放行
 * 3. 无 TOTP 会话且无验证码 → 返回 401，要求提供 TOTP 验证码
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TotpAspect {
    
    private final TotpService totpService;
    private final TotpSessionService totpSessionService;
    private final SystemConfigService systemConfigService;
    
    @Around("@annotation(com.leo.myblog.annotation.RequireTotp)")
    public Object checkTotp(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前用户ID（从 RequestAttribute）
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
            // 根据系统配置决定是否要求 TOTP
            boolean requireTotp = systemConfigService.isTotpEnabledForModule(moduleKey);
            if (!requireTotp) {
                log.debug("模块 {} 的 TOTP 已禁用，跳过验证", moduleKey);
                return joinPoint.proceed();
            }
        }
        
        // 检查用户是否已启用 TOTP
        // 如果用户未启用 TOTP，直接放行（要求未启用 TOTP 的用户输入验证码毫无意义）
        if (!totpService.isTotpEnabled(userId)) {
            log.debug("用户未启用 TOTP，跳过验证: userId={}", userId);
            return joinPoint.proceed();
        }
        
        // 获取 HttpServletRequest
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(500, "无法获取请求上下文");
        }
        HttpServletRequest request = attributes.getRequest();
        
        // 检查是否有有效的 TOTP 会话
        // 注意：会话创建在 TotpController.verifyTotp 中也会执行，
        // 所以如果用户先调用了 /api/auth/totp/verify 成功，这里会话应该有效
        boolean sessionValid = totpSessionService.isSessionValid(userId);
        log.debug("TOTP 会话检查: userId={}, sessionValid={}", userId, sessionValid);
        
        if (sessionValid) {
            log.debug("TOTP 会话有效，跳过二次验证: userId={}", userId);
            
            // 将 TOTP 会话状态添加到响应头，供前端使用
            try {
                attributes.getResponse().setHeader("X-TOTP-Session", "valid");
            } catch (Exception e) {
                log.warn("设置 TOTP 会话响应头失败", e);
            }
            
            return joinPoint.proceed();
        }
        
        // 获取 TOTP 验证码（从请求头）
        String totpCode = request.getHeader("X-TOTP-Code");
        
        if (totpCode == null || totpCode.trim().isEmpty()) {
            log.warn("敏感操作缺少 TOTP 验证码: userId={}, method={}", 
                userId, joinPoint.getSignature().getName());
            // 使用 401 状态码，让前端知道需要提供 TOTP 验证码
            throw new BusinessException(401, "此操作需要双因素认证，请提供 TOTP 验证码");
        }
        
        log.debug("收到 TOTP 验证码: userId={}, codeLength={}", userId, totpCode.length());
        
        // 验证 TOTP 码
        String code = totpCode.trim();
        boolean isValid = totpService.verifyTotpCode(userId, code);
        
        if (!isValid) {
            // 尝试作为备份码验证
            log.debug("TOTP 验证失败，尝试备份码验证: userId={}", userId);
            isValid = totpService.verifyBackupCode(userId, code);
        }
        
        if (!isValid) {
            log.warn("TOTP 验证码错误: userId={}, codeLength={}", userId, code.length());
            throw new BusinessException(401, "TOTP 验证码错误或已过期");
        }
        
        // 验证成功，创建会话
        totpSessionService.createSession(userId);
        // 验证会话创建成功后再执行目标方法
        boolean sessionCreated = totpSessionService.isSessionValid(userId);
        log.info("TOTP 验证成功: userId={}, operation={}, sessionCreated={}", 
            userId, getOperationDescription(joinPoint), sessionCreated);
        
        if (!sessionCreated) {
            log.error("TOTP 会话创建失败: userId={}", userId);
            throw new BusinessException(500, "TOTP 会话创建失败，请稍后重试");
        }
        
        // 将 TOTP 会话状态添加到响应头，供前端使用
        try {
            attributes.getResponse().setHeader("X-TOTP-Session", "valid");
            attributes.getResponse().setHeader("X-TOTP-Session-Expires", String.valueOf(System.currentTimeMillis() + 5 * 60 * 1000));
        } catch (Exception e) {
            log.warn("设置 TOTP 会话响应头失败", e);
        }
        
        // 执行目标方法
        return joinPoint.proceed();
    }
    
    /**
     * 获取当前用户ID
     */
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
    
    /**
     * 获取操作描述
     */
    private String getOperationDescription(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireTotp annotation = method.getAnnotation(RequireTotp.class);
        return annotation != null ? annotation.operation() : method.getName();
    }
}
