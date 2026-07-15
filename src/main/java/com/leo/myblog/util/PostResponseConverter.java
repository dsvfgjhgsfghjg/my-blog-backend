package com.leo.myblog.util;

import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.User;
import com.leo.myblog.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/**
 * 文章响应转换器
 */
@Component
@RequiredArgsConstructor
public class PostResponseConverter {
    
    private final UserCacheService userCacheService;
    
    /**
     * 将Post实体转换为PostListItemResponse
     */
    public PostListItemResponse convertToPostListItemResponse(Post post) {
        PostListItemResponse response = new PostListItemResponse();
        
        // 设置String类型的ID
        response.setId(String.valueOf(post.getId()));
        response.setUserId(String.valueOf(post.getUserId()));
        
        // 获取作者信息
        User author = userCacheService.getUserById(post.getUserId());
        if (author != null) {
            response.setAuthorUsername(author.getUsername());
            response.setAuthorNickname(author.getNickname());
        }
        
        // 复制其他属性
        BeanUtils.copyProperties(post, response, "id", "userId");
        
        // 设置String类型的分类ID
        if (post.getCategoryId() != null) {
            response.setCategoryId(String.valueOf(post.getCategoryId()));
        }
        
        // 设置String类型的阅读量
        if (post.getViewCount() != null) {
            response.setViewCount(String.valueOf(post.getViewCount()));
        }
        
        return response;
    }
}
