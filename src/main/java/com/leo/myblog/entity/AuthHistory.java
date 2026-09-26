package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leo.myblog.config.PostgreJsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App 认证历史实体
 * 记录 App 双因素认证的挑战历史
 */
@Data
@TableName(value = "t_auth_history", autoResultMap = true)
public class AuthHistory {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 响应设备 ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 挑战 ID
     */
    private String challengeId;

    /**
     * 状态: pending / approved / rejected / expired
     */
    private String status;

    /**
     * 3 个候选数字（JSON 格式）
     */
    @TableField(typeHandler = PostgreJsonTypeHandler.class)
    private int[] numbers;

    /**
     * 正确答案数字
     */
    private Integer correctNumber;

    /**
     * 挑战发起时间
     */
    private LocalDateTime requestedAt;

    /**
     * 用户响应时间
     */
    private LocalDateTime respondedAt;

    /**
     * 被敏感操作消费的时间
     * <p>
     * NULL 表示尚未使用（可用于验证）；非 NULL 表示已被某次敏感操作消费，
     * 不可再次用于验证。见 {@code AppAuthService.consumeApprovedChallenge}。
     */
    private LocalDateTime usedAt;
}
