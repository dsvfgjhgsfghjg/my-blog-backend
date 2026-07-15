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
     * 正确答案数字
     */
    private Integer correctNumber;

    /**
     * 状态: pending / approved / rejected / expired
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
