package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录日志实体类
 */
@Data
@TableName("t_login_log")
public class LoginLog {
    
    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 登录IP地址
     */
    private String ipAddress;
    
    /**
     * 设备类型：DESKTOP/MOBILE/TABLET/UNKNOWN
     */
    private String deviceType;
    
    /**
     * 操作系统名称
     */
    private String osName;
    
    /**
     * 操作系统版本
     */
    private String osVersion;
    
    /**
     * 浏览器名称
     */
    private String browserName;
    
    /**
     * 浏览器版本
     */
    private String browserVersion;
    
    /**
     * 原始User-Agent字符串
     */
    private String userAgent;
    
    /**
     * 登录时间
     */
    private LocalDateTime loginTime;
    
    /**
     * 登录结果：SUCCESS/FAILED
     */
    private String loginResult;
    
    /**
     * 失败原因
     */
    private String failureReason;
}
