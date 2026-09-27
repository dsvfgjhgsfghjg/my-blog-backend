package com.leo.myblog.controller;

import com.leo.myblog.common.PageResult;
import com.leo.myblog.exception.GlobalExceptionHandler;
import com.leo.myblog.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站内通知控制器测试
 * <p>
 * standalone MockMvc，不依赖数据库。重点验证两件事：
 * 用户身份只从请求属性取（不接受前端传 userId），以及标记已读的幂等语义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    private static final Long CURRENT_USER_ID = 42L;

    @Mock private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("列表：返回分页结构")
    void listReturnsPage() throws Exception {
        when(notificationService.list(anyLong(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), "0", "20", "1"));

        mockMvc.perform(get("/api/notifications").requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("列表：unreadOnly 参数透传到 service")
    void listPassesUnreadOnly() throws Exception {
        when(notificationService.list(anyLong(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), "0", "20", "1"));

        mockMvc.perform(get("/api/notifications")
                        .requestAttr("userId", CURRENT_USER_ID)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(notificationService).list(CURRENT_USER_ID, 1, 20, true);
    }

    @Test
    @DisplayName("列表：身份取请求属性，不认前端传的 userId 参数")
    void listIgnoresUserIdParam() throws Exception {
        // 否则任何人都能读别人的站内信
        when(notificationService.list(anyLong(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), "0", "20", "1"));

        mockMvc.perform(get("/api/notifications")
                        .requestAttr("userId", CURRENT_USER_ID)
                        .param("userId", "999999"))
                .andExpect(status().isOk());

        verify(notificationService).list(eq(CURRENT_USER_ID), any(), any(), any());
        verify(notificationService, never()).list(eq(999999L), any(), any(), any());
    }

    @Test
    @DisplayName("未读数：返回数字")
    void unreadCount() throws Exception {
        when(notificationService.unreadCount(CURRENT_USER_ID)).thenReturn(5);

        mockMvc.perform(get("/api/notifications/unread-count").requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("标记已读：即使没改到任何行也返回成功（幂等）")
    void markReadIsIdempotent() throws Exception {
        // 重复点击、或那条本就不属于我，都不该报错；也不从响应里泄露 ID 是否存在
        when(notificationService.markRead(CURRENT_USER_ID, 7L)).thenReturn(false);

        mockMvc.perform(post("/api/notifications/{id}/read", 7L).requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationService).markRead(CURRENT_USER_ID, 7L);
    }

    @Test
    @DisplayName("标记已读：把当前用户一起传给 service（防止改到别人的通知）")
    void markReadScopesToCurrentUser() throws Exception {
        when(notificationService.markRead(anyLong(), anyLong())).thenReturn(true);

        mockMvc.perform(post("/api/notifications/{id}/read", 7L).requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk());

        verify(notificationService).markRead(CURRENT_USER_ID, 7L);
    }

    @Test
    @DisplayName("全部已读：返回本次标记条数")
    void markAllRead() throws Exception {
        when(notificationService.markAllRead(CURRENT_USER_ID)).thenReturn(3);

        mockMvc.perform(post("/api/notifications/read-all").requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));

        verify(notificationService).markAllRead(CURRENT_USER_ID);
    }
}
