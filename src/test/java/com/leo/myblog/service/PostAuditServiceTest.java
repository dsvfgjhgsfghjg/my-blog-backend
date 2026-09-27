package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.PostAudit;
import com.leo.myblog.enums.NotificationType;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.PostAuditMapper;
import com.leo.myblog.mapper.PostMapper;
import com.leo.myblog.util.AbilityValidator;
import com.leo.myblog.util.PostResponseConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章审计服务测试
 * <p>
 * 用 Mockito 纯单元测试，不起 Spring 容器 —— 这个服务的依赖（Mapper、能力校验、通知）
 * 全部可以替换，不需要 PostgreSQL / Redis / MinIO，跑起来是秒级的。
 * <p>
 * 覆盖 TODO 里点名的四个场景：创建文章自动进待审核、审计员通过/拒绝、
 * 非审计员审计应失败、普通用户看不到待审核文章。
 * <p>
 * 注意 {@code lenient}：本轮为「审计通过」路径写的桩，在只读用例里用不上，
 * Mockito 默认的 STRICT_STUBS 会因为「桩没被用到」直接报错。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostAuditServiceTest {

    private static final Long POST_ID = 1001L;
    private static final Long AUTHOR_ID = 2002L;
    private static final Long AUDITOR_ID = 3003L;

    @Mock private PostAuditMapper postAuditMapper;
    @Mock private PostMapper postMapper;
    @Mock private UserCacheService userCacheService;
    @Mock private AbilityValidator abilityValidator;
    @Mock private PostResponseConverter postResponseConverter;
    @Mock private NotificationService notificationService;

    @InjectMocks private PostAuditService postAuditService;

    private Post post;

    @BeforeEach
    void setUp() {
        post = new Post();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
        post.setTitle("测试文章");
        post.setStatus(3); // 待审核
    }

    private AuditPostRequest request(int status, String comment) {
        AuditPostRequest request = new AuditPostRequest();
        request.setAuditStatus(status);
        request.setAuditComment(comment);
        return request;
    }

    private PostAudit pendingAudit() {
        PostAudit audit = new PostAudit();
        audit.setId(1L);
        audit.setPostId(POST_ID);
        audit.setAuditStatus(0);
        return audit;
    }

    @Nested
    @DisplayName("创建文章自动进待审核")
    class CreateAuditRecord {

        @Test
        @DisplayName("新文章写入一条待审核记录")
        void createsPendingRecord() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.createAuditRecord(POST_ID);

            ArgumentCaptor<PostAudit> captor = ArgumentCaptor.forClass(PostAudit.class);
            verify(postAuditMapper).insert(captor.capture());

            PostAudit saved = captor.getValue();
            assertEquals(POST_ID, saved.getPostId(), "审计记录要挂在正确的文章上");
            assertEquals(0, saved.getAuditStatus(), "新记录必须是待审核（0）");
            assertNull(saved.getAuditorId(), "还没人审，审计员应为空");
            assertNotNull(saved.getCreateTime());
        }

        @Test
        @DisplayName("文章不存在时拒绝创建，且不落库")
        void rejectsMissingPost() {
            when(postMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class, () -> postAuditService.createAuditRecord(999L));
            verify(postAuditMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("审计员通过 / 拒绝")
    class AuditPost {

        @Test
        @DisplayName("通过：文章置为已发布并写发布时间")
        void approvePublishesPost() {
            PostAudit audit = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(audit);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(1, null));

            assertEquals(1, post.getStatus(), "通过后文章应为已发布（1）");
            assertNotNull(post.getPublishTime(), "通过时要补上发布时间");
            assertEquals(AUDITOR_ID, audit.getAuditorId(), "要记下是谁审的");
            assertEquals(1, audit.getAuditStatus());
            assertNotNull(audit.getAuditTime());
            verify(postAuditMapper).updateById(audit);
            verify(postMapper).updateById(post);
        }

        @Test
        @DisplayName("拒绝：文章退回草稿，拒绝原因落库")
        void rejectReturnsToDraft() {
            PostAudit audit = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(audit);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(2, "内容不合格"));

            assertEquals(0, post.getStatus(), "拒绝后文章应退回草稿（0）");
            assertEquals("内容不合格", audit.getAuditComment(), "拒绝原因必须存下来，作者要能看到");
            assertEquals(2, audit.getAuditStatus());
            verify(postMapper).updateById(post);
        }

        @Test
        @DisplayName("非审计员审计必须失败，且不产生任何写入")
        void nonAuditorIsRejected() {
            // 没有 audit_posts 能力的人在能力校验这一步就该被挡下
            doThrow(new BusinessException("权限不足，需要管理员权限"))
                    .when(abilityValidator).validateAbility(AUTHOR_ID, "audit_posts");

            assertThrows(BusinessException.class,
                    () -> postAuditService.auditPost(AUTHOR_ID, POST_ID, request(1, null)));

            verify(postAuditMapper, never()).updateById(any());
            verify(postMapper, never()).updateById(any());
            verify(notificationService, never()).notify(any(), any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("已审计过的文章不能重复审计")
        void rejectsDoubleAudit() {
            PostAudit alreadyApproved = pendingAudit();
            alreadyApproved.setAuditStatus(1);
            when(postAuditMapper.selectOne(any())).thenReturn(alreadyApproved);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> postAuditService.auditPost(AUDITOR_ID, POST_ID, request(2, "反悔了")));

            assertTrue(e.getMessage().contains("已审计"), "报错要说清原因，实际: " + e.getMessage());
            verify(postAuditMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("审计结果只接受 1（通过）和 2（拒绝）")
        void rejectsInvalidAuditStatus() {
            when(postAuditMapper.selectOne(any())).thenReturn(pendingAudit());

            assertThrows(BusinessException.class,
                    () -> postAuditService.auditPost(AUDITOR_ID, POST_ID, request(3, null)));
            assertThrows(BusinessException.class,
                    () -> postAuditService.auditPost(AUDITOR_ID, POST_ID, request(0, null)));

            verify(postAuditMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("历史文章没有审计记录时自动补建，而不是直接失败")
        void autoCreatesMissingAuditRecord() {
            // 第一次 selectOne 返回 null（没有记录），createAuditRecord 之后再查能查到
            PostAudit created = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(null, created);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(1, null));

            verify(postAuditMapper).insert(any());
            assertEquals(1, post.getStatus(), "补建记录后仍应正常走完审计");
        }
    }

    @Nested
    @DisplayName("审计结果通知作者")
    class NotifyAuthor {

        @Test
        @DisplayName("通过时通知作者，且不带拒绝原因")
        void notifiesOnApprove() {
            PostAudit audit = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(audit);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(1, null));

            ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notify(
                    eq(AUTHOR_ID), eq(NotificationType.POST_AUDIT_PASSED), anyString(),
                    content.capture(), link.capture());

            assertTrue(content.getValue().contains("测试文章"), "通知里要带上文章标题: " + content.getValue());
            assertTrue(content.getValue().contains("通过"), "要说明是通过: " + content.getValue());
            assertEquals("/admin/articles", link.getValue(), "#link 应指向文章管理页");
        }

        @Test
        @DisplayName("拒绝时把审核意见一并告知作者")
        void notifiesOnRejectWithComment() {
            PostAudit audit = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(audit);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(2, "标题党"));

            ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notify(
                    eq(AUTHOR_ID), eq(NotificationType.POST_AUDIT_REJECTED), anyString(), content.capture(), anyString());

            assertTrue(content.getValue().contains("标题党"),
                    "拒绝原因必须让作者看到，否则他无从修改: " + content.getValue());
        }

        @Test
        @DisplayName("通知的是作者本人，不是审计员")
        void notifiesAuthorNotAuditor() {
            PostAudit audit = pendingAudit();
            when(postAuditMapper.selectOne(any())).thenReturn(audit);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postAuditService.auditPost(AUDITOR_ID, POST_ID, request(1, null));

            verify(notificationService, never()).notify(eq(AUDITOR_ID), any(), anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("待审核列表只对审计员开放")
    class PendingPosts {

        @Test
        @DisplayName("普通用户查询待审核列表被拒")
        void nonAdminIsRejected() {
            doThrow(new BusinessException("权限不足，需要管理员权限"))
                    .when(abilityValidator).validateAbility(AUTHOR_ID, "audit_posts");

            assertThrows(BusinessException.class,
                    () -> postAuditService.getPendingPosts(AUTHOR_ID, 1, 20));
        }

        @Test
        @DisplayName("没有待审核文章时返回空页而不是报错")
        void returnsEmptyPageWhenNothingPending() {
            // selectPage 正常会往传入的 Page 里填结果，这里让它原样返回即可（records 默认为空）
            when(postAuditMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            PageResult<PostListItemResponse> result = postAuditService.getPendingPosts(AUDITOR_ID, 1, 20);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty(), "没有待审核文章时列表应为空");
            assertEquals("0", result.getTotal());
        }

        @Test
        @DisplayName("待审核文章转成列表项返回")
        void mapsPendingPosts() {
            when(postAuditMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<PostAudit> page = inv.getArgument(0);
                page.setRecords(List.of(pendingAudit()));
                page.setTotal(1);
                return page;
            });
            when(postMapper.selectList(any())).thenReturn(List.of(post));

            PostListItemResponse item = new PostListItemResponse();
            item.setTitle("测试文章");
            when(postResponseConverter.convertToPostListItemResponse(post)).thenReturn(item);

            PageResult<PostListItemResponse> result = postAuditService.getPendingPosts(AUDITOR_ID, 1, 20);

            assertEquals(1, result.getRecords().size());
            assertEquals("测试文章", result.getRecords().get(0).getTitle());
            assertEquals("1", result.getTotal());
        }
    }

    @Nested
    @DisplayName("重新提交审核")
    class Resubmit {

        @Test
        @DisplayName("被拒的文章重置回待审核，清掉上一轮的审计信息")
        void resetsRejectedAudit() {
            PostAudit rejected = pendingAudit();
            rejected.setAuditStatus(2);
            rejected.setAuditorId(AUDITOR_ID);
            rejected.setAuditComment("标题党");

            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postAuditMapper.selectOne(any())).thenReturn(rejected);

            postAuditService.resubmitForAudit(AUTHOR_ID, POST_ID);

            assertEquals(0, rejected.getAuditStatus(), "应重置为待审核");
            assertNull(rejected.getAuditorId(), "上一轮审计员要清掉");
            assertNull(rejected.getAuditComment(), "上一轮的意见要清掉");
            assertEquals(3, post.getStatus(), "文章回到待审核状态");
        }

        @Test
        @DisplayName("非作者不能重新提交")
        void onlyAuthorCanResubmit() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> postAuditService.resubmitForAudit(AUDITOR_ID, POST_ID));

            assertTrue(e.getMessage().contains("作者"), "报错要说清原因，实际: " + e.getMessage());
            verify(postAuditMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("审核中的文章不能重复提交")
        void rejectsResubmitWhilePending() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postAuditMapper.selectOne(any())).thenReturn(pendingAudit());

            assertThrows(BusinessException.class, () -> postAuditService.resubmitForAudit(AUTHOR_ID, POST_ID));
            verify(postAuditMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("已通过的文章不能重新提交")
        void rejectsResubmitWhenApproved() {
            PostAudit approved = pendingAudit();
            approved.setAuditStatus(1);
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postAuditMapper.selectOne(any())).thenReturn(approved);

            assertThrows(BusinessException.class, () -> postAuditService.resubmitForAudit(AUTHOR_ID, POST_ID));
        }
    }

    @Nested
    @DisplayName("审计记录查询权限")
    class AuditRecord {

        @Test
        @DisplayName("作者本人可以查看自己文章的审计记录")
        void authorCanReadOwnRecord() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            PostAudit audit = pendingAudit();
            audit.setAuditStatus(2);
            audit.setAuditorId(AUDITOR_ID);
            when(postAuditMapper.selectOne(any())).thenReturn(audit);

            assertNotNull(postAuditService.getAuditRecord(AUTHOR_ID, false, POST_ID));
        }

        @Test
        @DisplayName("无关用户不能查看别人文章的审计记录")
        void strangerCannotReadRecord() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            assertThrows(BusinessException.class,
                    () -> postAuditService.getAuditRecord(9999L, false, POST_ID));
        }

        @Test
        @DisplayName("管理员可以查看任意文章的审计记录")
        void adminCanReadAnyRecord() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postAuditMapper.selectOne(any())).thenReturn(pendingAudit());

            assertNotNull(postAuditService.getAuditRecord(AUDITOR_ID, true, POST_ID));
        }

        @Test
        @DisplayName("没有审计记录时返回 null 而不是报错")
        void returnsNullWhenNoRecord() {
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postAuditMapper.selectOne(any())).thenReturn(null);

            assertNull(postAuditService.getAuditRecord(AUTHOR_ID, false, POST_ID));
        }
    }
}
