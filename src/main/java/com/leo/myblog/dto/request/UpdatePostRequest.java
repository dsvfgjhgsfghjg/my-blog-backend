package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新文章请求
 */
@Data
public class UpdatePostRequest {
    
    /**
     * 文章标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;
    
    /**
     * SEO友好的URL标识
     */
    private String slug;
    
    /**
     * Markdown正文
     */
    @NotBlank(message = "内容不能为空")
    private String content;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 标签ID列表
     */
    private List<Long> tagIds;
    
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
}
