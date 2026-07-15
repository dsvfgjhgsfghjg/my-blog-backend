package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 第三方登录请求
 */
@Data
public class ThirdPartyLoginRequest {

    /** 第三方平台名称（github, google 等） */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    /** 第三方授权码（OAuth2 callback 获取的 code） */
    @NotBlank(message = "授权码不能为空")
    private String code;

    /** 重定向 URI（用于验证 state 和 code） */
    private String redirectUri;
}
