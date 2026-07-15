package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 查询评论列表请求
 */
@Data
public class GetCommentListRequest {
    
    /**
     * 文章ID
     */
    @NotNull(message = "文章ID不能为空")
    private Long postId;
    
    /**
     * 页码（默认1）
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量（默认20，最大100）
     */
    private Integer pageSize = 20;
}
