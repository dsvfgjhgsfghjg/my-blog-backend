package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.config.CookieSecurityConfig;
import com.leo.myblog.dto.request.OAuth2TokenRequest;
import com.leo.myblog.dto.request.ThirdPartyLoginRequest;
import com.leo.myblog.dto.response.AbilityResponse;
import com.leo.myblog.dto.response.OAuth2TokenResponse;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.*;
import com.leo.myblog.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth 2.0 认证控制器
 *
 * 提供标准 OAuth 2.0 Token 端点，同时作为系统的 认证API 2.0
 * 兼容现有认证体系，支持多种 grant_type
 *
 * 端点列表:
 * - POST /api/auth2/token          颁发 Token（password/client_credentials/authorization_code）
 * - POST /api/auth2/token/refresh  刷新 Token
 * - POST /api/auth2/token/revoke   撤销 Token
 * - GET  /api/auth2/token/info     查询 Token 信息
 * - POST /api/auth2/authorize      创建授权码（第三方应用接入）
 * - GET  /api/auth2/user           获取当前用户信息
 * - POST /api/auth2/oauth/login    第三方平台登录（GitHub/Google）
 * - POST /api/auth2/oauth/callback 第三方平台回调
 */
@Slf4j
@RestController
@RequestMapping("/api/auth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2TokenService oAuth2TokenService;
    private final JwtUtil jwtUtil;
    private final UserCacheService userCacheService;
    private final CookieSecurityConfig cookieSecurityConfig;
    private final AbilityService abilityService;

    @Value("${jwt.cookie.expose-token-in-body:true}")
    private boolean exposeTokenInBody;

    /**
     * 颁发 Token
     *
     * 支持 grant_type:
     * - password: 用户名密码 + 验证码登录
     * - client_credentials: 客户端凭证模式（M2M）
     * - authorization_code: 授权码模式（第三方应用接入）
     */
    @PostMapping("/token")
    public Result<OAuth2TokenResponse> token(@Valid @RequestBody OAuth2TokenRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("OAuth2 Token 请求: grantType={}, username={}, clientId={}",
                request.getGrantType(), request.getUsername(), request.getClientId());

        OAuth2TokenResponse response = oAuth2TokenService.issueToken(request, clientIp, userAgent);

        // 将 Token 设置到 HttpOnly Cookie 中（防止 XSS 攻击窃取 Token）
        if (response.getAccessToken() != null) {
            Cookie authCookie = cookieSecurityConfig.createSecureCookie(response.getAccessToken());
            httpResponse.addCookie(authCookie);
        }

        // 根据配置决定是否在 JSON 响应体中暴露 Token
        if (!exposeTokenInBody) {
            response.setAccessToken(null);
            response.setTokenType(null);
            response.setRefreshToken(null);
        }

        return Result.success("Token 颁发成功", response);
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/token/refresh")
    public Result<OAuth2TokenResponse> refreshToken(@Valid @RequestBody OAuth2TokenRequest request,
                                                     HttpServletResponse httpResponse) {
        log.info("OAuth2 刷新 Token 请求");

        // 复用 OAuth2TokenService 的 refresh_token grant
        request.setGrantType("refresh_token");
        OAuth2TokenResponse response = oAuth2TokenService.issueToken(request, null, null);

        // 将新 Token 设置到 HttpOnly Cookie 中
        if (response.getAccessToken() != null) {
            Cookie authCookie = cookieSecurityConfig.createSecureCookie(response.getAccessToken());
            httpResponse.addCookie(authCookie);
        }

        // 根据配置决定是否在 JSON 响应体中暴露 Token
        if (!exposeTokenInBody) {
            response.setAccessToken(null);
            response.setTokenType(null);
            response.setRefreshToken(null);
        }

        return Result.success("Token 刷新成功", response);
    }

    /**
     * 撤销 Token（登出）
     * <p>
     * access_token 可放在请求体，也可省略 —— 省略时从 HttpOnly Cookie 中取。
     * Cookie 模式下前端读不到 access_token，因此不接受"没带 token 就报错"，
     * 否则每次登出都会失败且 Cookie 无法被清除。
     */
    @PostMapping("/token/revoke")
    public Result<Void> revokeToken(@RequestBody(required = false) Map<String, String> body,
                                     HttpServletRequest request,
                                     HttpServletResponse httpResponse) {
        String accessToken = body != null ? body.get("access_token") : null;
        if (accessToken == null || accessToken.trim().isEmpty()) {
            // 兜底：从 Cookie / Authorization 头取
            accessToken = extractBearerToken(request);
        }

        if (accessToken == null || accessToken.trim().isEmpty()) {
            log.info("OAuth2 登出请求：未携带 access_token，仅清除 Cookie");
        } else {
            log.info("OAuth2 撤销 Token 请求");
            oAuth2TokenService.revokeToken(accessToken);
        }

        // 清除 HttpOnly Cookie
        Cookie clearCookie = cookieSecurityConfig.createClearCookie();
        httpResponse.addCookie(clearCookie);

        return Result.success("Token 已撤销", null);
    }

    /**
     * 查询 Token 信息
     * 需要 Authorization header 中的 Bearer token
     */
    @GetMapping("/token/info")
    public Result<Map<String, Object>> tokenInfo(HttpServletRequest request) {
        String accessToken = extractBearerToken(request);
        if (accessToken == null) {
            return Result.error(401, "缺少 access_token");
        }

        try {
            Long userId = jwtUtil.getUserIdFromToken(accessToken);
            String username = jwtUtil.getUsernameFromToken(accessToken);
            long remainingExpiration = jwtUtil.getRemainingExpiration(accessToken);

            Map<String, Object> info = Map.of(
                    "user_id", userId,
                    "username", username,
                    "expires_in", remainingExpiration,
                    "active", true
            );
            return Result.success(info);
        } catch (Exception e) {
            // 尝试从 OAuth2 授权记录中查找
            Long userId = oAuth2TokenService.getUserIdFromToken(accessToken);
            if (userId != null) {
                Map<String, Object> info = Map.of(
                        "user_id", userId,
                        "active", true
                );
                return Result.success(info);
            }
            return Result.error(401, "Token 无效或已过期");
        }
    }

    /**
     * 创建授权码（用于 authorization_code 流程）
     * 需要用户已登录（携带 Bearer token）
     */
    @PostMapping("/authorize")
    public Result<Map<String, String>> authorize(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String clientId = body.get("client_id");
        String redirectUri = body.get("redirect_uri");
        String scopeStr = body.get("scope");

        // 获取当前登录用户
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (clientId == null || redirectUri == null) {
            return Result.error(400, "client_id 和 redirect_uri 不能为空");
        }

        java.util.List<String> scopes = scopeStr != null ?
                java.util.Arrays.asList(scopeStr.split(",")) : null;

        String code = oAuth2TokenService.createAuthorizationCode(userId, clientId, redirectUri, scopes);

        return Result.success("授权码创建成功",
                java.util.Map.of("code", code, "redirect_uri", redirectUri));
    }

    /**
     * 获取当前用户信息（从 OAuth2 Token 中解析）
     */
    @GetMapping("/user")
    public Result<Map<String, Object>> currentUser(HttpServletRequest request) {
        String accessToken = extractBearerToken(request);
        if (accessToken == null) {
            return Result.error(401, "缺少 access_token");
        }

        try {
            Long userId = jwtUtil.getUserIdFromToken(accessToken);
            User user = userCacheService.getUserById(userId);

            if (user == null) {
                return Result.error(404, "用户不存在");
            }

            // 能力列表（仅能力名，供前端 hasAbility() 判断；管理员由 is_admin 短路，不依赖此列表）
            List<String> abilities = abilityService.getUserAbilities(userId).stream()
                    .map(AbilityResponse::getName)
                    .collect(Collectors.toList());

            Map<String, Object> userInfo = Map.of(
                    "user_id", user.getId(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname() != null ? user.getNickname() : "",
                    "avatar", user.getAvatar() != null ? user.getAvatar() : "",
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "is_admin", user.getIsAdmin() != null ? user.getIsAdmin() : false,
                    "abilities", abilities
            );
            return Result.success(userInfo);
        } catch (Exception e) {
            return Result.error(401, "Token 无效或已过期");
        }
    }

    /**
     * 第三方 OAuth 登录入口
     * 前端调用此接口获取第三方登录的跳转 URL
     */
    @PostMapping("/oauth/login")
    public Result<Map<String, String>> oauthLogin(@Valid @RequestBody ThirdPartyLoginRequest request) {
        String provider = request.getProvider();
        String redirectUri = request.getRedirectUri();

        log.info("第三方 OAuth 登录请求: provider={}", provider);

        // 目前支持 GitHub 和 Google
        if (!"github".equals(provider) && !"google".equals(provider)) {
            return Result.error(400, "不支持的第三方平台: " + provider);
        }

        // 返回授权页面的跳转 URL（前端处理跳转）
        String authorizeUrl = buildAuthorizeUrl(provider, redirectUri);
        return Result.success("获取授权URL成功",
                java.util.Map.of("authorize_url", authorizeUrl));
    }

    // ==================== 内部方法 ====================

    /**
     * 从请求中提取 Bearer Token
     * 优先从 Authorization header，兜底从 HttpOnly Cookie
     */
    private String extractBearerToken(HttpServletRequest request) {
        // 1. 优先从 Authorization header 提取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // 2. 兜底从 HttpOnly Cookie 提取（前端用 Cookie 模式时）
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (cookieSecurityConfig.getCookieName().equals(cookie.getName())) {
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
     * 从当前请求中获取登录用户 ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String accessToken = extractBearerToken(request);
        if (accessToken == null) {
            return null;
        }
        try {
            return jwtUtil.getUserIdFromToken(accessToken);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建第三方授权 URL
     */
    private String buildAuthorizeUrl(String provider, String redirectUri) {
        // 这些参数应该从配置中读取，这里简化处理
        String clientId = "";
        String callbackUrl = "";

        if ("github".equals(provider)) {
            clientId = System.getenv("GITHUB_CLIENT_ID");
            if (clientId == null || clientId.isEmpty()) {
                clientId = "${GITHUB_CLIENT_ID}";
            }
            callbackUrl = System.getenv("GITHUB_REDIRECT_URI");
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                callbackUrl = "http://localhost:3000/auth/oauth/github/callback";
            }
            return "https://github.com/login/oauth/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + callbackUrl +
                    "&scope=user:email" +
                    "&state=" + (redirectUri != null ? redirectUri : "");
        } else if ("google".equals(provider)) {
            clientId = System.getenv("GOOGLE_CLIENT_ID");
            if (clientId == null || clientId.isEmpty()) {
                clientId = "${GOOGLE_CLIENT_ID}";
            }
            callbackUrl = System.getenv("GOOGLE_REDIRECT_URI");
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                callbackUrl = "http://localhost:3000/auth/oauth/google/callback";
            }
            return "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + callbackUrl +
                    "&response_type=code" +
                    "&scope=openid%20email%20profile" +
                    "&state=" + (redirectUri != null ? redirectUri : "");
        }

        throw new BusinessException("不支持的第三方平台: " + provider);
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
