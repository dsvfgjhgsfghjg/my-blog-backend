package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.UpdateSystemConfigRequest;
import com.leo.myblog.dto.response.CleanupProgressEvent;
import com.leo.myblog.dto.response.SystemConfigResponse;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.OrphanFileCleanupService;
import com.leo.myblog.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final OrphanFileCleanupService orphanFileCleanupService;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有系统配置（按分类分组，需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "查看系统设置", module = "security.totp.system_config")
    @GetMapping
    public Result<Map<String, List<SystemConfigResponse>>> getAllConfigs(@RequestAttribute Long userId) {
        log.info("获取所有系统配置: userId={}", userId);
        return Result.success(systemConfigService.getAllConfigs(userId));
    }

    /**
     * 获取指定分类的配置（需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "查看系统设置", module = "security.totp.system_config")
    @GetMapping("/category/{category}")
    public Result<List<SystemConfigResponse>> getConfigsByCategory(
            @RequestAttribute Long userId,
            @PathVariable String category) {
        log.info("获取分类配置: userId={}, category={}", userId, category);
        return Result.success(systemConfigService.getConfigsByCategory(userId, category));
    }

    /**
     * 获取单个配置项（需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "查看系统设置", module = "security.totp.system_config")
    @GetMapping("/{configKey}")
    public Result<SystemConfigResponse> getConfigByKey(
            @RequestAttribute Long userId,
            @PathVariable String configKey) {
        log.info("获取配置项: userId={}, configKey={}", userId, configKey);
        return Result.success(systemConfigService.getConfigByKey(userId, configKey));
    }

    /**
     * 更新配置项（需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "修改系统设置", module = "security.totp.system_config")
    @PutMapping
    public Result<Void> updateConfig(
            @RequestAttribute Long userId,
            @Valid @RequestBody UpdateSystemConfigRequest request) {
        log.info("更新配置: userId={}, configKey={}", userId, request.getConfigKey());
        systemConfigService.updateConfig(userId, request.getConfigKey(), request.getConfigValue());
        return Result.success("配置更新成功", null);
    }

    /**
     * 批量更新配置（需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "修改系统设置", module = "security.totp.system_config")
    @PutMapping("/batch")
    public Result<Void> batchUpdateConfigs(
            @RequestAttribute Long userId,
            @RequestBody Map<String, String> configs) {
        log.info("批量更新配置: userId={}, count={}", userId, configs.size());
        systemConfigService.batchUpdateConfigs(userId, configs);
        return Result.success("配置批量更新成功", null);
    }

    /**
     * 重置配置到默认值（需要 TOTP 双因素认证）
     * 需要 manage_system_config 能力或超级管理员权限
     */
    @RequireTotp(operation = "修改系统设置", module = "security.totp.system_config")
    @PostMapping("/{configKey}/reset")
    public Result<Void> resetConfig(
            @RequestAttribute Long userId,
            @PathVariable String configKey) {
        log.info("重置配置: userId={}, configKey={}", userId, configKey);
        systemConfigService.resetConfig(userId, configKey);
        return Result.success("配置已重置", null);
    }

    // ==================== 公开接口（无需权限）====================

    /**
     * 获取公开配置（前端可访问，无需登录）
     */
    @GetMapping("/public")
    public Result<Map<String, String>> getPublicConfigs() {
        log.debug("获取公开配置");
        return Result.success(systemConfigService.getPublicConfigs());
    }

    // ==================== 运维操作 ====================

    /**
     * 清理对象存储中的孤立垃圾数据（SSE 流式响应）
     * <p>
     * 通过 Server-Sent Events 实时推送清理进度到前端：
     * <ul>
     *   <li><b>progress</b> 事件：当前操作描述和进度百分比</li>
     *   <li><b>error</b> 事件：清理过程中出现的错误列表</li>
     *   <li><b>complete</b> 事件：最终完成结果，含完整统计数据和错误详情</li>
     * </ul>
     * <p>
     * 每个删除失败都会跳过，继续删除下一个，最终汇总错误信息。
     * 历史头像（avatars/）、博主头像（blogger/）、文章封面（covers/）、
     * 文章内容（posts/）、音频文件（audio/）均不受影响。
     * <p>
     * 需要 TOTP 双因素认证，需要 manage_system_config 能力或超级管理员权限
     *
     * @param userId 用户 ID（从 JWT 过滤器注入）
     * @return SseEmitter 流式事件
     */
    @RequireTotp(operation = "清除对象存储垃圾数据", module = "security.totp.system_config")
    @PostMapping(value = "/cleanup/orphan-files", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter cleanupOrphanFiles(@RequestAttribute Long userId) {
        log.info("开始清理孤立垃圾文件（SSE 流式）: userId={}", userId);

        // 超时时间设为 30 分钟（清理大文件可能需要较长时间）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 异步执行清理任务
        new Thread(() -> {
            try {
                orphanFileCleanupService.cleanupOrphanFiles(userId, event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        SseEmitter.SseEventBuilder sseEvent = SseEmitter.event()
                                .name(event.getType())
                                .data(json, MediaType.APPLICATION_JSON);

                        emitter.send(sseEvent);

                        // type=complete 时，发送完成标记后关闭
                        if ("complete".equals(event.getType())) {
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.warn("SSE 推送失败（前端可能已断开连接）: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
            } catch (BusinessException e) {
                // 防并发等已知异常，推送给前端
                try {
                    CleanupProgressEvent errorEvent = CleanupProgressEvent.error(e.getMessage(), 0, List.of());
                    String json = objectMapper.writeValueAsString(errorEvent);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(json, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    log.warn("SSE 推送错误事件失败: {}", ex.getMessage());
                }
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("清理任务执行异常", e);
                emitter.completeWithError(e);
            }
        }).start();

        // 超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时（清理任务可能仍在后台执行）: userId={}", userId);
        });

        // 完成回调
        emitter.onCompletion(() -> {
            log.info("SSE 连接已关闭（清理任务结束）: userId={}", userId);
        });

        // 错误回调
        emitter.onError(ex -> {
            log.warn("SSE 连接发生错误: {}", ex.getMessage());
        });

        return emitter;
    }
}
