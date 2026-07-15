package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.UploadAudioRequest;
import com.leo.myblog.dto.response.AudioResponse;
import com.leo.myblog.service.AudioService;
import com.leo.myblog.service.AudioUploadService;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 音频控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
public class AudioController {
    
    private final AudioService audioService;
    private final AudioUploadService audioUploadService;
    private final MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    /**
     * 上传音频文件（仅管理员）
     */
    @RequireAdmin
    @PostMapping("/upload")
    public Result<AudioResponse> uploadAudio(@Valid @ModelAttribute UploadAudioRequest request,
                                             @RequestAttribute Long userId) {
        log.info("用户上传音频: userId={}, title={}", userId, request.getTitle());
        
        AudioResponse response = audioUploadService.uploadAudio(
                request.getFile(),
                request.getTitle(),
                request.getArtist(),
                request.getAlbum(),
                request.getCoverImage(),
                request.getIsPublic(),
                userId
        );
        
        return Result.success("音频上传成功", response);
    }
    
    /**
     * 获取随机音频ID
     * 用于前端初始化时随机播放一个音频
     * 公开访问，无需认证
     */
    @GetMapping("/random")
    public Result<Long> getRandomAudioId() {
        log.debug("请求随机音频ID");
        
        Long audioId = audioService.getRandomAudioId();
        
        if (audioId == null) {
            return Result.error(404, "暂无可用音频");
        }
        
        return Result.success(audioId);
    }
    
    /**
     * 获取专辑封面图片
     * 公开访问，无需认证
     * @param objectPath MinIO对象路径（从URL中提取）
     */
    @GetMapping("/cover/**")
    public ResponseEntity<byte[]> getCoverImage(HttpServletRequest request) {
        // 提取路径部分 /api/audio/cover/ 后面的内容
        String requestUri = request.getRequestURI();
        String objectPath = requestUri.replace("/api/audio/cover/", "");
        
        log.debug("请求封面图片: {}", objectPath);
        
        try {
            // 从 MinIO 获取文件
            InputStream inputStream = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );
            
            // 转换为 byte[]
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            inputStream.close();
            
            byte[] imageData = buffer.toByteArray();
            
            if (imageData.length == 0) {
                return ResponseEntity.notFound().build();
            }
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            
            // 根据文件扩展名设置 Content-Type
            String contentType = getImageContentType(objectPath);
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
            headers.setContentLength(imageData.length);
            headers.setCacheControl("public, max-age=86400"); // 缓存1天
            
            return new ResponseEntity<>(imageData, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("获取封面图片失败: {}", objectPath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 流式播放音频（公开访问，支持 Range 请求和断点续传）
     */
    @GetMapping("/stream/{audioId}")
    public ResponseEntity<StreamingResponseBody> streamAudio(
            @PathVariable Long audioId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        
        AudioResponse audio = audioService.getAudioInfo(audioId);
        
        // 确定 Content-Type
        String contentType = getContentType(audio.getFormat());
        
        // 解析 Range 请求头
        Long start = 0L;
        Long end = audio.getFileSize() - 1;
        
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                log.warn("Range 格式错误: {}", rangeHeader);
                start = 0L;
                end = audio.getFileSize() - 1;
            }
        }
        
        // 构建响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        
        // 计算内容长度
        long contentLength = end - start + 1;
        
        // 判断是否是 Range 请求
        if (rangeHeader != null) {
            // 206 Partial Content
            String contentRange = String.format("bytes %d-%d/%d", start, end, audio.getFileSize());
            headers.set(HttpHeaders.CONTENT_RANGE, contentRange);
            headers.setContentLength(contentLength);
            
            StreamingResponseBody streamBody = audioService.streamAudio(audioId, start, end);
            
            return new ResponseEntity<>(streamBody, headers, HttpStatus.PARTIAL_CONTENT);
        } else {
            // 200 OK
            headers.setContentLength(audio.getFileSize());
            
            StreamingResponseBody streamBody = audioService.streamAudio(audioId, null, null);
            
            return new ResponseEntity<>(streamBody, headers, HttpStatus.OK);
        }
    }
    
    /**
     * 获取音频信息（公开访问）
     */
    @GetMapping("/{audioId}")
    public Result<AudioResponse> getAudioInfo(@PathVariable Long audioId) {
        return Result.success(audioService.getAudioInfo(audioId));
    }
    
    /**
     * 更新音频信息（仅管理员）
     * 可更新字段：title, artist, album, isPublic, coverUrl
     */
    @RequireAdmin
    @PutMapping("/{audioId}")
    public Result<AudioResponse> updateAudioInfo(
            @PathVariable Long audioId,
            @Valid @RequestBody com.leo.myblog.dto.request.UpdateAudioRequest request) {
        log.info("管理员更新音频信息: audioId={}, title={}", audioId, request.getTitle());
        
        AudioResponse response = audioService.updateAudioInfo(audioId, request);
        
        return Result.success("音频信息更新成功", response);
    }
    
    /**
     * 删除音频（仅管理员）
     * 会同时删除音频文件和封面图片
     */
    @RequireAdmin
    @DeleteMapping("/{audioId}")
    public Result<Void> deleteAudio(@PathVariable Long audioId) {
        log.info("管理员删除音频: audioId={}", audioId);
        
        audioService.deleteAudio(audioId);
        
        return Result.success("音频删除成功", null);
    }
    
    /**
     * 上传专辑封面图片（仅管理员）
     * 上传后自动关联到指定音频
     */
    @RequireAdmin
    @PostMapping("/cover/upload")
    public Result<String> uploadCoverImage(
            @Valid @ModelAttribute com.leo.myblog.dto.request.UploadCoverRequest request) {
        log.info("管理员上传专辑封面: audioId={}", request.getAudioId());
        
        String coverUrl = audioService.uploadCoverImage(request.getAudioId(), request.getFile());
        
        return Result.success("封面上传成功", coverUrl);
    }
    
    /**
     * 分页查询音频列表（仅管理员）
     */
    @RequireAdmin
    @GetMapping("/list")
    public Result<Page<AudioResponse>> getAudioList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Boolean isPublic) {
        return Result.success(audioService.getAudioList(pageNum, pageSize, isPublic));
    }
    
    /**
     * 根据文件格式获取 Content-Type
     */
    private String getContentType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        
        return switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }
    
    /**
     * 根据文件扩展名获取图片 Content-Type
     */
    private String getImageContentType(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            return "image/jpeg";
        }
        
        String lowerPath = objectPath.toLowerCase();
        
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        } else {
            // 默认 JPEG
            return "image/jpeg";
        }
    }
}
