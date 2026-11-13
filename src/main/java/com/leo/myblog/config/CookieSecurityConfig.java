package com.leo.myblog.config;

import jakarta.servlet.http.Cookie;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Cookie安全配置
 * 使用HttpOnly Cookie存储JWT Token，防止XSS攻击窃取Token
 */
@Slf4j
@Configuration
public class CookieSecurityConfig {

    private final Environment environment;

    public CookieSecurityConfig(Environment environment) {
        this.environment = environment;
    }
    
    @Value("${jwt.cookie.name:AUTH_TOKEN}")
    private String cookieName;
    
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.cookie.max-age:3600}")
    private int accessCookieMaxAge;

    @Value("${jwt.refresh-cookie.name:REFRESH_TOKEN}")
    private String refreshCookieName;

    @Value("${jwt.refresh-cookie.max-age:2592000}")
    private int refreshCookieMaxAge;

    @Value("${jwt.refresh-cookie.path:/api/auth2}")
    private String refreshCookiePath;

    @PostConstruct
    void validateProductionCookieSecurity() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !cookieSecure) {
            throw new IllegalStateException("Production requires jwt.cookie.secure=true");
        }
    }
    
    /**
     * 创建安全的HttpOnly Cookie
     * 
     * @param token JWT Token
     * @return 配置好的Cookie对象
     */
    public Cookie createSecureCookie(String token) {
        return createCookie(cookieName, token, "/", accessCookieMaxAge);
    }

    public Cookie createRefreshCookie(String token) {
        return createCookie(refreshCookieName, token, refreshCookiePath, refreshCookieMaxAge);
    }
    
    /**
     * 创建清除Cookie（用于登出）
     * 
     * @return 配置为删除的Cookie对象
     */
    public Cookie createClearCookie() {
        return createCookie(cookieName, "", "/", 0);
    }

    public Cookie createClearRefreshCookie() {
        return createCookie(refreshCookieName, "", refreshCookiePath, 0);
    }

    private Cookie createCookie(String name, String value, String path, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Strict");
        log.debug("创建Cookie: name={}, path={}, secure={}, maxAge={}",
                name, path, cookieSecure, maxAge);
        return cookie;
    }
    
    /**
     * 获取Cookie名称
     */
    public String getCookieName() {
        return cookieName;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }
}
