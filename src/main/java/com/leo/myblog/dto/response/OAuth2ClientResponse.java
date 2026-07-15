package com.leo.myblog.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2 客户端响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ClientResponse {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("client_name")
    private String clientName;

    private String description;

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    private List<String> scopes;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("homepage_url")
    private String homepageUrl;

    @JsonProperty("is_confidential")
    private Boolean isConfidential;

    @JsonProperty("is_enabled")
    private Boolean isEnabled;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
