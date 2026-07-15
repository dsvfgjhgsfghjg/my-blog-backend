package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.AddSayingRequest;
import com.leo.myblog.dto.response.SayingResponse;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.SayingService;
import com.leo.myblog.util.AbilityValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 名言名句控制器
 * <p>
 * 公开接口（无需鉴权）：
 *   GET /api/public/sayings/{module}/random - 随机获取一条名言
 *   GET /api/public/sayings/{module}         - 获取模块所有名言
 * <p>
 * 管理接口（需要管理员 + manage_sayings 能力）：
 *   POST /api/admin/sayings/{module}        - 添加名言到指定模块
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SayingController {

    private final SayingService sayingService;
    private final AbilityValidator abilityValidator;

    /**
     * 从指定模块随机获取一条名言（公开接口）
     */
    @GetMapping("/api/public/sayings/{module}/random")
    public Result<SayingResponse> getRandomSaying(@PathVariable String module) {
        if (!sayingService.isValidModule(module)) {
            throw new BusinessException("无效的模块名: " + module + "，可选值: cs, design, study, technology, thought");
        }
        SayingResponse saying = sayingService.getRandomSaying(module);
        if (saying == null) {
            throw new BusinessException("该模块暂无名言数据: " + module);
        }
        return Result.success(saying);
    }

    /**
     * 获取指定模块所有名言（公开接口）
     */
    @GetMapping("/api/public/sayings/{module}")
    public Result<List<SayingResponse>> getSayings(@PathVariable String module) {
        if (!sayingService.isValidModule(module)) {
            throw new BusinessException("无效的模块名: " + module + "，可选值: cs, design, study, technology, thought");
        }
        List<SayingResponse> sayings = sayingService.getSayings(module);
        return Result.success(sayings);
    }

    /**
     * 添加名言到指定模块（管理接口）
     * 需要 manage_sayings 能力
     */
    @RequireAdmin
    @PostMapping("/api/admin/sayings/{module}")
    public Result<SayingResponse> addSaying(@PathVariable String module,
                                             @RequestAttribute Long userId,
                                             @Valid @RequestBody AddSayingRequest request) {
        if (!sayingService.isValidModule(module)) {
            throw new BusinessException("无效的模块名: " + module + "，可选值: cs, design, study, technology, thought");
        }

        // 验证 manage_sayings 能力
        abilityValidator.validateAbility(userId, "manage_sayings");

        log.info("添加名言: userId={}, module={}, content={}", userId, module, request.getContent());
        SayingResponse saying = sayingService.addSaying(module, request);
        return Result.success("名言添加成功", saying);
    }
}