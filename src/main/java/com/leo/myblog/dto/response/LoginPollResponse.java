package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 登录挑战轮询响应
 * <p>
 * Token 字段与 OAuth2 的 {@link OAuth2TokenResponse} 对齐（access_token / refresh_token），
 * 这样前端登录页两条路径（密码、认证器）可以用同一套归一化逻辑写入 store，
 * Token 刷新链路也无需区分登录方式。
 */
@Data
public class LoginPollResponse {

    /**
     * 挑战状态: pending / approved / rejected / expired
     */
    private String status;

    /**
     * 轮询用的挑战 ID
     */
    private String challengeId;

    // ==================== approved 时返回的 Token ====================

    /**
     * 访问令牌（JWT）
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌类型
     */
    private String tokenType;

    /**
     * access_token 有效期（秒）
     */
    private Long expiresIn;

    /**
     * refresh_token 有效期（秒）
     */
    private Long refreshTokenExpiresIn;

    // ==================== 用户信息 ====================

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
