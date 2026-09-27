package com.leo.myblog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.request.CreateCommentRequest;
import com.leo.myblog.dto.request.GetCommentListRequest;
import com.leo.myblog.dto.response.CommentResponse;
import com.leo.myblog.entity.Comment;
import com.leo.myblog.entity.CommentLike;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.User;
import com.leo.myblog.mapper.CommentLikeMapper;
import com.leo.myblog.mapper.CommentMapper;
import com.leo.myblog.mapper.PostMapper;
import com.leo.myblog.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论列表 N+1 改造的回归测试
 * <p>
 * 纯 Mockito 单元测试，不起 Spring 容器、不需要 PG / Redis / MinIO。
 * <p>
 * 这一轮的重点不是「功能对不对」（功能本来是对的），而是**每页的查库次数不能随页大小增长**：
 * 改动前每条评论都要查一次点赞、一次作者、一次被回复者，每条顶级评论还要单独查一次子评论，
 * 一页 20 条顶级评论 + 平均 3 条回复约 240 次查询。因此这里用 {@code times(1)} / {@code never()}
 * 把「批量取数」钉死，再用嵌套结构、点赞状态、空结果等用例保证重构没改行为。
 * <p>
 * {@code lenient}：不同用例用到的桩不一样，Mockito 默认的 STRICT_STUBS 会因为「桩没被用到」报错。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceTest {

    private static final Long POST_ID = 100L;
    private static final Long CURRENT_USER_ID = 9L;

    @Mock private CommentMapper commentMapper;
    @Mock private CommentLikeMapper commentLikeMapper;
    @Mock private PostMapper postMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserCacheService userCacheService;

    @InjectMocks private CommentService commentService;

    @BeforeEach
    void setUp() {
        Post post = new Post();
        post.setId(POST_ID);
        when(postMapper.selectById(POST_ID)).thenReturn(post);
    }

    // ---------------------------------------------------------------- 查询次数

    @Test
    @DisplayName("一页 20 条顶级评论 + 60 条回复：查库次数固定为个位数（原实现约 240 次）")
    void listQueryCountIsConstantForFullPage() {
        List<Comment> tops = new ArrayList<>();
        List<Comment> replies = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            tops.add(comment(i, POST_ID, 1000L + i, null, null));
            for (long j = 1; j <= 3; j++) {
                replies.add(comment(i * 100 + j, POST_ID, 2000L + i, i, i == 1 && j == 1 ? 1001L : null));
            }
        }
        stubPage(tops, 20);
        stubReplies(replies);
        stubLikes(like(1L));
        stubUsers(tops, replies);

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        assertEquals(20, result.getRecords().size());
        assertEquals(20L, result.getTotal());
        assertTrue(result.getRecords().stream().allMatch(r -> r.getReplyCount() == 3));

        // 顶级评论：1 次分页查询；子评论：1 次 in(parentId, ...)，而不是每个顶级评论一次
        verify(commentMapper, times(1)).selectPage(any(), any());
        verify(commentMapper, times(1)).selectList(any());
        // 点赞：1 次 in(commentId, ...)，不再逐条 selectCount
        verify(commentLikeMapper, times(1)).selectList(any());
        verify(commentLikeMapper, never()).selectCount(any());
        // 用户：1 次批量（走缓存），不再逐条 selectById
        verify(userCacheService, times(1)).getUsersByIds(any());
        verify(userMapper, never()).selectById(any());
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("页大小 2 与页大小 20 的查库次数完全一致")
    void listQueryCountIsIndependentOfPageSize() {
        List<Comment> tops = List.of(
                comment(1L, POST_ID, 1001L, null, null),
                comment(2L, POST_ID, 1002L, null, null));
        List<Comment> replies = List.of(comment(11L, POST_ID, 1001L, 1L, null));
        stubPage(tops, 2);
        stubReplies(replies);
        stubLikes(like(1L));
        stubUsers(tops, replies);

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 2));

        assertEquals(2, result.getRecords().size());
        // 与 20 条那组用例的断言完全相同 —— 次数与页大小无关
        verify(commentMapper, times(1)).selectPage(any(), any());
        verify(commentMapper, times(1)).selectList(any());
        verify(commentLikeMapper, times(1)).selectList(any());
        verify(commentLikeMapper, never()).selectCount(any());
        verify(userCacheService, times(1)).getUsersByIds(any());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("用户批量取数按 userId 去重：同一作者在一页里只取一次")
    void userLookupIsDeduplicated() {
        // 3 条评论同属一个作者，另有 2 条回复指向同一个被回复者
        List<Comment> tops = List.of(
                comment(1L, POST_ID, 1001L, null, 1002L),
                comment(2L, POST_ID, 1001L, null, 1002L),
                comment(3L, POST_ID, 1001L, null, null));
        List<Comment> replies = List.of(
                comment(11L, POST_ID, 1001L, 1L, 1002L),
                comment(12L, POST_ID, 1002L, 1L, null));
        stubPage(tops, 3);
        stubReplies(replies);
        stubLikes();
        stubUsers(tops, replies);

        commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userCacheService, times(1)).getUsersByIds(captor.capture());
        assertEquals(new HashSet<>(List.of(1001L, 1002L)), new HashSet<>(captor.getValue()));
    }

    // ---------------------------------------------------------------- 嵌套结构

    @Test
    @DisplayName("replies 按 parentId 正确嵌套、按 createTime 升序，子评论自身不再带 replies")
    void repliesAreNestedPerParent() {
        List<Comment> tops = List.of(
                comment(1L, POST_ID, 1001L, null, null),
                comment(2L, POST_ID, 1002L, null, null),
                comment(3L, POST_ID, 1003L, null, null));
        // 批量查询按 createTime 升序返回，父评论交错出现，模拟真实排序
        List<Comment> replies = List.of(
                comment(11L, POST_ID, 2001L, 1L, null),
                comment(21L, POST_ID, 2002L, 2L, null),
                comment(12L, POST_ID, 2003L, 1L, null),
                comment(22L, POST_ID, 2004L, 2L, null));
        stubPage(tops, 3);
        stubReplies(replies);
        stubLikes();
        stubUsers(tops, replies);

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        CommentResponse first = result.getRecords().get(0);
        assertEquals("1", first.getId());
        assertEquals(2, first.getReplyCount());
        assertEquals(List.of("11", "12"),
                first.getReplies().stream().map(CommentResponse::getId).collect(Collectors.toList()));

        CommentResponse second = result.getRecords().get(1);
        assertEquals(List.of("21", "22"),
                second.getReplies().stream().map(CommentResponse::getId).collect(Collectors.toList()));

        CommentResponse third = result.getRecords().get(2);
        assertEquals(0, third.getReplyCount());
        assertNotNull(third.getReplies());
        assertTrue(third.getReplies().isEmpty());

        // 子评论的 replies / replyCount 保持 null（与改动前逐条查询时的行为一致）
        CommentResponse reply = first.getReplies().get(0);
        assertNull(reply.getReplies());
        assertNull(reply.getReplyCount());
        assertEquals("1", reply.getParentId());
    }

    // ---------------------------------------------------------------- 点赞状态

    @Test
    @DisplayName("点赞状态来自批量结果：顶级与子评论都能正确标记，未点赞的为 false")
    void isLikedIsResolvedInMemory() {
        List<Comment> tops = List.of(
                comment(1L, POST_ID, 1001L, null, null),
                comment(2L, POST_ID, 1002L, null, null));
        List<Comment> replies = List.of(
                comment(11L, POST_ID, 2001L, 1L, null),
                comment(12L, POST_ID, 2002L, 1L, null));
        stubPage(tops, 2);
        stubReplies(replies);
        stubLikes(like(1L), like(12L));   // 点赞了顶级 1 和 1 号评论下的子评论 12
        stubUsers(tops, replies);

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        assertTrue(result.getRecords().get(0).getIsLiked());
        assertFalse(result.getRecords().get(1).getIsLiked());
        assertFalse(result.getRecords().get(0).getReplies().get(0).getIsLiked());
        assertTrue(result.getRecords().get(0).getReplies().get(1).getIsLiked());
        // 只批量查一次点赞
        verify(commentLikeMapper, times(1)).selectList(any());
        verify(commentLikeMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("未登录用户：isLiked 全为 false，且完全不查点赞表")
    void anonymousUserNeverQueriesLikes() {
        List<Comment> tops = List.of(comment(1L, POST_ID, 1001L, null, null));
        List<Comment> replies = List.of(comment(11L, POST_ID, 2001L, 1L, null));
        stubPage(tops, 1);
        stubReplies(replies);
        stubUsers(tops, replies);

        Page<CommentResponse> result = commentService.getCommentList(null, request(1, 20));

        assertFalse(result.getRecords().get(0).getIsLiked());
        assertFalse(result.getRecords().get(0).getReplies().get(0).getIsLiked());
        verify(commentLikeMapper, never()).selectList(any());
        verify(commentLikeMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("点赞表为空：全部 isLiked 为 false 且不报错")
    void emptyLikeTableYieldsFalse() {
        List<Comment> tops = List.of(comment(1L, POST_ID, 1001L, null, null));
        stubPage(tops, 1);
        stubReplies(List.of());
        stubLikes();
        stubUsers(tops, List.of());

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        assertFalse(result.getRecords().get(0).getIsLiked());
        verify(commentLikeMapper, times(1)).selectList(any());
    }

    // ---------------------------------------------------------------- 边界

    @Test
    @DisplayName("顶级评论为空：不触发任何批量查询，分页元数据保持不变")
    void emptyTopLevelListIsSafe() {
        stubPage(List.of(), 0);
        stubReplies(List.of());
        stubLikes();
        stubUsers(List.of(), List.of());

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(2, 10));

        assertTrue(result.getRecords().isEmpty());
        assertEquals(2L, result.getCurrent());
        assertEquals(10L, result.getSize());
        assertEquals(0L, result.getTotal());

        verify(commentMapper, times(1)).selectPage(any(), any());
        verify(commentMapper, never()).selectList(any());
        verify(commentLikeMapper, never()).selectList(any());
        verify(userCacheService, never()).getUsersByIds(any());
    }

    @Test
    @DisplayName("用户/被回复者信息：replyToUserId 为 null 或用户查不到时不设置昵称，也不炸")
    void missingUsersAreTolerated() {
        List<Comment> tops = List.of(comment(1L, POST_ID, 1001L, null, null));
        List<Comment> replies = List.of(
                comment(11L, POST_ID, 2001L, 1L, 9999L),  // replyToUserId 存在但缓存里没有这个用户
                comment(12L, POST_ID, 2002L, 1L, null));  // replyToUserId 为 null
        stubPage(tops, 1);
        stubReplies(replies);
        stubLikes();
        // 故意不返回 9999L 这个用户
        when(userCacheService.getUsersByIds(any())).thenReturn(new HashMap<>(Map.of(
                1001L, user(1001L), 2001L, user(2001L), 2002L, user(2002L))));

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        CommentResponse top = result.getRecords().get(0);
        assertEquals("用户1001", top.getUserNickname());
        assertEquals("/api/users/avatar/public/1001", top.getUserAvatar());
        assertNull(top.getReplies().get(0).getReplyToUserNickname());
        assertNull(top.getReplies().get(1).getReplyToUserNickname());
        assertEquals("用户2001", top.getReplies().get(0).getUserNickname());
    }

    @Test
    @DisplayName("用户缓存返回空 map（比如 Redis 降级查库也没查到）时，昵称保持 null 且不报错")
    void emptyUserMapIsSafe() {
        List<Comment> tops = List.of(comment(1L, POST_ID, 1001L, null, null));
        stubPage(tops, 1);
        stubReplies(List.of());
        stubLikes();
        when(userCacheService.getUsersByIds(any())).thenReturn(Collections.emptyMap());

        Page<CommentResponse> result = commentService.getCommentList(CURRENT_USER_ID, request(1, 20));

        assertNull(result.getRecords().get(0).getUserNickname());
        assertNull(result.getRecords().get(0).getUserAvatar());
    }

    // ---------------------------------------------------------------- 其它入口

    @Test
    @DisplayName("getUserComments 同样按页批量取用户，isLiked 保持 false")
    void getUserCommentsBatchesUsers() {
        List<Comment> comments = List.of(
                comment(1L, POST_ID, 1001L, null, null),
                comment(2L, POST_ID, 1001L, null, null));
        stubPage(comments, 2);
        stubUsers(comments, List.of());

        Page<CommentResponse> result = commentService.getUserComments(1001L, 1, 20);

        assertEquals(2, result.getRecords().size());
        assertFalse(result.getRecords().get(0).getIsLiked());
        verify(userCacheService, times(1)).getUsersByIds(any());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("createComment 单条返回仍然正常，用户信息走缓存而不是直连 userMapper")
    void createCommentStillWorks() {
        when(commentMapper.insert(any(Comment.class))).thenAnswer(invocation -> {
            Comment inserted = invocation.getArgument(0);
            inserted.setId(999L);
            return 1;
        });
        when(userCacheService.getUsersByIds(any()))
                .thenReturn(new HashMap<>(Map.of(1001L, user(1001L))));

        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(POST_ID);
        request.setParentId(0L);
        request.setContent("  内容  ");

        CommentResponse response = commentService.createComment(1001L, request);

        assertEquals("999", response.getId());
        assertEquals("100", response.getPostId());
        assertEquals("内容", response.getContent());
        assertEquals(0, response.getLikeCount());
        assertFalse(response.getIsLiked());
        assertEquals("用户1001", response.getUserNickname());
        verify(userMapper, never()).selectById(any());
    }

    // ---------------------------------------------------------------- helpers

    private GetCommentListRequest request(int pageNum, int pageSize) {
        GetCommentListRequest request = new GetCommentListRequest();
        request.setPostId(POST_ID);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

    private static Comment comment(long id, long postId, Long userId, Long parentId, Long replyToUserId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent("内容" + id);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(id));
        return comment;
    }

    private static CommentLike like(long commentId) {
        CommentLike like = new CommentLike();
        like.setCommentId(commentId);
        like.setUserId(CURRENT_USER_ID);
        return like;
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setNickname("用户" + id);
        user.setAvatar("avatar/" + id + ".png");
        return user;
    }

    private void stubPage(List<Comment> records, long total) {
        when(commentMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(new ArrayList<>(records));
            page.setTotal(total);
            return page;
        });
    }

    private void stubReplies(List<Comment> replies) {
        when(commentMapper.selectList(any())).thenReturn(new ArrayList<>(replies));
    }

    private void stubLikes(CommentLike... likes) {
        when(commentLikeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(likes)));
    }

    /** 用户缓存按「评论里出现过的所有 userId」返回，模拟全部命中缓存 */
    private void stubUsers(List<Comment> tops, List<Comment> replies) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Comment comment : tops) {
            ids.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                ids.add(comment.getReplyToUserId());
            }
        }
        for (Comment comment : replies) {
            ids.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                ids.add(comment.getReplyToUserId());
            }
        }
        Map<Long, User> users = new HashMap<>();
        for (Long id : ids) {
            if (id != null) {
                users.put(id, user(id));
            }
        }
        when(userCacheService.getUsersByIds(any())).thenReturn(users);
    }
}
