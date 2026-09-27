package com.leo.myblog.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private final Environment environment;

    public JwtUtil(Environment environment) {
        this.environment = environment;
    }

    /**
     * 启动期校验签名密钥
     * <p>
     * 分两段，是因为它们的适用范围不同：
     * <ol>
     *   <li><b>永远执行</b>：空白或不足 32 字节直接拒绝启动。这能兜住
     *       {@code JWT_SECRET=}（注入了一个空串）这类真实事故 —— 否则应用会**正常启动**，
     *       直到第一次签发 token 才抛 jjwt 的 WeakKeyException，表现为一整片 500，
     *       而且报错信息完全不指向根因。</li>
     *   <li><b>仅 prod</b>：要求环境变量确实存在。配置里的默认值是**公开的**
     *       （就写在 application.yml 里），用它启动等于任何人可伪造 admin token。
     *       这条不能无条件执行 —— 本地开发与 {@code @SpringBootTest} 都没有这个环境变量，
     *       无条件拒绝会把全部集成测试打挂。</li>
     * </ol>
     * 已知残留风险：prod profile 未激活（例如不走安装脚本直接 {@code java -jar}）时第 2 段会被跳过。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置。请设置 JWT_SECRET 环境变量（至少 32 字节的随机串）。");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足：当前 " + length + " 字节，要求至少 32 字节。请设置更长的 JWT_SECRET。");
        }

        String fromEnv = System.getenv("JWT_SECRET");
        boolean envProvided = fromEnv != null && !fromEnv.isBlank();

        if (!envProvided && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "生产环境必须通过 JWT_SECRET 环境变量提供 jwt.secret。"
                            + "当前用的是配置文件里的默认值，而它是公开的（写在 application.yml 里），"
                            + "用它启动意味着任何人都能伪造管理员 token。");
        }

        log.info("JWT 密钥校验通过：长度 {} 字节，来源 {}", length, envProvided ? "环境变量" : "配置文件默认值（仅限开发）");
    }

    /**
     * 生成密钥
     */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 生成Token
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }
    
    /**
     * 从Token中获取Claims
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }
    
    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }
    
    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取Token剩余有效期（秒）
     */
    public long getRemainingExpiration(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remainingMs / 1000); // 转换为秒
        } catch (Exception e) {
            return 0;
        }
    }
}
