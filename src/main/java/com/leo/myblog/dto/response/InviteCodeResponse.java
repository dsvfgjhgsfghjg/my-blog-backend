package com.leo.myblog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InviteCodeResponse {
    private Long id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Long usedBy;
    private LocalDateTime revokedAt;
    private String status;
}
