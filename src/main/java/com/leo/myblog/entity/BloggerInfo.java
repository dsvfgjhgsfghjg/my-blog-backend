package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博主信息实体
 */
@Data
@TableName("t_blogger_info")
public class BloggerInfo {
    
    /**
     * 主键ID（只有一条记录，固定为1）
     */
    @TableId(type = IdType.INPUT)
    private Long id;
    
    /**
     * 博主名字
     */
    private String name;
    
    /**
     * 头像URL（MinIO对象路径）
     */
    private String avatar;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * GitHub地址
     */
    private String github;
    
    /**
     * 简介
     */
    private String introduction;
    
    /**
     * 技术栈（JSON格式存储，方便扩展）
     * 例如: ["Java", "Spring Boot", "Vue", "MySQL"]
     */
    private String techStack;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
