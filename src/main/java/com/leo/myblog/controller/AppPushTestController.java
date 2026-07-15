package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.AuthHistoryResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.SystemConfigService;
import com.leo.myblog.websocket.AuthWebSocketHandler;
import com.leo.myblog.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * App 推送测试控制器
 * <p>
 * 提供管理后台测试推送功能的 REST API
 * 用于向指定用户的 App 设备推送 3 选 1 挑战
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/app/test-push")
@RequiredArgsConstructor
public class AppPushTestController {

    private final AppAuthService appAuthService;
    private final AuthWebSocketHandler authWebSocketHandler;
    private final WebSocketSessionManager sessionManager;
    private final SystemConfigService systemConfigService;

    /**
     * 获取所有有绑定设备的用户列表（含在线状态）
     * <p>
     * 返回每个用户的设备信息，包括是否在线
     */
    @GetMapping("/devices")
    public Result<?> getBoundDevices(@RequestAttribute Long userId) {
        log.info("获取绑定设备列表: userId={}", userId);

        // 获取当前用户的所有绑定设备
        List<AppDevice> devices = appAuthService.getUserDevices(userId);
        log.info("用户 {} 有 {} 个绑定设备", userId, devices.size());

        // 为每个设备添加在线状态
        List<Map<String, Object>> deviceList = devices.stream().map(device -> {
            String deviceId = device.getDeviceId();
            boolean online = sessionManager.isDeviceOnline(deviceId);
            log.info("设备在线状态检查: deviceId={}, deviceName={}, online={}",
                    deviceId, device.getDeviceName(), online);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deviceId", deviceId);
            map.put("deviceName", device.getDeviceName());
            map.put("deviceType", device.getDeviceType());
            map.put("cipherPref", device.getCipherPref());
            map.put("lastActiveAt", device.getLastActiveAt());
            map.put("online", online);
            return map;
        }).collect(Collectors.toList());

        long onlineCount = deviceList.stream().filter(d -> (boolean) d.get("online")).count();
        log.info("设备列表返回: total={}, onlineCount={}", deviceList.size(), onlineCount);

        return Result.success(Map.of(
                "userId", userId,
                "devices", deviceList,
                "total", deviceList.size(),
                "onlineCount", onlineCount
        ));
    }

    /**
     * 向指定用户的所有在线设备推送挑战
     * <p>
     * 用于管理后台测试推送功能
     * 返回每个设备的推送结果
     */
    @PostMapping("/push")
    public Result<?> pushChallenge(@RequestAttribute Long userId,
                                    @RequestBody Map<String, Object> request) {
        // 获取目标用户 ID（从请求体）
        Object targetUserIdObj = request.get("targetUserId");
        if (targetUserIdObj == null) {
            return Result.error(400, "缺少目标用户 ID");
        }

        Long targetUserId;
        if (targetUserIdObj instanceof Number) {
            targetUserId = ((Number) targetUserIdObj).longValue();
        } else {
            try {
                targetUserId = Long.parseLong(targetUserIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error(400, "目标用户 ID 格式错误");
            }
        }

        log.info("推送挑战: operatorUserId={}, targetUserId={}", userId, targetUserId);

        // 如果 targetUserId 为 0，表示推送给当前用户自己
        if (targetUserId == 0) {
            targetUserId = userId;
            log.info("targetUserId 为 0，使用当前用户 ID: {}", targetUserId);
        }

        // 获取目标用户的在线设备
        Map<String, org.springframework.web.socket.WebSocketSession> onlineSessions =
                sessionManager.getUserSessions(targetUserId);

        if (onlineSessions.isEmpty()) {
            return Result.error(400, "目标用户没有在线设备");
        }

        // 推送新挑战前，使该用户之前的所有挑战失效，确保旧挑战不能复用
        appAuthService.invalidateUserChallenges(targetUserId);

        // 读取系统配置：是否在推送结果中显示正确答案（调试用）
        boolean showCorrect = "true".equalsIgnoreCase(
                systemConfigService.getConfigValue("security.app_auth.push_challenge_show_correct", "false"));

        // 向每个在线设备推送挑战
        List<Map<String, Object>> pushResults = new ArrayList<>();
        for (Map.Entry<String, org.springframework.web.socket.WebSocketSession> entry : onlineSessions.entrySet()) {
            String deviceId = entry.getKey();
            org.springframework.web.socket.WebSocketSession session = entry.getValue();

            try {
                // 调用 AuthWebSocketHandler 推送挑战
                com.leo.myblog.service.AppAuthService.ChallengeInfo challengeInfo =
                        authWebSocketHandler.pushChallengeToUser(session, targetUserId, deviceId);

                // 调试模式：只返回正确答案，避免泄漏多余信息
                if (showCorrect) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("deviceId", deviceId);
                    result.put("correctNumber", challengeInfo.getCorrectNumber());
                    result.put("status", "pushed");
                    pushResults.add(result);

                    log.info("调试模式：返回正确答案 correctNumber={} (challengeId={})",
                            challengeInfo.getCorrectNumber(), challengeInfo.getChallengeId());
                } else {
                    // 正常模式：返回完整挑战信息
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("deviceId", deviceId);
                    result.put("challengeId", challengeInfo.getChallengeId());
                    result.put("numbers", challengeInfo.getNumbers());
                    result.put("expiresAt", challengeInfo.getExpiresAt());
                    result.put("status", "pushed");
                    pushResults.add(result);
                }

                log.info("挑战推送成功: deviceId={}, challengeId={}", deviceId, challengeInfo.getChallengeId());
            } catch (Exception e) {
                log.error("挑战推送失败: deviceId={}", deviceId, e);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("deviceId", deviceId);
                result.put("status", "failed");
                result.put("error", e.getMessage());
                pushResults.add(result);
            }
        }

        return Result.success(Map.of(
                "targetUserId", targetUserId,
                "results", pushResults,
                "totalPushed", pushResults.stream().filter(r -> "pushed".equals(r.get("status"))).count(),
                "totalFailed", pushResults.stream().filter(r -> "failed".equals(r.get("status"))).count()
        ));
    }

    /**
     * 轮询获取挑战结果
     * <p>
     * 根据 challengeId 查询挑战的最终状态
     */
    @GetMapping("/result/{challengeId}")
    public Result<?> getChallengeResult(@RequestAttribute Long userId,
                                         @PathVariable String challengeId) {
        log.debug("查询挑战结果: userId={}, challengeId={}", userId, challengeId);

        // 从认证历史中查询挑战结果
        // 使用分页查询，但只查一条
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuthHistoryResponse> historyPage =
                appAuthService.getAuthHistory(userId, 1, 50, null);

        AuthHistoryResponse result = historyPage.getRecords().stream()
                .filter(h -> h.getChallengeId().equals(challengeId))
                .findFirst()
                .orElse(null);

        if (result == null) {
            return Result.success(Map.of(
                    "challengeId", challengeId,
                    "status", "unknown",
                    "message", "未找到该挑战记录"
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("challengeId", result.getChallengeId());
        response.put("deviceId", result.getDeviceId());
        response.put("deviceName", result.getDeviceName());
        response.put("numbers", result.getNumbers());
        response.put("status", result.getStatus());
        response.put("requestedAt", result.getRequestedAt());
        response.put("respondedAt", result.getRespondedAt());

        return Result.success(response);
    }

    /**
     * 调试接口：查看 WebSocket 会话管理器状态
     * <p>
     * 返回当前所有注册的 WebSocket 会话信息，用于排查设备在线状态问题
     */
    @GetMapping("/debug/sessions")
    public Result<?> debugSessions(@RequestAttribute Long userId) {
        log.info("调试查询 WebSocket 会话: userId={}", userId);

        // 获取当前用户的所有绑定设备
        List<AppDevice> devices = appAuthService.getUserDevices(userId);

        List<Map<String, Object>> sessionInfoList = new ArrayList<>();
        for (AppDevice device : devices) {
            String deviceId = device.getDeviceId();
            boolean online = sessionManager.isDeviceOnline(deviceId);
            org.springframework.web.socket.WebSocketSession session = sessionManager.getSession(deviceId);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("deviceId", deviceId);
            info.put("deviceName", device.getDeviceName());
            info.put("online", online);
            info.put("sessionExists", session != null);
            if (session != null) {
                info.put("sessionId", session.getId());
                info.put("sessionOpen", session.isOpen());
                info.put("sessionAttributes", session.getAttributes().keySet());
            }
            sessionInfoList.add(info);
        }

        // 获取所有注册的会话（不限用户）
        int totalSessions = sessionManager.getOnlineDeviceCount();

        return Result.success(Map.of(
                "userId", userId,
                "deviceCount", devices.size(),
                "totalRegisteredSessions", totalSessions,
                "deviceDetails", sessionInfoList
        ));
    }
}
