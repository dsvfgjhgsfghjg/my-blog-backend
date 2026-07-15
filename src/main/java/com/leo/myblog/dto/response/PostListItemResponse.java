package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项响应（简化版，不包含正文）
 */
@Data
public class PostListItemResponse {
    
    /**
     * 文章ID（String类型避免JavaScript精度丢失）
     */
    private String id;
    
    /**
     * 作者ID
     */
    private String userId;
    
    /**
     * 作者用户名
     */
    private String authorUsername;
    
    /**
     * 作者昵称
     */
    private String authorNickname;
    
    /**
     * 分类ID
     */
    private String categoryId;
    
    /**
     * 分类名称
     */
    private String categoryName;
    
    /**
     * 文章标题
     */
    private String title;
    
    /**
     * SEO友好的URL标识
     */
    private String slug;
    
    /**
     * 文章摘要
     */
    private String summary;
    
    /**
     * 字数统计
     */
    private Integer wordCount;
    
    /**
     * 封面图片URL
     */
    private String coverImage;
    
    /**
     * 状态：0-草稿, 1-已发布, 2-私密
     */
    private Integer status;
    
    /**
     * 是否置顶
     */
    private Boolean isTop;
    
    /**
     * 阅读量
     */
    private String viewCount;
    
    /**
     * 标签列表
     */
    private List<PostDetailResponse.TagInfo> tags;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
