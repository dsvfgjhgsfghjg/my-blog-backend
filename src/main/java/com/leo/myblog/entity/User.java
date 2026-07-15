package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@TableName("t_user")
public class User {
    
    /**
     * 用户ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 用户名（唯一）
     */
    private String username;
    
    /**
     * 密码（BCrypt加密）
     */
    private String password;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 手机号码
     */
    private String phone;
    
    /**
     * 电子邮箱
     */
    private String email;
    
    /**
     * 头像URL（MinIO存储）
     */
    private String avatar;
    
    /**
     * 是否为管理员
     */
    private Boolean isAdmin;
    
    /**
     * 是否为超级管理员（可管理能力分配）
     */
    private Boolean isSuperAdmin;
    
    /**
     * 是否启用（软删除用）
     */
    private Boolean enabled;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 上次登录IP地址
     */
    private String lastLoginIp;
    
    /**
     * 上次登录时间
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * 登录次数
     */
    private Long loginCount;
}
