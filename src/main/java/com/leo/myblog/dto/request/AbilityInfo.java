package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 能力信息（用于批量设置）
 */
@Data
public class AbilityInfo {
    
    /**
     * 能力名称（如：管理用户、编辑文章、管理系统配置）
     */
    @NotBlank(message = "能力名称不能为空")
    private String name;
    
    /**
     * 能力描述
     */
    private String description;
    
    /**
     * 图标URL
     */
    private String icon;
    
    /**
     * 排序
     */
    private Integer sortOrder = 0;
}
