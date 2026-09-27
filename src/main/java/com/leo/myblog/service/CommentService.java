package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.request.CreateCommentRequest;
import com.leo.myblog.dto.request.GetCommentListRequest;
import com.leo.myblog.dto.response.CommentResponse;
import com.leo.myblog.entity.Comment;
import com.leo.myblog.entity.CommentLike;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.CommentLikeMapper;
import com.leo.myblog.mapper.CommentMapper;
import com.leo.myblog.mapper.PostMapper;
import com.leo.myblog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {
    
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final UserCacheService userCacheService;

    /**
     * 创建评论
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentResponse createComment(Long userId, CreateCommentRequest request) {
        // 1. 验证文章是否存在
        Post post = postMapper.selectById(request.getPostId());
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 处理parentId：0或null都表示顶级评论
        Long parentId = request.getParentId();
        if (parentId != null && parentId == 0) {
            parentId = null;
        }
        
        // 3. 如果是回复评论，验证父评论是否存在
        if (parentId != null) {
            Comment parentComment = commentMapper.selectById(parentId);
            if (parentComment == null) {
                throw new BusinessException("父评论不存在");
            }
            if (parentComment.getStatus() != 1) {
                throw new BusinessException("该评论已被隐藏或删除");
            }
        }
        
        // 4. 如果@了用户，验证被回复用户是否存在
        if (request.getReplyToUserId() != null) {
            User replyToUser = userMapper.selectById(request.getReplyToUserId());
            if (replyToUser == null) {
                throw new BusinessException("被回复的用户不存在");
            }
        }
        
        // 5. 创建评论
        Comment comment = new Comment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(parentId);  // 使用处理后的parentId
        comment.setReplyToUserId(request.getReplyToUserId());
        comment.setContent(request.getContent().trim());
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        
        commentMapper.insert(comment);
        
        log.info("用户 {} 发表评论: commentId={}, postId={}", userId, comment.getId(), request.getPostId());
        
        // 6. 返回评论信息
        return convertToResponse(comment);
    }
    
    /**
     * 获取文章的评论列表（分页）
     */
    public Page<CommentResponse> getCommentList(Long currentUserId, GetCommentListRequest request) {
        // 1. 验证文章是否存在
        Post post = postMapper.selectById(request.getPostId());
        if (post == null) {
            throw new BusinessException("文章不存在");
        }
        
        // 2. 参数校验
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        
        // 限制最大页面大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        
        // 3. 查询顶级评论（parentId为null）
        Page<Comment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getPostId, request.getPostId())
               .isNull(Comment::getParentId)
               .eq(Comment::getStatus, 1)
               .orderByDesc(Comment::getCreateTime);
        
        Page<Comment> commentPage = commentMapper.selectPage(page, wrapper);

        // 4. 批量取数：这一页所有评论（顶级 + 子评论）要用到的用户、点赞一次性查出来，
        //    再在内存里组装嵌套结构与点赞状态。
        //    原实现是「每条评论各自查用户/查点赞/查子评论」，一页 20 条 + 均 3 条回复时约 240 次查询；
        //    现在固定为 1 次分页 + 1 次子评论 + 1 次批量用户（缓存未命中时）+ 1 次批量点赞。
        List<Comment> topComments = commentPage.getRecords();
        List<Comment> replies = loadReplies(topComments);
        CommentViewContext context = buildViewContext(topComments, replies, currentUserId);
        Map<Long, List<Comment>> repliesByParentId = groupRepliesByParentId(replies);

        List<CommentResponse> responses = topComments.stream()
                .map(comment -> toResponseWithReplies(comment,
                        repliesByParentId.getOrDefault(comment.getId(), Collections.emptyList()),
                        context))
                .collect(Collectors.toList());

        // 5. 构建分页结果
        Page<CommentResponse> responsePage = new Page<>();
        responsePage.setCurrent(commentPage.getCurrent());
        responsePage.setSize(commentPage.getSize());
        responsePage.setTotal(commentPage.getTotal());
        responsePage.setRecords(responses);
        
        log.debug("查询文章评论: postId={}, total={}", request.getPostId(), responsePage.getTotal());
        
        return responsePage;
    }
    
    /**
     * 删除评论（仅评论作者或管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId, Boolean isAdmin) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        
        // 权限检查：只有评论作者或管理员可以删除
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            // TODO: 此处应按权限异常返回 403；当前 BusinessException(String) 走默认构造，code 是 500。
            //       本次重构保持语义不变，等异常契约统一时一并改。
            throw new BusinessException("无权删除此评论");
        }
        
        // 软删除：将状态设置为-1
        comment.setStatus(-1);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);
        
        log.info("评论已删除: commentId={}, userId={}, isAdmin={}", commentId, userId, isAdmin);
    }
    
    /**
     * 隐藏/显示评论（仅管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleCommentStatus(Long commentId, Integer status) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        
        comment.setStatus(status);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);
        
        log.info("评论状态已更新: commentId={}, status={}", commentId, status);
    }
    
    /**
     * 获取用户的评论列表
     */
    public Page<CommentResponse> getUserComments(Long userId, Integer pageNum, Integer pageSize) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getUserId, userId)
               .eq(Comment::getStatus, 1)
               .orderByDesc(Comment::getCreateTime);
        
        Page<Comment> commentPage = commentMapper.selectPage(page, wrapper);
        
        List<CommentResponse> responses = convertToResponses(commentPage.getRecords());

        Page<CommentResponse> responsePage = new Page<>();
        responsePage.setCurrent(commentPage.getCurrent());
        responsePage.setSize(commentPage.getSize());
        responsePage.setTotal(commentPage.getTotal());
        responsePage.setRecords(responses);

        return responsePage;
    }
    
    /**
     * 转换为响应对象（不包含子评论）
     */
    private CommentResponse convertToResponse(Comment comment) {
        return convertToResponses(Collections.singletonList(comment)).get(0);
    }

    /**
     * 批量转换为响应对象（不包含子评论、不判断点赞状态）
     * <p>
     * 用户信息按 userId 去重后一次性取（走 {@link UserCacheService}），调用次数与入参条数无关。
     */
    private List<CommentResponse> convertToResponses(List<Comment> comments) {
        if (comments.isEmpty()) {
            return new ArrayList<>();
        }
        CommentViewContext context = buildViewContext(comments, Collections.emptyList(), null);
        return comments.stream()
                .map(comment -> toResponse(comment, context))
                .collect(Collectors.toList());
    }

    /**
     * 一次性查出这一页顶级评论下的所有子评论（回复）
     * <p>
     * 原实现是每个顶级评论查一次；这里用 {@code parentId IN (...)} 一次查完，
     * 再按 parentId 分组还原嵌套结构。排序（createTime 升序）与逐条查询时一致。
     */
    private List<Comment> loadReplies(List<Comment> topComments) {
        if (topComments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> parentIds = topComments.stream()
                .map(Comment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<>();
        replyWrapper.in(Comment::getParentId, parentIds)
                    .eq(Comment::getStatus, 1)
                    .orderByAsc(Comment::getCreateTime);

        return commentMapper.selectList(replyWrapper);
    }

    /**
     * 按 parentId 把子评论分组，组内保持查询返回的顺序（createTime 升序）
     */
    private Map<Long, List<Comment>> groupRepliesByParentId(List<Comment> replies) {
        Map<Long, List<Comment>> grouped = new LinkedHashMap<>();
        for (Comment reply : replies) {
            grouped.computeIfAbsent(reply.getParentId(), key -> new ArrayList<>()).add(reply);
        }
        return grouped;
    }

    /**
     * 构建这一页评论共用的组装上下文：批量取用户 + 批量取当前用户的点赞集合
     */
    private CommentViewContext buildViewContext(List<Comment> topComments, List<Comment> replies, Long currentUserId) {
        List<Comment> all = new ArrayList<>(topComments.size() + replies.size());
        all.addAll(topComments);
        all.addAll(replies);

        // 作者与被回复者按 userId 去重，避免同一作者在一页里重复命中缓存
        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> commentIds = new LinkedHashSet<>();
        for (Comment comment : all) {
            if (comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }
            if (comment.getReplyToUserId() != null) {
                userIds.add(comment.getReplyToUserId());
            }
            if (comment.getId() != null) {
                commentIds.add(comment.getId());
            }
        }

        Map<Long, User> users = userIds.isEmpty()
                ? Collections.emptyMap()
                : userCacheService.getUsersByIds(userIds);

        // 点赞：一次 in(commentId, ...) 查完这一页（含子评论），在内存里判断
        Set<Long> likedCommentIds = currentUserId == null || commentIds.isEmpty()
                ? Collections.emptySet()
                : loadLikedCommentIds(currentUserId, commentIds);

        return new CommentViewContext(users, likedCommentIds, currentUserId != null);
    }

    /**
     * 一次性查出当前用户在这一页所有评论里的点赞记录，返回已点赞的评论ID集合
     */
    private Set<Long> loadLikedCommentIds(Long currentUserId, Set<Long> commentIds) {
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CommentLike::getCommentId, commentIds)
               .eq(CommentLike::getUserId, currentUserId);

        return commentLikeMapper.selectList(wrapper).stream()
                .map(CommentLike::getCommentId)
                .collect(Collectors.toSet());
    }

    /**
     * 转换为响应对象（含子评论，数据全部来自上下文，不再打库）
     */
    private CommentResponse toResponseWithReplies(Comment comment, List<Comment> replies, CommentViewContext context) {
        CommentResponse response = toResponse(comment, context);

        List<CommentResponse> replyResponses = replies.stream()
                .map(reply -> toResponse(reply, context))
                .collect(Collectors.toList());

        response.setReplies(replyResponses);
        response.setReplyCount(replyResponses.size());

        return response;
    }

    /**
     * 转换为响应对象（不含子评论），用户信息与点赞状态都从上下文里取
     */
    private CommentResponse toResponse(Comment comment, CommentViewContext context) {
        CommentResponse response = new CommentResponse();

        // 设置String类型的ID字段
        response.setId(String.valueOf(comment.getId()));
        response.setPostId(String.valueOf(comment.getPostId()));
        response.setUserId(String.valueOf(comment.getUserId()));
        response.setParentId(comment.getParentId() != null ? String.valueOf(comment.getParentId()) : null);
        response.setReplyToUserId(comment.getReplyToUserId() != null ? String.valueOf(comment.getReplyToUserId()) : null);

        response.setContent(comment.getContent());
        response.setLikeCount(comment.getLikeCount());
        response.setStatus(comment.getStatus());
        response.setCreateTime(comment.getCreateTime());

        // 点赞状态：未登录时恒为 false（与改动前一致）
        response.setIsLiked(context.isLiked(comment.getId()));

        // 加载用户信息（来自批量查询 / 缓存）
        User user = context.getUser(comment.getUserId());
        if (user != null) {
            response.setUserNickname(user.getNickname());
            // 设置公开的头像访问URL
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                response.setUserAvatar("/api/users/avatar/public/" + comment.getUserId());
            }
        }

        // 加载被回复用户信息
        if (comment.getReplyToUserId() != null) {
            User replyToUser = context.getUser(comment.getReplyToUserId());
            if (replyToUser != null) {
                response.setReplyToUserNickname(replyToUser.getNickname());
            }
        }

        return response;
    }

    /**
     * 评论列表组装上下文：装载「一页评论共用」的批量查询结果，
     * 让单条评论的转换在内存里完成，不再逐条打库。
     */
    private static final class CommentViewContext {

        private final Map<Long, User> users;
        private final Set<Long> likedCommentIds;
        private final boolean hasCurrentUser;

        CommentViewContext(Map<Long, User> users, Set<Long> likedCommentIds, boolean hasCurrentUser) {
            this.users = users != null ? users : Collections.emptyMap();
            this.likedCommentIds = likedCommentIds != null ? likedCommentIds : Collections.emptySet();
            this.hasCurrentUser = hasCurrentUser;
        }

        User getUser(Long userId) {
            return userId == null ? null : users.get(userId);
        }

        boolean isLiked(Long commentId) {
            return hasCurrentUser && commentId != null && likedCommentIds.contains(commentId);
        }
    }
    
    /**
     * 点赞评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void likeComment(Long userId, Long commentId) {
        // 1. 验证评论是否存在
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        
        // 2. 检查是否已经点过赞
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentLike::getCommentId, commentId)
               .eq(CommentLike::getUserId, userId);
        
        Long count = commentLikeMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("您已经点过赞了");
        }
        
        // 3. 创建点赞记录
        CommentLike commentLike = new CommentLike();
        commentLike.setCommentId(commentId);
        commentLike.setUserId(userId);
        commentLike.setCreateTime(LocalDateTime.now());
        commentLikeMapper.insert(commentLike);
        
        // 4. 更新评论的点赞数
        comment.setLikeCount(comment.getLikeCount() + 1);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);
        
        log.info("用户 {} 点赞评论: commentId={}", userId, commentId);
    }
    
    /**
     * 取消点赞评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlikeComment(Long userId, Long commentId) {
        // 1. 验证评论是否存在
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        
        // 2. 查找点赞记录
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentLike::getCommentId, commentId)
               .eq(CommentLike::getUserId, userId);
        
        CommentLike commentLike = commentLikeMapper.selectOne(wrapper);
        if (commentLike == null) {
            throw new BusinessException("您还没有点过赞");
        }
        
        // 3. 删除点赞记录
        commentLikeMapper.deleteById(commentLike.getId());
        
        // 4. 更新评论的点赞数
        int newLikeCount = Math.max(0, comment.getLikeCount() - 1);
        comment.setLikeCount(newLikeCount);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);
        
        log.info("用户 {} 取消点赞评论: commentId={}", userId, commentId);
    }
    
    /**
     * 检查用户是否已点赞
     */
    public boolean isLiked(Long userId, Long commentId) {
        if (userId == null) {
            return false;
        }
        
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentLike::getCommentId, commentId)
               .eq(CommentLike::getUserId, userId);
        
        return commentLikeMapper.selectCount(wrapper) > 0;
    }
}

