package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会话信息响应
 */
@Data
public class UserSessionResponse {
    
    /**
     * 会话ID（Token Hash）
     */
    private String sessionId;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 设备类型：DESKTOP/MOBILE/TABLET/UNKNOWN
     */
    private String deviceType;
    
    /**
     * 操作系统
     */
    private String osName;
    
    /**
     * 浏览器
     */
    private String browserName;
    
    /**
     * 设备详细信息（品牌+型号+浏览器）
     */
    private String deviceInfo;
    
    /**
     * 登录时间
     */
    private LocalDateTime loginTime;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 是否为当前会话
     */
    private Boolean isCurrent;
}
