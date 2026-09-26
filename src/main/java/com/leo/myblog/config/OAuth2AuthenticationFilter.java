package com.leo.myblog.config;

import com.leo.myblog.service.OAuth2TokenService;
import com.leo.myblog.service.TokenBlacklistService;
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

    public OAuth2AuthenticationFilter(JwtUtil jwtUtil,
                                       TokenBlacklistService tokenBlacklistService,
                                       @Lazy OAuth2TokenService oAuth2TokenService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.oAuth2TokenService = oAuth2TokenService;
    }

    /**
     * 公开路径列表（无需认证即可访问）
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth2/token",
            "/api/auth2/oauth/login",
            "/api/auth2/oauth/callback"
    };

    // 其他公开路径（与 SecurityConfig 中的公开接口保持一致）
    private static final String[] OTHER_PUBLIC_PATHS = {
        "/api/users/login",
        "/api/users/register",
        // 两步登录：必须跳过，否则浏览器里存有失效 AUTH_TOKEN Cookie 时，
        // 过滤器会取到该 token、校验失败并直接返回 403「登录信息已经失效」，
        // 导致用户连登录页都提交不了（前端 403 处理还会把他重定向回 /login，形成死循环）。
        // 与 /api/users/login、/api/auth2/token 被列入本清单是同一个原因。
        "/api/auth/login/",
        "/api/captcha/",
        "/actuator/health",
        "/api/images/stream/",
        "/api/audio/stream/",
        "/api/audio/random",
        "/api/audio/cover/",
        "/api/blogger/info",
        "/api/blogger/avatar",
        "/api/posts/public",
        "/api/posts/category/",
        "/api/homepage/",
        "/api/comments/post",
        "/api/users/avatar/public/",
        "/api/system/config/public",
        "/api/public/sayings/"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // OAuth2 公开路径跳过
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                log.debug("OAuth2 公开路径，跳过过滤器: {}", path);
                return true;
            }
        }

        // 其他公开接口跳过（避免浏览器携带过期 Cookie 导致 403）
        for (String publicPath : OTHER_PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                log.debug("OAuth2 过滤器跳过公开路径: {}", path);
                return true;
            }
        }

        // OPTIONS 请求跳过
        if ("OPTIONS".equalsIgnoreCase(method)) {
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
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"登录信息已经失效，请重新登录\",\"data\":null}");
                        return;
                    }

                    setAuthentication(userId, username, request);
                    filterChain.doFilter(request, response);
                    return;
                }

                // JWT 解析失败，尝试从 OAuth2 授权记录查找
                Long userId = oAuth2TokenService.getUserIdFromToken(token);
                if (userId != null) {
                    log.debug("OAuth2 Token 验证通过（通过授权记录）: userId={}", userId);
                    setAuthentication(userId, null, request);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Token 无效
                log.warn("OAuth2 Token 无效");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"登录信息已经失效，请重新登录\",\"data\":null}");
                return;
            }
        } catch (Exception e) {
            log.error("OAuth2 认证失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"认证失败\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 设置认证信息到 SecurityContext
     */
    private void setAuthentication(Long userId, String username, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("userId", userId);
        if (username != null) {
            request.setAttribute("username", username);
        }
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
            for (Cookie cookie : cookies) {
                if ("AUTH_TOKEN".equals(cookie.getName())) {
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
