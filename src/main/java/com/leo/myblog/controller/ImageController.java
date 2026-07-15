package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.ImageUploadResponse;
import com.leo.myblog.service.ImageUploadService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 文章图片控制器
 * <p>
 * 提供图片上传和图片访问功能：
 * <ul>
 *   <li>POST /api/images/upload — 上传文章内嵌图片（需要身份认证）</li>
 *   <li>POST /api/images/cover/upload — 上传文章封面图片（需要身份认证）</li>
 *   <li>GET /api/images/stream/** — 获取图片（公开访问，通过 MinIO 代理）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageUploadService imageUploadService;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 上传文章图片
     * <p>
     * 需要身份认证（登录用户）。前端通过 Ctrl+V 粘贴或文件选择上传。
     *
     * @param file   图片文件（multipart/form-data）
     * @param userId 用户 ID（从 JWT 过滤器注入）
     * @return 图片上传响应（含可公开访问的 URL）
     */
    @PostMapping("/upload")
    public Result<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file,
                                                   @RequestAttribute Long userId) {
        log.info("用户上传文章图片: userId={}, fileName={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        ImageUploadResponse response = imageUploadService.uploadImage(file, userId);

        return Result.success("上传成功", response);
    }

    /**
     * 上传文章封面图片
     * <p>
     * 需要身份认证（登录用户）。上传后返回封面图片 URL，
     * 前端需将此 URL 传入创建/更新文章的 coverImage 字段。
     * <p>
     * 文件限制：jpg/png/gif/webp/svg，最大 2MB
     *
     * @param file   封面图片文件（multipart/form-data）
     * @param userId 用户 ID（从 JWT 过滤器注入）
     * @return 图片上传响应（含可公开访问的封面 URL）
     */
    @PostMapping("/cover/upload")
    public Result<ImageUploadResponse> uploadCoverImage(@RequestParam("file") MultipartFile file,
                                                        @RequestAttribute Long userId) {
        log.info("用户上传文章封面: userId={}, fileName={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        ImageUploadResponse response = imageUploadService.uploadCoverImage(file, userId);

        return Result.success("封面上传成功", response);
    }

    /**
     * 获取文章图片（代理 MinIO）
     * <p>
     * 公开访问，无需认证。前端通过此接口直接显示图片。
     * 路径格式：/api/images/stream/images/{yyyy}/{MM}/{uuid}.{ext}
     */
    @GetMapping("/stream/**")
    public ResponseEntity<byte[]> getImage(HttpServletRequest request) {
        // 提取 /api/images/stream/ 后面的完整路径
        String requestUri = request.getRequestURI();
        String objectPath = requestUri.substring(
                requestUri.indexOf("/api/images/stream/") + "/api/images/stream/".length()
        );

        log.debug("请求文章图片: {}", objectPath);

        // 安全校验：防止目录穿越
        if (objectPath.contains("..") || objectPath.contains("\\")) {
            log.warn("检测到可疑的图片路径: {}", objectPath);
            return ResponseEntity.badRequest().build();
        }

        try {
            // 从 MinIO 获取文件
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .build()
            );

            // 读取全部字节
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
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
            headers.setContentType(MediaType.parseMediaType(getImageContentType(objectPath)));
            headers.setContentLength(imageData.length);
            headers.setCacheControl("public, max-age=31536000"); // 缓存 1 年（图片不变）
            headers.set("X-Content-Type-Options", "nosniff");   // 防止 MIME 嗅探

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageData);

        } catch (MinioException e) {
            log.warn("获取图片失败 (MinIO): {}, message={}", objectPath, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取图片失败: {}", objectPath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getImageContentType(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            return "image/jpeg";
        }

        String lowerPath = objectPath.toLowerCase();

        if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerPath.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "image/jpeg";
        }
    }
}
