package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.OAuth2TokenRequest;
import com.leo.myblog.dto.response.OAuth2TokenResponse;
import com.leo.myblog.entity.OAuth2Authorization;
import com.leo.myblog.entity.OAuth2Client;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.OAuth2AuthorizationMapper;
import com.leo.myblog.util.JwtUtil;
import com.leo.myblog.util.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * OAuth2 Token 颁发/刷新/撤销核心服务
 *
 * 支持 Grant Types:
 * - password: 用户名密码登录
 * - refresh_token: 刷新 Token
 * - client_credentials: 客户端凭证模式（M2M）
 * - authorization_code: 授权码模式（第三方应用接入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final OAuth2ClientService oAuth2ClientService;
    private final OAuth2AuthorizationMapper authorizationMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenSessionService tokenSessionService;
    private final SliderCaptchaService sliderCaptchaService;
    private final LoginRateLimitService loginRateLimitService;
    private final LoginLogService loginLogService;
    private final UserCacheService userCacheService;
    private final UserAgentParser userAgentParser;
    private final IpBanService ipBanService;

    @Value("${oauth2.access-token.expires-in:3600}")
    private long accessTokenExpiresIn;       // 默认1小时

    @Value("${oauth2.refresh-token.expires-in:2592000}")
    private long refreshTokenExpiresIn;      // 默认30天

    @Value("${oauth2.authorization-code.expires-in:300}")
    private long authorizationCodeExpiresIn; // 授权码5分钟有效

    /**
     * 颁发 Token 主入口
     */
    public OAuth2TokenResponse issueToken(OAuth2TokenRequest request, String clientIp, String userAgent) {
        String grantType = request.getGrantType();

        return switch (grantType) {
            case "password" -> handlePasswordGrant(request, clientIp, userAgent);
            case "refresh_token" -> handleRefreshTokenGrant(request);
            case "client_credentials" -> handleClientCredentialsGrant(request);
            case "authorization_code" -> handleAuthorizationCodeGrant(request);
            default -> throw new BusinessException("不支持的 grant_type: " + grantType);
        };
    }

    /**
     * 为已完成身份校验的用户直接颁发 Token（两步登录专用）
     * <p>
     * 与 {@link #issueToken} 的区别：<b>不重复校验旋转验证码、IP 封禁与登录频率限制</b>。
     * 两步登录的两条路径（step2-password 的密码校验、认证器挑战批准的轮询）都已在各自
     * 环节完成安全校验，若再走 {@code issueToken} 会重复校验验证码并重复累计失败计数。
     * <p>
     * 产出与 {@code POST /api/auth2/token} 完全一致：JWT access_token + rt_ 前缀 refresh_token，
     * 并写入 oauth2_authorization（只落 sha256 哈希），从而复用整条 Token 刷新链路。
     * <p>
     * 注意：本方法只签发 Token，不碰 Cookie —— Cookie 由 Controller 用
     * {@code CookieSecurityConfig.createSecureCookie} 写入响应。
     *
     * @param user      已完成身份校验的用户
     * @param grantType 授权类型，"password" / "authenticator_app"，仅用于审计归因
     * @param clientIp  客户端 IP（记录设备会话用；WS 线程里传 null 表示拿不到真实 IP）
     * @param userAgent 客户端 User-Agent（同上）
     */
    public OAuth2TokenResponse issueTokenForUser(User user, String grantType,
                                                 String clientIp, String userAgent) {
        if (user == null || user.getId() == null) {
            throw new BusinessException("用户不存在");
        }
        return createTokenResponse(user, null, grantType, clientIp, userAgent);
    }

    // ==================== Password Grant ====================

    /**
     * 密码模式：用户名 + 密码 + 验证码
     */
    private OAuth2TokenResponse handlePasswordGrant(OAuth2TokenRequest request, String clientIp, String userAgent) {
        // 1. 验证码校验（同现有登录逻辑）
        if (request.getCaptchaNonceStr() == null || request.getCaptchaAngle() == null) {
            throw new BusinessException("请完成旋转验证码");
        }

        List<SliderCaptchaService.TrajectoryPoint> trajectory = null;
        if (request.getCaptchaTrajectory() != null) {
            trajectory = request.getCaptchaTrajectory().stream()
                    .map(p -> new SliderCaptchaService.TrajectoryPoint(p.getX(), p.getY(), p.getT()))
                    .toList();
        }

        boolean captchaPassed = sliderCaptchaService.verifyCaptcha(
                request.getCaptchaNonceStr(),
                request.getCaptchaAngle(),
                trajectory,
                request.getCaptchaTotalTime()
        );

        if (!captchaPassed) {
            throw new BusinessException("验证码验证失败，请重试");
        }

        // 2. IP封禁检查：如果IP被封禁，直接拒绝登录
        if (ipBanService.isIpBanned(clientIp)) {
            loginLogService.recordLoginFailure(null, clientIp, userAgent, "IP已被封禁");
            log.warn("IP被封禁，拒绝OAuth2登录: ip={}", clientIp);
            throw new BusinessException("IP已被封禁，无法登录");
        }

        // 3. 用户名密码验证
        String username = request.getUsername().trim().toLowerCase();
        String password = request.getPassword();

        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        // 登录频率限制
        if (!loginRateLimitService.isLoginAllowed(username, clientIp)) {
            long remainingTime = loginRateLimitService.getRemainingLockTime(username, clientIp);
            loginLogService.recordLoginFailure(username, clientIp, userAgent, "登录频繁，已被锁定");
            throw new BusinessException("登录过于频繁，请" + remainingTime + "秒后再试");
        }

        // 查询用户
        User user = userService.getUserByUsername(username);
        if (user == null) {
            loginRateLimitService.recordLoginFailure(username, clientIp);
            loginLogService.recordLoginFailure(username, clientIp, userAgent, "用户名不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            loginRateLimitService.recordLoginFailure(username, clientIp);
            loginLogService.recordLoginFailure(username, clientIp, userAgent, "账号已被禁用");
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        if (!userService.verifyPassword(user.getId(), password)) {
            loginRateLimitService.recordLoginFailure(username, clientIp);
            loginLogService.recordLoginFailure(username, clientIp, userAgent, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 登录成功
        loginRateLimitService.recordLoginSuccess(username, clientIp);
        loginLogService.recordLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);

        // 5. 生成 Token 并记录会话（传递客户端IP和UA以记录设备信息）
        return createTokenResponse(user, null, "password", clientIp, userAgent);
    }

    // ==================== Refresh Token Grant ====================

    /**
     * 刷新 Token
     */
    @Transactional(rollbackFor = Exception.class)
    public OAuth2TokenResponse handleRefreshTokenGrant(OAuth2TokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new BusinessException("refresh_token 不能为空");
        }

        // 找到对应的授权记录
        String refreshTokenHash = sha256(refreshToken);
        LambdaQueryWrapper<OAuth2Authorization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OAuth2Authorization::getRefreshTokenHash, refreshTokenHash);
        wrapper.eq(OAuth2Authorization::getIsRevoked, false);

        OAuth2Authorization auth = authorizationMapper.selectOne(wrapper);
        if (auth == null) {
            throw new BusinessException("refresh_token 无效或已过期");
        }

        // 检查 refresh_token 是否过期
        if (auth.getRefreshTokenExpiresAt() != null && auth.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("refresh_token 已过期，请重新登录");
        }

        // 撤销旧的 access_token 和 refresh_token
        auth.setIsRevoked(true);
        auth.setRevokedAt(LocalDateTime.now());
        authorizationMapper.updateById(auth);

        // 将旧的 access_token 加入黑名单
        if (auth.getAccessTokenHash() != null) {
            tokenBlacklistService.addToBlacklistByHash(auth.getUserId(), auth.getAccessTokenHash(), (long) accessTokenExpiresIn);
        }

        // 查询用户
        User user = userCacheService.getUserById(auth.getUserId());
        if (user == null || !user.getEnabled()) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        // 生成新的 Token
        return createTokenResponse(user, auth.getClientId(), "refresh_token");
    }

    // ==================== Client Credentials Grant ====================

    /**
     * 客户端凭证模式（机器对机器通信）
     */
    private OAuth2TokenResponse handleClientCredentialsGrant(OAuth2TokenRequest request) {
        String clientId = request.getClientId();
        String clientSecret = request.getClientSecret();

        if (clientId == null || clientSecret == null) {
            throw new BusinessException("client_id 和 client_secret 不能为空");
        }

        // 验证客户端
        OAuth2Client client = oAuth2ClientService.getByClientId(clientId);
        if (client == null || !client.getIsEnabled()) {
            throw new BusinessException("客户端不存在或已被禁用");
        }

        if (!oAuth2ClientService.validateClientSecret(clientId, clientSecret)) {
            throw new BusinessException("客户端密钥错误");
        }

        // 检查 grant_type 支持
        List<String> grantTypes = oAuth2ClientService.parseJsonArray(client.getGrantTypes());
        if (!grantTypes.contains("client_credentials")) {
            throw new BusinessException("该客户端不支持 client_credentials grant type");
        }

        // 生成客户端凭证 Token（无用户关联）
        String accessToken = jwtUtil.generateToken(-1L, "client_" + clientId); // 特殊 ID 标识

        // 记录授权
        String accessTokenHash = sha256(accessToken);
        OAuth2Authorization auth = new OAuth2Authorization();
        auth.setClientId(clientId);
        auth.setUserId(null); // 无用户关联
        auth.setAccessTokenHash(accessTokenHash);
        auth.setGrantType("client_credentials");
        auth.setScope(client.getScopes());
        auth.setAuthTime(LocalDateTime.now());
        auth.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(accessTokenExpiresIn));
        auth.setIsRevoked(false);
        authorizationMapper.insert(auth);

        log.info("OAuth2 client_credentials Token 颁发: clientId={}", clientId);

        return OAuth2TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiresIn)
                .scope(client.getScopes())
                .build();
    }

    // ==================== Authorization Code Grant ====================

    /**
     * 授权码模式
     */
    private OAuth2TokenResponse handleAuthorizationCodeGrant(OAuth2TokenRequest request) {
        String code = request.getCode();
        String redirectUri = request.getRedirectUri();
        String clientId = request.getClientId();
        String clientSecret = request.getClientSecret();

        if (code == null || redirectUri == null || clientId == null) {
            throw new BusinessException("code, redirect_uri, client_id 不能为空");
        }

        // 验证客户端
        OAuth2Client client = oAuth2ClientService.getByClientId(clientId);
        if (client == null || !client.getIsEnabled()) {
            throw new BusinessException("客户端不存在或已被禁用");
        }

        if (client.getIsConfidential()) {
            if (clientSecret == null || !oAuth2ClientService.validateClientSecret(clientId, clientSecret)) {
                throw new BusinessException("客户端密钥验证失败");
            }
        }

        // 验证授权码
        String codeHash = sha256(code);
        LambdaQueryWrapper<OAuth2Authorization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OAuth2Authorization::getAuthorizationCode, codeHash);
        wrapper.eq(OAuth2Authorization::getClientId, clientId);
        wrapper.eq(OAuth2Authorization::getIsRevoked, false);

        OAuth2Authorization auth = authorizationMapper.selectOne(wrapper);
        if (auth == null) {
            throw new BusinessException("授权码无效");
        }

        // 检查授权码是否过期
        if (auth.getAuthTime().plusSeconds(authorizationCodeExpiresIn).isBefore(LocalDateTime.now())) {
            auth.setIsRevoked(true);
            auth.setRevokedAt(LocalDateTime.now());
            authorizationMapper.updateById(auth);
            throw new BusinessException("授权码已过期");
        }

        // 验证 redirect_uri
        List<String> allowedUris = oAuth2ClientService.parseJsonArray(client.getRedirectUris());
        if (!allowedUris.contains(redirectUri)) {
            throw new BusinessException("redirect_uri 不在允许列表中");
        }

        // 标记授权码为已使用
        auth.setIsRevoked(true);
        auth.setRevokedAt(LocalDateTime.now());
        authorizationMapper.updateById(auth);

        // 查询用户
        User user = userCacheService.getUserById(auth.getUserId());
        if (user == null || !user.getEnabled()) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        // 生成 Token
        return createTokenResponse(user, clientId, "authorization_code");
    }

    // ==================== 授权码创建 ====================

    /**
     * 创建授权码（第三方应用跳转）
     */
    @Transactional(rollbackFor = Exception.class)
    public String createAuthorizationCode(Long userId, String clientId, String redirectUri, List<String> scopes) {
        // 验证客户端
        OAuth2Client client = oAuth2ClientService.getByClientId(clientId);
        if (client == null || !client.getIsEnabled()) {
            throw new BusinessException("客户端不存在或已被禁用");
        }

        List<String> allowedUris = oAuth2ClientService.parseJsonArray(client.getRedirectUris());
        if (!allowedUris.contains(redirectUri)) {
            throw new BusinessException("redirect_uri 不在允许列表中");
        }

        // 生成授权码
        String code = generateRandomCode();
        String codeHash = sha256(code);

        OAuth2Authorization auth = new OAuth2Authorization();
        auth.setClientId(clientId);
        auth.setUserId(userId);
        auth.setAuthorizationCode(codeHash);
        auth.setGrantType("authorization_code");
        auth.setScope(scopes != null ? String.join(",", scopes) : null);
        auth.setRedirectUri(redirectUri);
        auth.setAuthTime(LocalDateTime.now());
        auth.setIsRevoked(false);
        authorizationMapper.insert(auth);

        log.info("OAuth2 授权码已创建: userId={}, clientId={}", userId, clientId);

        return code;
    }

    // ==================== Token 撤销 ====================

    /**
     * 撤销 Token
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeToken(String accessToken) {
        String tokenHash = sha256(accessToken);
        LambdaQueryWrapper<OAuth2Authorization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OAuth2Authorization::getAccessTokenHash, tokenHash);
        wrapper.eq(OAuth2Authorization::getIsRevoked, false);

        OAuth2Authorization auth = authorizationMapper.selectOne(wrapper);
        if (auth == null) {
            log.warn("撤销 Token 失败：未找到对应记录");
            return;
        }

        auth.setIsRevoked(true);
        auth.setRevokedAt(LocalDateTime.now());
        authorizationMapper.updateById(auth);

        if (auth.getUserId() != null) {
            tokenBlacklistService.addToBlacklistByHash(auth.getUserId(), tokenHash, (long) accessTokenExpiresIn);
        }

        log.info("OAuth2 Token 已撤销: userId={}, clientId={}", auth.getUserId(), auth.getClientId());
    }

    /**
     * 从 access_token 获取用户 ID
     *
     * 优先尝试 JWT 解析，若 JWT 无效则从 OAuth2 授权记录回退查询，
     * 同时检查 access_token 是否已过期
     */
    public Long getUserIdFromToken(String accessToken) {
        try {
            return jwtUtil.getUserIdFromToken(accessToken);
        } catch (Exception e) {
            String tokenHash = sha256(accessToken);
            LambdaQueryWrapper<OAuth2Authorization> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OAuth2Authorization::getAccessTokenHash, tokenHash);
            wrapper.eq(OAuth2Authorization::getIsRevoked, false);
            OAuth2Authorization auth = authorizationMapper.selectOne(wrapper);
            if (auth == null) {
                return null;
            }
            // 检查 access_token 是否过期
            if (auth.getAccessTokenExpiresAt() != null && auth.getAccessTokenExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("OAuth2 access_token 已过期: userId={}, expiresAt={}", auth.getUserId(), auth.getAccessTokenExpiresAt());
                return null;
            }
            return auth.getUserId();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 创建 Token 响应并记录授权和设备会话信息
     */
    private OAuth2TokenResponse createTokenResponse(User user, String clientId, String grantType) {
        return createTokenResponse(user, clientId, grantType, null, null);
    }

    /**
     * 创建 Token 响应并记录授权，同时记录设备会话信息（支持OAuth2登录追踪）
     *
     * @param user      用户
     * @param clientId  客户端ID（password grant时为null）
     * @param grantType 授权类型
     * @param clientIp  客户端IP（记录设备信息用，可为null）
     * @param userAgent 客户端User-Agent（记录设备信息用，可为null）
     */
    private OAuth2TokenResponse createTokenResponse(User user, String clientId, String grantType,
                                                     String clientIp, String userAgent) {
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = generateRefreshToken();

        String accessTokenHash = sha256(accessToken);
        String refreshTokenHash = sha256(refreshToken);

        // 记录授权
        OAuth2Authorization auth = new OAuth2Authorization();
        auth.setClientId(clientId);
        auth.setUserId(user.getId());
        auth.setAccessTokenHash(accessTokenHash);
        auth.setRefreshTokenHash(refreshTokenHash);
        auth.setGrantType(grantType);
        auth.setAuthTime(LocalDateTime.now());
        auth.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(accessTokenExpiresIn));
        auth.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiresIn));
        auth.setIsRevoked(false);
        authorizationMapper.insert(auth);

        // 记录 Token 会话（设备信息），用于设备管理
        if (clientIp != null && userAgent != null) {
            try {
                UserAgentParser.DeviceInfo deviceInfo = userAgentParser.parseUserAgent(userAgent);
                String deviceDescription = deviceInfo.getDescription();
                // 如果是Android设备，使用更详细的描述
                if ("Android".equals(deviceInfo.getOsName()) && deviceInfo.getAndroidRemark() != null) {
                    deviceDescription = deviceInfo.getAndroidDescription();
                }
                tokenSessionService.recordSession(user.getId(), accessToken, clientIp, userAgent, deviceDescription);
                log.debug("OAuth2 登录记录Token会话: userId={}, ip={}, grantType={}", user.getId(), clientIp, grantType);
            } catch (Exception e) {
                log.warn("记录OAuth2 Token会话失败: userId={}", user.getId(), e);
            }
        }

        log.info("OAuth2 Token 颁发: userId={}, clientId={}, grantType={}", user.getId(), clientId, grantType);

        return OAuth2TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiresIn)
                .refreshToken(refreshToken)
                .refreshTokenExpiresIn(refreshTokenExpiresIn)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    /**
     * 生成 refresh_token
     */
    private String generateRefreshToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成随机授权码
     */
    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 哈希
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 哈希失败", e);
        }
    }
}