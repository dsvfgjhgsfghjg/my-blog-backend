package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 客户端注册/更新请求
 */
@Data
public class OAuth2ClientRequest {

    /** 客户端名称 */
    @NotBlank(message = "客户端名称不能为空")
    private String clientName;

    /** 描述 */
    private String description;

    /** 允许的回调地址列表 */
    private List<String> redirectUris;

    /** 支持的授权类型 */
    private List<String> grantTypes;

    /** 允许的权限范围 */
    private List<String> scopes;

    /** Logo URL */
    private String logoUrl;

    /** 首页 URL */
    private String homepageUrl;

    /** 是否为机密型客户端 */
    private Boolean isConfidential;
}
