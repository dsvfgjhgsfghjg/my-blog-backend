package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论响应
 */
@Data
public class CommentResponse {
    
    /**
     * 评论ID（String类型避免JavaScript精度丢失）
     */
    private String id;
    
    /**
     * 文章ID
     */
    private String postId;
    
    /**
     * 评论者ID
     */
    private String userId;
    
    /**
     * 评论者昵称
     */
    private String userNickname;
    
    /**
     * 评论者头像
     */
    private String userAvatar;
    
    /**
     * 父评论ID
     */
    private String parentId;
    
    /**
     * 被回复的用户ID
     */
    private String replyToUserId;
    
    /**
     * 被回复的用户昵称
     */
    private String replyToUserNickname;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 点赞数
     */
    private Integer likeCount;
    
    /**
     * 是否已点赞（当前用户）
     */
    private Boolean isLiked;
    
    /**
     * 状态：1正常 0隐藏 -1已删除
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 子评论列表（回复）
     */
    private List<CommentResponse> replies;
    
    /**
     * 回复数量
     */
    private Integer replyCount;
}
