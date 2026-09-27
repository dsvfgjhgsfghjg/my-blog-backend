package com.leo.myblog.aspect;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.exception.ForbiddenException;
import com.leo.myblog.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 管理员权限切面
 * 验证：1. Token中的userId与当前用户一致 2. 该用户是管理员
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAspect {
    
    private final UserCacheService userCacheService;
    
    @Around("@annotation(requireAdmin)")
    public Object checkAdminPermission(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        // 获取当前请求的 userId（从 RequestAttribute 中获取，由 JwtAuthenticationFilter 设置）
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException("无法获取请求上下文");
        }
        
        Long tokenUserId = (Long) attributes.getRequest().getAttribute("userId");
        if (tokenUserId == null) {
            log.warn("Token中未找到userId，拒绝访问");
            throw new BusinessException("未认证或Token无效");
        }
        
        // 从 SecurityContext 获取当前用户ID
        Long securityUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // 验证：Token中的userId必须与SecurityContext中的userId一致
        if (!tokenUserId.equals(securityUserId)) {
            log.warn("Token userId不匹配: tokenUserId={}, securityUserId={}", tokenUserId, securityUserId);
            // 与 JwtAuthenticationFilter / OAuth2AuthenticationFilter 里同样的场景：
            // 那两处直接写 HTTP 403 + 同一条消息，这里保持一致（前端按这条消息清会话回登录页）
            throw new ForbiddenException("登录信息已经失效，请重新登录");
        }
        
        // 使用缓存查询用户信息
        User user = userCacheService.getUserById(tokenUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 检查是否为管理员
        if (!user.getIsAdmin()) {
            log.warn("非管理员尝试访问管理员接口: userId={}, username={}", user.getId(), user.getUsername());
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        
        log.debug("管理员权限验证通过: userId={}, username={}", user.getId(), user.getUsername());
        return joinPoint.proceed();
    }
}
