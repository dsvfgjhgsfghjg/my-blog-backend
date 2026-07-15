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
        
        // 4. 转换为响应对象，并加载子评论
        List<CommentResponse> responses = commentPage.getRecords().stream()
                .map(comment -> convertToResponseWithReplies(comment, currentUserId))
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
        
        List<CommentResponse> responses = commentPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
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
        return convertToResponse(comment, null);
    }
    
    /**
     * 转换为响应对象（不包含子评论，带当前用户ID）
     */
    private CommentResponse convertToResponse(Comment comment, Long currentUserId) {
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
        
        // 检查当前用户是否已点赞
        if (currentUserId != null) {
            response.setIsLiked(isLiked(currentUserId, comment.getId()));
        } else {
            response.setIsLiked(false);
        }
        
        // 加载用户信息
        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            response.setUserNickname(user.getNickname());
            // 设置公开的头像访问URL
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                response.setUserAvatar("/api/users/avatar/public/" + comment.getUserId());
            }
        }
        
        // 加载被回复用户信息
        if (comment.getReplyToUserId() != null) {
            User replyToUser = userMapper.selectById(comment.getReplyToUserId());
            if (replyToUser != null) {
                response.setReplyToUserNickname(replyToUser.getNickname());
            }
        }
        
        return response;
    }
    
    /**
     * 转换为响应对象（包含子评论）
     */
    private CommentResponse convertToResponseWithReplies(Comment comment) {
        return convertToResponseWithReplies(comment, null);
    }
    
    /**
     * 转换为响应对象（包含子评论，带当前用户ID）
     */
    private CommentResponse convertToResponseWithReplies(Comment comment, Long currentUserId) {
        CommentResponse response = convertToResponse(comment, currentUserId);
        
        // 查询子评论（回复）
        LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<>();
        replyWrapper.eq(Comment::getParentId, comment.getId())
                    .eq(Comment::getStatus, 1)
                    .orderByAsc(Comment::getCreateTime);
        
        List<Comment> replies = commentMapper.selectList(replyWrapper);
        
        // 转换子评论
        List<CommentResponse> replyResponses = replies.stream()
                .map(reply -> convertToResponse(reply, currentUserId))
                .collect(Collectors.toList());
        
        response.setReplies(replyResponses);
        response.setReplyCount(replyResponses.size());
        
        return response;
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

