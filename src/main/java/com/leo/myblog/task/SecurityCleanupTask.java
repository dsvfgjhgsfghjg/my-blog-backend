package com.leo.myblog.task;

import com.leo.myblog.service.IpBanService;
import com.leo.myblog.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 安全相关定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityCleanupTask {
    
    private final SecurityEventService securityEventService;
    private final IpBanService ipBanService;
    
    /**
     * 每天凌晨2点清理过期的安全事件日志（保留90天）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupSecurityEvents() {
        log.info("开始清理过期安全事件日志...");
        try {
            int deleted = securityEventService.cleanupOldEvents();
            log.info("安全事件日志清理完成: deleted={}", deleted);
        } catch (Exception e) {
            log.error("清理安全事件日志失败", e);
        }
    }
    
    /**
     * 每小时清理过期的IP封禁记录
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredIpBans() {
        log.info("开始清理过期IP封禁记录...");
        try {
            int deleted = ipBanService.cleanupExpiredBans();
            log.info("IP封禁记录清理完成: deleted={}", deleted);
        } catch (Exception e) {
            log.error("清理IP封禁记录失败", e);
        }
    }
}
