package com.leo.myblog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import com.leo.myblog.security.AllowedOrigins;
import com.leo.myblog.security.CsrfOriginFilter;
import com.leo.myblog.security.RestAccessDeniedHandler;
import com.leo.myblog.security.RestAuthenticationEntryPoint;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationFilter oAuth2AuthenticationFilter;
    private final CsrfOriginFilter csrfOriginFilter;
    private final AllowedOrigins allowedOrigins;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Lazy OAuth2AuthenticationFilter oAuth2AuthenticationFilter,
                          CsrfOriginFilter csrfOriginFilter,
                          AllowedOrigins allowedOrigins,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2AuthenticationFilter = oAuth2AuthenticationFilter;
        this.csrfOriginFilter = csrfOriginFilter;
        this.allowedOrigins = allowedOrigins;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 密码编码器（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置HTTP防火墙，允许URL中包含双斜杠
     * 解决 RequestRejectedException: The request was rejected because the URL contained a potentially malicious String "//"
     */
    @Bean
    public HttpFirewall allowDoubleSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        // 允许URL中包含双斜杠
        firewall.setAllowUrlEncodedDoubleSlash(true);
        return firewall;
    }

    /**
     * 安全过滤链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（使用JWT不需要）
            .csrf(csrf -> csrf.disable())

            // 配置CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 设置会话管理为无状态
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))

            // 安全修复V8/V9: 添加安全响应头
            .headers(headers -> headers
                // CSP - Content Security Policy（防止XSS）
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self'; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none';"  // 禁止iframe嵌入
                    )
                )
                // HSTS - HTTP Strict Transport Security（强制HTTPS）
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)  // 1年
                    .preload(true)
                )
                // X-Frame-Options（防止点击劫持）
                .frameOptions(frame -> frame.deny())
                // X-Content-Type-Options（防止MIME类型嗅探）
                .contentTypeOptions(contentType -> {})
                // X-XSS-Protection（XSS防护，现代浏览器已废弃但保留兼容）
                .xssProtection(xss -> xss.disable())
                // Referrer-Policy（控制引用信息）
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Permissions-Policy（控制浏览器功能）
                .permissionsPolicy(permissions -> permissions.policy(
                    "geolocation=(), microphone=(), camera=()"  // 禁用地理位置、麦克风、摄像头
                ))
            )

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开路径统一白名单（含按 HTTP 方法区分），与 JwtAuthenticationFilter /
                // OAuth2AuthenticationFilter 的 shouldNotFilter 共用同一份定义。
                // 必须声明在最前面：这里的规则是「首个命中即生效」，
                // 下面 /api/auth2/token、/api/auth/app/bind 等 permitAll 正是靠声明顺序
                // 抢在 /api/auth2/**、/api/auth/app/** 的 authenticated() 之前。
                .requestMatchers(PublicPaths::isPublic).permitAll()

                // 公开接口
                .requestMatchers("/api/users/register", "/api/users/login").permitAll()

                // 两步登录公开接口
                .requestMatchers("/api/auth/login/**").permitAll()

                // 验证码公开接口（登录前需要）
                .requestMatchers("/api/captcha/**").permitAll()           // 滑块验证码

                // TOTP 双因素认证接口（需要登录）
                .requestMatchers("/api/auth/totp/**").authenticated()

                // 验证服务接口（需要登录）
                .requestMatchers("/api/auth/verify/**").authenticated()

                // Actuator 健康检查（Docker 健康检查需要）
                .requestMatchers("/actuator/health").permitAll()

                // 文章图片公开接口（通过后端代理访问 MinIO 中的图片）
                .requestMatchers("/api/images/stream/**").permitAll() // 文章图片（公开访问）

                // 音频相关公开接口
                .requestMatchers("/api/audio/stream/**").permitAll()  // 音频流式播放
                .requestMatchers("/api/audio/random").permitAll()     // 随机获取音频ID
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/audio/cover/**").permitAll()   // 专辑封面图片（GET公开）
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/audio/{audioId}").permitAll()  // 音频元数据（GET公开）

                // 博客页面公开接口（仅允许 GET 请求公开访问，写操作需要认证）
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/blogger/info").permitAll()     // 博主信息（GET）
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/blogger/avatar").permitAll()   // 博主头像（GET）

                // 文章公开接口
                .requestMatchers("/api/posts/public").permitAll()     // 公开文章列表
                .requestMatchers("/api/posts/category/**").permitAll() // 分类文章列表（软件开发、AI、网络安全）
                .requestMatchers("/api/posts/{postId}").permitAll()   // 文章详情（访客可查看已发布文章）
                .requestMatchers("/api/posts/categories").authenticated()  // 分类列表需要登录

                // 首页公开接口
                .requestMatchers("/api/homepage/**").permitAll()      // 首页数据（文章、分类等）

                // 评论公开接口
                .requestMatchers("/api/comments/post").permitAll()   // 获取文章评论列表（公开）

                // 用户头像公开接口（用于评论区等场景）
                .requestMatchers("/api/users/avatar/public/**").permitAll()   // 公开头像访问

                // 系统配置公开接口
                .requestMatchers("/api/system/config/public").permitAll()  // 公开配置（前端可见）

                // 名言名句公开接口
                .requestMatchers("/api/public/sayings/**").permitAll()     // 名言名句（公开访问）

                // OAuth2 认证公开接口
                .requestMatchers("/api/auth2/token").permitAll()           // OAuth2 颁发 Token
                // 刷新与撤销各自以 refresh_token / 待撤销的 token 本身作为凭证，
                // 不能要求先有有效的 access_token —— 那正是这两个端点要解决的问题
                // （access_token 过期时才需要刷新；Cookie 里的 token 失效时仍要能登出并清 Cookie）
                .requestMatchers("/api/auth2/token/refresh").permitAll()   // 刷新 Token
                .requestMatchers("/api/auth2/token/revoke").permitAll()    // 撤销 Token（登出）
                // App 双因素认证公开接口
                .requestMatchers("/api/auth/app/bind").permitAll()        // App 密码绑定（公开）
                .requestMatchers("/api/auth/app/device-secret").permitAll() // 设备码生成（公开，设备未绑定时调用）
                .requestMatchers("/ws/app/auth/native").permitAll()      // App WebSocket（公开，内部认证）

                
                // App 双因素认证需要登录的接口
                .requestMatchers("/api/auth/app/**").authenticated()     // 其他 App 接口需要登录

                // OAuth2 内部接口（需要认证）
                .requestMatchers("/api/auth2/**").authenticated()

                // 管理员接口（需要根据isAdmin字段判断，这里先允许所有认证用户访问）
                .requestMatchers("/api/users/admin/**").authenticated()

                // 其他接口需要认证
                .anyRequest().authenticated()
            )

            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // OAuth2 opaque token fallback runs after the standard username/password slot.
            .addFilterAfter(oAuth2AuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Origin validation only needs request headers and cookies.
            .addFilterBefore(csrfOriginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置源
     * 允许前端跨域访问后端API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源（生产环境应该替换为实际域名）
        // 注意：当 allowCredentials=true 时，不能使用 "*"，必须明确指定源
        configuration.setAllowedOrigins(allowedOrigins.getOrigins());

        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许携带认证信息（cookies、authorization headers等）
        configuration.setAllowCredentials(true);

        // 预检请求的有效期（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
