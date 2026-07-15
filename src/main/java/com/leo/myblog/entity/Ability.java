package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员能力实体
 */
@Data
@TableName("t_ability")
public class Ability {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 用户ID（关联 t_user.id）
     */
    private Long userId;
    
    /**
     * 能力名称（如：管理用户、编辑文章）
     */
    private String name;
    
    /**
     * 能力描述
     */
    private String description;
    
    /**
     * 图标URL（存储在MinIO）
     */
    private String icon;
    
    /**
     * 排序（前端展示顺序）
     */
    private Integer sortOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
