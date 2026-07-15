package com.leo.myblog.service;

import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.messages.Item;
import io.minio.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * MinIO 背景图服务
 * 从 MinIO 加载验证码背景图片
 */
@Slf4j
@Service
public class MinioBackgroundService {
    
    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;
    
    @Value("${minio.access-key:minioadmin}")
    private String accessKey;
    
    @Value("${minio.secret-key:}")
    private String secretKey;
    
    @Value("${minio.bucket:captcha-backgrounds}")
    private String bucketName;
    
    private MinioClient minioClient;
    private List<String> imageKeys = new ArrayList<>();
    private final Random random = new Random();
    
    @PostConstruct
    public void init() {
        try {
            // 初始化 MinIO 客户端
            minioClient = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            
            // 列出所有图片
            loadImagesFromMinio();
            
            log.info("MinIO 背景图服务初始化完成，共加载 {} 张图片", imageKeys.size());
        } catch (Exception e) {
            log.error("MinIO 背景图服务初始化失败", e);
        }
    }
    
    /**
     * 从 MinIO 加载图片列表
     */
    private void loadImagesFromMinio() {
        try {
            log.info("开始从 MinIO 加载背景图片列表: bucket={}", bucketName);
            
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix("bg_")
                            .recursive(true)
                            .build()
            );
            
            int count = 0;
            for (Result<Item> result : results) {
                Item item = result.get();
                // 只处理 .jpg 文件
                if (item.objectName().endsWith(".jpg")) {
                    imageKeys.add(item.objectName());
                    count++;
                    log.debug("发现图片: {} (size={} bytes)", item.objectName(), item.size());
                }
            }
            
            Collections.sort(imageKeys);
            
            log.info("从 MinIO 加载背景图片完成: 共{}张", count);
            
        } catch (Exception e) {
            log.error("从 MinIO 加载图片列表失败: bucket={}, error={}", bucketName, e.getMessage(), e);
        }
    }
    
    /**
     * 获取随机背景图
     */
    public BufferedImage getRandomBackground() {
        if (imageKeys.isEmpty()) {
            log.debug("MinIO 中没有可用的背景图片");
            return null;
        }
        
        int index = random.nextInt(imageKeys.size());
        String objectKey = imageKeys.get(index);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 从 MinIO 获取图片
            log.debug("从 MinIO 读取图片: bucket={}, object={}", bucketName, objectKey);
            
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            
            // 转换为 BufferedImage
            BufferedImage image = ImageIO.read(inputStream);
            inputStream.close();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (image != null) {
                log.debug("成功加载背景图: {} (耗时={}ms, 尺寸={}x{})", 
                        objectKey, duration, image.getWidth(), image.getHeight());
                return image;
            } else {
                log.warn("无法解析图片: {}", objectKey);
                return null;
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("从 MinIO 加载图片失败: bucket={}, object={}, 耗时={}ms, error={}", 
                    bucketName, objectKey, duration, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 获取背景图数量
     */
    public int getImageCount() {
        return imageKeys.size();
    }
}
