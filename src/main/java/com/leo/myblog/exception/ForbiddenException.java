package com.leo.myblog.exception;

import lombok.EqualsAndHashCode;

/**
 * 权限拒绝异常：已认证但无权执行该操作。
 *
 * <p>存在的意义是把「权限拒绝」从 {@link BusinessException} 的默认构造里摘出来，
 * 好在 {@link GlobalExceptionHandler} 里被单独识别并映射成 <b>HTTP 403</b>。
 * 改造前它走 {@code BusinessException(String)} 的默认 code 500，于是：
 * <ul>
 *   <li>前端 {@code utils/request.ts} 的 403 分支永远不命中（它读的是 HTTP status）；</li>
 *   <li>全局异常处理器按 {@code code >= 500} 分级，每一次越权尝试都打成 ERROR，淹没真实故障；</li>
 *   <li>{@code t_audit_log.response_status} 记成 500，和真实故障无法区分。</li>
 * </ul>
 *
 * <p><b>为什么不直接用 {@code new BusinessException(403, msg)}</b>：那样改 {@code GlobalExceptionHandler}
 * 只能按 code == 403 判定，会连带把 {@code PostServiceImpl} 里
 * 「文章不存在或无权访问」「文章正在审核中，暂不可见」（同样是 code 403，但属于内容可见性规则）
 * 也变成 HTTP 403 —— 那些接口在公开的文章详情页上，前端读的是
 * {@code error.message}（HTTP 错误下是 "Request failed with status code 403"）而非
 * {@code error.response.data.message}，页面逻辑会失配。用子类把「谁该变」收敛到显式声明的一处。
 *
 * <p>继承 {@link BusinessException} 是刻意的：既有那些
 * {@code catch (BusinessException e)} / {@code instanceof BusinessException}
 * （见 {@code aspect/AuditLogAspect} 与 {@code aspect/audit/AuditLevelResolver}）
 * 的判定与 {@code getCode()} 取值都不必改，审计日志会自动记下 403。
 */
@EqualsAndHashCode(callSuper = true)
public class ForbiddenException extends BusinessException {

    /**
     * 权限拒绝的业务码。
     * <p>它同时是响应体里的 {@code code} 与 HTTP 状态码，两者保持一致，
     * 避免出现「body 说 403、status 说 200」这种前端要分两处判断的契约。
     */
    public static final int CODE = 403;

    public ForbiddenException(String message) {
        super(CODE, message);
    }
}
