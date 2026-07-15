package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.UpdateBloggerInfoRequest;
import com.leo.myblog.dto.response.BloggerInfoResponse;
import com.leo.myblog.service.BloggerInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 博主信息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/blogger")
@RequiredArgsConstructor
public class BloggerInfoController {
    
    private final BloggerInfoService bloggerInfoService;
    
    /**
     * 获取博主信息（公开接口）
     * 用于首页展示
     */
    @GetMapping("/info")
    public Result<BloggerInfoResponse> getBloggerInfo() {
        log.debug("获取博主信息");
        return Result.success(bloggerInfoService.getBloggerInfo());
    }
    
    /**
     * 上传博主头像（仅管理员）
     */
    @RequireAdmin
    @PostMapping("/avatar/upload")
    public Result<String> uploadBloggerAvatar(
            @Valid @ModelAttribute com.leo.myblog.dto.request.UploadBloggerAvatarRequest request) {
        log.info("管理员上传博主头像");
        
        String avatarUrl = bloggerInfoService.uploadBloggerAvatar(request.getFile());
        
        return Result.success("头像上传成功", avatarUrl);
    }
    
    /**
     * 获取博主头像（公开接口）
     * 安全修复V11: 添加Content-Disposition防止内联执行
     */
    @GetMapping("/avatar")
    public org.springframework.http.ResponseEntity<byte[]> getBloggerAvatar() {
        try {
            byte[] avatarData = bloggerInfoService.getBloggerAvatar();
            
            if (avatarData == null || avatarData.length == 0) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            
            // 设置响应头
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.IMAGE_JPEG); // 默认JPEG
            headers.setContentLength(avatarData.length);
            headers.setCacheControl("public, max-age=86400"); // 缓存1天
            // 安全修复V11: 添加Content-Disposition为attachment，防止浏览器内联执行恶意脚本
            headers.setContentDisposition(
                org.springframework.http.ContentDisposition
                    .attachment()
                    .filename("blogger-avatar.jpg")
                    .build()
            );
            
            return new org.springframework.http.ResponseEntity<>(avatarData, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("获取博主头像失败", e);
            return org.springframework.http.ResponseEntity.status(500).body(null);
        }
    }
    
    /**
     * 更新博主信息（仅管理员）
     */
    @RequireAdmin
    @PutMapping("/info")
    public Result<BloggerInfoResponse> updateBloggerInfo(
            @Valid @RequestBody UpdateBloggerInfoRequest request) {
        log.info("管理员更新博主信息: name={}", request.getName());
        
        BloggerInfoResponse response = bloggerInfoService.updateBloggerInfo(request);
        
        return Result.success("博主信息更新成功", response);
    }
}
