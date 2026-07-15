package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.GetAuditLogRequest;
import com.leo.myblog.dto.response.AuditLogResponse;
import com.leo.myblog.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器
 * 只有管理员可以访问
 */
@Slf4j
@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    /**
     * 分页查询审计日志（需要 TOTP 双因素认证）
     * GET /api/system/audit-logs?pageNum=1&pageSize=20&username=admin&operationType=LOGIN&module=USER&success=true&startTime=2026-05-01T00:00:00&endTime=2026-05-13T23:59:59
     */
    @RequireTotp(operation = "查看审计日志", module = "security.totp.audit_log")
    @GetMapping
    @RequireAdmin
    public Result<PageResult<AuditLogResponse>> getAuditLogs(@Valid GetAuditLogRequest request) {
        log.debug("查询审计日志: {}", request);
        Page<AuditLogResponse> page = auditLogService.getAuditLogs(request);
        return Result.success(PageResult.of(page));
    }
    
    /**
     * 获取审计日志详情（需要 TOTP 双因素认证）
     * GET /api/system/audit-logs/{id}
     */
    @RequireTotp(operation = "查看审计日志详情", module = "security.totp.audit_log")
    @GetMapping("/{id}")
    @RequireAdmin
    public Result<AuditLogResponse> getAuditLogById(@PathVariable Long id) {
        log.debug("查询审计日志详情: id={}", id);
        AuditLogResponse log = auditLogService.getAuditLogById(id);
        return Result.success(log);
    }
    
    /**
     * 获取审计日志统计信息（需要 TOTP 双因素认证）
     * GET /api/system/audit-logs/stats
     */
    @RequireTotp(operation = "查看审计日志统计", module = "security.totp.audit_log")
    @GetMapping("/stats")
    @RequireAdmin
    public Result<AuditLogService.AuditLogStatsResponse> getAuditLogStats() {
        log.debug("查询审计日志统计信息");
        AuditLogService.AuditLogStatsResponse stats = auditLogService.getAuditLogStats();
        return Result.success(stats);
    }
    
    /**
     * 删除过期的审计日志
     * DELETE /api/system/audit-logs/expired?days=30
     */
    @DeleteMapping("/expired")
    @RequireAdmin
    public Result<Integer> deleteExpiredLogs(@RequestParam(defaultValue = "30") int days) {
        log.info("删除过期审计日志: 保留{}天", days);
        
        if (days < 1 || days > 365) {
            return Result.error("保留天数必须在1-365之间");
        }
        
        int deletedCount = auditLogService.deleteExpiredLogs(days);
        return Result.success("已删除" + deletedCount + "条过期日志", deletedCount);
    }
}
