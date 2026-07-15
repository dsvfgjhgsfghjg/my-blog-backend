package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端公开文章列表项响应（包含预计阅读时间）
 */
@Data
public class PublicPostListItemResponse {
    
    /**
     * 文章ID
     */
    private String id;
    
    /**
     * 文章标题
     */
    private String title;
    
    /**
     * 封面图片URL
     */
    private String coverImage;
    
    /**
     * 文章摘要（前50字）
     */
    private String summary;
    
    /**
     * 字数统计
     */
    private Integer wordCount;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 预计阅读时间（分钟）
     */
    private Integer readTime;
}
