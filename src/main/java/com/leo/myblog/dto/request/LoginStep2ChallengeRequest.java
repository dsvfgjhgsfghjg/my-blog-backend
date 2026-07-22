package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 两步登录：第二步挑战推送方式请求
 */
@Data
public class LoginStep2ChallengeRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录方式，固定为 "authenticator_app"
     */
    private String method = "authenticator_app";
}