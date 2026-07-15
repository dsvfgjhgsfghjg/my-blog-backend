package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.entity.BugReport;
import com.leo.myblog.service.BugReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bug 日志上报控制器（App 端接口）
 * <p>
 * 提供 App 端上传 Bug 日志的 REST API
 * 日志内容使用 Gzip + Base64 压缩后传输
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/app/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    /**
     * 提交 Bug 日志
     * <p>
     * App 端将日志文本 Gzip 压缩后 Base64 编码，通过此接口上传。
     * 后端解压后存储到 MinIO，元数据写入数据库。
     *
     * @param userId  当前用户 ID（从 JWT 中解析）
     * @param request 请求体
     * @return 上报结果
     */
    @PostMapping
    public Result<?> submitReport(
            @RequestAttribute Long userId,
            @RequestBody BugReportRequest request) {

        log.info("App 上报 Bug 日志: userId={}, appVersion={}, deviceModel={}",
                userId, request.getAppVersion(), request.getDeviceModel());

        BugReport report = bugReportService.submitReport(
                userId,
                request.getLogData(),
                request.getAppVersion(),
                request.getDeviceModel(),
                request.getOsVersion(),
                request.getSummary()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", report.getId());
        response.put("status", report.getStatus());
        response.put("createdAt", report.getCreatedAt());

        log.info("Bug 日志上报完成: id={}, userId={}", report.getId(), userId);
        return Result.success(response);
    }

    /**
     * Bug 日志上报请求体
     */
    @lombok.Data
    public static class BugReportRequest {
        /**
         * Gzip + Base64 编码的日志内容
         */
        private String logData;

        /**
         * App 版本号
         */
        private String appVersion;

        /**
         * 设备型号
         */
        private String deviceModel;

        /**
         * 操作系统版本
         */
        private String osVersion;

        /**
         * 日志摘要（可选）
         */
        private String summary;
    }
}
