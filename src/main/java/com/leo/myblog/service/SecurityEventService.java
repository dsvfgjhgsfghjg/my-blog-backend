package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.entity.SecurityEvent;
import com.leo.myblog.mapper.SecurityEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 安全事件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {
    
    private final SecurityEventMapper securityEventMapper;
    
    /**
     * 异步记录安全事件
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void recordEvent(String eventType, String ipAddress, String userAgent, 
                           String requestUrl, String requestMethod, String matchedPattern,
                           String threatLevel, String actionTaken) {
        try {
            SecurityEvent event = new SecurityEvent();
            event.setEventType(eventType);
            event.setIpAddress(ipAddress);
            event.setUserAgent(userAgent);
            event.setRequestUrl(requestUrl);
            event.setRequestMethod(requestMethod);
            event.setMatchedPattern(matchedPattern);
            event.setThreatLevel(threatLevel);
            event.setActionTaken(actionTaken);
            event.setTriggerCount(1);
            event.setCreateTime(LocalDateTime.now());
            
            securityEventMapper.insert(event);
            
            // 高危事件立即记录日志
            if ("CRITICAL".equals(threatLevel) || "HIGH".equals(threatLevel)) {
                log.warn("【高危安全事件】type={}, ip={}, ua={}, url={}, pattern={}, threat={}", 
                        eventType, ipAddress, userAgent, requestUrl, matchedPattern, threatLevel);
            } else {
                log.debug("安全事件: type={}, ip={}, pattern={}, threat={}", 
                        eventType, ipAddress, matchedPattern, threatLevel);
            }
        } catch (Exception e) {
            log.error("记录安全事件失败", e);
        }
    }
    
    /**
     * 获取频繁违规的IP（最近24小时）
     */
    public List<Map<String, Object>> getFrequentViolators(int minCount) {
        return securityEventMapper.getFrequentViolators(minCount);
    }
    
    /**
     * 获取威胁等级统计（最近7天）
     */
    public List<Map<String, Object>> getThreatLevelStats() {
        return securityEventMapper.getThreatLevelStats();
    }
    
    /**
     * 清理过期事件日志（保留90天）
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOldEvents() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90);
        LambdaQueryWrapper<SecurityEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SecurityEvent::getCreateTime, cutoffTime);
        
        int deleted = securityEventMapper.delete(wrapper);
        log.info("清理过期安全事件: count={}", deleted);
        return deleted;
    }
}
