package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户偏好设置实体
 */
@Data
@TableName("user_preferences")
public class UserPreference {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 粒子特效模式
     * 可选值由数据库 t_particle_effect 表配置（白名单）
     */
    private String particleMode;
    
    /**
     * 默认登录方式
     * PASSWORD - 密码登录
     * AUTHENTICATOR_APP - 通过 App 挑战推送登录
     */
    private String defaultLoginMethod;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
