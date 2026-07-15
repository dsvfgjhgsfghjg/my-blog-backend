package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体
 */
@Data
@TableName("t_post")
public class Post {
    
    /**
     * 文章ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 文章标题
     */
    private String title;
    
    /**
     * SEO友好的URL标识
     */
    private String slug;
    
    /**
     * 文章摘要（前150字符）
     */
    private String summary;
    
    /**
     * Markdown正文内容路径（MinIO对象路径）
     * 格式: posts/{postId}/{uuid}.md
     */
    private String contentPath;
    
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
    private Long viewCount;
    
    /**
     * 软删除标记
     */
    @TableLogic
    private Boolean isDeleted;
    
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
