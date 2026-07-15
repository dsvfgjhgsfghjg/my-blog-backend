package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章标签实体
 */
@Data
@TableName("t_tag")
public class Tag {
    
    /**
     * 标签ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 标签颜色（可选，用于前端展示）
     */
    private String color;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
