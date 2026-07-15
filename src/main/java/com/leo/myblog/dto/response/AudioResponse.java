package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音频信息响应
 */
@Data
public class AudioResponse {
    
    private Long id;
    private String title;
    private String artist;
    private String album;
    private String format;
    private Long fileSize;
    private Integer duration;
    private String coverUrl;
    private Long userId;
    private Boolean isPublic;
    private Long playCount;
    private LocalDateTime createTime;
    
    /**
     * 流式播放URL
     */
    private String streamUrl;
}
