package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.UserProfileResponse;
import com.leo.myblog.service.AppAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * App 用户控制器
 * <p>
 * 提供 App 端用户个人信息等接口
 * 路径前缀为 /api/user（区别于网页端的 /api/users）
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AppAuthService appAuthService;

    /**
     * 获取用户个人信息（用于 App 端 Profile 页面）
     * <p>
     * 返回用户基本信息以及设备绑定信息
     */
    @GetMapping("/profile")
    public Result<?> getProfile(@RequestAttribute Long userId) {
        log.debug("App 获取用户个人信息: userId={}", userId);

        UserProfileResponse profile = appAuthService.getUserProfile(userId);

        return Result.success(profile);
    }
}
