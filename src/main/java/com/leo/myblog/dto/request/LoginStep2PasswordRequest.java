package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 两步登录：第二步密码方式请求
 */
@Data
public class LoginStep2PasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 登录方式，固定为 "password"
     */
    private String method = "password";
}