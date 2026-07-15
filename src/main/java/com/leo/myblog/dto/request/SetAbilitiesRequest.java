package com.leo.myblog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量设置管理员能力请求
 */
@Data
public class SetAbilitiesRequest {
    
    /**
     * 目标用户ID
     */
    private Long userId;
    
    /**
     * 能力列表（一次性设置多个能力）
     */
    @Valid
    @NotEmpty(message = "能力列表不能为空")
    private List<AbilityInfo> abilities;
}
