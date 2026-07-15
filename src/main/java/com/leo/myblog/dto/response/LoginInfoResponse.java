package com.leo.myblog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录信息响应（用于管理页面欢迎页）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginInfoResponse {
    
    /**
     * 当前登录IP地址
     */
    private String currentLoginIp;
    
    /**
     * 上次登录IP地址
     */
    private String lastLoginIp;
    
    /**
     * 上次登录时间
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * 登录总次数
     */
    private Long loginCount;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
}
