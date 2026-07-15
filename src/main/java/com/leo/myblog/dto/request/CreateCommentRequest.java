package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建评论请求
 */
@Data
public class CreateCommentRequest {
    
    /**
     * 文章ID
     */
    @NotNull(message = "文章ID不能为空")
    private Long postId;
    
    /**
     * 父评论ID（回复评论时填写，顶级评论为null）
     */
    private Long parentId;
    
    /**
     * 被回复的用户ID（@功能）
     */
    private Long replyToUserId;
    
    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    private String content;
}
