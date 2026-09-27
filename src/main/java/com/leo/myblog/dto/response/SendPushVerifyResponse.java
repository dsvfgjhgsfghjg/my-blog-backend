package com.leo.myblog.dto.response;
import lombok.Data;
import java.util.List;

/**
 * 敏感操作「发送推送挑战」响应
 * <p>
 * 仿微软数字匹配（number matching）：浏览器展示 {@link #correctNumber}，
 * App 只收到 3 个候选数字，用户挑出与浏览器一致的那个。
 * <p>
 * 与登录流程的 {@code LoginStep2ChallengeResponse} 是同一套模型。此前这里**漏掉了
 * correctNumber**，导致管理后台走 App 推送验证时，页面根本没显示该选哪个数字，
 * 用户只能盲选 —— 这正是「admin 页面用验证器验证时不显示数字」的原因。
 * <p>
 * 安全性说明（同登录流程）：把 correctNumber 回给发起方不算泄漏，该模型的安全性
 * 来自「攻击者无法把数字转达给受害者」—— 攻击者只能看到自己浏览器里的数字，
 * 没法让受害者手机上选中它。但反过来的约束必须严格遵守：
 * <b>推送给 App 的消息里绝不能包含 correctNumber，只能有 3 个候选</b>。
 */
@Data
public class SendPushVerifyResponse {
    private String challengeId;
    private String status;
    private List<String> deviceNames;
    private long expiresAt;

    /** 正确数字（0-99），由发起操作的浏览器展示给用户 */
    private Integer correctNumber;
}
