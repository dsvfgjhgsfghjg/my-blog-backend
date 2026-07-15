package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP封禁实体类
 */
@Data
@TableName("t_ip_ban")
public class IpBan {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 封禁原因
     */
    private String reason;
    
    /**
     * 封禁到期时间
     */
    private LocalDateTime banUntil;
    
    /**
     * 触发次数
     */
    private Integer triggerCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
