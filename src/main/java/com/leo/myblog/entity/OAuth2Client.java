package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 客户端实体
 */
@Data
@TableName("oauth2_clients")
public class OAuth2Client {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 客户端ID（唯一标识） */
    private String clientId;

    /** 客户端密钥（BCrypt加密） */
    private String clientSecret;

    /** 客户端名称 */
    private String clientName;

    /** 描述 */
    private String description;

    /** 允许的回调地址列表（JSON数组） */
    private String redirectUris;

    /** 支持的授权类型（JSON数组） */
    private String grantTypes;

    /** 允许的权限范围（JSON数组） */
    private String scopes;

    /** Logo URL */
    private String logoUrl;

    /** 首页 URL */
    private String homepageUrl;

    /** 是否为机密型客户端 */
    private Boolean isConfidential;

    /** 是否启用 */
    private Boolean isEnabled;

    /** 创建者用户ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
