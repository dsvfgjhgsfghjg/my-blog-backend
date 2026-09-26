package com.leo.myblog.aspect.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.request.LoginRequest;
import com.leo.myblog.dto.request.OAuth2TokenRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求参数脱敏测试
 * <p>
 * 回归目标：改造前切面只判断**形参名**是否含 "password"，而登录方法的形参名是 {@code request}，
 * 判断永远不成立，于是整个 DTO 被序列化进 {@code request_params} —— 密码、clientSecret、
 * refreshToken 明文入库。这里用真实的登录 DTO 验证修复。
 */
public class AuditParamSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditParamSanitizer sanitizer = new AuditParamSanitizer(objectMapper);

    /** 造一个带 @RequestAttribute 的形参，用于验证操作者参数被跳过 */
    @SuppressWarnings("unused")
    private void operatorParamSample(@RequestAttribute Long userId, String keyword) {
    }

    @Test
    @DisplayName("登录 DTO 的密码必须被打码（形参名叫 request 也要打）")
    void masksPasswordInsideRequestBody() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("SuperSecret123!");
        request.setCaptchaNonceStr("nonce-1");

        // 形参名就是 "request" —— 改造前正是因为这个名字不含 password 而漏掉
        String json = sanitizer.sanitize(new Object[]{request}, new String[]{"request"}, null);

        assertFalse(json.contains("SuperSecret123!"), "密码明文入库了：" + json);
        assertTrue(json.contains(AuditParamSanitizer.MASK), "密码字段应替换为掩码：" + json);
        // 非敏感字段必须保留，否则审计失真
        assertTrue(json.contains("alice"), "用户名不该被抹掉：" + json);
        assertTrue(json.contains("nonce-1"), "验证码 nonce 不属于凭据，应保留：" + json);
    }

    @Test
    @DisplayName("OAuth2 令牌请求的密码/授权码/客户端密钥/刷新令牌全部打码")
    void masksOAuth2Credentials() {
        OAuth2TokenRequest request = new OAuth2TokenRequest();
        request.setUsername("alice");
        request.setPassword("p@ssw0rd");
        request.setCode("auth-code-xyz");
        request.setClientId("myblog-web");
        request.setClientSecret("cs-very-secret");
        request.setRefreshToken("rt-abcdef");

        String json = sanitizer.sanitize(new Object[]{request}, new String[]{"request"}, null);

        for (String secret : new String[]{"p@ssw0rd", "auth-code-xyz", "cs-very-secret", "rt-abcdef"}) {
            assertFalse(json.contains(secret), "凭据 " + secret + " 未被打码：" + json);
        }
        // clientId 不是凭据，保留它便于审计核对是哪个客户端在换令牌
        assertTrue(json.contains("myblog-web"), "clientId 应保留：" + json);
    }

    @Test
    @DisplayName("验证码轨迹降采样，不再灌进几千个坐标点")
    void downsamplesCaptchaTrajectory() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setCaptchaTrajectory(java.util.List.of(
                new LoginRequest.TrajectoryPointDTO(), new LoginRequest.TrajectoryPointDTO(),
                new LoginRequest.TrajectoryPointDTO()));

        String json = sanitizer.sanitize(new Object[]{request}, new String[]{"request"}, null);

        assertTrue(json.contains("3 点轨迹"), "轨迹应被降采样为点数摘要：" + json);
    }

    @Test
    @DisplayName("@RequestAttribute 形参（操作者）不写入请求参数")
    void skipsOperatorParams() throws Exception {
        Method method = AuditParamSanitizerTest.class
                .getDeclaredMethod("operatorParamSample", Long.class, String.class);
        Annotation[][] annotations = method.getParameterAnnotations();

        String json = sanitizer.sanitize(new Object[]{123L, "标题"}, new String[]{"userId", "keyword"}, annotations);

        assertFalse(json.contains("123"), "操作者的 userId 不该出现在请求参数里（已有独立列）：" + json);
        assertTrue(json.contains("标题"), "普通参数应保留：" + json);
    }

    @Test
    @DisplayName("超长参数整体换成合法的截断说明，而不是把 JSON 砍成半截")
    void truncatesToValidJson() throws Exception {
        // 单个值会被逐值截到 256 字符，所以要多参数才能突破总量上限
        int count = 60;
        Object[] args = new Object[count];
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = "x".repeat(250);
            names[i] = "field" + i;
        }

        String json = sanitizer.sanitize(args, names, null);

        assertTrue(json.contains("_truncated"), "应给出截断说明：" + json);
        // 关键：仍是合法 JSON，前端 JSON.parse 不会炸
        assertTrue(objectMapper.readTree(json).isObject(), "截断后必须仍是合法 JSON：" + json);
    }

    @Test
    @DisplayName("任何情况下输出都是合法 JSON")
    void alwaysValidJson() throws Exception {
        assertTrue(objectMapper.readTree(sanitizer.sanitize(new Object[]{}, new String[]{}, null)).isObject());
        assertTrue(objectMapper.readTree(sanitizer.sanitize(null, null, null)).isObject());
        assertTrue(objectMapper.readTree(sanitizer.sanitize(new Object[]{null}, new String[]{"x"}, null)).isObject());
    }
}
