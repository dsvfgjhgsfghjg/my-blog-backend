package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.entity.BugReport;
import com.leo.myblog.service.BugReportService;
import com.leo.myblog.service.BugReportService.BugReportDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bug 日志管理控制器（管理员接口）
 * <p>
 * 提供管理后台查看和管理 Bug 日志的 REST API
 * 仅管理员可访问
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/bug-reports")
@RequiredArgsConstructor
public class AdminBugReportController {

    private final BugReportService bugReportService;

    /**
     * 分页查询 Bug 日志列表
     *
     * @param userId 当前用户 ID（从 JWT 中解析）
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param status 状态筛选（可选: all/unread/read/resolved）
     * @return 分页结果
     */
    @RequireAdmin
    @GetMapping
    public Result<?> getReportList(
            @RequestAttribute Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String status) {

        log.debug("管理员查询 Bug 日志列表: userId={}, page={}, size={}, status={}",
                userId, page, size, status);

        Page<BugReport> reportPage = bugReportService.getReportList(page, size, status);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", reportPage.getRecords());
        response.put("total", reportPage.getTotal());
        response.put("page", reportPage.getCurrent());
        response.put("size", reportPage.getSize());
        response.put("pages", reportPage.getPages());

        return Result.success(response);
    }

    /**
     * 获取 Bug 日志详情（含日志内容）
     *
     * @param userId 当前用户 ID
     * @param id     日志 ID
     * @return 日志详情（含完整日志内容）
     */
    @RequireAdmin
    @GetMapping("/{id}")
    public Result<?> getReportDetail(
            @RequestAttribute Long userId,
            @PathVariable Long id) {

        log.debug("管理员查询 Bug 日志详情: userId={}, id={}", userId, id);

        BugReportDetail detail = bugReportService.getReportDetail(id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("report", detail.getReport());
        response.put("logContent", detail.getLogContent());

        return Result.success(response);
    }

    /**
     * 更新 Bug 日志状态
     *
     * @param userId  当前用户 ID
     * @param id      日志 ID
     * @param request 状态更新请求
     * @return 操作结果
     */
    @RequireAdmin
    @PutMapping("/{id}/status")
    public Result<?> updateStatus(
            @RequestAttribute Long userId,
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {

        log.info("管理员更新 Bug 日志状态: userId={}, id={}, status={}",
                userId, id, request.getStatus());

        bugReportService.updateStatus(id, request.getStatus(), request.getResolveNote());

        return Result.success("状态更新成功");
    }

    /**
     * 删除 Bug 日志
     *
     * @param userId 当前用户 ID
     * @param id     日志 ID
     * @return 操作结果
     */
    @RequireAdmin
    @DeleteMapping("/{id}")
    public Result<?> deleteReport(
            @RequestAttribute Long userId,
            @PathVariable Long id) {

        log.info("管理员删除 Bug 日志: userId={}, id={}", userId, id);

        bugReportService.deleteReport(id);

        return Result.success("日志删除成功");
    }

    /**
     * 状态更新请求体
     */
    @lombok.Data
    public static class UpdateStatusRequest {
        /**
         * 新状态: unread / read / resolved
         */
        private String status;

        /**
         * 解决备注（仅 resolved 时需要）
         */
        private String resolveNote;
    }
}
