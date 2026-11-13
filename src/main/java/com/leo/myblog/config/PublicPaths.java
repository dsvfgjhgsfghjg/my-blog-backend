package com.leo.myblog.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * 公开路径（无需认证即可访问）的唯一定义处。
 *
 * <p>历史上这份白名单在三个地方各写了一份，且内容互不一致：
 * {@link SecurityConfig} 的 permitAll 规则、{@link JwtAuthenticationFilter} 的
 * {@code PUBLIC_PATHS}、{@link OAuth2AuthenticationFilter} 的
 * {@code PUBLIC_PATHS + OTHER_PUBLIC_PATHS}。三份清单漂移后产生了实际缺陷：
 * 过滤器先于 Spring Security 的授权规则执行，凡是没被过滤器跳过的公开路径，
 * 只要浏览器里带着一个失效的 Cookie，就会被过滤器直接拦成 403
 * （{@code "登录信息已经失效"}），permitAll 根本没有机会生效 —— 这正是
 * {@code POST /api/auth2/token/refresh}（Cookie 里的 access_token 已过期，
 * 恰恰需要刷新）和 {@code POST /api/auth2/token/revoke}（要清掉失效 Cookie）
 * 当时不可达的原因。
 *
 * <p>因此本类统一维护 {@code (HTTP 方法, 路径模式)} 列表，三处共用：
 * 新增公开接口只需要改这一个文件。
 *
 * <p><b>为什么必须区分方法</b>：跳过 JWT 过滤器意味着请求上不会有
 * {@code userId} 属性，而写操作要么由 {@code AdminAspect} 读该属性、
 * 要么由 {@code @RequestAttribute Long userId}（无默认值）绑定，一旦被跳过
 * 就会直接报错。像 {@code /api/blogger/info}（GET 公开 / PUT 管理员）、
 * {@code /api/audio/{audioId}}（GET 公开 / PUT、DELETE 管理员）、
 * {@code /api/posts/{postId}}（GET 公开 / PUT、DELETE 管理员）这类同路径不同方法的
 * 接口，只能按方法收窄 —— 只写字符串前缀会把管理员写操作一起放进来。
 *
 * <p><b>匹配器选型</b>：本项目 Spring Security 为 6.2.4，没有 6.3 才引入的
 * {@code PathPatternRequestMatcher}，而 {@code AntPathRequestMatcher} 在 6.2 已废弃，
 * 故这里直接用 spring-web 的 {@link PathPatternParser} 编译出
 * {@link PathPattern} 做匹配。
 */
public final class PublicPaths {

    /** 路径模式编译器（无状态，可复用） */
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /**
     * 一条公开路径规则。
     *
     * @param method  允许的 HTTP 方法；{@code null} 表示不限方法（与历史前缀匹配的语义保持一致）
     * @param pattern 已编译的路径模式
     */
    private record Rule(HttpMethod method, PathPattern pattern) {

        boolean matches(HttpServletRequest request) {
            if (method != null && !method.name().equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            return pattern.matches(PathContainer.parsePath(pathWithinApplication(request)));
        }
    }

    /**
     * 全部公开路径。顺序无副作用（命中即公开），但建议按业务分组排列便于核对。
     *
     * <p>注意 {@code /api/auth2/token/refresh} 与 {@code /api/auth2/token/revoke} 必须
     * <b>显式列出</b>：历史实现靠 {@code startsWith("/api/auth2/token")} 顺带覆盖了它们，
     * 收敛时若只照抄“路径”会把“碰巧能用”的刷新/登出改坏。
     */
    private static final List<Rule> RULES = List.of(
            // ==================== 注册 / 登录 / 验证码（登录页必须可达） ====================
            any("/api/users/register"),
            any("/api/users/login"),
            any("/api/auth/login/**"),
            any("/api/captcha/**"),

            // ==================== 健康检查（Docker 探针） ====================
            any("/actuator/health"),

            // ==================== 公开只读内容 ====================
            // 图片/音频流只提供 GET，其写接口挂在别的路径下，故按 GET 收窄
            get("/api/images/stream/**"),
            get("/api/audio/stream/**"),
            any("/api/audio/random"),
            get("/api/audio/cover/**"),

            // 音频元数据 GET 公开，但 PUT/DELETE /api/audio/{audioId} 是 @RequireAdmin。
            // 用 \\d+ 约束路径变量，除了表意准确，还避免误命中 GET /api/audio/list（管理员接口）
            get("/api/audio/{audioId:\\d+}"),

            // 博主信息/头像 GET 公开，PUT /api/blogger/info、POST /api/blogger/avatar/upload 是 @RequireAdmin
            get("/api/blogger/info"),
            get("/api/blogger/avatar"),

            // 文章详情 GET 公开，PUT/DELETE /api/posts/{postId} 是 @RequireAdmin。
            // \\d+ 约束同时避免误命中 GET /api/posts/categories、GET /api/posts/favorites
            // 这两个需要登录的接口（它们的 @RequestAttribute Long userId 没有默认值）
            get("/api/posts/{postId:\\d+}"),

            any("/api/posts/public"),
            any("/api/posts/category/**"),
            any("/api/homepage/**"),
            any("/api/comments/post"),
            any("/api/users/avatar/public/**"),
            any("/api/system/config/public"),
            any("/api/public/sayings/**"),

            // ==================== OAuth2 / 认证 2.0 ====================
            post("/api/auth2/token"),
            // 刷新与撤销各自以 refresh_token / 待撤销的 token 本身作为凭证，
            // 不能要求先有有效的 access_token —— 那正是这两个端点要解决的问题
            // （access_token 过期时才需要刷新；Cookie 里的 token 失效时仍要能登出并清 Cookie）
            post("/api/auth2/token/refresh"),
            post("/api/auth2/token/revoke"),
            // ==================== App 双因素认证（设备未绑定时调用） ====================
            any("/api/auth/app/bind"),
            any("/api/auth/app/device-secret"),
            any("/ws/app/auth/native")
    );

    private PublicPaths() {
    }

    /**
     * 判断当前请求是否命中公开路径白名单。
     * 过滤器（{@code shouldNotFilter}）与 {@link SecurityConfig} 的 permitAll 规则共用此判定。
     */
    public static boolean isPublic(HttpServletRequest request) {
        for (Rule rule : RULES) {
            if (rule.matches(request)) {
                return true;
            }
        }
        return false;
    }

    /** 不限方法的公开路径 */
    private static Rule any(String pattern) {
        return rule(null, pattern);
    }

    /** 仅 GET 的公开路径 */
    private static Rule get(String pattern) {
        return rule(HttpMethod.GET, pattern);
    }

    /** 仅 POST 的公开路径 */
    private static Rule post(String pattern) {
        return rule(HttpMethod.POST, pattern);
    }

    private static Rule rule(HttpMethod method, String pattern) {
        return new Rule(method, PARSER.parse(pattern));
    }

    /**
     * 去掉 context-path，得到应用内路径（本应用未配置 context-path，等价于 requestURI）。
     * 与 Spring Security 内部按 servletPath 匹配的口径保持一致。
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
