package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置响应DTO
 */
@Data
public class SystemConfigResponse {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 配置键
     */
    private String configKey;
    
    /**
     * 配置值
     */
    private String configValue;

    /**
     * 默认值（null 表示该配置不可重置）
     */
    private String defaultValue;

    /**
     * 配置类型
     */
    private String configType;
    
    /**
     * 配置描述
     */
    private String description;
    
    /**
     * 配置分类
     */
    private String category;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 是否可编辑
     */
    private Boolean isEditable;
    
    /**
     * 排序
     */
    private Integer sortOrder;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
