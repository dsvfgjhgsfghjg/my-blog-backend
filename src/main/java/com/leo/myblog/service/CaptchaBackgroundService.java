package com.leo.myblog.service;

import com.leo.myblog.util.ProceduralBackgroundGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 滑块验证码背景图服务
 * 负责加载和管理背景图片库
 * 优先级：MinIO > 本地classpath > 程序生成
 */
@Slf4j
@Service
public class CaptchaBackgroundService {
    
    @Autowired(required = false)
    private MinioBackgroundService minioBackgroundService;
    
    /**
     * 标准画布尺寸（正方形）
     * 旋转验证码要求图片为正方形，以便前端修剪为圆形后旋转验证
     */
    private static final int CANVAS_SIZE = 310;
    
    /**
     * 背景图片缓存
     */
    private List<BufferedImage> backgroundCache = new ArrayList<>();
    
    /**
     * 随机数生成器
     */
    private final Random random = new Random();
    
    /**
     * 初始化：加载所有背景图片
     */
    @PostConstruct
    public void init() {
        loadBackgroundImages();
        log.info("滑块验证码背景图服务初始化完成，共加载 {} 张图片", backgroundCache.size());
    }
    
    /**
     * 从 resources 目录加载背景图片
     */
    private void loadBackgroundImages() {
        try {
            // 从 classpath 加载图片
            String[] imageNames = {
                "bg_001.jpg", "bg_002.jpg", "bg_003.jpg", "bg_004.jpg", "bg_005.jpg",
                "bg_006.jpg", "bg_007.jpg", "bg_008.jpg", "bg_009.jpg", "bg_010.jpg",
                "bg_011.jpg", "bg_012.jpg", "bg_013.jpg", "bg_014.jpg", "bg_015.jpg",
                "bg_016.jpg", "bg_017.jpg", "bg_018.jpg", "bg_019.jpg", "bg_020.jpg"
            };
            
            for (String imageName : imageNames) {
                try {
                    BufferedImage image = loadImageFromClasspath(imageName);
                    if (image != null) {
                        // 调整尺寸为标准正方形尺寸
                        BufferedImage resized = resizeImage(image, CANVAS_SIZE, CANVAS_SIZE);
                        backgroundCache.add(resized);
                        log.debug("加载背景图: {}", imageName);
                    }
                } catch (Exception e) {
                    log.warn("加载背景图失败: {}", imageName, e);
                }
            }
            
            if (backgroundCache.isEmpty()) {
                log.warn("未加载到任何背景图片，将使用程序生成的背景");
            }
            
        } catch (Exception e) {
            log.error("加载背景图片失败", e);
        }
    }
    
    /**
     * 从 classpath 加载图片
     */
    private BufferedImage loadImageFromClasspath(String imageName) {
        try {
            String path = "/static/captcha-backgrounds/" + imageName;
            return ImageIO.read(getClass().getResourceAsStream(path));
        } catch (IOException e) {
            log.warn("无法读取图片: {}", imageName);
            return null;
        }
    }
    
    /**
     * 调整图片尺寸
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        // 创建目标尺寸的图像
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // 设置高质量缩放
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制缩放后的图片
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * 获取随机背景图及其来源
     * 
     * @return 包含背景图和来源信息的对象
     */
    public BackgroundWithSource getRandomBackgroundWithSource() {
        // 1. 优先使用 MinIO 中的图片
        if (minioBackgroundService != null && minioBackgroundService.getImageCount() > 0) {
            BufferedImage minioImage = minioBackgroundService.getRandomBackground();
            if (minioImage != null) {
                log.debug("从云端存储获取背景图");
                return new BackgroundWithSource(minioImage, "cloud_storage");
            }
        }
        
        // 2. 其次使用本地图片库
        if (!backgroundCache.isEmpty()) {
            int index = random.nextInt(backgroundCache.size());
            log.debug("从本地缓存获取背景图（共{}张）", backgroundCache.size());
            return new BackgroundWithSource(backgroundCache.get(index), "local_cache");
        }
        
        // 3. 降级：使用程序生成的艺术背景（无版权风险）
        log.debug("使用程序生成的背景图");
        return new BackgroundWithSource(
            ProceduralBackgroundGenerator.generateArtisticBackground(), 
            "generated"
        );
    }
    
    /**
     * 获取随机背景图（兼容旧接口）
     * 
     * @return 背景图，如果没有可用图片则使用程序生成
     */
    public BufferedImage getRandomBackground() {
        return getRandomBackgroundWithSource().getImage();
    }
    
    /**
     * 背景图及来源信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BackgroundWithSource {
        private BufferedImage image;
        private String sourceHint;
    }
    
    /**
     * 获取背景图数量
     */
    public int getBackgroundCount() {
        return backgroundCache.size();
    }
}
