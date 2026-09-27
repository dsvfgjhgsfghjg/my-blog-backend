package com.leo.myblog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PublicPaths} 的回归测试（纯单测，不起 Spring 上下文，不依赖 Redis / PostgreSQL / MinIO）。
 *
 * <p>为什么值得单独钉住：{@code PublicPaths.isPublic} 是<b>授权判定</b>，
 * 且被三个地方共用（{@link SecurityConfig} 的 permitAll 规则、
 * {@link JwtAuthenticationFilter} 与 {@link OAuth2AuthenticationFilter} 的
 * {@code shouldNotFilter}）。这里的判定出错不会抛异常，只会<b>静默放行</b>；
 * 而放行一条本该受保护的路径，等于跳过 JWT 解析，请求上就没有 {@code userId} 属性，
 * 轻则 {@code AdminAspect} 抛「未认证」，重则 {@code @RequestAttribute} 绑定失败。
 *
 * <p>因此下面每一组都是「正例 + 相邻反例」成对出现：新增或修改规则时，
 * 反例就是防止有人顺手把方法维度或 {@code \\d+} 正则拆掉的钉子。
 */
class PublicPathsTest {

    /** 构造一个不带 context-path 的请求（本应用未配置 context-path） */
    private static MockHttpServletRequest req(String method, String requestUri) {
        return new MockHttpServletRequest(method, requestUri);
    }

    /** 断言该请求应被放行；失败时直接指出是哪一条规则没生效 */
    private static void assertPublic(String method, String uri, String why) {
        assertTrue(PublicPaths.isPublic(req(method, uri)),
                "【该放行却被拦】" + method + " " + uri + " —— 期望命中公开规则：" + why);
    }

    /** 断言该请求不得被放行；失败时直接指出是哪一条规则过宽 */
    private static void assertNotPublic(String method, String uri, String why) {
        assertFalse(PublicPaths.isPublic(req(method, uri)),
                "【不该放行却放行】" + method + " " + uri + " —— 该规则过宽：" + why);
    }

    @Test
    @DisplayName("同路径不同方法必须分开：GET 放行，PUT/DELETE/POST 一律不得放行（否则管理员写操作拿不到 userId）")
    void 同路径按方法区分() {
        // —— 正例：这三个 GET 是访客可见的只读接口 ——
        assertPublic("GET", "/api/blogger/info", "博主信息 GET 公开（BloggerInfoController:32）");
        assertPublic("GET", "/api/audio/123", "音频元数据 GET 公开（AudioController:201）");
        assertPublic("GET", "/api/posts/123", "文章详情 GET 公开（PostController:105）");

        // —— 反例：同路径的写操作是 @RequireAdmin，被跳过就是 AdminAspect 抛「未认证或Token无效」 ——
        assertNotPublic("PUT", "/api/blogger/info",
                "同路径 BloggerInfoController:86 是 @RequireAdmin 的 PUT，AdminAspect 从 request 属性读 userId");
        assertNotPublic("PUT", "/api/audio/123",
                "AudioController:211 是 @RequireAdmin 的 PUT，跳过 JWT 解析后 AdminAspect 拿不到 userId");
        assertNotPublic("DELETE", "/api/audio/123",
                "AudioController:227 是 @RequireAdmin 的 DELETE，同理");
        assertNotPublic("PUT", "/api/posts/123",
                "PostController:61 的 @RequestAttribute Long userId 无默认值，被跳过不是 403 而是绑定异常");
        assertNotPublic("DELETE", "/api/posts/123",
                "PostController:84 的 @RequestAttribute Long userId 无默认值，同理");

        // —— 反例：/api/blogger/avatar 是精确匹配，不得按前缀吞掉上传接口 ——
        assertNotPublic("POST", "/api/blogger/avatar/upload",
                "BloggerInfoController:42 是 @RequireAdmin 的头像上传，历史 startsWith 写法会连它一起跳过");
    }

    @Test
    @DisplayName("路径变量必须带 \\d+ 约束：不得吞掉同级的字面量端点（这些端点的 userId 无默认值，被跳过就是 500）")
    void 路径变量不得吞掉同级字面量端点() {
        // —— 正例：纯数字 id 命中 {id:\\d+} ——
        assertPublic("GET", "/api/audio/123", "数字 id 命中 /api/audio/{audioId:\\d+}");
        assertPublic("GET", "/api/posts/123", "数字 id 命中 /api/posts/{postId:\\d+}");

        // —— 反例：去掉 \\d+ 后 {audioId} / {postId} 会匹配这些同级字面量端点 ——
        assertNotPublic("GET", "/api/audio/list",
                "AudioController:255 是 @RequireAdmin 的音频列表，去掉 \\d+ 会被 /api/audio/{audioId} 吞掉");
        assertNotPublic("GET", "/api/posts/categories",
                "PostController:156 的 @RequestAttribute Long userId 无默认值，被吞掉就是 500");
        assertNotPublic("GET", "/api/posts/favorites",
                "PostController:239 的 @RequestAttribute Long userId 无默认值，被跳过则返回 500 而非正常列表");
    }

    @Test
    @DisplayName("前缀规则必须限定方法：GET 放行封面/图片流，POST 上传不得被跳过（否则管理员传封面失败）")
    void 前缀规则不得吞掉子路径上的写操作() {
        // —— 正例 ——
        assertPublic("GET", "/api/blogger/avatar", "博主头像 GET 公开（BloggerInfoController:58）");
        assertPublic("GET", "/api/audio/cover/1.jpg", "专辑封面 GET 公开（AudioController:90）");
        assertPublic("GET", "/api/images/stream/a/b.png", "文章图片 GET 公开（ImageController:93）");
        assertPublic("GET", "/api/audio/stream/123", "音频流 GET 公开（AudioController:142）");

        // —— 反例：这些写操作此前被两个过滤器同时跳过，AdminAspect 必然抛「未认证」 ——
        assertNotPublic("POST", "/api/audio/cover/upload",
                "AudioController:241 是 @RequireAdmin 的上传封面，前缀写法会把 POST 一起放行");
        assertNotPublic("POST", "/api/images/upload",
                "/api/images/upload 不在白名单内，只有 /stream/** 是公开的");
    }

    @Test
    @DisplayName("刷新与撤销必须可达；相邻的 /api/auth2 受保护端点必须拦住（Cookie 失效时仍要能刷新和清 Cookie）")
    void 刷新与撤销必须可达() {
        // —— 正例：这三个是「以自身凭证换/撤 token」，不能要求先有有效 access_token ——
        assertPublic("POST", "/api/auth2/token/refresh",
                "刷新本身就是给过期 access_token 用的，被拦则 Cookie 永远刷不了");
        assertPublic("POST", "/api/auth2/token/revoke",
                "登出要清掉失效 Cookie，被拦则 token 已拉黑而 Cookie 清不掉，形成自锁");
        assertPublic("POST", "/api/auth2/token", "OAuth2 颁发 Token 端点公开（SecurityConfig:163）");

        // —— 反例：收敛时若照抄 startsWith(\"/api/auth2/token\") 的语义，下面这些会被顺带放行 ——
        assertNotPublic("GET", "/api/auth2/token/info",
                "token 信息接口需要认证，精确匹配 /api/auth2/token 不得前缀命中它");
        assertNotPublic("GET", "/api/auth2/user",
                "/auth2/user 下发 abilities，必须认证后才能拿，漏放行等于泄露权限列表");
    }

    @Test
    @DisplayName("其余公开入口仍放行，相邻的受保护端点不得被顺带放行")
    void 其余公开入口与相邻受保护端点() {
        // —— 正例：登录 / 验证码 / App 绑定 / 公开内容 ——
        assertPublic("POST", "/api/auth/login/step1", "两步登录第一步（LoginController:46）");
        assertPublic("POST", "/api/captcha/verify", "滑块验证码不限方法（/api/captcha/**）");
        assertPublic("GET", "/api/public/sayings/hitokoto/random", "名言名句公开（SayingController:38）");
        assertPublic("POST", "/api/auth/app/bind", "App 绑定在设备未绑定时调用（AppAuthController:61）");
        assertPublic("POST", "/api/auth/app/device-secret", "设备码生成公开（AppAuthController:43）");
        assertPublic("GET", "/api/users/avatar/public/9", "公开头像（UserController:402）");

        // —— 反例 ——
        assertNotPublic("GET", "/api/auth/app/status",
                "PublicPaths 里 /api/auth/app/bind 是精确匹配，/api/auth/app/** 其余端点仍需登录");
        assertNotPublic("POST", "/api/auth/app/bind/qrcode",
                "AppAuthController:96 不在白名单内，不得被精确项 /api/auth/app/bind 顺带放行");
        assertNotPublic("GET", "/api/users/avatar/publicXYZ",
                "前缀必须是 /api/users/avatar/public/，去掉末尾斜杠会放宽到同前缀的其它路径");
    }

    @Test
    @DisplayName("配置了 context-path 时也要正确剥离（与 Spring Security 按 servletPath 匹配的口径一致）")
    void 支持contextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ctx/api/posts/123");
        request.setContextPath("/ctx");
        assertTrue(PublicPaths.isPublic(request),
                "requestURI 含 context-path 时应先剥离再匹配，否则公开路径全部失效");
    }
}
