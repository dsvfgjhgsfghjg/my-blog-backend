package com.leo.myblog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth2 Token 颁发请求
 * 支持多种 grant_type: password, authorization_code, client_credentials, refresh_token
 *
 * 字段使用 @JsonProperty 映射 snake_case 和 camelCase，
 * 前端请求体中的 grant_type 将正确映射到 grantType 字段
 */
@Data
public class OAuth2TokenRequest {

    /** 授权类型 */
    @NotBlank(message = "grant_type 不能为空")
    @JsonProperty("grant_type")
    private String grantType;

    // ============ password grant ============
    /** 用户名（password grant） */
    private String username;

    /** 密码（password grant） */
    private String password;

    // ============ authorization_code grant ============
    /** 授权码（authorization_code grant） */
    private String code;

    /** 重定向URI（authorization_code grant） */
    @JsonProperty("redirect_uri")
    private String redirectUri;

    // ============ client_credentials grant ============
    /** 客户端ID */
    @JsonProperty("client_id")
    private String clientId;

    /** 客户端密钥 */
    @JsonProperty("client_secret")
    private String clientSecret;

    // ============ refresh_token grant ============
    /** 刷新令牌 */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** 权限范围（可选） */
    private String scope;

    // ============ 验证码（password grant 安全增强） ============
    /** 旋转验证码标识 */
    @JsonProperty("captcha_nonce_str")
    private String captchaNonceStr;

    /** 旋转验证码角度 */
    @JsonProperty("captcha_angle")
    private Double captchaAngle;

    /** 旋转验证码轨迹 */
    @JsonProperty("captcha_trajectory")
    private java.util.List<OAuth2TrajectoryPointDTO> captchaTrajectory;

    /** 旋转验证码总耗时（毫秒） */
    @JsonProperty("captcha_total_time")
    private Long captchaTotalTime;

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class OAuth2TrajectoryPointDTO {
        private Double x;
        private Double y;
        private Long t;
    }
}
