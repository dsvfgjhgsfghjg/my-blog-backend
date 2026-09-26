package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.config.CookieSecurityConfig;
import com.leo.myblog.dto.request.LoginStep1Request;
import com.leo.myblog.dto.request.LoginStep2ChallengeRequest;
import com.leo.myblog.dto.request.LoginStep2PasswordRequest;
import com.leo.myblog.dto.response.LoginPollResponse;
import com.leo.myblog.dto.response.LoginStep1Response;
import com.leo.myblog.dto.response.LoginStep2ChallengeResponse;
import com.leo.myblog.dto.response.OAuth2TokenResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.websocket.AuthWebSocketHandler;
import com.leo.myblog.websocket.WebSocketSessionManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 两步登录控制器
 * <p>
 * 仿微软登录流程：
 * 1. POST /api/auth/login/step1 - 提交用户名 + 旋转验证码
 * 2a. POST /api/auth/login/step2-password - 密码登录
 * 2b. POST /api/auth/login/step2-challenge - 挑战推送登录（数字匹配）
 * 3. GET /api/auth/login/poll-result/{challengeId} - 轮询挑战结果，命中后签发 Token
 * <p>
 * 挑战的主动推送放在本层而不是 {@link LoginService}：{@link AuthWebSocketHandler}
 * 反向依赖了 LoginService，若在 Service 里注入它会造成循环依赖。
 * 写法与 {@code VerificationController.sendPushChallenge} 保持一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/login")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final AuthWebSocketHandler authWebSocketHandler;
    private final WebSocketSessionManager webSocketSessionManager;
    private final AppAuthService appAuthService;
    private final CookieSecurityConfig cookieSecurityConfig;

    @Value("${jwt.cookie.expose-token-in-body:true}")
    private boolean exposeTokenInBody;

    /**
     * Step 1: 提交用户名和旋转验证码
     * 返回可用的登录方式
     */
    @PostMapping("/step1")
    public Result<LoginStep1Response> step1(@Valid @RequestBody LoginStep1Request request) {
        log.debug("两步登录Step1: username={}", request.getUsername());
        LoginStep1Response response = loginService.step1(request);
        return Result.success(response);
    }

    /**
     * Step 2a: 密码方式登录
     * <p>
     * 签发的 Token 与 {@code POST /api/auth2/token} 完全一致（含 refresh_token），
     * Cookie 也由本层写入 —— Service 不接触 Web 层对象。
     */
    @PostMapping("/step2-password")
    public Result<OAuth2TokenResponse> step2Password(@Valid @RequestBody LoginStep2PasswordRequest request,
                                                      HttpServletResponse httpResponse) {
        log.debug("两步登录Step2-密码: username={}", request.getUsername());

        OAuth2TokenResponse response = loginService.step2Password(request);

        if (response.getAccessToken() != null) {
            Cookie authCookie = cookieSecurityConfig.createSecureCookie(response.getAccessToken());
            httpResponse.addCookie(authCookie);
        }

        applyExposeTokenPolicy(response);

        return Result.success("登录成功", response);
    }

    /**
     * Step 2b: 挑战推送方式登录
     * <p>
     * 生成三选一挑战并推送给该用户的在线设备，返回要在网页上展示的正确数字。
     * 无在线设备时不报错：挑战已排队，设备稍后上线会自动补推。
     */
    @PostMapping("/step2-challenge")
    public Result<LoginStep2ChallengeResponse> step2Challenge(@Valid @RequestBody LoginStep2ChallengeRequest request) {
        log.debug("两步登录Step2-挑战推送: username={}", request.getUsername());

        LoginService.LoginChallenge challenge = loginService.step2Challenge(request);

        // 向所有在线设备推送
        Map<String, WebSocketSession> onlineSessions =
                webSocketSessionManager.getUserSessions(challenge.getUserId());

        List<String> deviceNames = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : onlineSessions.entrySet()) {
            String deviceId = entry.getKey();
            deviceNames.add(resolveDeviceName(challenge.getUserId(), deviceId));
            try {
                authWebSocketHandler.pushLoginChallenge(entry.getValue(), challenge);
            } catch (Exception e) {
                log.error("推送登录挑战失败: deviceId={}, challengeId={}",
                        deviceId, challenge.getChallengeId(), e);
            }
        }

        LoginStep2ChallengeResponse response = new LoginStep2ChallengeResponse();
        response.setStatus("queued");
        response.setChallengeId(challenge.getChallengeId());
        response.setCorrectNumber(challenge.getCorrectNumber());
        response.setExpiresAt(challenge.getExpiresAt());
        response.setDeviceNames(deviceNames);
        response.setDevicesOnline(!onlineSessions.isEmpty());

        return Result.success("挑战已发送，请在 App 上验证", response);
    }

    /**
     * 轮询登录挑战结果
     * <p>
     * 这是认证器路径<b>唯一</b>写 Cookie 的位置：挑战批准发生在 WebSocket 线程里，
     * 那里既没有 HttpServletResponse，也拿不到真实 IP/UA。
     */
    @GetMapping("/poll-result/{challengeId}")
    public Result<LoginPollResponse> pollResult(@PathVariable String challengeId,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        LoginPollResponse response = loginService.pollLoginResult(
                challengeId,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));

        if ("approved".equals(response.getStatus()) && response.getAccessToken() != null) {
            Cookie authCookie = cookieSecurityConfig.createSecureCookie(response.getAccessToken());
            httpResponse.addCookie(authCookie);
            applyExposeTokenPolicy(response);
        }

        return Result.success(response);
    }

    // ==================== 内部方法 ====================

    /**
     * 按配置决定是否在响应体中暴露 Token
     * <p>
     * 与 OAuth2Controller 共用同一个配置键，保证两条登录路径行为一致。
     */
    private void applyExposeTokenPolicy(OAuth2TokenResponse response) {
        if (!exposeTokenInBody) {
            response.setAccessToken(null);
            response.setTokenType(null);
            response.setRefreshToken(null);
        }
    }

    private void applyExposeTokenPolicy(LoginPollResponse response) {
        if (!exposeTokenInBody) {
            response.setAccessToken(null);
            response.setTokenType(null);
            response.setRefreshToken(null);
        }
    }

    private String resolveDeviceName(Long userId, String deviceId) {
        List<AppDevice> devices = appAuthService.getUserDevices(userId);
        if (devices == null) {
            return deviceId;
        }
        return devices.stream()
                .filter(d -> deviceId.equals(d.getDeviceId()))
                .map(AppDevice::getDeviceName)
                .filter(name -> name != null && !name.isEmpty())
                .findFirst()
                .orElse(deviceId);
    }

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
