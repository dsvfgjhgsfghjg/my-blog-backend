package com.leo.myblog.dto.request;

import lombok.Data;

/**
 * 审计日志查询请求
 */
@Data
public class GetAuditLogRequest {
    
    /**
     * 页码（默认1）
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量（默认20，最大100）
     */
    private Integer pageSize = 20;
    
    /**
     * 用户名（模糊搜索）
     */
    private String username;
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作模块
     */
    private String module;

    /**
     * 按日志等级筛选：INFO / NOTICE / WARNING / ERROR / CRITICAL
     */
    private String level;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 开始时间
     */
    private String startTime;
    
    /**
     * 结束时间
     */
    private String endTime;
}
