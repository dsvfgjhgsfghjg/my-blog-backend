package com.leo.myblog.config;

import com.leo.myblog.service.TokenBlacklistService;
import com.leo.myblog.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    
    // 公开路径列表（无需认证即可访问，登录页需要绕过黑名单检查）
    // 注意：PUT/POST/DELETE 方法的管理接口不应加入此列表，
    // 否则 JWT 过滤器会跳过 Token 解析，导致 @RequireAdmin 等注解无法获取 userId
    private static final String[] PUBLIC_PATHS = {
        "/api/users/login",
        "/api/users/register",
        // 两步登录：必须跳过，否则浏览器里存有失效 AUTH_TOKEN Cookie 时，
        // 本过滤器会取到该 token、校验失败并返回 403「登录信息已经失效」，
        // 用户将连登录页都提交不了。与 /api/users/login 列入本清单同理。
        "/api/auth/login/",
        "/api/captcha/",
        "/actuator/health",
        "/api/audio/stream/",
        "/api/audio/random",
        "/api/audio/cover/",
        "/api/posts/public",
        "/api/posts/category/",
        "/api/homepage/",
        "/api/comments/post",
        "/api/users/avatar/public/",
        "/api/system/config/public"
    };
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                log.debug("公开路径，跳过JWT过滤器: {}", path);
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求头获取Token
            String token = getTokenFromRequest(request);
                
            if (token != null && jwtUtil.validateToken(token)) {
                // 从 Token中获取用户信息
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                    
                // 检查Token是否在黑名单中
                if (tokenBlacklistService.isBlacklisted(userId, token)) {
                    log.warn("Token已被加入黑名单: userId={}, username={}", userId, username);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"登录信息已经失效，请重新登录\",\"data\":null}");
                    return;
                }
                    
                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    
                // 设置到Security上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                // 将用户ID存入request属性，方便后续使用
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
            } else if (token != null) {
                // Token存在但无效，返回403
                log.warn("无效的Token: {}", token.substring(0, Math.min(20, token.length())));
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"登录信息已经失效，请重新登录\",\"data\":null}");
                return;
            }
            // 如果token为null，继续执行（允许公开接口访问）
        } catch (Exception e) {
            log.error("JWT认证失败：{}", e.getMessage());
            // 认证异常，返回401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"认证失败\",\"data\":null}");
            return;
        }
            
        filterChain.doFilter(request, response);
    }
    
    /**
     * 从请求头或 Cookie 中提取 JWT Token
     * 优先从 Authorization header，兜底从 HttpOnly Cookie
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 1. 优先从 Authorization header 提取
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // 2. 兜底从 HttpOnly Cookie 提取（前端用 Cookie 模式时）
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieName = getCookieName();
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (token != null && !token.isEmpty()) {
                        log.debug("从HttpOnly Cookie中提取到Token");
                        return token;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取Cookie名称（与 CookieSecurityConfig 保持一致）
     */
    private String getCookieName() {
        return "AUTH_TOKEN";
    }
}
