package com.leo.myblog;

import com.leo.myblog.service.LoginRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录速率限制服务测试
 * 验证渐进式锁定策略的正确性
 */
@SpringBootTest
public class LoginRateLimitServiceTest {

    @Autowired
    private LoginRateLimitService loginRateLimitService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        cleanupTestData();
    }

    private void cleanupTestData() {
        redisTemplate.delete("login:failures:" + TEST_USERNAME + ":" + TEST_IP);
        redisTemplate.delete("login:lock:" + TEST_USERNAME + ":" + TEST_IP);
    }

    @Test
    @DisplayName("测试第一阶段：前4次失败不锁定")
    void testFirstFourFailuresNoLock() {
        // 模拟前4次失败
        for (int i = 1; i <= 4; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
            
            // 验证未被锁定
            assertTrue(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                    "第" + i + "次失败后不应被锁定");
            
            // 验证失败次数
            assertEquals(i, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        }
        
        System.out.println("✓ 前4次失败未锁定，测试通过");
    }

    @Test
    @DisplayName("测试第一阶段：第5次失败后锁定3分钟")
    void testFifthFailureLocksFor3Minutes() {
        // 模拟前5次失败
        for (int i = 1; i <= 5; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        }
        
        // 验证被锁定
        assertFalse(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第5次失败后应该被锁定");
        
        // 验证剩余时间在合理范围内（170-180秒）
        long remainingTime = loginRateLimitService.getRemainingLockTime(TEST_USERNAME, TEST_IP);
        assertTrue(remainingTime >= 170 && remainingTime <= 180,
                "锁定时间应在170-180秒之间，实际: " + remainingTime);
        
        // 验证失败次数
        assertEquals(5, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        
        System.out.println("✓ 第5次失败后锁定3分钟，测试通过");
    }

    @Test
    @DisplayName("测试第二阶段：第6-7次失败不锁定")
    void testSixthAndSeventhFailuresNoLock() {
        // 先触发第一阶段锁定
        for (int i = 1; i <= 5; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        }
        
        // 手动清除锁，模拟等待锁定结束
        redisTemplate.delete("login:lock:" + TEST_USERNAME + ":" + TEST_IP);
        
        // 第6次失败
        loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        assertTrue(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第6次失败后不应被锁定");
        
        // 第7次失败
        loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        assertTrue(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第7次失败后不应被锁定");
        
        assertEquals(7, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        
        System.out.println("✓ 第6-7次失败未锁定，测试通过");
    }

    @Test
    @DisplayName("测试第二阶段：第8次失败后锁定10分钟")
    void testEighthFailureLocksFor10Minutes() {
        // 模拟前8次失败
        for (int i = 1; i <= 8; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        }
        
        // 验证被锁定
        assertFalse(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第8次失败后应该被锁定");
        
        // 验证剩余时间在合理范围内（590-600秒）
        long remainingTime = loginRateLimitService.getRemainingLockTime(TEST_USERNAME, TEST_IP);
        assertTrue(remainingTime >= 590 && remainingTime <= 600,
                "锁定时间应在590-600秒之间，实际: " + remainingTime);
        
        // 验证失败次数
        assertEquals(8, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        
        System.out.println("✓ 第8次失败后锁定10分钟，测试通过");
    }

    @Test
    @DisplayName("测试第三阶段：第9次及以后每次失败锁定30分钟")
    void testNinthAndLaterFailuresLockFor30Minutes() {
        // 模拟前9次失败
        for (int i = 1; i <= 9; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        }
        
        // 验证被锁定
        assertFalse(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第9次失败后应该被锁定");
        
        // 验证剩余时间在合理范围内（1790-1800秒）
        long remainingTime = loginRateLimitService.getRemainingLockTime(TEST_USERNAME, TEST_IP);
        assertTrue(remainingTime >= 1790 && remainingTime <= 1800,
                "锁定时间应在1790-1800秒之间，实际: " + remainingTime);
        
        assertEquals(9, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        
        // 清除锁，继续测试第10次
        redisTemplate.delete("login:lock:" + TEST_USERNAME + ":" + TEST_IP);
        loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        
        // 验证第10次也被锁定30分钟
        assertFalse(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "第10次失败后应该被锁定");
        
        long remainingTime10 = loginRateLimitService.getRemainingLockTime(TEST_USERNAME, TEST_IP);
        assertTrue(remainingTime10 >= 1790 && remainingTime10 <= 1800,
                "第10次锁定时间应在1790-1800秒之间，实际: " + remainingTime10);
        
        System.out.println("✓ 第9次及以后每次锁定30分钟，测试通过");
    }

    @Test
    @DisplayName("测试成功登录后清除失败计数")
    void testSuccessClearsFailureCount() {
        // 模拟3次失败
        for (int i = 1; i <= 3; i++) {
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
        }
        
        assertEquals(3, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        
        // 模拟成功登录
        loginRateLimitService.recordLoginSuccess(TEST_USERNAME, TEST_IP);
        
        // 验证失败计数被清除
        assertEquals(0, loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP));
        assertTrue(loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP),
                "成功登录后应允许再次登录");
        
        System.out.println("✓ 成功登录后清除失败计数，测试通过");
    }

    @Test
    @DisplayName("测试完整流程：从第1次到第10次失败")
    void testCompleteFlowFrom1To10Failures() {
        System.out.println("\n=== 完整流程测试 ===");
        
        for (int i = 1; i <= 10; i++) {
            // 检查是否允许登录
            boolean allowed = loginRateLimitService.isLoginAllowed(TEST_USERNAME, TEST_IP);
            
            if (!allowed) {
                long remainingTime = loginRateLimitService.getRemainingLockTime(TEST_USERNAME, TEST_IP);
                System.out.println("尝试 " + i + ": 被锁定，剩余时间 " + remainingTime + " 秒");
                
                // 清除锁以继续测试
                redisTemplate.delete("login:lock:" + TEST_USERNAME + ":" + TEST_IP);
            } else {
                System.out.println("尝试 " + i + ": 允许登录");
            }
            
            // 记录失败
            loginRateLimitService.recordLoginFailure(TEST_USERNAME, TEST_IP);
            
            long failureCount = loginRateLimitService.getFailureCount(TEST_USERNAME, TEST_IP);
            System.out.println("  → 失败次数: " + failureCount);
        }
        
        System.out.println("✓ 完整流程测试完成");
    }
}
