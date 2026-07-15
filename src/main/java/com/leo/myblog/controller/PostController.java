package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.CreatePostRequest;
import com.leo.myblog.dto.request.GetPostListRequest;
import com.leo.myblog.dto.request.UpdatePostRequest;
import com.leo.myblog.dto.response.FavoriteStatusResponse;
import com.leo.myblog.dto.response.PostDetailResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.dto.response.PublicPostListItemResponse;
import com.leo.myblog.entity.Category;
import com.leo.myblog.entity.User;
import com.leo.myblog.service.PostFavoriteService;
import com.leo.myblog.service.PostService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.util.AbilityValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    
    private final PostService postService;
    private final UserCacheService userCacheService;
    private final PostFavoriteService postFavoriteService;
    private final AbilityValidator abilityValidator;
    
    /**
     * 创建文章
     */
    @PostMapping
    public Result<Long> createPost(@RequestAttribute Long userId,
                                    @Valid @RequestBody CreatePostRequest request) {
        try {
            log.info("创建文章: userId={}, title={}", userId, request.getTitle());
            Long postId = postService.createPost(userId, request);
            return Result.success("文章创建成功", postId);
        } catch (Exception e) {
            // 错误时返回0作为默认ID，保持data类型为Long
            log.error("创建文章失败: userId={}, error={}", userId, e.getMessage());
            return Result.errorWithDefault(500, e.getMessage(), 0L);
        }
    }
    
    /**
     * 更新文章（仅作者或拥有edit_posts能力的管理员）
     */
    @PutMapping("/{postId}")
    public Result<Void> updatePost(@RequestAttribute Long userId,
                                    @PathVariable Long postId,
                                    @Valid @RequestBody UpdatePostRequest request) {
        log.info("更新文章: userId={}, postId={}", userId, postId);
        
        // 获取当前用户信息，判断是否为管理员
        User currentUser = userCacheService.getUserById(userId);
        Boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        // 如果是管理员，需要验证是否有 edit_posts 能力
        if (Boolean.TRUE.equals(isAdmin)) {
            abilityValidator.validateAbility(userId, "edit_posts");
        }
        
        postService.updatePost(userId, isAdmin, postId, request);
        return Result.success("文章更新成功", null);
    }
    
    /**
     * 删除文章（软删除，仅作者或拥有delete_posts能力的管理员 + TOTP双因素认证）
     */
    @RequireTotp(operation = "删除文章", module = "security.totp.post_management")
    @DeleteMapping("/{postId}")
    public Result<Void> deletePost(@RequestAttribute Long userId,
                                    @PathVariable Long postId) {
        log.info("删除文章: userId={}, postId={}", userId, postId);
        
        // 获取当前用户信息，判断是否为管理员
        User currentUser = userCacheService.getUserById(userId);
        Boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        // 如果是管理员，需要验证是否有 delete_posts 能力
        if (Boolean.TRUE.equals(isAdmin)) {
            abilityValidator.validateAbility(userId, "delete_posts");
        }
        
        postService.deletePost(userId, isAdmin, postId);
        return Result.success("文章删除成功", null);
    }
    
    /**
     * 获取文章详情（阅读量+1，私密文章仅作者可见）
     */
    @GetMapping("/{postId}")
    public Result<PostDetailResponse> getPostDetail(@RequestAttribute(required = false) Long userId,
                                                      @PathVariable Long postId,
                                                      jakarta.servlet.http.HttpServletRequest request) {
        log.debug("获取文章详情: userId={}, postId={}", userId, postId);
        
        // 获取当前用户信息，判断是否为管理员
        Boolean isAdmin = false;
        if (userId != null) {
            User currentUser = userCacheService.getUserById(userId);
            isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        }
        
        PostDetailResponse detail = postService.getPostDetail(userId, isAdmin, postId, request);
        return Result.success(detail);
    }
    
    /**
     * 前台公开列表（只查"已发布"的文章）
     */
    @GetMapping("/public")
    public Result<PageResult<PostListItemResponse>> getPublicPostList(@Valid GetPostListRequest request) {
        log.debug("查询公开文章列表: pageNum={}, pageSize={}, categoryId={}, tagId={}, keyword={}",
                request.getPageNum(), request.getPageSize(), request.getCategoryId(), 
                request.getTagId(), request.getKeyword());
        
        PageResult<PostListItemResponse> result = postService.getPublicPostList(request);
        return Result.success(result);
    }
    
    /**
     * 后台管理列表（管理员/作者查看，支持查所有状态）
     */
    @RequireAdmin
    @PostMapping("/admin/list")
    public Result<PageResult<PostListItemResponse>> getAdminPostList(@RequestAttribute Long userId,
                                                                       @Valid @RequestBody GetPostListRequest request) {
        log.debug("管理员查询文章列表: userId={}, pageNum={}, pageSize={}, status={}",
                userId, request.getPageNum(), request.getPageSize(), request.getStatus());
        
        // 获取当前用户信息，判断是否为管理员
        User currentUser = userCacheService.getUserById(userId);
        Boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        PageResult<PostListItemResponse> result = postService.getAdminPostList(userId, isAdmin, request);
        return Result.success(result);
    }
    
    /**
     * 获取所有文章分类（需要登录）
     */
    @GetMapping("/categories")
    public Result<List<Category>> getAllCategories(@RequestAttribute Long userId) {
        log.debug("获取所有文章分类: userId={}", userId);
        List<Category> categories = postService.getAllCategories();
        return Result.success(categories);
    }
    
    /**
     * 获取软件开发分类文章列表（公开，分页）
     */
    @GetMapping("/category/software-development")
    public Result<PageResult<PublicPostListItemResponse>> getSoftwareDevelopmentPosts(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.debug("获取软件开发分类文章列表: pageNum={}, pageSize={}", pageNum, pageSize);
        PageResult<PublicPostListItemResponse> posts = postService.getPublicPostsByCategoryName("软件开发", pageNum, pageSize);
        return Result.success(posts);
    }
    
    /**
     * 获取AI分类文章列表（公开，分页）
     */
    @GetMapping("/category/ai")
    public Result<PageResult<PublicPostListItemResponse>> getAiPosts(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.debug("获取AI分类文章列表: pageNum={}, pageSize={}", pageNum, pageSize);
        PageResult<PublicPostListItemResponse> posts = postService.getPublicPostsByCategoryName("AI", pageNum, pageSize);
        return Result.success(posts);
    }
    
    /**
     * 获取网络安全分类文章列表（公开，分页）
     */
    @GetMapping("/category/cybersecurity")
    public Result<PageResult<PublicPostListItemResponse>> getCybersecurityPosts(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.debug("获取网络安全分类文章列表: pageNum={}, pageSize={}", pageNum, pageSize);
        PageResult<PublicPostListItemResponse> posts = postService.getPublicPostsByCategoryName("网络安全", pageNum, pageSize);
        return Result.success(posts);
    }
    
    /**
     * 检查文章是否被当前用户收藏
     * 需要登录
     */
    @GetMapping("/{postId}/favorite/status")
    public Result<FavoriteStatusResponse> checkFavoriteStatus(@RequestAttribute Long userId,
                                                               @PathVariable Long postId) {
        log.debug("检查收藏状态: userId={}, postId={}", userId, postId);
        FavoriteStatusResponse status = postFavoriteService.checkFavoriteStatus(userId, postId);
        return Result.success(status);
    }
    
    /**
     * 收藏文章
     * 需要登录
     */
    @PostMapping("/{postId}/favorite")
    public Result<Void> favoritePost(@RequestAttribute Long userId,
                                      @PathVariable Long postId) {
        log.info("用户 {} 收藏文章 {}", userId, postId);
        postFavoriteService.favoritePost(userId, postId);
        return Result.success("收藏成功", null);
    }
    
    /**
     * 取消收藏
     * 需要登录
     */
    @DeleteMapping("/{postId}/favorite")
    public Result<Void> unfavoritePost(@RequestAttribute Long userId,
                                        @PathVariable Long postId) {
        log.info("用户 {} 取消收藏文章 {}", userId, postId);
        postFavoriteService.unfavoritePost(userId, postId);
        return Result.success("取消收藏成功", null);
    }
    
    /**
     * 获取用户的收藏列表（分页）
     * 需要登录
     */
    @GetMapping("/favorites")
    public Result<PageResult<PostListItemResponse>> getUserFavorites(@RequestAttribute Long userId,
                                                                       @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                       @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.debug("获取用户收藏列表: userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        PageResult<PostListItemResponse> favorites = postFavoriteService.getUserFavorites(userId, pageNum, pageSize);
        return Result.success(favorites);
    }
}
