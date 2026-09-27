package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.response.NotificationResponse;
import com.leo.myblog.entity.Notification;
import com.leo.myblog.enums.NotificationType;
import com.leo.myblog.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知服务测试
 * <p>
 * 纯 Mockito 单元测试，不需要数据库。重点在两处：
 * 一是 {@code notify} 对非法入参（空接收者 / 空标题）要能安全跳过而不是把上游操作搞挂；
 * 二是「未读数为 0」和「标记已读没改到任何行」这类边界要有确定行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private NotificationMapper notificationMapper;

    @InjectMocks private NotificationService notificationService;

    /**
     * MyBatis-Plus 的 lambda 缓存平时由 Spring 启动时扫描实体来填充。纯单元测试里没有这一步，
     * {@code LambdaUpdateWrapper.set(Notification::getIsRead, …)} 在解析列名时会直接抛
     * {@code can not find lambda cache for this entity [Notification]}。这里手动初始化一次。
     * <p>
     * 只有 {@code set(...)} 会在调用时立刻解析（所以标记已读那几条会踩到）；
     * {@code eq(...)} 是惰性的，等真正生成 SQL 时才解析 —— 这也解释了为什么
     * PostAuditServiceTest 里同样用了 Lambda 条件却不报错。
     */
    @BeforeAll
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
    }

    private Notification sample(Long id, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setType(NotificationType.POST_AUDIT_PASSED.getValue());
        n.setTitle("文章审核通过");
        n.setContent("《测试文章》已通过审核并发布。");
        n.setLink("/admin/articles");
        n.setIsRead(read);
        n.setCreateTime(LocalDateTime.now());
        return n;
    }

    @Nested
    @DisplayName("发送通知")
    class Notify {

        @Test
        @DisplayName("写入的通知默认未读，字段逐项落到位")
        void insertsUnreadNotification() {
            notificationService.notify(USER_ID, NotificationType.POST_AUDIT_REJECTED,
                    "文章审核未通过", "原因：标题党", "/admin/articles");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationMapper).insert(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(USER_ID, saved.getUserId());
            assertEquals("POST_AUDIT_REJECTED", saved.getType(), "type 存枚举名");
            assertEquals("文章审核未通过", saved.getTitle());
            assertEquals("原因：标题党", saved.getContent());
            assertEquals("/admin/articles", saved.getLink());
            assertFalse(saved.getIsRead(), "新通知必须是未读，否则角标不会亮");
            assertNotNull(saved.getCreateTime());
        }

        @Test
        @DisplayName("接收者为空时静默跳过，不落库也不抛异常")
        void skipsWhenNoRecipient() {
            // 历史文章的作者可能为空，这种情况不能让上游的审核/重置密码操作失败
            notificationService.notify(null, NotificationType.POST_AUDIT_PASSED, "标题", "正文", null);

            verify(notificationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("标题为空时跳过（数据库该列非空）")
        void skipsWhenTitleBlank() {
            notificationService.notify(USER_ID, NotificationType.SYSTEM, null, "正文", null);
            notificationService.notify(USER_ID, NotificationType.SYSTEM, "   ", "正文", null);

            verify(notificationMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("查询")
    class Query {

        @Test
        @DisplayName("未读数直接反映数据库计数")
        void returnsUnreadCount() {
            when(notificationMapper.selectCount(any())).thenReturn(7L);

            assertEquals(7, notificationService.unreadCount(USER_ID));
        }

        @Test
        @DisplayName("未读数返回 int 而不是 long（Long 会被全局 Jackson 转成字符串）")
        void unreadCountIsNotBoxedLong() throws Exception {
            // "0" 在 JS 里是真值，用 Long 下发会让前端角标永远亮着
            java.lang.reflect.Method m = NotificationService.class.getMethod("unreadCount", Long.class);
            assertEquals(int.class, m.getReturnType(), "返回值必须是 int，不能是 long/Long");
        }

        @Test
        @DisplayName("分页结果把雪花 ID 转成字符串下发")
        void mapsIdToString() {
            // 雪花 ID 超出 JavaScript 安全整数范围，必须用字符串下发，否则前端会截断
            when(notificationMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<Notification> page = inv.getArgument(0);
                page.setRecords(List.of(sample(1234567890123456789L, false)));
                page.setTotal(1);
                return page;
            });

            PageResult<NotificationResponse> result = notificationService.list(USER_ID, 1, 20, null);

            assertEquals(1, result.getRecords().size());
            NotificationResponse first = result.getRecords().get(0);
            assertEquals("1234567890123456789", first.getId(), "ID 必须是字符串形式");
            assertEquals("POST_AUDIT_PASSED", first.getType());
            assertFalse(first.getIsRead());
            assertEquals("1", result.getTotal());
        }

        @Test
        @DisplayName("pageSize 被夹在上限内，防止一次拉爆")
        void clampsOversizedPageSize() {
            when(notificationMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            notificationService.list(USER_ID, 1, 100000, null);

            ArgumentCaptor<Page<Notification>> captor = ArgumentCaptor.forClass(Page.class);
            verify(notificationMapper).selectPage(captor.capture(), any());
            assertEquals(100, captor.getValue().getSize(), "超过上限的 pageSize 应被夹到 100");
        }

        @Test
        @DisplayName("非法页码/页大小回落到默认值")
        void fallsBackToDefaults() {
            when(notificationMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            notificationService.list(USER_ID, 0, 0, null);

            ArgumentCaptor<Page<Notification>> captor = ArgumentCaptor.forClass(Page.class);
            verify(notificationMapper).selectPage(captor.capture(), any());
            assertEquals(1, captor.getValue().getCurrent(), "页码最小为 1");
            assertEquals(20, captor.getValue().getSize(), "页大小回落默认 20");
        }

        @Test
        @DisplayName("空结果返回空列表而不是 null")
        void returnsEmptyList() {
            when(notificationMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            PageResult<NotificationResponse> result = notificationService.list(USER_ID, 1, 20, null);

            assertNotNull(result.getRecords());
            assertTrue(result.getRecords().isEmpty());
        }
    }

    @Nested
    @DisplayName("标记已读")
    class MarkRead {

        @Test
        @DisplayName("改动到行时返回 true")
        void returnsTrueWhenUpdated() {
            when(notificationMapper.update(any(), any())).thenReturn(1);

            assertTrue(notificationService.markRead(USER_ID, 5L));
        }

        @Test
        @DisplayName("没改动到行（已读 / 不存在 / 不属于我）时返回 false，不抛异常")
        void returnsFalseWhenNothingUpdated() {
            // 幂等：重复点击不该报错，也不能从响应里泄露「某个通知 ID 是否存在」
            when(notificationMapper.update(any(), any())).thenReturn(0);

            assertFalse(notificationService.markRead(USER_ID, 5L));
        }

        @Test
        @DisplayName("全部标记为已读返回实际条数")
        void marksAllRead() {
            when(notificationMapper.update(any(), any())).thenReturn(3);

            assertEquals(3, notificationService.markAllRead(USER_ID));
        }
    }
}
