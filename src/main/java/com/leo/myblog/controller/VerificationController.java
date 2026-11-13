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
import com.leo.myblog.service.MfaAttemptService;
import com.leo.myblog.security.ClientIpResolver;
import com.leo.myblog.websocket.WebSocketSessionManager;
import com.leo.myblog.websocket.AuthWebSocketHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    private final MfaAttemptService mfaAttemptService;
    private final ClientIpResolver clientIpResolver;

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

        mfaAttemptService.assertAllowed(userId);
        boolean isValid;
        if (code.length() == 6) {
            isValid = totpService.verifyTotpCode(userId, code.trim());
        } else if (code.length() == 8) {
            isValid = totpService.verifyBackupCode(userId, code.trim());
        } else {
            return Result.error(400, "无效的验证码格式");
        }

        if (!isValid) {
            mfaAttemptService.recordFailure(userId);
            return Result.error(401, "TOTP 验证码错误或已过期");
        }
        mfaAttemptService.recordSuccess(userId);

        // 创建 TOTP 会话
        totpSessionService.createSession(userId);

        // 记录最后验证方式
        loginService.recordLastVerifyMethod(userId, "totp");

        log.info("TOTP 验证成功: userId={}", userId);
        return Result.success();
    }

    /**
     * 发送推送挑战
     * <p>
     * 与两步登录（{@code LoginController.step2Challenge}）保持同一套做法：
     * <b>无条件</b>先生成一个挑战并落库，再<b>尽力</b>推给所有在线设备。
     * 手机在兜里、App 在后台时 socket 大概率已断，「没有在线设备」是常态而不是错误 ——
     * 此时返回 {@code status=queued}，挑战已排队，App 上线后会补推或由其主动拉取。
     * <p>
     * 生成与推送分离还修掉了一个多设备缺陷：此前对每台在线设备各调一次
     * {@code pushChallengeToUser}，后者每次都重新 {@code generateChallenge}，
     * N 台设备就得到 N 个数字不同的挑战，而页面只展示第一个的数字、轮询也只认第一个，
     * 其余设备上的挑战用户选中也验不过。
     */
    @PostMapping("/send-push")
    public Result<SendPushVerifyResponse> sendPushChallenge(@RequestAttribute Long userId,
                                                             @RequestBody(required = false) Map<String, String> request) {
        // 检查是否有绑定设备
        if (!appAuthService.hasBoundDevice(userId)) {
            return Result.error(400, "没有绑定的 App 设备");
        }

        // 先使该用户此前的挑战失效（单活跃挑战）
        appAuthService.invalidateUserChallenges(userId);

        // 1. 无条件生成「一个」挑战并落库。
        //    落库是断线补推与 App 拉取的前提：t_auth_history 里 status='pending' 的记录
        //    是敏感操作挑战的权威数据源。deviceId 此刻未知（哪台设备应答才记哪台），
        //    应答时由 AuthWebSocketHandler 补记。
        com.leo.myblog.service.AppAuthService.ChallengeInfo challenge =
                appAuthService.generateChallenge(userId);
        appAuthService.recordChallenge(userId, challenge.getChallengeId(),
                challenge.getNumbers(), challenge.getCorrectNumber(), null, null);

        // 2. 尽力推给所有在线设备 —— 全部使用同一个 challengeId 与同一组候选数字
        Map<String, org.springframework.web.socket.WebSocketSession> onlineSessions =
                webSocketSessionManager.getUserSessions(userId);

        // 设备名一次查全，避免每台设备各查一次库
        Map<String, String> deviceNameById = new HashMap<>();
        for (AppDevice device : appAuthService.getUserDevices(userId)) {
            deviceNameById.put(device.getDeviceId(), device.getDeviceName());
        }

        List<String> deviceNames = new ArrayList<>();
        int pushedCount = 0;
        for (Map.Entry<String, org.springframework.web.socket.WebSocketSession> entry : onlineSessions.entrySet()) {
            String deviceId = entry.getKey();
            String deviceName = deviceNameById.get(deviceId);
            deviceNames.add(deviceName != null && !deviceName.isEmpty() ? deviceName : deviceId);
            try {
                authWebSocketHandler.pushChallenge(entry.getValue(), challenge);
                pushedCount++;
            } catch (Exception e) {
                log.error("推送挑战失败: deviceId={}, challengeId={}", deviceId, challenge.getChallengeId(), e);
            }
        }

        SendPushVerifyResponse response = new SendPushVerifyResponse();
        response.setChallengeId(challenge.getChallengeId());
        // pushed = 至少有一台在线设备收到；queued = 暂时没有设备在线，等设备上线再推
        response.setStatus(pushedCount > 0 ? "pushed" : "queued");
        response.setDeviceNames(deviceNames);
        response.setExpiresAt(challenge.getExpiresAt());
        // 数字匹配：浏览器要把这个数字显示出来，用户才能在 App 的 3 个候选里挑中它。
        // 此前漏传，页面上看不到该选哪个，只能盲选。注意只能给浏览器，绝不能进 App 的推送消息。
        response.setCorrectNumber(challenge.getCorrectNumber());

        log.info("敏感操作挑战已生成: userId={}, challengeId={}, status={}, onlineDevices={}",
                userId, challenge.getChallengeId(), response.getStatus(), pushedCount);

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
     * <p>
     * 注意：这是「登录挑战」的结果查询，与 {@code /check-push} 的敏感操作挑战无关。
     * 本接口会消费掉挑战并在 approved 时签发 Token（见 LoginService.pollLoginResult），
     * 因此只应被发起登录的那个浏览器调用。
     */
    @GetMapping("/check-login-result/{challengeId}")
    public Result<LoginPollResponse> checkLoginResult(@PathVariable String challengeId,
                                                       jakarta.servlet.http.HttpServletRequest httpRequest) {
        LoginPollResponse pollResult = loginService.pollLoginResult(
                challengeId,
                clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return Result.success(pollResult);
    }

}
