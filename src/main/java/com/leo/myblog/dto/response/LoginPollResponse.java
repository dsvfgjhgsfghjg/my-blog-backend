package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 登录挑战轮询响应
 */
@Data
public class LoginPollResponse {

    /**
     * 挑战状态: pending / approved / rejected / expired
     */
    private String status;

    /**
     * 认证成功时返回的 JWT Token
     */
    private String token;

    /**
     * 认证成功时返回的用户ID
     */
    private Long userId;

    /**
     * 认证成功时返回的昵称
     */
    private String nickname;

    /**
     * 认证成功时返回的头像URL
     */
    private String avatar;
}