package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 更新博主信息请求
 */
@Data
public class UpdateBloggerInfoRequest {
    
    /**
     * 博主名字
     */
    @NotBlank(message = "名字不能为空")
    private String name;
    
    /**
     * 头像URL（MinIO对象路径，如: avatars/1/uuid.jpg）
     */
    private String avatar;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * GitHub地址
     */
    private String github;
    
    /**
     * 简介
     */
    private String introduction;
    
    /**
     * 技术栈列表
     */
    private List<String> techStack;
}
