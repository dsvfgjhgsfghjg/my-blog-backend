package com.leo.myblog.config;

import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cookie安全配置
 * 使用HttpOnly Cookie存储JWT Token，防止XSS攻击窃取Token
 */
@Slf4j
@Configuration
public class CookieSecurityConfig {
    
    @Value("${jwt.cookie.name:AUTH_TOKEN}")
    private String cookieName;
    
    @Value("${jwt.cookie.max-age:86400}")
    private int cookieMaxAge;
    
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;
    
    /**
     * 创建安全的HttpOnly Cookie
     * 
     * @param token JWT Token
     * @return 配置好的Cookie对象
     */
    public Cookie createSecureCookie(String token) {
        Cookie cookie = new Cookie(cookieName, token);
        
        // 核心安全设置：禁止JavaScript访问（防止XSS窃取）
        cookie.setHttpOnly(true);
        
        // 仅通过HTTPS传输（生产环境必须设为true）
        if (cookieSecure) {
            cookie.setSecure(true);
        }
        
        // 限制Cookie作用路径
        cookie.setPath("/");
        
        // 设置过期时间（秒）
        cookie.setMaxAge(cookieMaxAge);
        
        // SameSite属性防止CSRF攻击
        // Strict: 最严格，同站请求才发送
        // Lax: 默认值，导航到目标URL的GET请求会发送
        // None: 跨域请求也会发送（需要Secure=true）
        // 注意：Spring Boot 3.x需要通过响应头手动设置SameSite
        cookie.setAttribute("SameSite", "Strict");
        
        log.debug("创建安全Cookie: name={}, httpOnly=true, secure={}, maxAge={}", cookieName, cookieSecure, cookieMaxAge);
        
        return cookie;
    }
    
    /**
     * 创建清除Cookie（用于登出）
     * 
     * @return 配置为删除的Cookie对象
     */
    public Cookie createClearCookie() {
        Cookie cookie = new Cookie(cookieName, null);
        
        cookie.setHttpOnly(true);
        if (cookieSecure) {
            cookie.setSecure(true);
        }
        cookie.setPath("/");
        cookie.setMaxAge(0);  // 立即过期
        cookie.setAttribute("SameSite", "Strict");
        
        log.debug("创建清除Cookie: name={}, secure={}", cookieName, cookieSecure);
        
        return cookie;
    }
    
    /**
     * 获取Cookie名称
     */
    public String getCookieName() {
        return cookieName;
    }
}
