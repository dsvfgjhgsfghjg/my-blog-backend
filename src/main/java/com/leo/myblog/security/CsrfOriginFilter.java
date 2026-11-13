package com.leo.myblog.security;

import com.leo.myblog.config.CookieSecurityConfig;
import com.leo.myblog.service.SecurityMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Validates browser origin on Cookie-authenticated state-changing requests.
 * Bearer-authenticated App requests are exempt because a cross-site page cannot
 * attach Authorization headers without a successful CORS preflight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AllowedOrigins allowedOrigins;
    private final CookieSecurityConfig cookieSecurityConfig;
    private final SecurityMetricsService securityMetricsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())
                || request.getHeader("Authorization") != null
                || !hasAuthCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = allowedOrigins.normalize(request.getHeader("Origin"));
        if (origin == null) {
            origin = originFromReferer(request.getHeader("Referer"));
        }
        if (!allowedOrigins.isAllowed(origin)) {
            log.warn("拒绝 Cookie 写请求的非法 Origin: method={}, uri={}, origin={}",
                    request.getMethod(), request.getRequestURI(), origin);
            securityMetricsService.csrfRejected();
            SecurityResponseWriter.write(response, 403, "请求来源不受信任");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookieSecurityConfig.getCookieName().equals(cookie.getName())
                    || cookieSecurityConfig.getRefreshCookieName().equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }
}
