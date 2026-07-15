package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新音频信息请求
 */
@Data
public class UpdateAudioRequest {
    
    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;
    
    /**
     * 艺术家
     */
    private String artist;
    
    /**
     * 专辑
     */
    private String album;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 封面图片路径（MinIO对象路径）
     */
    private String coverUrl;
}
