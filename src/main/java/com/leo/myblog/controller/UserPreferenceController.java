package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.SetParticleModeRequest;
import com.leo.myblog.dto.request.SetLoginMethodRequest;
import com.leo.myblog.dto.response.LoginMethodsResponse;
import com.leo.myblog.dto.response.ParticleEffectResponse;
import com.leo.myblog.dto.response.ParticleModeResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.UserPreferenceService;
import com.leo.myblog.websocket.WebSocketSessionManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户偏好设置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;
    private final AppAuthService appAuthService;
    private final WebSocketSessionManager webSocketSessionManager;

    /**
     * 获取用户粒子特效设置
     * 需要登录
     */
    @GetMapping("/particle-mode")
    public Result<ParticleModeResponse> getParticleMode(@RequestAttribute Long userId) {
        log.debug("获取用户粒子特效设置: userId={}", userId);
        ParticleModeResponse response = userPreferenceService.getParticleMode(userId);
        return Result.success(response);
    }

    /**
     * 设置/更新用户粒子特效
     * 需要登录
     */
    @PostMapping("/particle-mode")
    public Result<Void> setParticleMode(@RequestAttribute Long userId,
                                         @Valid @RequestBody SetParticleModeRequest request) {
        log.info("用户 {} 设置粒子特效: {}", userId, request.getParticleMode());
        userPreferenceService.setParticleMode(userId, request);
        return Result.success("设置成功", null);
    }

    /**
     * 删除用户粒子特效设置（恢复默认）
     * 需要登录
     */
    @DeleteMapping("/particle-mode")
    public Result<Void> deleteParticleMode(@RequestAttribute Long userId) {
        log.info("用户 {} 删除粒子特效设置", userId);
        userPreferenceService.deleteParticleMode(userId);
        return Result.success("已恢复默认设置", null);
    }

    /**
     * 获取所有可用的粒子特效列表
     * 公开接口，无需登录
     */
    @GetMapping("/effects")
    public Result<List<ParticleEffectResponse>> getAvailableEffects() {
        log.debug("获取可用粒子特效列表");
        List<ParticleEffectResponse> effects = userPreferenceService.getAvailableEffects();
        return Result.success(effects);
    }

    // ==================== 登录方式设置 ====================

    /**
     * 获取用户的登录方式设置
     */
    @GetMapping("/login-methods")
    public Result<LoginMethodsResponse> getLoginMethods(@RequestAttribute Long userId) {
        log.debug("获取用户登录方式设置: userId={}", userId);

        String defaultMethod = userPreferenceService.getDefaultLoginMethod(userId);
        boolean hasBoundDevices = appAuthService.hasBoundDevice(userId);

        List<String> availableMethods = new java.util.ArrayList<>();
        availableMethods.add("password");
        if (hasBoundDevices) {
            availableMethods.add("authenticator_app");
        }

        List<AppDevice> devices = appAuthService.getUserDevices(userId);
        List<LoginMethodsResponse.DeviceInfo> deviceInfos = devices.stream().map(device -> {
            LoginMethodsResponse.DeviceInfo info = new LoginMethodsResponse.DeviceInfo();
            info.setDeviceId(device.getDeviceId());
            info.setDeviceName(device.getDeviceName());
            info.setOnline(webSocketSessionManager.isDeviceOnline(device.getDeviceId()));
            return info;
        }).collect(Collectors.toList());

        LoginMethodsResponse response = new LoginMethodsResponse();
        response.setDefaultMethod(defaultMethod);
        response.setAvailableMethods(availableMethods);
        response.setHasBoundDevices(hasBoundDevices);
        response.setDevices(deviceInfos);

        return Result.success(response);
    }

    /**
     * 设置用户默认登录方式
     */
    @PutMapping("/login-methods")
    public Result<Void> setLoginMethod(@RequestAttribute Long userId,
                                        @Valid @RequestBody SetLoginMethodRequest request) {
        log.info("用户 {} 设置默认登录方式: {}", userId, request.getDefaultMethod());

        // 如果设置为 authenticator_app，检查是否有绑定设备
        if ("authenticator_app".equals(request.getDefaultMethod()) && !appAuthService.hasBoundDevice(userId)) {
            return Result.error(400, "请先绑定 App 设备后再设置此登录方式");
        }

        userPreferenceService.setDefaultLoginMethod(userId, request.getDefaultMethod());
        return Result.success("设置成功", null);
    }
}