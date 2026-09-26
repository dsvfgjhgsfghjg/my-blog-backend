package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.request.GetAuditLogRequest;
import com.leo.myblog.dto.response.AuditLogResponse;
import com.leo.myblog.entity.AuditLog;
import com.leo.myblog.enums.AuditLevel;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审计日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {
    
    private final AuditLogMapper auditLogMapper;
    
    /**
     * 记录审计日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordAuditLog(AuditLog auditLog) {
        try {
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
            log.debug("审计日志记录成功: operationType={}, module={}, userId={}", 
                    auditLog.getOperationType(), auditLog.getModule(), auditLog.getUserId());
        } catch (Exception e) {
            log.error("审计日志记录失败", e);
            // 不抛出异常，避免影响主业务流程
        }
    }
    
    /**
     * 分页查询审计日志（管理员）
     */
    public Page<AuditLogResponse> getAuditLogs(GetAuditLogRequest request) {
        // 参数校验
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        
        // 限制最大页面大小为100
        if (pageSize > 100) {
            throw new BusinessException("每页最多只能获取100条日志");
        }
        
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        
        // 按用户名模糊查询
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            wrapper.like(AuditLog::getUsername, request.getUsername());
        }
        
        // 按操作类型筛选
        if (request.getOperationType() != null && !request.getOperationType().isEmpty()) {
            wrapper.eq(AuditLog::getOperationType, request.getOperationType());
        }
        
        // 按模块筛选
        if (request.getModule() != null && !request.getModule().isEmpty()) {
            wrapper.eq(AuditLog::getModule, request.getModule());
        }

        // 按等级筛选。这里刻意用严格校验而不是 AuditLevel.from 的宽松回退 ——
        // 后者会把 WARN 这种拼错的值当成 NOTICE，静默查出一堆 NOTICE 行，看着像筛选生效了。
        if (request.getLevel() != null && !request.getLevel().isEmpty()) {
            if (!AuditLevel.isValid(request.getLevel())) {
                throw new BusinessException("无效的日志等级: " + request.getLevel());
            }
            wrapper.eq(AuditLog::getLevel, AuditLevel.from(request.getLevel()).getValue());
        }
        
        // 按成功状态筛选
        if (request.getSuccess() != null) {
            wrapper.eq(AuditLog::getSuccess, request.getSuccess());
        }
        
        // 按时间范围筛选
        if (request.getStartTime() != null && !request.getStartTime().isEmpty()) {
            LocalDateTime startTime = parseDateTime(request.getStartTime());
            wrapper.ge(AuditLog::getCreateTime, startTime);
        }
        if (request.getEndTime() != null && !request.getEndTime().isEmpty()) {
            LocalDateTime endTime = parseDateTime(request.getEndTime());
            wrapper.le(AuditLog::getCreateTime, endTime);
        }
        
        // 按创建时间倒序排列
        wrapper.orderByDesc(AuditLog::getCreateTime);
        
        Page<AuditLog> logPage = auditLogMapper.selectPage(page, wrapper);
        
        // 转换为响应对象
        Page<AuditLogResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(logPage, responsePage, "records");
        responsePage.setRecords(logPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        
        log.info("管理员查询审计日志: pageNum={}, pageSize={}, total={}", 
                pageNum, pageSize, responsePage.getTotal());
        
        return responsePage;
    }
    
    /**
     * 根据ID获取审计日志详情
     */
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog auditLog = auditLogMapper.selectById(id);
        if (auditLog == null) {
            throw new BusinessException("审计日志不存在");
        }
        return convertToResponse(auditLog);
    }
    
    /**
     * 删除过期的审计日志（管理员）
     * @param days 保留天数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteExpiredLogs(int days) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(AuditLog::getCreateTime, cutoffTime);
        
        int deletedCount = auditLogMapper.delete(wrapper);
        log.info("删除过期审计日志: 保留{}天, 删除{}条", days, deletedCount);
        return deletedCount;
    }
    
    /**
     * 获取审计日志统计信息
     */
    public AuditLogStatsResponse getAuditLogStats() {
        AuditLogStatsResponse stats = new AuditLogStatsResponse();
        
        // 总日志数
        Long totalCount = auditLogMapper.selectCount(null);
        stats.setTotalCount(totalCount);
        
        // 今日日志数
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<AuditLog> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(AuditLog::getCreateTime, todayStart);
        Long todayCount = auditLogMapper.selectCount(todayWrapper);
        stats.setTodayCount(todayCount);
        
        // 成功/失败统计
        LambdaQueryWrapper<AuditLog> successWrapper = new LambdaQueryWrapper<>();
        successWrapper.eq(AuditLog::getSuccess, true);
        Long successCount = auditLogMapper.selectCount(successWrapper);
        stats.setSuccessCount(successCount);
        
        LambdaQueryWrapper<AuditLog> failWrapper = new LambdaQueryWrapper<>();
        failWrapper.eq(AuditLog::getSuccess, false);
        Long failCount = auditLogMapper.selectCount(failWrapper);
        stats.setFailCount(failCount);
        
        // 各模块统计：按实际数据分组，不再写死模块列表
        // （原先只循环 6 个模块，BLOGGER/AUDIO 永远统计不到）
        for (Map<String, Object> row : auditLogMapper.countByModule()) {
            stats.getModuleStats().put(
                    String.valueOf(row.get("name")), toLong(row.get("total")));
        }

        // 各等级统计：新增，供前端展示"最严重的那些有多少"
        for (Map<String, Object> row : auditLogMapper.countByLevel()) {
            stats.getLevelStats().put(
                    String.valueOf(row.get("name")), toLong(row.get("total")));
        }

        return stats;
    }

    /** 统计查询返回的计数可能是 Long/Integer/BigInteger，统一转换 */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    /**
     * 转换为响应对象
     */
    private AuditLogResponse convertToResponse(AuditLog auditLog) {
        AuditLogResponse response = new AuditLogResponse();
        BeanUtils.copyProperties(auditLog, response);
        
        // 处理空值，将null转换为"N/A"
        if (response.getUsername() == null || response.getUsername().isEmpty()) {
            response.setUsername("N/A");
        }
        if (response.getIpAddress() == null || response.getIpAddress().isEmpty()) {
            response.setIpAddress("N/A");
        }
        if (response.getDescription() == null || response.getDescription().isEmpty()) {
            response.setDescription("N/A");
        }
        if (response.getRequestUrl() == null || response.getRequestUrl().isEmpty()) {
            response.setRequestUrl("N/A");
        }
        if (response.getRequestMethod() == null || response.getRequestMethod().isEmpty()) {
            response.setRequestMethod("N/A");
        }
        if (response.getUserAgent() == null || response.getUserAgent().isEmpty()) {
            response.setUserAgent("N/A");
        }
        
        // 从extraData中解析设备信息
        parseDeviceInfo(response, auditLog.getExtraData());
        
        return response;
    }
    
    /**
     * 从extraData中解析设备信息
     */
    private void parseDeviceInfo(AuditLogResponse response, String extraData) {
        if (extraData == null || extraData.isEmpty()) {
            response.setDeviceType("N/A");
            response.setOsName("N/A");
            response.setBrowserName("N/A");
            return;
        }
        
        try {
            // 解析JSON格式的extraData
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> extraMap = objectMapper.readValue(extraData, java.util.Map.class);
            
            // 提取设备信息
            String deviceType = (String) extraMap.get("deviceType");
            String osName = (String) extraMap.get("osName");
            String browserName = (String) extraMap.get("browserName");
            
            response.setDeviceType(deviceType != null && !deviceType.isEmpty() ? deviceType : "N/A");
            response.setOsName(osName != null && !osName.isEmpty() ? osName : "N/A");
            response.setBrowserName(browserName != null && !browserName.isEmpty() ? browserName : "N/A");
            
            // 提取Android细化备注
            String androidRemark = (String) extraMap.get("androidRemark");
            String androidDescription = (String) extraMap.get("androidDescription");
            
            // 如果有Android细化信息，添加到描述中
            if (androidRemark != null && !androidRemark.isEmpty()) {
                String currentDesc = response.getDescription();
                if (currentDesc == null || currentDesc.equals("N/A")) {
                    response.setDescription(androidDescription != null ? androidDescription : androidRemark);
                } else {
                    // 追加到现有描述后面
                    response.setDescription(currentDesc + " [" + androidRemark + "]");
                }
            }
        } catch (Exception e) {
            log.debug("解析设备信息失败: {}", extraData);
            response.setDeviceType("N/A");
            response.setOsName("N/A");
            response.setBrowserName("N/A");
        }
    }
    
    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            throw new BusinessException("时间格式错误，请使用ISO格式：yyyy-MM-ddTHH:mm:ss");
        }
    }
    
    /**
     * 审计日志统计响应
     */
    @lombok.Data
    public static class AuditLogStatsResponse {
        private Long totalCount;
        private Long todayCount;
        private Long successCount;
        private Long failCount;
        private java.util.Map<String, Long> moduleStats = new java.util.HashMap<>();
        /** 各等级条数，键为 INFO/NOTICE/WARNING/ERROR/CRITICAL */
        private java.util.Map<String, Long> levelStats = new java.util.HashMap<>();
    }
}
