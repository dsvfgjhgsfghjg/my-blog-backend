package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.LoginStep1Request;
import com.leo.myblog.dto.request.LoginStep2ChallengeRequest;
import com.leo.myblog.dto.request.LoginStep2PasswordRequest;
import com.leo.myblog.dto.response.LoginPollResponse;
import com.leo.myblog.dto.response.LoginResponse;
import com.leo.myblog.dto.response.LoginStep1Response;
import com.leo.myblog.service.LoginService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 两步登录控制器
 * <p>
 * 仿微软登录流程：
 * 1. POST /api/auth/login/step1 - 提交用户名 + 旋转验证码
 * 2a. POST /api/auth/login/step2-password - 密码登录
 * 2b. POST /api/auth/login/step2-challenge - 挑战推送登录
 * 3. GET /api/auth/login/poll-result/{challengeId} - 轮询挑战结果
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/login")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

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
     */
    @PostMapping("/step2-password")
    public Result<LoginResponse> step2Password(@Valid @RequestBody LoginStep2PasswordRequest request,
                                                HttpServletResponse httpResponse) {
        log.debug("两步登录Step2-密码: username={}", request.getUsername());
        LoginResponse response = loginService.step2Password(request, httpResponse);
        return Result.success("登录成功", response);
    }

    /**
     * Step 2b: 挑战推送方式登录
     * 生成三选一挑战，等待 App 端验证
     */
    @PostMapping("/step2-challenge")
    public Result<LoginPollResponse> step2Challenge(@Valid @RequestBody LoginStep2ChallengeRequest request) {
        log.debug("两步登录Step2-挑战推送: username={}", request.getUsername());
        LoginPollResponse response = loginService.step2Challenge(request);
        return Result.success("挑战已发送，请在 App 上验证", response);
    }

    /**
     * 轮询登录挑战结果
     */
    @GetMapping("/poll-result/{challengeId}")
    public Result<LoginPollResponse> pollResult(@PathVariable String challengeId) {
        log.debug("轮询登录挑战结果: challengeId={}", challengeId);
        LoginPollResponse response = loginService.pollLoginResult(challengeId);
        return Result.success(response);
    }
}
