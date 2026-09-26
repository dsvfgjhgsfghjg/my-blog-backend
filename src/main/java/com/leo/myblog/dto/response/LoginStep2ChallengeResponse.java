package com.leo.myblog.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 两步登录：Step2 挑战推送响应
 * <p>
 * 仿微软数字匹配（number matching）：
 * 浏览器展示 {@link #correctNumber}，App 只收到 3 个候选数字，用户挑出与浏览器一致的那个。
 * <p>
 * 关于把 {@code correctNumber} 回给未认证的发起方是否算信息泄漏：
 * 不算。该模型的安全性恰恰来自「攻击者无法把数字转达给受害者」——
 * 攻击者只能看到自己浏览器里的数字，无法让受害者手机上选中它。
 * 但反过来的约束必须严格遵守：<b>App 侧永远只能收到 3 个候选数字，绝不能收到 correctNumber</b>。
 */
@Data
public class LoginStep2ChallengeResponse {

    /**
     * 挑战状态，固定为 queued
     */
    private String status = "queued";

    /**
     * 挑战 ID，前端用它轮询结果
     */
    private String challengeId;

    /**
     * 正确数字（0-99），由发起登录的浏览器展示给用户
     */
    private Integer correctNumber;

    /**
     * 挑战过期时间，<b>Unix 秒</b>（不是毫秒）
     * <p>
     * App 端 ChallengeDialogFragment 会做 {@code expiresAt * 1000}，前端倒计时同理。
     */
    private Long expiresAt;

    /**
     * 已推送到的在线设备名称
     */
    private List<String> deviceNames;

    /**
     * 是否有在线设备
     * <p>
     * 为 false 时前端展示排队态而非报错：挑战已排队，设备稍后上线会自动补推。
     */
    private Boolean devicesOnline;
}
