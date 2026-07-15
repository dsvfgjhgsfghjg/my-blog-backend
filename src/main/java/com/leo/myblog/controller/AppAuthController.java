package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.AuthHistoryResponse;
import com.leo.myblog.dto.response.CipherInfoResponse;
import com.leo.myblog.dto.response.DashboardResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * App 双因素认证控制器
 * <p>
 * 提供设备绑定、解绑、配对码生成等 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/app")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;

    /**
     * 生成设备码（Device Secret）
     * <p>
     * 服务端生成设备唯一标识和密钥，用于设备身份验证。
     * 设备码通过加密信道下发，App 端应将其持久化存储到本地。
     * <p>
     * 调用时机：
     * - App 首次启动时
     * - 本地存储的设备码丢失时
     * <p>
     * 注意：此接口不需要认证，因为设备尚未绑定用户。
     * 生成的设备码将预创建一条设备记录（未绑定用户状态）。
     */
    @PostMapping("/device-secret")
    public Result<?> generateDeviceSecret() {
        log.info("生成设备码请求");

        AppAuthService.DeviceSecretResult result = appAuthService.generateDeviceSecret();

        return Result.success(Map.of(
                "deviceId", result.getDeviceId(),
                "deviceSecret", result.getDeviceSecret(),
                "hint", result.getHint()
        ));
    }

    /**
     * 账号密码绑定 App 设备
     * <p>
     * App 端直接使用用户名密码绑定
     */
    @PostMapping("/bind")
    public Result<?> bindWithPassword(@RequestBody BindPasswordRequest request) {
        log.info("App 设备绑定请求: deviceId={}, deviceName={}", request.getDeviceId(), request.getDeviceName());

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return Result.error(400, "密码不能为空");
        }
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            return Result.error(400, "设备 ID 不能为空");
        }

        AppAuthService.BindResult result = appAuthService.bindWithPassword(
                request.getUsername().trim(),
                request.getPassword(),
                request.getDeviceId().trim(),
                request.getDeviceName(),
                request.getDeviceType(),
                request.getDeviceSecret()
        );

        return Result.success("绑定成功", Map.of(
                "token", result.getToken(),
                "deviceId", result.getDeviceId()
        ));
    }


    /**
     * 扫码绑定 App 设备
     * <p>
     * App 端输入网页端生成的配对码完成绑定
     */
    @PostMapping("/bind/qrcode")
    public Result<?> bindWithQrCode(@RequestBody BindQrCodeRequest request,
                                     @RequestAttribute Long userId) {
        log.info("扫码绑定请求: userId={}, pairCode={}, deviceId={}, deviceName={}",
                userId, request.getPairCode(), request.getDeviceId(), request.getDeviceName());

        if (request.getPairCode() == null || request.getPairCode().trim().isEmpty()) {
            return Result.error(400, "配对码不能为空");
        }
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            return Result.error(400, "设备 ID 不能为空");
        }

        // 验证配对码，绑定设备
        String deviceId = appAuthService.bindWithPairCode(
                userId,
                request.getPairCode().trim(),
                request.getDeviceId().trim(),
                request.getDeviceName()
        );

        return Result.success("绑定成功", Map.of("deviceId", deviceId));
    }

    /**
     * 网页端生成配对码（用于扫码绑定）
     * <p>
     * 用户登录网页后，点击"绑定 App"生成 6 位配对码
     */
    @PostMapping("/generate-pair-code")
    public Result<?> generatePairCode(@RequestAttribute Long userId) {
        log.info("生成配对码: userId={}", userId);

        AppAuthService.PairCodeInfo pairCodeInfo = appAuthService.generatePairCode(userId);

        return Result.success(Map.of(
                "pairCode", pairCodeInfo.getPairCode(),
                "expiresIn", pairCodeInfo.getExpiresIn()

        ));
    }

    /**
     * 获取用户的绑定设备列表
     */
    @GetMapping("/devices")
    public Result<?> getDevices(@RequestAttribute Long userId) {
        log.debug("获取设备列表: userId={}", userId);

        List<AppDevice> devices = appAuthService.getUserDevices(userId);

        return Result.success(devices);
    }

    /**
     * 解绑设备
     */
    @DeleteMapping("/devices/{deviceId}")
    public Result<?> unbindDevice(@RequestAttribute Long userId,
                                   @PathVariable String deviceId) {
        log.info("解绑设备: userId={}, deviceId={}", userId, deviceId);

        appAuthService.unbindDevice(userId, deviceId);

        return Result.success("设备已解绑", null);
    }

    /**
     * 解绑所有其他设备（保留指定设备）
     * <p>
     * 用于清理重复的旧设备记录
     */
    @DeleteMapping("/devices/other")
    public Result<?> unbindOtherDevices(@RequestAttribute Long userId,
                                         @RequestParam(required = false) String keepDeviceId) {
        log.info("解绑其他设备: userId={}, keepDeviceId={}", userId, keepDeviceId);

        int removedCount = appAuthService.unbindOtherDevices(userId, keepDeviceId);

        return Result.success(Map.of(
                "removedCount", removedCount,
                "message", "已解绑 " + removedCount + " 台设备"
        ));
    }

    /**
     * 解绑所有设备（完全重置）
     */
    @DeleteMapping("/devices/all")
    public Result<?> unbindAllDevices(@RequestAttribute Long userId) {
        log.info("解绑所有设备: userId={}", userId);

        int removedCount = appAuthService.unbindOtherDevices(userId, null);

        return Result.success(Map.of(
                "removedCount", removedCount,
                "message", "已解绑全部 " + removedCount + " 台设备"
        ));
    }

    /**
     * 检查用户是否绑定了 App 设备
     */
    @GetMapping("/status")
    public Result<?> checkStatus(@RequestAttribute Long userId) {
        boolean hasDevice = appAuthService.hasBoundDevice(userId);
        return Result.success(Map.of("hasBoundDevice", hasDevice));
    }

    /**
     * 获取加密算法信息
     * <p>
     * 返回当前系统支持的密钥交换和数据加密算法信息
     * 可选参数 deviceId 用于获取设备首选的加密算法
     */
    @GetMapping("/cipher-info")
    public Result<?> getCipherInfo(@RequestAttribute Long userId,
                                    @RequestParam(required = false) String deviceId) {
        log.debug("获取加密算法信息: userId={}, deviceId={}", userId, deviceId);

        CipherInfoResponse cipherInfo = appAuthService.getCipherInfo(userId, deviceId);

        return Result.success(cipherInfo);
    }

    /**
     * 更新设备首选加密算法
     * <p>
     * App 端切换加密算法时调用，更新设备记录的 cipherPref 字段
     *
     * @param userId   用户 ID（从 JWT 认证获取）
     * @param deviceId 设备唯一标识
     * @param request  请求体，包含新的加密算法名称
     * @return 更新结果
     */
    @PutMapping("/devices/{deviceId}/cipher")
    public Result<?> updateDeviceCipher(@RequestAttribute Long userId,
                                         @PathVariable String deviceId,
                                         @RequestBody Map<String, String> request) {
        // 兼容两种字段名：cipherPref（App端使用）和 cipher（旧版）
        String cipher = request.get("cipherPref");
        if (cipher == null || cipher.trim().isEmpty()) {
            cipher = request.get("cipher");
        }
        if (cipher == null || cipher.trim().isEmpty()) {
            return Result.error(400, "加密算法不能为空");
        }

        log.info("更新设备加密算法: userId={}, deviceId={}, cipher={}", userId, deviceId, cipher);

        appAuthService.updateDeviceCipher(userId, deviceId, cipher.trim());

        return Result.success("加密算法已更新", Map.of("cipher", cipher));
    }

    /**
     * 获取认证历史
     * <p>
     * 分页查询 App 双因素认证的挑战历史记录
     */
    @GetMapping("/history")
    public Result<?> getAuthHistory(@RequestAttribute Long userId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String status) {
        log.debug("获取认证历史: userId={}, page={}, size={}, status={}", userId, page, size, status);

        Page<AuthHistoryResponse> historyPage = appAuthService.getAuthHistory(userId, page, size, status);

        return Result.success(Map.of(
                "records", historyPage.getRecords(),
                "total", historyPage.getTotal(),
                "page", historyPage.getCurrent(),
                "size", historyPage.getSize(),
                "pages", historyPage.getPages()
        ));
    }

    /**
     * 获取 Authenticator 首页聚合数据
     * <p>
     * 聚合展示用户信息、当前设备信息、最近认证记录等
     * 用于 MyAuthenticator Android App 的首页展示
     *
     * @param userId   用户 ID（从 JWT 认证获取）
     * @param deviceId 当前设备 ID（可选，用于获取设备信息和设备专属认证记录）
     * @return DashboardResponse 聚合数据
     */
    @GetMapping("/dashboard")
    public Result<?> getDashboard(@RequestAttribute Long userId,
                                   @RequestParam(required = false) String deviceId) {
        log.info("获取 Dashboard: userId={}, deviceId={}", userId, deviceId);

        DashboardResponse dashboard = appAuthService.getDashboard(userId, deviceId);

        return Result.success(dashboard);
    }

    // ==================== 请求体 ====================

    /**
     * 密码绑定请求
     */
    public static class BindPasswordRequest {
        private String username;
        private String password;
        private String deviceId;
        private String deviceName;
        private String deviceType;
        private String deviceSecret;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        public String getDeviceSecret() { return deviceSecret; }
        public void setDeviceSecret(String deviceSecret) { this.deviceSecret = deviceSecret; }
    }

    /**
     * 扫码绑定请求
     */
    public static class BindQrCodeRequest {
        private String pairCode;
        private String deviceId;
        private String deviceName;

        public String getPairCode() { return pairCode; }
        public void setPairCode(String pairCode) { this.pairCode = pairCode; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    }
}
