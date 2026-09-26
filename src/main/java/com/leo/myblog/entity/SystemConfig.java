package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体
 */
@Data
@TableName("t_system_config")
public class SystemConfig {
    
    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 配置键（唯一）
     */
    private String configKey;
    
    /**
     * 配置值
     */
    private String configValue;

    /**
     * 默认值（重置配置时使用，null 表示该配置不可重置）
     */
    private String defaultValue;

    /**
     * 配置类型：STRING, NUMBER, BOOLEAN, JSON
     */
    private String configType;
    
    /**
     * 配置描述
     */
    private String description;
    
    /**
     * 配置分类：BASIC, SECURITY, EMAIL, STORAGE, POST, COMMENT等
     */
    private String category;
    
    /**
     * 是否公开（前端可见）
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
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
