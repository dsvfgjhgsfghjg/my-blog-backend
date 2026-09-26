package com.leo.myblog.service;

import com.leo.myblog.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两步登录「第二步门禁」测试
 * <p>
 * 两个 step2 接口自身不校验验证码，靠 step1 登记的标记放行。没有这道门时：
 * {@code step2-password} 是无验证码的密码爆破入口，{@code step2-challenge} 可用来
 * 枚举用户名是否存在 / 是否绑定设备。
 * <p>
 * 这两个方法只读写 Redis，不碰数据库，因此可以直接测。
 */
@SpringBootTest
public class LoginStepGateTest {

    private static final String USERNAME = "stepgate-test-user";
    private static final String OTHER_USERNAME = "stepgate-test-other";
    /** TEST-NET-3 段，不会与真实流量撞上 */
    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    @Autowired
    private LoginService loginService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String key(String username, String ip) {
        return "login:step1_passed:" + username + ":" + ip;
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        redisTemplate.delete(key(USERNAME, IP));
        redisTemplate.delete(key(USERNAME, OTHER_IP));
        redisTemplate.delete(key(OTHER_USERNAME, IP));
    }

    @Test
    @DisplayName("没走过第一步验证码时，第二步必须被拒")
    void step2RejectedWithoutStep1() {
        assertThrows(BusinessException.class,
                () -> loginService.requireStep1Passed(USERNAME, IP),
                "没有 step1 标记时必须拒绝，否则验证码可被完全绕过");
    }

    @Test
    @DisplayName("走过第一步验证码后，第二步放行")
    void step2AllowedAfterStep1() {
        loginService.markStep1Passed(USERNAME, IP);
        assertDoesNotThrow(() -> loginService.requireStep1Passed(USERNAME, IP),
                "正常流程不能被误拒，否则所有人都登不进来");
    }

    @Test
    @DisplayName("标记绑定到「用户名 + IP」，换任一维度都不放行")
    void markerIsBoundToUsernameAndIp() {
        loginService.markStep1Passed(USERNAME, IP);

        assertThrows(BusinessException.class,
                () -> loginService.requireStep1Passed(USERNAME, OTHER_IP),
                "换 IP 不应沿用标记");
        assertThrows(BusinessException.class,
                () -> loginService.requireStep1Passed(OTHER_USERNAME, IP),
                "换用户名不应沿用标记 —— 否则一次验证码可解锁任意账号");
    }

    @Test
    @DisplayName("标记是重复可用而非一次性，认证器路径的「重新发送」才不会被挡")
    void markerIsReusableWithinTtl() {
        loginService.markStep1Passed(USERNAME, IP);

        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> loginService.requireStep1Passed(USERNAME, IP),
                    "第 " + (i + 1) + " 次校验就失败的话，挑战推送的重新发送会失效");
        }
    }

    @Test
    @DisplayName("标记带 TTL，不会永久放行")
    void markerExpires() {
        loginService.markStep1Passed(USERNAME, IP);

        Long ttl = redisTemplate.getExpire(key(USERNAME, IP), TimeUnit.SECONDS);
        assertNotNull(ttl, "标记必须带过期时间");
        assertTrue(ttl > 0 && ttl <= 300, "TTL 应在有效期窗口内，实际为 " + ttl);
    }
}
