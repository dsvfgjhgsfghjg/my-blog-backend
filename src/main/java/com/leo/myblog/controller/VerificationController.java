package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.VerifyStatusResponse;
import com.leo.myblog.dto.response.SendPushVerifyResponse;
import com.leo.myblog.dto.response.LoginPollResponse;
import com.leo.myblog.dto.response.AuthHistoryResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.service.TotpSessionService;
import com.leo.myblog.service.SystemConfigService;
import com.leo.myblog.websocket.WebSocketSessionManager;
import com.leo.myblog.websocket.AuthWebSocketHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 敏感操作验证控制器
 * <p>
 * 支持 TOTP 和挑战推送两种验证方式
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final LoginService loginService;
    private final TotpService totpService;
    private final TotpSessionService totpSessionService;
    private final AppAuthService appAuthService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final AuthWebSocketHandler authWebSocketHandler;
    private final SystemConfigService systemConfigService;

    /**
     * 查询验证状态
     * 前端可在发起敏感操作前调用，确定是否需要弹窗
     */
    @GetMapping("/status")
    public Result<VerifyStatusResponse> getVerifyStatus(@RequestAttribute Long userId,
                                                         @RequestParam(required = false) String operation) {
        boolean totpEnabled = totpService.isTotpEnabled(userId);
        boolean hasBoundDevices = appAuthService.hasBoundDevice(userId);
        boolean totpSessionValid = totpSessionService.isSessionValid(userId);

        // 可用验证方式
        List<String> availableMethods = new ArrayList<>();
        if (totpEnabled) {
            availableMethods.add("totp");
        }
        if (hasBoundDevices) {
            availableMethods.add("challenge_push");
        }

        // 获取上次验证方式
        String lastMethod = loginService.getLastVerifyMethod(userId);

        // 如果上次使用的方式不可用，回退到第一个可用方式
        if (!availableMethods.contains(lastMethod) && !availableMethods.isEmpty()) {
            lastMethod = availableMethods.get(0);
        }

        // 检查是否有有效会话
        boolean needVerify = !totpSessionValid;

        // 获取在线设备名称
        List<String> deviceNames = new ArrayList<>();
        if (hasBoundDevices) {
            List<AppDevice> devices = appAuthService.getUserDevices(userId);
            for (AppDevice device : devices) {
                if (webSocketSessionManager.isDeviceOnline(device.getDeviceId())) {
                    deviceNames.add(device.getDeviceName());
                }
            }
        }

        VerifyStatusResponse response = new VerifyStatusResponse();
        response.setNeedVerify(needVerify);
        response.setLastMethod(lastMethod);
        response.setAvailableMethods(availableMethods);
        response.setTotpEnabled(totpEnabled);
        response.setHasBoundDevices(hasBoundDevices);
        response.setDeviceNames(deviceNames);

        return Result.success(response);
    }

    /**
     * 通过 TOTP 验证
     */
    @PostMapping("/totp")
    public Result<Void> verifyTotp(@RequestAttribute Long userId,
                                    @RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.trim().isEmpty()) {
            return Result.error(400, "TOTP 验证码不能为空");
        }

        boolean isValid;
        if (code.length() == 6) {
            isValid = totpService.verifyTotpCode(userId, code.trim());
        } else if (code.length() == 8) {
            isValid = totpService.verifyBackupCode(userId, code.trim());
        } else {
            return Result.error(400, "无效的验证码格式");
        }

        if (!isValid) {
            return Result.error(401, "TOTP 验证码错误或已过期");
        }

        // 创建 TOTP 会话
        totpSessionService.createSession(userId);

        // 记录最后验证方式
        loginService.recordLastVerifyMethod(userId, "totp");

        log.info("TOTP 验证成功: userId={}", userId);
        return Result.success();
    }

    /**
     * 发送推送挑战
     */
    @PostMapping("/send-push")
    public Result<SendPushVerifyResponse> sendPushChallenge(@RequestAttribute Long userId,
                                                             @RequestBody(required = false) Map<String, String> request) {
        // 检查是否有绑定设备
        if (!appAuthService.hasBoundDevice(userId)) {
            return Result.error(400, "没有绑定的 App 设备");
        }

        // 获取在线设备名称
        List<AppDevice> devices = appAuthService.getUserDevices(userId);
        List<String> deviceNames = new ArrayList<>();
        for (AppDevice device : devices) {
            if (webSocketSessionManager.isDeviceOnline(device.getDeviceId())) {
                deviceNames.add(device.getDeviceName());
            }
        }

        // 生成挑战（使用 PushTestController 相同的推送机制）
        // 先使旧挑战失效
        appAuthService.invalidateUserChallenges(userId);

        // 向在线设备推送挑战
        Map<String, org.springframework.web.socket.WebSocketSession> onlineSessions =
                webSocketSessionManager.getUserSessions(userId);

        String challengeId = null;
        long expiresAt = 0;

        for (Map.Entry<String, org.springframework.web.socket.WebSocketSession> entry : onlineSessions.entrySet()) {
            String deviceId = entry.getKey();
            org.springframework.web.socket.WebSocketSession session = entry.getValue();

            try {
                com.leo.myblog.service.AppAuthService.ChallengeInfo challengeInfo =
                        authWebSocketHandler.pushChallengeToUser(session, userId, deviceId);
                if (challengeId == null) {
                    challengeId = challengeInfo.getChallengeId();
                    expiresAt = challengeInfo.getExpiresAt();
                }
            } catch (Exception e) {
                log.error("推送挑战失败: deviceId={}", deviceId, e);
            }
        }

        if (challengeId == null) {
            return Result.error(400, "没有在线设备可推送");
        }

        SendPushVerifyResponse response = new SendPushVerifyResponse();
        response.setChallengeId(challengeId);
        response.setStatus("pushed");
        response.setDeviceNames(deviceNames);
        response.setExpiresAt(expiresAt);

        return Result.success(response);
    }

    /**
     * 检查推送挑战结果
     */
    @GetMapping("/check-push/{challengeId}")
    public Result<Map<String, Object>> checkPushResult(@RequestAttribute Long userId,
                                                        @PathVariable String challengeId) {
        // 查询挑战结果 - 从认证历史中查找
        Page<AuthHistoryResponse> historyPage =
                appAuthService.getAuthHistory(userId, 1, 50, null);

        AuthHistoryResponse result = historyPage.getRecords().stream()
                .filter(h -> h.getChallengeId() != null && h.getChallengeId().equals(challengeId))
                .findFirst()
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("challengeId", challengeId);

        if (result == null) {
            response.put("status", "pending");
        } else {
            response.put("status", result.getStatus());
            response.put("deviceId", result.getDeviceId());
            response.put("deviceName", result.getDeviceName());
        }

        return Result.success(response);
    }

    /**
     * 检查登录挑战结果
     */
    @GetMapping("/check-login-result/{challengeId}")
    public Result<LoginPollResponse> checkLoginResult(@PathVariable String challengeId) {
        LoginPollResponse pollResult = loginService.pollLoginResult(challengeId);
        return Result.success(pollResult);
    }
}