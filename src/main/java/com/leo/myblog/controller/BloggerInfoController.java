package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.UpdateBloggerInfoRequest;
import com.leo.myblog.dto.response.BloggerInfoResponse;
import com.leo.myblog.service.BloggerInfoService;
import com.leo.myblog.util.ImageContentTypeUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     *
     * <p>Content-Type 按对象路径的实际扩展名推导 —— 此前硬编码为 image/jpeg，
     * 导致 GIF 头像「字节是 GIF、声明是 JPEG」而无法播放。
     */
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getBloggerAvatar() {
        try {
            BloggerInfoService.AvatarContent avatar = bloggerInfoService.getBloggerAvatar();

            if (avatar == null) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            // 位图 → inline（GIF 可正常播放），SVG 与未知格式 → attachment
            ImageContentTypeUtils.applyImageHeaders(headers, avatar.objectPath(),
                    ImageContentTypeUtils.Disposition.AUTO);
            headers.setContentLength(avatar.data().length);
            headers.setCacheControl("public, max-age=86400"); // 缓存1天

            return new ResponseEntity<>(avatar.data(), headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("获取博主头像失败", e);
            return ResponseEntity.status(500).build();
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
