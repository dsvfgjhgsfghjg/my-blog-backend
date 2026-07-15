package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章审计响应
 */
@Data
public class PostAuditResponse {
    
    /**
     * 审计记录ID（String类型避免JavaScript精度丢失）
     */
    private String id;
    
    /**
     * 文章ID
     */
    private String postId;
    
    /**
     * 审计员ID
     */
    private String auditorId;
    
    /**
     * 审计员昵称
     */
    private String auditorNickname;
    
    /**
     * 审计状态：0-待审核, 1-已通过, 2-已拒绝
     */
    private Integer auditStatus;
    
    /**
     * 审计意见/拒绝原因
     */
    private String auditComment;
    
    /**
     * 审计时间
     */
    private LocalDateTime auditTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
