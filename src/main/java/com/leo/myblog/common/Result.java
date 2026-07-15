package com.leo.myblog.common;

import lombok.Data;

/**
 * 统一响应结果
 */
@Data
public class Result<T> {
    
    private Integer code;
    private String message;
    private T data;
    
    public Result() {}
    
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }
    
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }
    
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }
    
    /**
     * 失败响应
     * 注意：错误响应的data字段为null，前端应根据code判断是否成功
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
    
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
    
    /**
     * 失败响应（带默认data值）
     * 用于需要保持data类型一致性的场景
     */
    public static <T> Result<T> errorWithDefault(Integer code, String message, T defaultData) {
        return new Result<>(code, message, defaultData);
    }
}
