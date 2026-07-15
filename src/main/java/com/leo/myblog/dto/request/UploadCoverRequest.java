package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传专辑封面请求
 */
@Data
public class UploadCoverRequest {
    
    /**
     * 音频ID
     */
    @NotNull(message = "音频ID不能为空")
    private Long audioId;
    
    /**
     * 封面图片文件
     */
    @NotNull(message = "封面图片不能为空")
    private MultipartFile file;
}
