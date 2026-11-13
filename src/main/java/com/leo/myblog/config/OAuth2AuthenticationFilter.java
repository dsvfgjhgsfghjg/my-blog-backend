package com.leo.myblog.config;

import com.leo.myblog.service.OAuth2TokenService;
import com.leo.myblog.service.TokenBlacklistService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.entity.User;
import com.leo.myblog.security.SecurityResponseWriter;
import com.leo.myblog.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * OAuth2 Token 认证过滤器
 *
 * 与 JwtAuthenticationFilter 共存，用于验证 OAuth2 标准的 Bearer Token
 * 同时支持新 OAuth2 端点和老接口的兼容访问
 *
 * 设计说明：
 * - 优先使用 JwtAuthenticationFilter 验证（老的 JWT Token）
 * - 本过滤器作为补充，处理 OAuth2 授权记录中的 Token 验证
 * - 两个过滤器不冲突，SecurityContextHolder 设置后不会被覆盖
 */
@Slf4j
@Component
public class OAuth2AuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final OAuth2TokenService oAuth2TokenService;
    private final CookieSecurityConfig cookieSecurityConfig;
    private final UserCacheService userCacheService;

    public OAuth2AuthenticationFilter(JwtUtil jwtUtil,
                                       TokenBlacklistService tokenBlacklistService,
                                       @Lazy OAuth2TokenService oAuth2TokenService,
                                       CookieSecurityConfig cookieSecurityConfig,
                                       UserCacheService userCacheService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.oAuth2TokenService = oAuth2TokenService;
        this.cookieSecurityConfig = cookieSecurityConfig;
        this.userCacheService = userCacheService;
    }

    /**
     * 公开路径统一由 {@link PublicPaths} 定义（与 SecurityConfig、JwtAuthenticationFilter 共用一份）。
     * 原先把 OAuth2 公开路径和「其他公开路径」拆成两个数组各写一份，已合并。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return false;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 如果已经认证（JwtAuthenticationFilter 已经处理），跳过
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 获取 Token
            String token = getTokenFromRequest(request);

            if (token != null) {
                // 先尝试解析为 JWT
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String username = jwtUtil.getUsernameFromToken(token);

                    // 检查黑名单
                    if (tokenBlacklistService.isBlacklisted(userId, token)) {
                        log.warn("OAuth2 Token 已被加入黑名单: userId={}", userId);
                        if (PublicPaths.isPublic(request)) {
                            filterChain.doFilter(request, response);
                        } else {
                            SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
                        }
                        return;
                    }

                    if (setAuthentication(userId, username, request)) {
                        filterChain.doFilter(request, response);
                    } else if (PublicPaths.isPublic(request)) {
                        filterChain.doFilter(request, response);
                    } else {
                        SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
                    }
                    return;
                }

                // JWT 解析失败，尝试从 OAuth2 授权记录查找
                Long userId = oAuth2TokenService.getUserIdFromToken(token);
                if (userId != null) {
                    log.debug("OAuth2 Token 验证通过（通过授权记录）: userId={}", userId);
                    if (setAuthentication(userId, null, request)) {
                        filterChain.doFilter(request, response);
                    } else if (PublicPaths.isPublic(request)) {
                        filterChain.doFilter(request, response);
                    } else {
                        SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
                    }
                    return;
                }

                if (!PublicPaths.isPublic(request)) {
                    log.warn("OAuth2 Token 无效");
                    SecurityResponseWriter.write(response, 401, "登录信息已经失效，请重新登录");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("OAuth2 认证失败: {}", e.getMessage());
            if (PublicPaths.isPublic(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityResponseWriter.write(response, 401, "认证失败");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 设置认证信息到 SecurityContext
     */
    private boolean setAuthentication(Long userId, String username, HttpServletRequest request) {
        User user = userCacheService.getUserById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return false;
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("userId", userId);
        if (username != null) {
            request.setAttribute("username", username);
        }
        request.setAttribute("isAdmin", Boolean.TRUE.equals(user.getIsAdmin()));
        return true;
    }

    /**
     * 从请求头或 HttpOnly Cookie 中提取 Token
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
            String cookieName = cookieSecurityConfig.getCookieName();
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (token != null && !token.isEmpty()) {
                        log.debug("从HttpOnly Cookie中提取到OAuth2 Token");
                        return token;
                    }
                }
            }
        }

        return null;
    }
}
