package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新系统配置请求
 */
@Data
public class UpdateSystemConfigRequest {
    
    /**
     * 配置键
     */
    @NotBlank(message = "配置键不能为空")
    private String configKey;
    
    /**
     * 配置值
     */
    private String configValue;
}
