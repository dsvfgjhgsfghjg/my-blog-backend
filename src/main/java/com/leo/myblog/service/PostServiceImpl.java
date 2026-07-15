package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.request.CreatePostRequest;
import com.leo.myblog.dto.request.GetPostListRequest;
import com.leo.myblog.dto.request.UpdatePostRequest;
import com.leo.myblog.dto.response.PostDetailResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.dto.response.PublicPostListItemResponse;
import com.leo.myblog.entity.*;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章服务实现类
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {
    
    /**
     * HTML 安全白名单配置
     * 注意：文章内容以 Markdown 格式存储，不进行 Markdown 到 HTML 的转换
     * 只进行基本的 XSS 防护，移除危险的脚本和事件处理器
     */
    private static final Safelist POST_CONTENT_SAFELIST = new Safelist()
        .addTags("p", "br", "strong", "em", "u", "s", "blockquote", "code", "pre", "hr")
        .addTags("h1", "h2", "h3", "h4", "h5", "h6")  // 允许标题标签（如果前端已转换）
        .addTags("ul", "ol", "li", "dl", "dt", "dd")
        .addTags("a", "img", "table", "thead", "tbody", "tr", "th", "td")
        .addAttributes("a", "href", "title", "target")
        .addAttributes("img", "src", "alt", "title", "width", "height")
        .addAttributes("code", "class")  // 允许代码高亮
        .addAttributes("pre", "class")
        .addProtocols("a", "href", "https", "http", "mailto")
        .addProtocols("img", "src", "https", "http", "data")
        .removeProtocols("a", "href", "javascript", "vbscript")
        .removeProtocols("img", "src", "javascript", "vbscript");
    
    private final PostMapper postMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final UserCacheService userCacheService;
    private final PostAuditMapper postAuditMapper;
    private final PostAuditService postAuditService;
    private final PostStorageService postStorageService;
    
    public PostServiceImpl(PostMapper postMapper,
                          CategoryMapper categoryMapper,
                          TagMapper tagMapper,
                          PostTagMapper postTagMapper,
                          UserCacheService userCacheService,
                          PostAuditMapper postAuditMapper,
                          @Lazy PostAuditService postAuditService,
                          PostStorageService postStorageService) {
        this.postMapper = postMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.postTagMapper = postTagMapper;
        this.userCacheService = userCacheService;
        this.postAuditMapper = postAuditMapper;
        this.postAuditService = postAuditService;
        this.postStorageService = postStorageService;
    }
    
    /**
     * 创建文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, CreatePostRequest request) {
        // 1. 验证分类是否存在（如果提供了有效的分类ID）
        if (request.getCategoryId() != null && request.getCategoryId() > 0) {
            Category category = categoryMapper.selectById(request.getCategoryId());
            if (category == null) {
                throw new BusinessException("分类不存在");
            }
        }
        
        // 2. 验证标签是否存在（如果提供了标签ID列表）
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(request.getTagIds());
            if (tags.size() != request.getTagIds().size()) {
                throw new BusinessException("存在无效的标签ID");
            }
        }
        
        // 3. 生成slug（如果未提供）
        String slug = request.getSlug();
        if (slug == null || slug.isEmpty()) {
            slug = generateSlug(request.getTitle());
            // 确保slug唯一
            slug = ensureUniqueSlug(slug, null);
        } else {
            // 验证slug是否已存在
            slug = ensureUniqueSlug(slug, null);
        }
        
        // 4. 处理文章内容（XSS 防护）
        // 注意：文章内容以 Markdown 格式存储，不进行 Jsoup HTML 过滤
        // 核心原则：Jsoup 是 HTML 解析器，对原始 Markdown 文本做过滤会破坏代码块语法
        // XSS 防护由前端 DOMPurify 在 Markdown→HTML 渲染完成后执行
        // 此处仅移除 Markdown 链接中的危险协议（如 javascript:），作为补充防护层
        String safeContent = cleanMarkdownUrls(request.getContent());
        
        // 5. 提取摘要和统计字数（使用处理后的内容）
        String summary = extractSummary(safeContent, 150);
        Integer wordCount = countWords(safeContent);
        
        // 6. 创建文章实体（先插入数据库获取ID）
        Post post = new Post();
        BeanUtils.copyProperties(request, post);
        post.setUserId(userId);
        post.setSlug(slug);
        post.setContentPath(null);  // 暂时为空，上传后再更新
        post.setSummary(summary);
        post.setWordCount(wordCount);
        post.setViewCount(0L);
        post.setIsDeleted(false);
        
        // 所有新创建的文章都设置为待审核状态（status=3）
        post.setStatus(3);
        
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        
        postMapper.insert(post);
        
        // 7. 上传文章内容到 MinIO
        try {
            String contentPath = postStorageService.uploadContent(post.getId(), safeContent);
            post.setContentPath(contentPath);
            postMapper.updateById(post);
            log.info("文章内容上传成功: postId={}, path={}", post.getId(), contentPath);
        } catch (Exception e) {
            log.error("上传文章内容失败，回滚文章创建: postId={}", post.getId(), e);
            // 删除已创建的文章记录
            postMapper.deleteById(post.getId());
            throw new BusinessException("文章内容存储失败: " + e.getMessage());
        }
        
        // 7.1 创建审计记录（所有文章都需要审计）
        postAuditService.createAuditRecord(post.getId());
        
        // 7.2 绑定标签
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            bindTagsToPost(post.getId(), request.getTagIds());
        }
        
        log.info("文章创建成功: postId={}, title={}, userId={}", post.getId(), request.getTitle(), userId);
        
        return post.getId();
    }
    
    /**
     * 更新文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long userId, Boolean isAdmin, Long postId, UpdatePostRequest request) {
        // 1. 查询文章
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 权限校验：仅作者或管理员可修改
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException("权限不足，仅作者或管理员可修改");
        }
        
        // 3. 验证分类是否存在
        if (request.getCategoryId() != null && request.getCategoryId() > 0) {
            Category category = categoryMapper.selectById(request.getCategoryId());
            if (category == null) {
                throw new BusinessException("分类不存在");
            }
        }
        
        // 4. 验证标签是否存在
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(request.getTagIds());
            if (tags.size() != request.getTagIds().size()) {
                throw new BusinessException("存在无效的标签ID");
            }
        }
        
        // 6. 处理slug
        String slug = request.getSlug();
        if (slug != null && !slug.equals(post.getSlug())) {
            slug = ensureUniqueSlug(slug, postId);
        } else {
            slug = post.getSlug();
        }
        
        // 4. 处理文章内容（XSS 防护）
        // 注意：文章内容以 Markdown 格式存储，不进行 Jsoup HTML 过滤
        // 核心原则：Jsoup 是 HTML 解析器，对原始 Markdown 文本做过滤会破坏代码块语法
        // XSS 防护由前端 DOMPurify 在 Markdown→HTML 渲染完成后执行
        // 此处仅移除 Markdown 链接中的危险协议（如 javascript:），作为补充防护层
        String safeContent = cleanMarkdownUrls(request.getContent());
        
        // 8. 提取摘要和统计字数（使用处理后的内容）
        String summary = extractSummary(safeContent, 150);
        Integer wordCount = countWords(safeContent);
        
        // 9. 更新文章内容到 MinIO
        try {
            String newContentPath = postStorageService.updateContent(
                post.getContentPath(), 
                postId, 
                safeContent
            );
            post.setContentPath(newContentPath);
            log.info("文章内容更新成功: postId={}, path={}", postId, newContentPath);
        } catch (Exception e) {
            log.error("更新文章内容失败: postId={}", postId, e);
            throw new BusinessException("文章内容更新失败: " + e.getMessage());
        }
        
        // 10. 更新文章其他字段（注意：不允许通过此接口修改文章状态，状态修改需通过专门的审核流程）
        post.setTitle(request.getTitle());
        post.setSlug(slug);
        post.setSummary(summary);
        post.setWordCount(wordCount);
        post.setCategoryId(request.getCategoryId());
        post.setCoverImage(request.getCoverImage());
        post.setIsTop(request.getIsTop());
        post.setUpdateTime(LocalDateTime.now());
        
        // 特别注意：不能通过updatePost接口修改文章状态，必须通过审核流程
        // 如果前端传递了status字段，进行严格验证
        if (request.getStatus() != null) {
            // 待审核文章(status=3)：任何人都不能通过此接口修改状态
            if (post.getStatus() == 3) {
                throw new BusinessException("待审核文章不能通过此接口修改状态，必须通过审核流程");
            }
            
            // 非管理员：不能修改任何文章的状态
            if (!isAdmin) {
                log.debug("非管理员尝试修改文章状态，忽略状态变更: postId={}, userId={}", postId, userId);
                // 不修改status字段，保持原状态
            } else {
                // 管理员：可以修改状态，但不能直接发布待审核文章
                if (post.getStatus() == 3 && request.getStatus() == 1) {
                    throw new BusinessException("待审核文章不能直接设置为已发布，必须通过审核流程");
                }
                
                // 管理员可以修改其他状态
                post.setStatus(request.getStatus());
                
                // 如果从非发布状态变为发布状态，设置发布时间
                if (request.getStatus() == 1 && 
                    (post.getPublishTime() == null || post.getStatus() != 1)) {
                    post.setPublishTime(LocalDateTime.now());
                }
            }
        }
        
        postMapper.updateById(post);
        
        // 10. 更新标签绑定（先删除旧的，再添加新的）
        if (request.getTagIds() != null) {
            unbindTagsFromPost(postId);
            if (!request.getTagIds().isEmpty()) {
                bindTagsToPost(postId, request.getTagIds());
            }
        }
        
        log.info("文章更新成功: postId={}, title={}, userId={}", postId, request.getTitle(), userId);
    }
    
    /**
     * 删除文章（软删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Boolean isAdmin, Long postId) {
        // 1. 查询文章
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 权限校验：仅作者或管理员可删除
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException("权限不足，仅作者或管理员可删除");
        }
        
        // 3. 删除 MinIO 中的文章内容
        if (post.getContentPath() != null && !post.getContentPath().isEmpty()) {
            try {
                postStorageService.deleteContent(post.getContentPath());
                log.info("文章内容已删除: postId={}, path={}", postId, post.getContentPath());
            } catch (Exception e) {
                log.error("删除文章内容失败，继续执行软删除: postId={}", postId, e);
                // 不抛出异常，继续执行软删除
            }
        }
        
        // 4. 软删除（使用 MyBatis-Plus 的逻辑删除）
        postMapper.deleteById(postId);
        
        log.info("文章删除成功: postId={}, userId={}", postId, userId);
    }
    
    /**
     * 获取文章详情（阅读量+1，私密文章仅作者可见）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostDetailResponse getPostDetail(Long currentUserId, Boolean isAdmin, Long postId, jakarta.servlet.http.HttpServletRequest request) {
        // 1. 查询文章
        Post post = postMapper.selectById(postId);
        if (post == null) {
            // 文章不存在也返回403，避免被扫描器探测
            throw new BusinessException(403, "文章不存在或无权访问");
        }
        
        // 2. 权限校验：非公开文章（草稿、私密、待审核）仅作者和管理员可见
        if (post.getStatus() != null && post.getStatus() != 1) {
            // 不是已发布状态，检查是否为作者或管理员
            if (currentUserId == null) {
                // 未登录用户不能查看非公开文章
                throw new BusinessException(403, "文章不存在或无权访问");
            }
            if (!isAdmin && !post.getUserId().equals(currentUserId)) {
                // 非管理员且非作者，不能查看
                // 如果是待审核状态，给出更明确的提示
                if (post.getStatus() == 3) {
                    throw new BusinessException(403, "文章正在审核中，暂不可见");
                }
                throw new BusinessException(403, "文章不存在或无权访问");
            }
        }
        
        // 3. 阅读量+1（仅公开文章且非作者本人访问时）
        if (post.getStatus() != null && post.getStatus() == 1 && 
            (currentUserId == null || !post.getUserId().equals(currentUserId))) {
            post.setViewCount(post.getViewCount() + 1);
            postMapper.updateById(post);
        }
        
        // 4. 从 MinIO 加载文章内容
        String content = "";
        if (post.getContentPath() != null && !post.getContentPath().isEmpty()) {
            try {
                content = postStorageService.downloadContent(post.getContentPath());
            } catch (Exception e) {
                log.error("加载文章内容失败: postId={}, path={}", postId, post.getContentPath(), e);
                content = "文章内容加载失败";
            }
        }
        
        // 5. 转换为响应对象
        return convertToDetailResponse(post, content);
    }
    
    /**
     * 前台公开列表（只查“已发布”且“已通过审计”的文章）
     */
    @Override
    public PageResult<PostListItemResponse> getPublicPostList(GetPostListRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            
        // 1. 查询已通过审计的文章ID
        LambdaQueryWrapper<PostAudit> auditWrapper = new LambdaQueryWrapper<>();
        auditWrapper.eq(PostAudit::getAuditStatus, 1); // 已通过
        List<Long> approvedPostIds = postAuditMapper.selectList(auditWrapper).stream()
                .map(PostAudit::getPostId)
                .collect(Collectors.toList());
            
        if (approvedPostIds.isEmpty()) {
            // 没有已通过审计的文章，返回空结果
            return PageResult.of(new Page<>(pageNum, pageSize));
        }
            
        Page<Post> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
            
        // 2. 只查询已发布且已通过审计的文章
        wrapper.eq(Post::getStatus, 1);
        wrapper.in(Post::getId, approvedPostIds);
        
        // 按分类筛选
        if (request.getCategoryId() != null) {
            wrapper.eq(Post::getCategoryId, request.getCategoryId());
        }
        
        // 按标签筛选（需要关联查询）
        if (request.getTagId() != null) {
            List<Long> postIds = getPostIdsByTagId(request.getTagId());
            if (postIds.isEmpty()) {
                // 没有该标签的文章，返回空结果
                return PageResult.of(new Page<>(pageNum, pageSize));
            }
            wrapper.in(Post::getId, postIds);
        }
        
        // 关键字搜索（标题、摘要）
        // 注意：文章内容存储在 MinIO 中，不支持数据库级别的全文搜索
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            String keyword = request.getKeyword();
            // 只搜索标题和摘要
            wrapper.and(w -> w.like(Post::getTitle, keyword)
                             .or()
                             .like(Post::getSummary, keyword));
        }
        
        // 按发布时间范围筛选
        if (request.getStartTime() != null) {
            wrapper.ge(Post::getPublishTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(Post::getPublishTime, request.getEndTime());
        }
        
        // 按阅读量范围筛选
        if (request.getMinViewCount() != null) {
            wrapper.ge(Post::getViewCount, request.getMinViewCount());
        }
        if (request.getMaxViewCount() != null) {
            wrapper.le(Post::getViewCount, request.getMaxViewCount());
        }
        
        // 动态排序
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "publish_time";
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        
        // 置顶文章始终排在前面（仅当按发布时间或创建时间排序时）
        if (!"view_count".equalsIgnoreCase(sortBy)) {
            // 先添加置顶排序
            wrapper.orderBy(true, false, Post::getIsTop);
        }
        
        // 再添加主要排序字段
        switch (sortBy.toLowerCase()) {
            case "view_count":
                wrapper.orderBy(true, isAsc, Post::getViewCount);
                break;
            case "create_time":
                wrapper.orderBy(true, isAsc, Post::getCreateTime);
                break;
            case "publish_time":
            default:
                wrapper.orderBy(true, isAsc, Post::getPublishTime);
                break;
        }
        
        Page<Post> postPage = postMapper.selectPage(page, wrapper);
        
        // 转换为响应对象
        Page<PostListItemResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(postPage, responsePage, "records");
        responsePage.setRecords(postPage.getRecords().stream()
                .map(this::convertToPostListItemResponse)
                .collect(Collectors.toList()));
        
        return PageResult.of(responsePage);
    }
    
    /**
     * 后台管理列表（管理员/作者查看，支持查所有状态）
     */
    @Override
    public PageResult<PostListItemResponse> getAdminPostList(Long currentUserId, Boolean isAdmin, GetPostListRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        
        Page<Post> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        
        // 非管理员只能查看自己的文章
        if (!isAdmin) {
            wrapper.eq(Post::getUserId, currentUserId);
        }
        
        // 按状态筛选
        if (request.getStatus() != null) {
            wrapper.eq(Post::getStatus, request.getStatus());
        }
        
        // 按分类筛选
        if (request.getCategoryId() != null) {
            wrapper.eq(Post::getCategoryId, request.getCategoryId());
        }
        
        // 按标签筛选
        if (request.getTagId() != null) {
            List<Long> postIds = getPostIdsByTagId(request.getTagId());
            if (postIds.isEmpty()) {
                return PageResult.of(new Page<>(pageNum, pageSize));
            }
            wrapper.in(Post::getId, postIds);
        }
        
        // 关键字搜索（标题、摘要）
        // 注意：文章内容存储在 MinIO 中，不支持数据库级别的全文搜索
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            String keyword = request.getKeyword();
            // 只搜索标题和摘要
            wrapper.and(w -> w.like(Post::getTitle, keyword)
                             .or()
                             .like(Post::getSummary, keyword));
        }
        
        // 按发布时间范围筛选
        if (request.getStartTime() != null) {
            wrapper.ge(Post::getPublishTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(Post::getPublishTime, request.getEndTime());
        }
        
        // 按阅读量范围筛选
        if (request.getMinViewCount() != null) {
            wrapper.ge(Post::getViewCount, request.getMinViewCount());
        }
        if (request.getMaxViewCount() != null) {
            wrapper.le(Post::getViewCount, request.getMaxViewCount());
        }
        
        // 动态排序
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "create_time";
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        
        switch (sortBy.toLowerCase()) {
            case "view_count":
                wrapper.orderBy(true, isAsc, Post::getViewCount);
                break;
            case "publish_time":
                wrapper.orderBy(true, isAsc, Post::getPublishTime);
                break;
            case "create_time":
            default:
                wrapper.orderBy(true, isAsc, Post::getCreateTime);
                break;
        }
        
        Page<Post> postPage = postMapper.selectPage(page, wrapper);
        
        // 转换为响应对象
        Page<PostListItemResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(postPage, responsePage, "records");
        responsePage.setRecords(postPage.getRecords().stream()
                .map(this::convertToPostListItemResponse)
                .collect(Collectors.toList()));
        
        return PageResult.of(responsePage);
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 生成slug（从标题转换）
     */
    private String generateSlug(String title) {
        if (title == null || title.isEmpty()) {
            return "post-" + System.currentTimeMillis();
        }
        
        // 简单实现：移除特殊字符，空格替换为连字符，转小写
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        
        if (slug.isEmpty()) {
            slug = "post-" + System.currentTimeMillis();
        }
        
        return slug;
    }
    
    /**
     * 确保slug唯一
     */
    private String ensureUniqueSlug(String slug, Long excludePostId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getSlug, slug);
        if (excludePostId != null) {
            wrapper.ne(Post::getId, excludePostId);
        }
        
        long count = postMapper.selectCount(wrapper);
        if (count == 0) {
            return slug;
        }
        
        // 如果已存在，添加时间戳后缀
        return slug + "-" + System.currentTimeMillis();
    }
    
    /**
     * 提取摘要（去除Markdown符号，取前n个字符）
     */
    private String extractSummary(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // 简单去除Markdown符号（实际项目中可以使用专门的Markdown解析库）
        String plainText = content.replaceAll("#+", "")          // 去除标题符号
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")           // 去除粗体
                .replaceAll("\\*(.+?)\\*", "$1")                 // 去除斜体
                .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1")       // 去除链接，保留文本
                .replaceAll("!\\[.+?\\]\\(.+?\\)", "")            // 去除图片
                .replaceAll("`{1,3}(.+?)`{1,3}", "$1")          // 去除代码块
                .replaceAll("^>+\\s?", "")                       // 去除引用
                .replaceAll("^[-*+]\\s+", "")                    // 去除列表符号
                .replaceAll("^\\d+\\.\\s+", "")                  // 去除有序列表
                .replaceAll("\\n+", " ")                         // 换行符替换为空格
                .trim();
        
        // 取前maxLength个字符
        if (plainText.length() > maxLength) {
            return plainText.substring(0, maxLength) + "...";
        }
        
        return plainText;
    }
    
    /**
     * 统计字数（中文字符和英文单词）
     */
    private Integer countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        
        // 简单实现：统计非空白字符数
        // 更精确的实现可以使用专门的文本分析库
        String text = content.replaceAll("\\s+", "");
        return text.length();
    }
    
    /**
     * 绑定标签到文章
     */
    private void bindTagsToPost(Long postId, List<Long> tagIds) {
        List<PostTag> postTags = tagIds.stream()
                .map(tagId -> new PostTag(postId, tagId))
                .collect(Collectors.toList());
        
        postTags.forEach(postTagMapper::insert);
    }
    
    /**
     * 解绑文章的所有标签
     */
    private void unbindTagsFromPost(Long postId) {
        LambdaQueryWrapper<PostTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostTag::getPostId, postId);
        postTagMapper.delete(wrapper);
    }
    
    /**
     * 根据标签ID获取文章ID列表
     */
    private List<Long> getPostIdsByTagId(Long tagId) {
        LambdaQueryWrapper<PostTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostTag::getTagId, tagId);
        List<PostTag> postTags = postTagMapper.selectList(wrapper);
        
        return postTags.stream()
                .map(PostTag::getPostId)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为详情响应对象
     */
    private PostDetailResponse convertToDetailResponse(Post post, String content) {
        PostDetailResponse response = new PostDetailResponse();
        response.setId(String.valueOf(post.getId()));
        response.setUserId(String.valueOf(post.getUserId()));
        response.setTitle(post.getTitle());
        response.setSlug(post.getSlug());
        response.setSummary(post.getSummary());
        
        response.setContent(content != null ? content : "");
        
        response.setWordCount(post.getWordCount());
        response.setCoverImage(post.getCoverImage());
        response.setStatus(post.getStatus());
        response.setIsTop(post.getIsTop());
        response.setViewCount(String.valueOf(post.getViewCount()));
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());
        response.setPublishTime(post.getPublishTime());
        
        // 获取分类ID和名称
        if (post.getCategoryId() != null) {
            response.setCategoryId(String.valueOf(post.getCategoryId()));
            Category category = categoryMapper.selectById(post.getCategoryId());
            if (category != null) {
                response.setCategoryName(category.getName());
            }
        }
        
        // 获取作者信息
        User author = userCacheService.getUserById(post.getUserId());
        if (author != null) {
            response.setAuthorUsername(author.getUsername());
            response.setAuthorNickname(author.getNickname());
        }
        
        // 获取标签列表
        List<PostDetailResponse.TagInfo> tags = getTagsByPostId(post.getId());
        response.setTags(tags);
        
        return response;
    }
    
    /**
     * 获取状态文本描述
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "草稿";
            case 1: return "已发布";
            case 2: return "私密";
            case 3: return "待审核";
            default: return "未知(" + status + ")";
        }
    }
    
    /**
     * 转换为列表项响应对象
     */
    @Override
    public PostListItemResponse convertToPostListItemResponse(Post post) {
        PostListItemResponse response = new PostListItemResponse();
        
        // 手动设置String类型的ID字段
        response.setId(String.valueOf(post.getId()));
        response.setUserId(String.valueOf(post.getUserId()));
        response.setCategoryId(post.getCategoryId() != null ? String.valueOf(post.getCategoryId()) : null);
        response.setViewCount(String.valueOf(post.getViewCount()));
        
        // 其他字段使用BeanUtils复制
        response.setTitle(post.getTitle());
        response.setSlug(post.getSlug());
        response.setSummary(post.getSummary());
        response.setWordCount(post.getWordCount());
        response.setCoverImage(post.getCoverImage());
        response.setStatus(post.getStatus());
        response.setIsTop(post.getIsTop());
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());
        response.setPublishTime(post.getPublishTime());
        
        // 获取作者信息
        User author = userCacheService.getUserById(post.getUserId());
        if (author != null) {
            response.setAuthorUsername(author.getUsername());
            response.setAuthorNickname(author.getNickname());
        }
        
        // 获取分类名称
        if (post.getCategoryId() != null) {
            Category category = categoryMapper.selectById(post.getCategoryId());
            if (category != null) {
                response.setCategoryName(category.getName());
            }
        }
        
        // 获取标签列表
        List<PostDetailResponse.TagInfo> tags = getTagsByPostId(post.getId());
        response.setTags(tags);
        
        return response;
    }
    
    /**
     * 根据文章ID获取标签列表
     */
    private List<PostDetailResponse.TagInfo> getTagsByPostId(Long postId) {
        LambdaQueryWrapper<PostTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostTag::getPostId, postId);
        List<PostTag> postTags = postTagMapper.selectList(wrapper);
        
        if (postTags.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Long> tagIds = postTags.stream()
                .map(PostTag::getTagId)
                .collect(Collectors.toList());
        
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        
        return tags.stream()
                .map(tag -> {
                    PostDetailResponse.TagInfo tagInfo = new PostDetailResponse.TagInfo();
                    tagInfo.setId(String.valueOf(tag.getId()));
                    tagInfo.setName(tag.getName());
                    tagInfo.setColor(tag.getColor());
                    return tagInfo;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有文章分类
     */
    @Override
    public List<Category> getAllCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }
    
    /**
     * 根据分类名称获取公开文章列表（前端专用，分页）
     */
    @Override
    public PageResult<PublicPostListItemResponse> getPublicPostsByCategoryName(String categoryName, Integer pageNum, Integer pageSize) {
        // 1. 设置默认值
        int currentPage = pageNum != null && pageNum > 0 ? pageNum : 1;
        int size = pageSize != null && pageSize > 0 ? pageSize : 20;
        
        // 2. 根据分类名称查找分类
        LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
        categoryWrapper.eq(Category::getName, categoryName);
        Category category = categoryMapper.selectOne(categoryWrapper);
        
        if (category == null) {
            log.warn("分类不存在: {}", categoryName);
            return PageResult.of(new Page<>(currentPage, size));
        }
        
        // 3. 分页查询该分类下已发布的文章，按更新时间降序
        Page<Post> page = new Page<>(currentPage, size);
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.eq(Post::getCategoryId, category.getId())
                   .eq(Post::getStatus, 1)  // 只查已发布
                   .orderByDesc(Post::getUpdateTime);
        
        Page<Post> postPage = postMapper.selectPage(page, postWrapper);
        
        // 4. 转换为响应对象
        Page<PublicPostListItemResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(postPage, responsePage, "records");
        responsePage.setRecords(postPage.getRecords().stream()
                .map(this::convertToPublicListItemResponse)
                .collect(Collectors.toList()));
        
        return PageResult.of(responsePage);
    }
    
    /**
     * 转换为前端公开列表项响应对象
     */
    private PublicPostListItemResponse convertToPublicListItemResponse(Post post) {
        PublicPostListItemResponse response = new PublicPostListItemResponse();
        
        // 设置String类型的ID
        response.setId(String.valueOf(post.getId()));
        
        response.setTitle(post.getTitle());
        response.setCoverImage(post.getCoverImage());
        
        // 截取前50字作为摘要
        String summary = post.getSummary();
        if (summary != null && summary.length() > 50) {
            summary = summary.substring(0, 50);
        }
        response.setSummary(summary);
        
        response.setWordCount(post.getWordCount());
        response.setUpdateTime(post.getUpdateTime());
        
        // 计算预计阅读时间（假设每分钟阅读300字）
        Integer wordCount = post.getWordCount() != null ? post.getWordCount() : 0;
        int readTime = (int) Math.ceil(wordCount / 300.0);
        // 最少1分钟
        readTime = Math.max(readTime, 1);
        response.setReadTime(readTime);
        
        return response;
    }
    
    /**
     * 清洗 HTML 内容（适用于已经由 Markdown 渲染成的 HTML）
     * 
     * Jsoup.clean 配合 prettyPrint(false) 保留换行和空格结构，
     * 适用于前端传递的已经渲染好的 HTML 内容做二次安全加固。
     * 
     * @param htmlContent 已经渲染好的 HTML 内容
     * @return 清洗后的安全 HTML
     */
    private String cleanRenderedHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }
        
        // 关键修复：使用 Document.OutputSettings.prettyPrint(false) 防止 Jsoup 压缩 whitespace
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .prettyPrint(false)
                .charset(StandardCharsets.UTF_8);
        
        String safeContent = Jsoup.clean(htmlContent, "", POST_CONTENT_SAFELIST, outputSettings);
        
        log.debug("渲染HTML清洗完成，原始长度：{}, 清洗后长度：{}", 
                htmlContent.length(), safeContent.length());
        
        return safeContent;
    }
    
    /**
     * 清洗 Markdown 内容，防止 XSS 攻击
     * 
     * 核心策略：使用 Jsoup 白名单方式清洗嵌入在 Markdown 文本中的 HTML 标签。
     * 使用 OutputSettings.prettyPrint(false) 保留 Markdown 结构所需的换行符和空格。
     * 
     * 工作流程：
     * 1. Jsoup 解析 Markdown 文本（将 Markdown 视为带有嵌入式 HTML 的纯文本）
     * 2. 白名单过滤：只保留 POST_CONTENT_SAFELIST 允许的安全标签和属性
     * 3. 自定义标签清理：移除危险的 Markdown 链接协议（javascript: 等）
     * 4. 保留所有换行符和 Markdown 语法符号
     * 
     * @param markdownContent 原始 Markdown 内容
     * @return 清洗后的安全内容（保留所有换行符和格式）
     */
    private String cleanMarkdownContent(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return markdownContent;
        }
        
        // Step 1: 使用 Jsoup 清洗嵌入式 HTML，关键：prettyPrint(false) 防止换行符丢失
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .prettyPrint(false)
                .charset(StandardCharsets.UTF_8);
        
        String cleaned = Jsoup.clean(markdownContent, "", POST_CONTENT_SAFELIST, outputSettings);
        
        // Step 2: 额外清理 Markdown 链接中的危险协议
        // 如: [click](javascript:alert(1)) 或 [click](data:text/html,...)
        cleaned = cleanMarkdownUrls(cleaned);
        
        log.debug("Markdown内容清洗完成，原始长度：{}，清洗后长度：{}", 
                markdownContent.length(), cleaned.length());
        
        return cleaned;
    }
    
    /**
     * 清理 Markdown 链接中的危险协议（补充 Jsoup 无法覆盖的 Markdown 语法）
     * Markdown 链接语法: [text](url) 或 [text](url "title")
     * Jsoup 只清理嵌入式 HTML 中的 href，但 Markdown 链接语法在渲染时才生成 HTML
     */
    private String cleanMarkdownUrls(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return markdownContent;
        }
        
        // 匹配 Markdown 链接: [text](url) 或 [text](url "title")
        // 正则捕获: [任意内容](url)
        StringBuffer result = new StringBuffer();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\[([^\\]]*)\\]\\(([^)]+)\\)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(markdownContent);
        
        while (matcher.find()) {
            String linkText = matcher.group(1);
            String linkUrl = matcher.group(2).trim();
            
            // 提取 URL（去除 title 部分）
            String url = linkUrl;
            int spaceIdx = url.indexOf(' ');
            if (spaceIdx > 0) {
                url = url.substring(0, spaceIdx);
            }
            
            // 检查危险协议
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.startsWith("javascript:") || 
                lowerUrl.startsWith("vbscript:") ||
                lowerUrl.startsWith("data:") ||
                lowerUrl.startsWith("file:")) {
                // 替换为纯文本（移除链接）
                log.warn("检测到 Markdown 链接中的危险协议: protocol={}, url={}", 
                        lowerUrl.split(":")[0], url);
                matcher.appendReplacement(result, 
                        java.util.regex.Matcher.quoteReplacement(linkText));
                continue;
            }
            
            matcher.appendReplacement(result, matcher.group(0));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 检查 Markdown 内容安全性（保留兼容接口，内部委托给 cleanMarkdownContent）
     * 
     * @deprecated 请使用 {@link #cleanMarkdownContent(String)}，该名称更容易理解
     */
    @Deprecated
    private String checkMarkdownSafety(String markdownContent) {
        return cleanMarkdownContent(markdownContent);
    }
    
    /**
     * 清洗 HTML 内容（已废弃，仅保留引用兼容）
     * 
     * @deprecated 请使用 {@link #cleanRenderedHtmlContent(String)}
     */
    @Deprecated
    private String sanitizeHtmlContent(String content) {
        return cleanRenderedHtmlContent(content);
    }
}
