package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方账户关联实体
 */
@Data
@TableName("third_party_accounts")
public class ThirdPartyAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 第三方平台名称 */
    private String provider;

    /** 第三方平台的用户ID */
    private String providerUserId;

    /** 第三方平台的用户名 */
    private String providerUsername;

    /** 第三方头像URL */
    private String providerAvatar;

    /** 第三方邮箱 */
    private String providerEmail;

    /** 第三方 access_token（加密存储） */
    private String accessToken;

    /** 第三方 refresh_token（加密存储） */
    private String refreshToken;

    /** 第三方 token 过期时间 */
    private LocalDateTime tokenExpiresAt;

    /** 关联时间 */
    private LocalDateTime linkedAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
