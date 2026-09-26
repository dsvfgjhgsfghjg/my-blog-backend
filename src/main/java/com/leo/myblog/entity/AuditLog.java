package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 * 记录所有敏感操作和系统事件
 */
@Data
@TableName("t_audit_log")
public class AuditLog {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 操作类型（LOGIN, LOGOUT, CREATE, UPDATE, DELETE, RESET_PASSWORD, CHANGE_ROLE等）
     */
    private String operationType;
    
    /**
     * 操作模块（USER, POST, COMMENT, CATEGORY, TAG, SYSTEM等）
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
     * 请求User-Agent
     */
    private String userAgent;
    
    /**
     * 请求URL
     */
    private String requestUrl;
    
    /**
     * 请求方法（GET, POST, PUT, DELETE等）
     */
    private String requestMethod;
    
    /**
     * 请求参数（JSON格式，敏感信息已脱敏）
     */
    private String requestParams;
    
    /**
     * 响应状态码
     */
    private Integer responseStatus;
    
    /**
     * 响应消息
     */
    private String responseMessage;
    
    /**
     * 执行时长（毫秒）
     */
    private Long executionTime;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;
    
    /**
     * 额外数据（JSON格式，存储业务相关数据）
     */
    private String extraData;

    /**
     * 日志等级：INFO(信息)/NOTICE(记录)/WARNING(警告)/ERROR(错误)/CRITICAL(关键)
     * <p>
     * 由操作类型推出（见 {@code AuditLevelResolver}），操作失败时自动升级。
     * 用 String 而非枚举入库，与 operationType/module 的既有风格一致，也避免
     * MyBatis 枚举映射带来的额外配置。
     */
    private String level;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
