package com.leo.myblog.service;

import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.request.CreatePostRequest;
import com.leo.myblog.dto.request.GetPostListRequest;
import com.leo.myblog.dto.request.UpdatePostRequest;
import com.leo.myblog.dto.response.PostDetailResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.dto.response.PublicPostListItemResponse;
import com.leo.myblog.entity.Category;

import java.util.List;

/**
 * 文章服务接口
 */
public interface PostService {
    
    /**
     * 创建文章
     */
    Long createPost(Long userId, CreatePostRequest request);
    
    /**
     * 更新文章
     */
    void updatePost(Long userId, Boolean isAdmin, Long postId, UpdatePostRequest request);
    
    /**
     * 删除文章（软删除）
     */
    void deletePost(Long userId, Boolean isAdmin, Long postId);
    
    /**
     * 获取文章详情（阅读量+1，私密文章仅作者可见）
     */
    PostDetailResponse getPostDetail(Long currentUserId, Boolean isAdmin, Long postId, jakarta.servlet.http.HttpServletRequest request);
    
    /**
     * 前台公开列表（只查"已发布"的文章）
     */
    PageResult<PostListItemResponse> getPublicPostList(GetPostListRequest request);
    
    /**
     * 后台管理列表（管理员/作者查看，支持查所有状态）
     */
    PageResult<PostListItemResponse> getAdminPostList(Long currentUserId, Boolean isAdmin, GetPostListRequest request);
    
    /**
     * 获取所有文章分类
     */
    List<Category> getAllCategories();
    
    /**
     * 根据分类名称获取公开文章列表(前端专用,分页)
     * @param categoryName 分类名称
     * @param pageNum 页码,从1开始
     * @param pageSize 每页数量,默认20
     * @return 分页结果
     */
    PageResult<PublicPostListItemResponse> getPublicPostsByCategoryName(String categoryName, Integer pageNum, Integer pageSize);
        
    /**
     * 将Post实体转换为PostListItemResponse(供其他服务使用)
     * @param post 文章实体
     * @return 列表项响应对象
     */
    PostListItemResponse convertToPostListItemResponse(com.leo.myblog.entity.Post post);
}
