package com.leo.myblog.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JWT 签名密钥的启动期校验
 * <p>
 * 纯单元测试，不起 Spring 容器。这个校验的价值在于「用公开默认密钥启动」和
 * 「密钥为空/过短」这两类事故都会**静默**发生 —— 前者让任何人可以伪造 admin token，
 * 后者要到第一次签发 token 才抛异常、表现为一整片 500。所以必须有测试钉住。
 */
class JwtUtilSecretTest {

    /** 32 字节以上，能通过长度检查 */
    private static final String GOOD_SECRET = "a-sufficiently-long-secret-for-tests-0123456789";

    private JwtUtil build(String secret, boolean prodProfile) {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(prodProfile);

        JwtUtil jwtUtil = new JwtUtil(environment);
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        return jwtUtil;
    }

    @Test
    @DisplayName("密钥为空/空白时拒绝启动")
    void rejectsBlankSecret() {
        // 对应 JWT_SECRET= （注入了一个空串）这种事故：现在它会让应用带到运行时才炸
        assertThrows(IllegalStateException.class, () -> build(null, false).validateSecret());
        assertThrows(IllegalStateException.class, () -> build("", false).validateSecret());
        assertThrows(IllegalStateException.class, () -> build("   ", false).validateSecret());
    }

    @Test
    @DisplayName("密钥不足 32 字节时拒绝启动")
    void rejectsShortSecret() {
        // 注意：application.yml 里那个默认字面量有 57 字节，长度检查拦不住它，
        // 所以它由下面那条 prod 检查负责
        assertThrows(IllegalStateException.class, () -> build("short", false).validateSecret());
        assertThrows(IllegalStateException.class, () -> build("0123456789".repeat(3), false).validateSecret());
    }

    @Test
    @DisplayName("本地开发（非 prod）用配置文件里的默认值可以正常启动")
    void allowsDefaultSecretOutsideProd() {
        // 本地开发与 @SpringBootTest 都没有 JWT_SECRET 环境变量，
        // 无条件拒绝会把全部集成测试打挂 —— 所以这条必须通过
        assertDoesNotThrow(() -> build(GOOD_SECRET, false).validateSecret());
    }

    @Test
    @DisplayName("prod 环境下必须由环境变量提供密钥")
    void rejectsDefaultSecretInProd() {
        // 前提：本测试进程没有设 JWT_SECRET。若外部环境恰好设了，这条会自动不成立，
        // 因此下面加了守卫，避免在设了该变量的机器上变成假阴性
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv("JWT_SECRET") == null || System.getenv("JWT_SECRET").isBlank(),
                "本机设置了 JWT_SECRET，跳过（该用例的前提不成立）");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> build(GOOD_SECRET, true).validateSecret());
        assertTrue(e.getMessage().contains("JWT_SECRET"),
                "报错信息要指明该设哪个环境变量，实际: " + e.getMessage());
    }
}
