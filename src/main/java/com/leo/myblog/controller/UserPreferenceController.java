package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.SetParticleModeRequest;
import com.leo.myblog.dto.response.ParticleEffectResponse;
import com.leo.myblog.dto.response.ParticleModeResponse;
import com.leo.myblog.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户偏好设置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {
    
    private final UserPreferenceService userPreferenceService;
    
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
}
