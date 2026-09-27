package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证历史记录响应
 */
@Data
public class AuthHistoryResponse {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 挑战 ID
     */
    private String challengeId;

    /**
     * 响应设备 ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 3 个候选数字
     */
    private int[] numbers;

    /**
     * ⚠️ 这里**刻意没有** correctNumber。
     * <p>
     * 本 DTO 是给 App 用的（`GET /api/auth/app/history`），而数字匹配模型有一条硬约束：
     * <b>App 永远只能拿到 3 个候选数字，绝不能拿到正确答案</b>。
     * 正确答案只发给发起操作的浏览器 —— 该模型的安全性恰恰建立在
     * 「攻击者无法把数字转达给受害者」之上。
     * <p>
     * 此前这里下发过 correctNumber，而且该接口**不排除 pending 中的挑战**，
     * 意味着 App 可以直接读到当前待处理挑战的答案。核实过 App 侧零引用该字段、
     * 后端内部也只有本 DTO 的转换器在写它，所以直接摘除，无消费方受影响。
     */
    private String status;

    /**
     * 挑战发起时间
     */
    private LocalDateTime requestedAt;

    /**
     * 用户响应时间
     */
    private LocalDateTime respondedAt;
}
