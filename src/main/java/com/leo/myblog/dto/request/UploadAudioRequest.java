package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 音频上传请求
 */
@Data
public class UploadAudioRequest {
    
    /**
     * 音频文件（必填）
     */
    @NotNull(message = "音频文件不能为空")
    private MultipartFile file;
    
    /**
     * 音频标题（必填）
     */
    @NotBlank(message = "音频标题不能为空")
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
     * 封面图片
     */
    private MultipartFile coverImage;
    
    /**
     * 是否公开
     */
    private Boolean isPublic = true;
}
