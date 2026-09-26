package com.leo.myblog;

import com.leo.myblog.service.AppAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感操作挑战的一次性消费测试
 * <p>
 * 回归目标：{@code t_auth_history} 中的 approved 记录是持久行。原本 App 应答挑战时会
 * 删掉 Redis 里的挑战（那一步是一次性的），但数据库侧没有任何消费标记，于是同一条历史
 * approved 挑战可以被无限次复用，每次换到一个新的 TOTP 会话 —— 即持有有效会话者可以
 * 绕过 {@code @RequireTotp} 的敏感操作验证。
 * <p>
 * 关于建表：应用的 flyway 是关闭的（{@code spring.flyway.enabled=false}，迁移由宿主侧
 * 的 flyway 镜像执行），因此 CI 的测试库里一张表都没有。这里自建本测试所需的最小表结构，
 * 用到的列与生产经 V29 + V35 建出的表保持一致。
 */
@SpringBootTest
public class ChallengeConsumeTest {

    private static final Long USER_ID = 900000000000000001L;
    private static final Long OTHER_USER_ID = 900000000000000002L;

    @Autowired
    private AppAuthService appAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_user (
                    id       BIGINT PRIMARY KEY,
                    username VARCHAR(64)  NOT NULL,
                    password VARCHAR(128) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_auth_history (
                    id             BIGINT PRIMARY KEY,
                    user_id        BIGINT      NOT NULL REFERENCES t_user(id),
                    device_id      VARCHAR(128),
                    device_name    VARCHAR(256),
                    challenge_id   VARCHAR(64) NOT NULL UNIQUE,
                    status         VARCHAR(16) NOT NULL DEFAULT 'pending',
                    numbers        JSON,
                    correct_number INTEGER,
                    requested_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    responded_at   TIMESTAMP,
                    used_at        TIMESTAMP
                )
                """);

        jdbcTemplate.update("DELETE FROM t_auth_history");
        jdbcTemplate.update("DELETE FROM t_user");
        for (Long id : new Long[]{USER_ID, OTHER_USER_ID}) {
            jdbcTemplate.update("INSERT INTO t_user (id, username, password) VALUES (?, ?, ?)",
                    id, "consume-test-" + id, "x");
        }
    }

    /**
     * 造一条挑战记录
     *
     * @param respondedAt 响应时间；传 null 表示尚未响应（配合 status=pending）
     */
    private String insertChallenge(Long userId, String status, LocalDateTime respondedAt) {
        String challengeId = "test-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO t_auth_history
                            (id, user_id, challenge_id, status, requested_at, responded_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                Math.abs(UUID.randomUUID().getMostSignificantBits()),
                userId, challengeId, status, LocalDateTime.now(), respondedAt);
        return challengeId;
    }

    @Test
    @DisplayName("刚批准的挑战可以消费一次")
    void approvedChallengeCanBeConsumedOnce() {
        String challengeId = insertChallenge(USER_ID, "approved", LocalDateTime.now());
        assertTrue(appAuthService.consumeApprovedChallenge(USER_ID, challengeId),
                "首次消费应当成功，否则敏感操作验证整条链路不可用");
    }

    @Test
    @DisplayName("同一条已批准的挑战不能被消费第二次（重放拦截）")
    void approvedChallengeCannotBeReplayed() {
        String challengeId = insertChallenge(USER_ID, "approved", LocalDateTime.now());
        assertTrue(appAuthService.consumeApprovedChallenge(USER_ID, challengeId));
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, challengeId),
                "同一条挑战第二次消费必须失败，否则 2FA 可被重放绕过");
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, challengeId));
    }

    @Test
    @DisplayName("超出时效窗口的历史批准记录不可消费")
    void staleApprovedChallengeIsRejected() {
        String challengeId = insertChallenge(USER_ID, "approved", LocalDateTime.now().minusMinutes(10));
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, challengeId),
                "10 分钟前的批准已超出消费窗口，不应再能换取 TOTP 会话");
    }

    @Test
    @DisplayName("未批准（pending）的挑战不可消费")
    void pendingChallengeIsRejected() {
        String challengeId = insertChallenge(USER_ID, "pending", null);
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, challengeId));
    }

    @Test
    @DisplayName("不能消费其他用户的挑战")
    void otherUsersChallengeIsRejected() {
        String challengeId = insertChallenge(OTHER_USER_ID, "approved", LocalDateTime.now());
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, challengeId));
    }

    @Test
    @DisplayName("不存在的挑战不可消费")
    void unknownChallengeIsRejected() {
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, "no-such-challenge"));
    }

    @Test
    @DisplayName("空参数不可消费")
    void blankArgumentsAreRejected() {
        assertFalse(appAuthService.consumeApprovedChallenge(null, "any"));
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, null));
        assertFalse(appAuthService.consumeApprovedChallenge(USER_ID, "   "));
    }
}
