package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户个人信息响应（用于 App 端 Profile 页面）
 */
@Data
public class UserProfileResponse {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatarUrl;

    /**
     * 首次绑定时间
     */
    private LocalDateTime boundAt;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 已绑定设备数量
     */
    private Integer deviceCount;
}
