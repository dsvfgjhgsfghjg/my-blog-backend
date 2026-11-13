package com.leo.myblog.exception;

import com.leo.myblog.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器的返回契约测试
 * <p>
 * 用 standalone MockMvc（不起 Spring 容器），所以不依赖 PostgreSQL / Redis。
 * <p>
 * 这里钉住的是本次改造的边界，核心是一条容易在后续重构里被"顺手统一掉"的规则：
 * <b>只有 {@link ForbiddenException} 会把 HTTP 状态码置成 403</b>，
 * 其余业务异常（包括显式 {@code new BusinessException(403, ...)} 和默认 code 500）
 * 一律维持 HTTP 200 + body 里带 code 的旧契约。
 * <p>
 * 后半条不是历史包袱，而是有实际依赖：{@code PostServiceImpl} 的
 * 「文章不存在或无权访问」「文章正在审核中，暂不可见」就是显式 code 403 的
 * {@link BusinessException}，它们出现在公开的文章详情页上，而该页面读的是
 * {@code error.message}（HTTP 错误下会变成 "Request failed with status code 403"）。
 * 一旦把 code 403 通用映射成 HTTP 403，那个页面的跳转逻辑就会失配。
 */
class GlobalExceptionHandlerTest {

    /** 探针控制器：只负责按需抛异常，验证异常处理器的映射结果 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/forbidden")
        Result<?> forbidden() {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }

        /** 内容可见性规则：显式 code 403 的普通业务异常，**不能**变成 HTTP 403 */
        @GetMapping("/probe/content-visibility")
        Result<?> contentVisibility() {
            throw new BusinessException(403, "文章不存在或无权访问");
        }

        /** 默认构造：code 500，维持 HTTP 200 */
        @GetMapping("/probe/business")
        Result<?> business() {
            throw new BusinessException("用户名已存在");
        }

        @GetMapping("/probe/token-authentication")
        Result<?> tokenAuthentication() {
            throw new TokenAuthenticationException("refresh_token 无效或已过期");
        }

        /** 评论特例：handleBusinessException 里那段 data 兜底不能被改动 */
        @GetMapping("/probe/comment")
        Result<?> comment() {
            throw new BusinessException("评论不存在");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ForbiddenException：HTTP 403，body 结构与其它分支完全一致")
    void forbiddenMapsToHttp403() throws Exception {
        mockMvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                // 前端 request.ts 的 403 分支读的就是这个 message，必须原样透出
                .andExpect(jsonPath("$.message").value("权限不足，需要管理员权限"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("显式 code 403 的普通业务异常仍是 HTTP 200（文章详情页依赖这个契约）")
    void plainBusinessExceptionWith403StaysHttp200() throws Exception {
        mockMvc.perform(get("/probe/content-visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("文章不存在或无权访问"));
    }

    @Test
    @DisplayName("默认构造的业务异常：HTTP 200 + code 500 不变")
    void defaultBusinessExceptionStaysHttp200() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("TokenAuthenticationException：HTTP 401，body code 也是 401")
    void tokenAuthenticationMapsToHttp401() throws Exception {
        mockMvc.perform(get("/probe/token-authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("refresh_token 无效或已过期"));
    }

    @Test
    @DisplayName("评论特例：仍返回带空 replies 的 data，且不落到 403 分支")
    void commentSpecialCaseUnchanged() throws Exception {
        mockMvc.perform(get("/probe/comment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data.replies").isEmpty());
    }
}
