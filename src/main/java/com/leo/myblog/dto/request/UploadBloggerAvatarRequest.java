package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传博主头像请求
 */
@Data
public class UploadBloggerAvatarRequest {
    
    /**
     * 头像图片文件
     */
    @NotNull(message = "头像文件不能为空")
    private MultipartFile file;
}
