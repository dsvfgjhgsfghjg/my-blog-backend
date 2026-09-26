package com.leo.myblog.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计元数据注解：显式声明"这个操作该怎么记"
 * <p>
 * 与 {@link RequireAdmin} <b>并存、不扩展它</b>：那个管"能不能调"（鉴权），这个管"怎么记"（审计）。
 * 合并会让权限与审计耦合，而且 {@code RequireAdmin} 覆盖的方法里有十来个是纯查询，
 * 表达不了"查询只记 INFO"。此外登录、改密码这类<b>非管理员</b>方法也要审计。
 * <p>
 * 未标注本注解的方法由 {@code AuditMetadataResolver} 自动推导，所以可以只给最关键的模块加注解，
 * 其余渐进覆盖 —— 两者产出的字段结构完全一致。
 * <p>
 * <b>等级不在这里指定</b>：等级由 {@link #action()} 经 {@code AuditLevelResolver} 推出
 * （够拿到账号 → 关键；能毁数据/关防护 → 警告；改东西 → 记录；只看 → 信息）。
 * 这样「等级」只有一处事实来源，避免注解里写了 level 却与实际动作语义矛盾。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 业务模块，写入 module。取值见 {@code com.leo.myblog.common.AuditModule}。
     * 留空则按目标控制器类推导。
     */
    String module() default "";

    /**
     * 操作类型，写入 operation_type，并<b>决定日志等级</b>。
     * 取值见 {@code com.leo.myblog.common.AuditAction}。留空则按方法名推导。
     */
    String action() default "";

    /**
     * 描述模板，支持三种占位：
     * <ul>
     *   <li>{@code {形参名}} —— 取该形参实参，例：{@code {targetUserId}}</li>
     *   <li>{@code {形参名.字段名}} —— 取该形参的一级 getter，例：{@code {request.configKey}}</li>
     *   <li>{@code {0}} / {@code {1}} —— 按位置取</li>
     * </ul>
     * 留空则用「动作中文名 + 操作对象」自动拼。
     * 例：{@code "重置用户 {targetUserId} 的密码"}
     */
    String description() default "";

    /**
     * 指定用哪个形参作为"操作对象"。留空则自动挑选
     * （见 {@code AuditObjectExtractor}：优先 targetUserId/postId/configKey 等白名单，
     * 再看带 {@code @PathVariable}/{@code @RequestParam} 的标量形参）。
     */
    String object() default "";

    /**
     * 是否记录请求参数。对参数体积大或纯噪声的接口可设 false。
     * 注意：无论是否记录参数，脱敏都会执行。
     */
    boolean recordParams() default true;

    /**
     * 成功时是否落库。给高频只读接口降噪用；<b>失败时仍然记录</b>。
     */
    boolean logOnSuccess() default true;
}
