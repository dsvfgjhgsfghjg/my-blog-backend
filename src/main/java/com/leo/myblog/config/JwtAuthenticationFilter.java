package com.leo.myblog.config;

import com.leo.myblog.service.TokenBlacklistService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.service.SecurityMetricsService;
import com.leo.myblog.entity.User;
import com.leo.myblog.security.SecurityResponseWriter;
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
    private final CookieSecurityConfig cookieSecurityConfig;
    private final UserCacheService userCacheService;
    private final SecurityMetricsService securityMetricsService;

    /**
     * 公开路径统一由 {@link PublicPaths} 定义（含按 HTTP 方法区分，
     * 避免把 PUT/DELETE 等管理接口一起跳过 —— 那会导致过滤器不解析 Token，
     * @RequireAdmin 与 @RequestAttribute 拿不到 userId）。
     *
     * <p>本过滤器先于 Spring Security 的授权规则执行，因此这里的判定
     * 直接决定了公开接口是否可达：浏览器里存有失效 Cookie 时，若公开路径
     * 不出现在 {@link PublicPaths} 中，请求会被本过滤器拦成 403
     * 「登录信息已经失效」，SecurityConfig 里的 permitAll 根本没机会生效。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        boolean publicPath = PublicPaths.isPublic(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.validateToken(token)) {
            if (publicPath) {
                filterChain.doFilter(request, response);
            } else {
                securityMetricsService.authenticationFailure("invalid_jwt");
                SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
            }
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        try {
            if (tokenBlacklistService.isBlacklisted(userId, token)) {
                if (publicPath) {
                    filterChain.doFilter(request, response);
                } else {
                    securityMetricsService.authenticationFailure("blacklisted");
                    SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
                }
                return;
            }
        } catch (Exception e) {
            log.error("Token 黑名单检查失败，安全策略拒绝请求: userId={}", userId, e);
            securityMetricsService.authenticationFailure("redis_unavailable");
            SecurityResponseWriter.write(response, 503, "认证服务暂时不可用，请稍后重试");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        User user = userCacheService.getUserById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            if (publicPath) {
                filterChain.doFilter(request, response);
            } else {
                securityMetricsService.authenticationFailure("user_disabled");
                SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
            }
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("userId", userId);
        if (username != null) {
            request.setAttribute("username", username);
        }
        request.setAttribute("isAdmin", Boolean.TRUE.equals(user.getIsAdmin()));

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
     * 获取Cookie名称（委托给 CookieSecurityConfig，避免读写两侧不一致）
     */
    private String getCookieName() {
        return cookieSecurityConfig.getCookieName();
    }
}
