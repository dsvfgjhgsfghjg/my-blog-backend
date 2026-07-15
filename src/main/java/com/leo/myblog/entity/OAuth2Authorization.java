package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 授权记录实体
 */
@Data
@TableName("oauth2_authorizations")
public class OAuth2Authorization {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 客户端ID */
    private String clientId;

    /** 用户ID */
    private Long userId;

    /** 授权码（authorization_code grant用） */
    private String authorizationCode;

    /** access_token 的哈希值 */
    private String accessTokenHash;

    /** refresh_token 的哈希值 */
    private String refreshTokenHash;

    /** 授权类型 */
    private String grantType;

    /** 实际授予的权限范围（JSON数组） */
    private String scope;

    /** 回调地址 */
    private String redirectUri;

    /** 授权时间 */
    private LocalDateTime authTime;

    /** access_token 过期时间 */
    private LocalDateTime accessTokenExpiresAt;

    /** refresh_token 过期时间 */
    private LocalDateTime refreshTokenExpiresAt;

    /** 是否已撤销 */
    private Boolean isRevoked;

    /** 撤销时间 */
    private LocalDateTime revokedAt;
}
