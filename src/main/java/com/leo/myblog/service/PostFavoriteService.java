package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.response.FavoriteStatusResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.PostFavorite;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.PostFavoriteMapper;
import com.leo.myblog.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章收藏服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostFavoriteService {
    
    private final PostFavoriteMapper postFavoriteMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    
    /**
     * 收藏文章
     */
    @Transactional(rollbackFor = Exception.class)
    public void favoritePost(Long userId, Long postId) {
        // 1. 验证文章是否存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 检查是否已收藏
        LambdaQueryWrapper<PostFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostFavorite::getUserId, userId)
               .eq(PostFavorite::getPostId, postId);
        PostFavorite existing = postFavoriteMapper.selectOne(wrapper);
        
        if (existing != null) {
            throw new BusinessException("已经收藏过该文章");
        }
        
        // 3. 创建收藏记录
        PostFavorite favorite = new PostFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(postId);
        favorite.setCreateTime(LocalDateTime.now());
        
        postFavoriteMapper.insert(favorite);
        
        log.info("用户 {} 收藏文章 {}", userId, postId);
    }
    
    /**
     * 取消收藏
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfavoritePost(Long userId, Long postId) {
        // 1. 查找收藏记录
        LambdaQueryWrapper<PostFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostFavorite::getUserId, userId)
               .eq(PostFavorite::getPostId, postId);
        PostFavorite favorite = postFavoriteMapper.selectOne(wrapper);
        
        if (favorite == null) {
            throw new BusinessException("未收藏该文章");
        }
        
        // 2. 删除收藏记录
        postFavoriteMapper.deleteById(favorite.getId());
        
        log.info("用户 {} 取消收藏文章 {}", userId, postId);
    }
    
    /**
     * 检查文章是否被当前用户收藏
     */
    public FavoriteStatusResponse checkFavoriteStatus(Long userId, Long postId) {
        FavoriteStatusResponse response = new FavoriteStatusResponse();
        
        if (userId == null) {
            // 未登录用户，返回未收藏
            response.setIsFavorited(false);
            return response;
        }
        
        LambdaQueryWrapper<PostFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostFavorite::getUserId, userId)
               .eq(PostFavorite::getPostId, postId);
        long count = postFavoriteMapper.selectCount(wrapper);
        
        response.setIsFavorited(count > 0);
        return response;
    }
    
    /**
     * 获取用户的收藏列表（分页）
     */
    public PageResult<PostListItemResponse> getUserFavorites(Long userId, Integer pageNum, Integer pageSize) {
        // 1. 查询用户的收藏记录
        int current = pageNum != null ? pageNum : 1;
        int size = pageSize != null ? pageSize : 20;
        
        Page<PostFavorite> favoritePage = new Page<>(current, size);
        LambdaQueryWrapper<PostFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostFavorite::getUserId, userId)
               .orderByDesc(PostFavorite::getCreateTime);
        
        Page<PostFavorite> resultPage = postFavoriteMapper.selectPage(favoritePage, wrapper);
        
        // 2. 提取文章ID列表
        List<Long> postIds = resultPage.getRecords().stream()
                .map(PostFavorite::getPostId)
                .collect(Collectors.toList());
        
        if (postIds.isEmpty()) {
            // 没有收藏，返回空结果
            Page<PostListItemResponse> emptyPage = new Page<>(current, size);
            emptyPage.setRecords(List.of());
            return PageResult.of(emptyPage);
        }
        
        // 3. 批量查询文章详情
        List<Post> posts = postMapper.selectBatchIds(postIds);
        
        // 4. 按照收藏顺序排序并转换为响应对象
        List<PostListItemResponse> responses = postIds.stream()
                .map(postId -> posts.stream()
                        .filter(p -> p.getId().equals(postId))
                        .findFirst()
                        .orElse(null))
                .filter(post -> post != null)
                .map(post -> {
                    // 使用PostService的转换方法
                    return convertToPostListItemResponse(post);
                })
                .collect(Collectors.toList());
        
        // 5. 构建分页结果
        Page<PostListItemResponse> responsePage = new Page<>();
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setPages(resultPage.getPages());
        responsePage.setRecords(responses);
        
        return PageResult.of(responsePage);
    }
    
    /**
     * 将Post实体转换为PostListItemResponse
     */
    private PostListItemResponse convertToPostListItemResponse(Post post) {
        // 这里需要复用PostServiceImpl中的转换逻辑
        // 为了简化，我们调用PostService的方法
        return postService.convertToPostListItemResponse(post);
    }
}
