package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UA黑名单实体类
 */
@Data
@TableName("t_ua_blacklist")
public class UaBlacklist {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * UA匹配模式（支持部分匹配）
     */
    private String pattern;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 威胁等级：LOW/MEDIUM/HIGH/CRITICAL
     */
    private String threatLevel;
    
    /**
     * 动作类型：BLOCK/HONEYPOT
     */
    private String actionType;
    
    /**
     * 是否启用
     */
    private Boolean isEnabled;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
