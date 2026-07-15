package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建标签请求
 */
@Data
public class CreateTagRequest {
    
    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称长度不能超过50")
    private String name;
    
    /**
     * 标签颜色（可选，用于前端展示）
     */
    @Size(max = 20, message = "颜色值长度不能超过20")
    private String color;
}
