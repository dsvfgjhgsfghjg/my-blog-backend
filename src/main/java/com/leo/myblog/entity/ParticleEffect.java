package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 粒子特效配置实体（白名单）
 */
@Data
@TableName("t_particle_effect")
public class ParticleEffect {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 特效代码（唯一标识）
     */
    private String effectCode;
    
    /**
     * 特效名称（中文显示）
     */
    private String effectName;
    
    /**
     * 特效描述
     */
    private String description;
    
    /**
     * 是否启用
     */
    private Boolean isEnabled;
    
    /**
     * 排序
     */
    private Integer sortOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
