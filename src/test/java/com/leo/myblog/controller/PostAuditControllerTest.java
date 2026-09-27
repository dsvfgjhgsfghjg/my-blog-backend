package com.leo.myblog.controller;

import com.leo.myblog.common.PageResult;
import com.leo.myblog.dto.request.AuditPostRequest;
import com.leo.myblog.dto.response.PostAuditResponse;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.exception.GlobalExceptionHandler;
import com.leo.myblog.service.PostAuditService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章审计控制器测试
 * <p>
 * 用 standalone MockMvc（不起 Spring 容器），所以不依赖 PostgreSQL / Redis。
 * 挂上了真实的 {@link GlobalExceptionHandler} 与 JSR-303 校验器，
 * 为的是让「参数非法」「业务异常」走和线上一样的响应格式。
 * <p>
 * 注意业务异常一律返回 HTTP 200，错误信息体现在 body 的 {@code code} 字段里，
 * 所以下面断言的是 {@code $.code} 而不是 HTTP 状态码。唯一的例外是
 * {@link com.leo.myblog.exception.ForbiddenException}（权限拒绝），它会被映射成真正的
 * HTTP 403；本类里用的是普通 {@code BusinessException(403, ...)}，因此下面的断言仍然成立
 * （其映射规则见 {@code GlobalExceptionHandlerTest}）。
 * <p>
 * 这里**不覆盖** {@code @RequireAdmin} / {@code @RequireTotp} 这两个注解的效果 ——
 * 它们是切面，standalone 环境下不会织入。本类只验证路由、参数绑定、请求属性的取用，
 * 以及响应包裹格式。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostAuditControllerTest {

    private static final Long POST_ID = 1001L;
    private static final Long CURRENT_USER_ID = 3003L;

    @Mock private PostAuditService postAuditService;
    @Mock private UserCacheService userCacheService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        PostAuditController controller = new PostAuditController(postAuditService, userCacheService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("待审核列表：正常返回分页结构")
    void pendingReturnsPage() throws Exception {
        when(postAuditService.getPendingPosts(CURRENT_USER_ID, 2, 5))
                .thenReturn(new PageResult<>(List.of(), "0", "5", "2"));

        mockMvc.perform(get("/api/posts/audit/pending")
                        .requestAttr("userId", CURRENT_USER_ID)
                        .param("pageNum", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());

        verify(postAuditService).getPendingPosts(CURRENT_USER_ID, 2, 5);
    }

    @Test
    @DisplayName("用户身份一律取请求属性，不认前端传的 userId 参数")
    void takesUserIdFromRequestAttributeNotParam() throws Exception {
        // 否则任何人都能伪造 userId 冒充审计员
        mockMvc.perform(get("/api/posts/audit/pending")
                        .requestAttr("userId", CURRENT_USER_ID)
                        .param("userId", "999999"))
                .andExpect(status().isOk());

        verify(postAuditService).getPendingPosts(CURRENT_USER_ID, 1, 20);
        verify(postAuditService, never()).getPendingPosts(eq(999999L), any(), any());
    }

    @Test
    @DisplayName("待审核列表：未登录（无 userId 请求属性）时请求失败")
    void pendingFailsWithoutUserId() throws Exception {
        try {
            mockMvc.perform(get("/api/posts/audit/pending")).andExpect(status().isOk());
            // 若没抛异常，说明 userId 缺失被静默当成 null 传进了 service —— 那才是问题
            verify(postAuditService, never()).getPendingPosts(any(), any(), any());
        } catch (Exception expected) {
            // 期望路径：缺少必需的请求属性，请求在进入控制器前就被拒
            verify(postAuditService, never()).getPendingPosts(any(), any(), any());
        }
    }

    @Test
    @DisplayName("审计通过：返回通过文案")
    void auditPassMessage() throws Exception {
        mockMvc.perform(post("/api/posts/audit/{postId}", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章已通过"));

        verify(postAuditService).auditPost(eq(CURRENT_USER_ID), eq(POST_ID), any(AuditPostRequest.class));
    }

    @Test
    @DisplayName("审计拒绝：返回拒绝文案，且审核意见传到 service")
    void auditRejectMessage() throws Exception {
        mockMvc.perform(post("/api/posts/audit/{postId}", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2,\"auditComment\":\"标题党\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文章已拒绝"));

        org.mockito.ArgumentCaptor<AuditPostRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AuditPostRequest.class);
        verify(postAuditService).auditPost(eq(CURRENT_USER_ID), eq(POST_ID), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("标题党", captor.getValue().getAuditComment());
    }

    @Test
    @DisplayName("审计结果缺失时被参数校验挡下，不会进到 service")
    void auditRejectsMissingStatus() throws Exception {
        mockMvc.perform(post("/api/posts/audit/{postId}", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数无效"));

        verify(postAuditService, never()).auditPost(any(), any(), any());
    }

    @Test
    @DisplayName("service 抛业务异常时按统一格式返回，且不返回 200 成功码")
    void surfacesBusinessException() throws Exception {
        doThrow(new BusinessException(403, "权限不足，需要管理员权限"))
                .when(postAuditService).auditPost(any(), any(), any());

        mockMvc.perform(post("/api/posts/audit/{postId}", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("权限不足，需要管理员权限"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("查审计记录：管理员身份由服务端判定后传给 service")
    void recordPassesAdminFlag() throws Exception {
        User admin = new User();
        admin.setIsAdmin(true);
        when(userCacheService.getUserById(CURRENT_USER_ID)).thenReturn(admin);
        when(postAuditService.getAuditRecord(anyLong(), anyBoolean(), anyLong()))
                .thenReturn(new PostAuditResponse());

        mockMvc.perform(get("/api/posts/audit/{postId}/record", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // isAdmin 必须由后端查库得出，而不是前端传进来
        verify(postAuditService).getAuditRecord(CURRENT_USER_ID, true, POST_ID);
    }

    @Test
    @DisplayName("查审计记录：用户不存在时按非管理员处理")
    void recordTreatsUnknownUserAsNonAdmin() throws Exception {
        when(userCacheService.getUserById(CURRENT_USER_ID)).thenReturn(null);
        when(postAuditService.getAuditRecord(anyLong(), anyBoolean(), anyLong()))
                .thenReturn(new PostAuditResponse());

        mockMvc.perform(get("/api/posts/audit/{postId}/record", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk());

        verify(postAuditService).getAuditRecord(CURRENT_USER_ID, false, POST_ID);
    }

    @Test
    @DisplayName("重新提交审核：返回提交成功文案")
    void resubmitReturnsMessage() throws Exception {
        mockMvc.perform(post("/api/posts/audit/{postId}/resubmit", POST_ID)
                        .requestAttr("userId", CURRENT_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已重新提交审核"));

        verify(postAuditService).resubmitForAudit(CURRENT_USER_ID, POST_ID);
    }
}
