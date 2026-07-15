package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bug 日志上报实体
 * <p>
 * 存储 App 端上报的 Bug 日志元数据
 * 日志内容存储在 MinIO，数据库只存元数据用于查询和管理
 */
@Data
@TableName("t_bug_report")
public class BugReport {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 上报用户 ID
     */
    private Long userId;

    /**
     * App 版本号
     */
    private String appVersion;

    /**
     * 设备型号
     */
    private String deviceModel;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 日志摘要（前 200 字）
     */
    private String logSummary;

    /**
     * MinIO 存储路径
     */
    private String minioPath;

    /**
     * 原始日志大小（字节）
     */
    private Integer logSize;

    /**
     * 压缩后大小（字节）
     */
    private Integer compressedSize;

    /**
     * 状态: unread / read / resolved
     */
    private String status;

    /**
     * 上报时间
     */
    private LocalDateTime createdAt;

    /**
     * 解决时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 解决备注
     */
    private String resolveNote;
}
