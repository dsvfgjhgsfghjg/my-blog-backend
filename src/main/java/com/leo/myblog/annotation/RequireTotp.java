package com.leo.myblog.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要 TOTP 双因素认证的注解
 * 用于标记需要二次验证的敏感操作
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireTotp {
    
    /**
     * 操作描述（用于日志）
     */
    String operation() default "";
    
    /**
     * TOTP 模块配置键（可选）
     * 如果指定，则根据系统配置动态决定是否要求 TOTP
     * 例如: security.totp.user_management
     * 如果不指定，则始终要求 TOTP
     */
    String module() default "";
}
