package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件实体类
 */
@Data
@TableName("t_security_event")
public class SecurityEvent {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 事件类型：UA_BLOCKED/IP_BANNED/HONEYPOT_TRIGGERED
     */
    private String eventType;
    
    /**
     * 客户端IP
     */
    private String ipAddress;
    
    /**
     * User-Agent
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
     * 匹配的黑名单模式
     */
    private String matchedPattern;
    
    /**
     * 威胁等级：LOW/MEDIUM/HIGH/CRITICAL
     */
    private String threatLevel;
    
    /**
     * 采取的动作：BLOCKED/HONEYPOT
     */
    private String actionTaken;
    
    /**
     * 触发次数
     */
    private Integer triggerCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
