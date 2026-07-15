package com.leo.myblog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 分页查询文章请求
 */
@Data
public class GetPostListRequest {
    
    /**
     * 页码（默认1）
     */
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = 1;
    
    /**
     * 每页数量（默认10，最大50）
     */
    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 50, message = "每页最多只能获取50篇文章")
    private Integer pageSize = 10;
    
    /**
     * 分类ID（可选）
     */
    private Long categoryId;
    
    /**
     * 标签ID（可选）
     */
    private Long tagId;
    
    /**
     * 关键字搜索（标题、摘要或内容，可选）
     */
    private String keyword;
    
    /**
     * 是否搜索全文（包括文章内容，默认false）
     */
    private Boolean searchContent = false;
    
    /**
     * 状态筛选（可选，仅管理员可用）
     */
    private Integer status;
    
    /**
     * 开始时间（可选，按发布时间筛选）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    /**
     * 结束时间（可选，按发布时间筛选）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    /**
     * 最小阅读量（可选）
     */
    private Long minViewCount;
    
    /**
     * 最大阅读量（可选）
     */
    private Long maxViewCount;
    
    /**
     * 排序字段（默认publish_time）
     * 可选值：publish_time, view_count, create_time
     */
    private String sortBy = "publish_time";
    
    /**
     * 排序方向（默认desc）
     * 可选值：asc, desc
     */
    private String sortOrder = "desc";
}
