package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音频文件实体
 */
@Data
@TableName("t_audio")
public class Audio {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 音频标题
     */
    private String title;
    
    /**
     * 艺术家/歌手
     */
    private String artist;
    
    /**
     * 专辑名称
     */
    private String album;
    
    /**
     * 文件路径（MinIO中的路径）
     */
    private String filePath;
    
    /**
     * 文件格式（mp3, flac等）
     */
    private String format;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 时长（秒）
     */
    private Integer duration;
    
    /**
     * 封面图片URL
     */
    private String coverUrl;
    
    /**
     * 上传用户ID
     */
    private Long userId;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 播放次数
     */
    private Long playCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
