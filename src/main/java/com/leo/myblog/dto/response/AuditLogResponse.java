package com.leo.myblog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志响应DTO
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {
    
    /**
     * 日志ID
     */
    private Long id;
    
    /**
     * 操作用户ID
     */
    private Long userId;
    
    /**
     * 操作用户名
     */
    private String username;
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作模块
     */
    private String module;
    
    /**
     * 操作描述
     */
    private String description;
    
    /**
     * 请求IP地址
     */
    private String ipAddress;
    
    /**
     * 客户端类型（浏览器/移动端/桌面端）
     */
    private String deviceType;
    
    /**
     * 操作系统名称
     */
    private String osName;
    
    /**
     * 浏览器/客户端名称
     */
    private String browserName;
    
    /**
     * 完整User-Agent字符串
     */
    private String userAgent;
    
    /**
     * 请求URL
     */
    private String requestUrl;
    
    /**
     * 请求方法
     */
    private String requestMethod;
    
    /**
     * 请求参数（JSON格式）
     */
    private String requestParams;
    
    /**
     * 额外数据（JSON格式，包含设备信息等）
     */
    private String extraData;
    
    /**
     * 响应状态码
     */
    private Integer responseStatus;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 执行时长（毫秒）
     */
    private Long executionTime;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
