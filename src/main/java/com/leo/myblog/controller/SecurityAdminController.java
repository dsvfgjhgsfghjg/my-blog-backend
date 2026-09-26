package com.leo.myblog.controller;

import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.Result;
import com.leo.myblog.entity.IpBan;
import com.leo.myblog.service.IpBanService;
import com.leo.myblog.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IP封禁管理控制器（管理员专用）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class SecurityAdminController {
    
    private final IpBanService ipBanService;
    private final SecurityEventService securityEventService;
    
    /**
     * 获取当前被封禁的IP列表
     * 需要管理员权限（不需要TOTP，因为是查看类操作）
     */
    @RequireAdmin
    @GetMapping("/banned-ips")
    public Result<List<IpBan>> getBannedIps(@RequestAttribute Long userId) {
        List<IpBan> bannedIps = ipBanService.getBannedIps();
        return Result.success(bannedIps);
    }
    
    /**
     * 手动封禁IP
     * 需要管理员权限和TOTP双因素认证
     */
    @RequireAdmin
    @RequireTotp(operation = "封禁IP", module = "security.totp.system_config")
    // 显式给描述：光靠推导只能得到「封禁 IP 1.2.3.4」，看不出封多久
    @AuditLog(action = AuditAction.BAN_IP,
              description = "封禁 IP {ipAddress}，时长 {durationHours} 小时")
    @PostMapping("/ban-ip")
    public Result<Void> banIp(
            @RequestAttribute Long userId,
            @RequestParam String ipAddress,
            @RequestParam(required = false, defaultValue = "管理员手动封禁") String reason,
            @RequestParam(required = false, defaultValue = "24") long durationHours) {
        
        ipBanService.banIp(ipAddress, reason, 0, durationHours);
        log.info("管理员手动封禁IP: userId={}, ip={}, reason={}, duration={}h", userId, ipAddress, reason, durationHours);
        return Result.success(null);
    }
    
    /**
     * 解除IP封禁
     * 需要管理员权限和TOTP双因素认证
     */
    @RequireAdmin
    @RequireTotp(operation = "解除IP封禁", module = "security.totp.system_config")
    @DeleteMapping("/unban-ip/{ipAddress}")
    public Result<Void> unbanIp(@PathVariable String ipAddress, @RequestAttribute Long userId) {
        ipBanService.unbanIp(ipAddress);
        log.info("管理员解除IP封禁: userId={}, ip={}", userId, ipAddress);
        return Result.success(null);
    }
    
    /**
     * 获取频繁违规的IP统计
     */
    @GetMapping("/frequent-violators")
    public Result<List<Map<String, Object>>> getFrequentViolators(
            @RequestParam(required = false, defaultValue = "5") int minCount) {
        List<Map<String, Object>> violators = securityEventService.getFrequentViolators(minCount);
        return Result.success(violators);
    }
    
    /**
     * 获取威胁等级统计
     */
    @GetMapping("/threat-stats")
    public Result<List<Map<String, Object>>> getThreatStats() {
        List<Map<String, Object>> stats = securityEventService.getThreatLevelStats();
        return Result.success(stats);
    }
    
    /**
     * 清理过期数据
     */
    @PostMapping("/cleanup")
    public Result<Map<String, Integer>> cleanup() {
        Map<String, Integer> result = new HashMap<>();
        
        // 清理安全事件
        int eventsDeleted = securityEventService.cleanupOldEvents();
        result.put("eventsDeleted", eventsDeleted);
        
        // 清理IP封禁
        int bansDeleted = ipBanService.cleanupExpiredBans();
        result.put("bansDeleted", bansDeleted);
        
        log.info("管理员手动清理过期数据: events={}, bans={}", eventsDeleted, bansDeleted);
        return Result.success(result);
    }
}
