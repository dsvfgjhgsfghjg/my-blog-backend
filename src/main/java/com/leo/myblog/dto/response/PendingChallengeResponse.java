package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 待处理挑战（统一结构：两步登录 + 敏感操作）
 * <p>
 * 供 App 主动拉取（{@code GET /api/auth/app/pending-challenges}）与断线补推使用。
 * 手机在兜里、App 在后台时 WebSocket 大概率已断，因此挑战的生成与推送必须解耦：
 * 生成时无条件落库，App 上线后再靠本结构把挑战捡回来。
 * <p>
 * ⚠️⚠️ <b>绝不能包含 correctNumber</b>。
 * 数字匹配（number matching）的安全性来自「攻击者无法把数字转达给受害者」——
 * 浏览器拿 correctNumber 并展示，App 只拿 3 个候选、由用户从网页上读出正确数字。
 * 一旦把正确答案下发给 App，这个机制就退化成 1/3 概率的盲猜。
 * 该约束在 {@link LoginStep2ChallengeResponse} 的类注释里有完整说明，务必遵守。
 */
@Data
public class PendingChallengeResponse {

    /** 挑战目的：两步登录 */
    public static final String PURPOSE_LOGIN = "login";
    /** 挑战目的：敏感操作验证 */
    public static final String PURPOSE_SENSITIVE_OPERATION = "sensitive_operation";

    /** 挑战 ID，App 应答时回传 */
    private String challengeId;

    /** 挑战目的：{@link #PURPOSE_LOGIN} 或 {@link #PURPOSE_SENSITIVE_OPERATION} */
    private String purpose;

    /** 3 个候选数字（0-99），App 展示给用户选择；不包含正确答案的额外信息 */
    private int[] numbers;

    /** 挑战过期时间，<b>Unix 秒</b>（App 端会做 expiresAt * 1000） */
    private Long expiresAt;

    public PendingChallengeResponse() {
    }

    public PendingChallengeResponse(String challengeId, String purpose, int[] numbers, Long expiresAt) {
        this.challengeId = challengeId;
        this.purpose = purpose;
        this.numbers = numbers;
        this.expiresAt = expiresAt;
    }
}
