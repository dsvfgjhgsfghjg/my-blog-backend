package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章审计记录实体
 */
@Data
@TableName("t_post_audit")
public class PostAudit {
    
    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 文章ID
     */
    private Long postId;
    
    /**
     * 审计员ID（NULL表示未审计）
     */
    private Long auditorId;
    
    /**
     * 审计状态：0-待审核, 1-已通过, 2-已拒绝
     */
    private Integer auditStatus;
    
    /**
     * 审计意见/拒绝原因
     */
    private String auditComment;
    
    /**
     * 审计时间
     */
    private LocalDateTime auditTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
