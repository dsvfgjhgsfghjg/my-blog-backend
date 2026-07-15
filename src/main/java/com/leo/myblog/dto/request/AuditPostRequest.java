package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审计文章请求
 */
@Data
public class AuditPostRequest {
    
    /**
     * 审计结果：1-通过, 2-拒绝
     */
    @NotNull(message = "审计结果不能为空")
    private Integer auditStatus;
    
    /**
     * 审计意见/拒绝原因
     */
    private String auditComment;
}
