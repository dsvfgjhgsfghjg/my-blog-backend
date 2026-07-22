package com.leo.myblog.enums;

/**
 * 登录方式枚举
 */
public enum LoginMethod {
    
    /**
     * 密码登录（默认方式）
     */
    PASSWORD("password", "密码登录"),
    
    /**
     * App 三选一挑战推送登录
     */
    AUTHENTICATOR_APP("authenticator_app", "通过 Authenticator App 验证");
    
    private final String value;
    private final String description;
    
    LoginMethod(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据值获取枚举
     */
    public static LoginMethod fromValue(String value) {
        for (LoginMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        return PASSWORD;
    }
}