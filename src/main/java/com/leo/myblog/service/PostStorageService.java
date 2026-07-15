package com.leo.myblog.service;

import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 文章内容存储服务
 * 使用 MinIO 对象存储管理文章 Markdown 内容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostStorageService {
    
    private final MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    /**
     * 上传文章内容到 MinIO
     * 
     * @param postId 文章ID
     * @param content Markdown 内容
     * @return MinIO 对象路径
     */
    public String uploadContent(Long postId, String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("文章内容不能为空");
        }
        
        try {
            // 生成唯一的对象路径: posts/{postId}/{uuid}.md
            String uuid = UUID.randomUUID().toString();
            String objectPath = String.format("posts/%d/%s.md", postId, uuid);
            
            // 将内容转换为字节流
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            
            // 上传到 MinIO
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(inputStream, contentBytes.length, -1)
                    .contentType("text/markdown; charset=utf-8")
                    .build();
            
            minioClient.putObject(args);
            
            log.info("文章内容上传成功: postId={}, path={}, size={} bytes", 
                    postId, objectPath, contentBytes.length);
            
            return objectPath;
            
        } catch (Exception e) {
            log.error("上传文章内容失败: postId={}", postId, e);
            throw new RuntimeException("文章内容上传失败", e);
        }
    }
    
    /**
     * 从 MinIO 下载文章内容
     * 
     * @param objectPath MinIO 对象路径
     * @return Markdown 内容
     */
    public String downloadContent(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            throw new IllegalArgumentException("对象路径不能为空");
        }
        
        try {
            // 安全验证：防止目录穿越攻击
            validateObjectPath(objectPath);
            
            // 从 MinIO 获取对象
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build();
            
            InputStream inputStream = minioClient.getObject(args);
            
            // 读取内容
            byte[] bytes = inputStream.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            inputStream.close();
            
            log.debug("文章内容下载成功: path={}, size={} bytes", objectPath, bytes.length);
            
            return content;
            
        } catch (Exception e) {
            log.error("下载文章内容失败: path={}", objectPath, e);
            throw new RuntimeException("文章内容下载失败", e);
        }
    }
    
    /**
     * 删除文章内容
     * 
     * @param objectPath MinIO 对象路径
     */
    public void deleteContent(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            log.warn("对象路径为空，跳过删除");
            return;
        }
        
        try {
            // 安全验证：防止目录穿越攻击
            validateObjectPath(objectPath);
            
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build();
            
            minioClient.removeObject(args);
            
            log.info("文章内容删除成功: path={}", objectPath);
            
        } catch (Exception e) {
            log.error("删除文章内容失败: path={}", objectPath, e);
            // 删除失败不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 更新文章内容（先删除旧内容，再上传新内容）
     * 
     * @param oldObjectPath 旧的对象路径
     * @param postId 文章ID
     * @param newContent 新的 Markdown 内容
     * @return 新的对象路径
     */
    public String updateContent(String oldObjectPath, Long postId, String newContent) {
        // 先删除旧内容
        if (oldObjectPath != null && !oldObjectPath.isEmpty()) {
            deleteContent(oldObjectPath);
        }
        
        // 上传新内容
        return uploadContent(postId, newContent);
    }
    
    /**
     * 验证对象路径，防止目录穿越攻击
     * 
     * @param objectPath 对象路径
     */
    private void validateObjectPath(String objectPath) {
        // 检查是否包含 ".." （目录穿越）
        if (objectPath.contains("..")) {
            throw new SecurityException("非法的对象路径: " + objectPath);
        }
        
        // 检查是否以 "/" 开头
        if (objectPath.startsWith("/")) {
            throw new SecurityException("对象路径不能以 '/' 开头: " + objectPath);
        }
        
        // 只允许字母、数字、下划线、连字符、斜杠和点
        if (!objectPath.matches("^[a-zA-Z0-9_\\-/.]+$")) {
            throw new SecurityException("对象路径包含非法字符: " + objectPath);
        }
        
        // 确保路径在 posts 目录下
        if (!objectPath.startsWith("posts/")) {
            throw new SecurityException("对象路径必须在 posts 目录下: " + objectPath);
        }
    }
}
