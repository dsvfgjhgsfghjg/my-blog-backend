package com.leo.myblog.util;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 程序化背景生成器
 * 用于生成无版权风险的验证码背景图
 */
@Slf4j
public class ProceduralBackgroundGenerator {
    
    private static final int WIDTH = 310;
    private static final int HEIGHT = 155;
    private static final Random random = new Random();
    
    /**
     * 生成随机艺术背景
     */
    public static BufferedImage generateArtisticBackground() {
        // 随机选择一种生成风格
        int style = random.nextInt(4);
        
        switch (style) {
            case 0:
                return generateGradientWithShapes();
            case 1:
                return generateAbstractWaves();
            case 2:
                return generateGeometricPattern();
            case 3:
                return generateNatureInspired();
            default:
                return generateGradientWithShapes();
        }
    }
    
    /**
     * 风格1：渐变 + 几何形状
     */
    private static BufferedImage generateGradientWithShapes() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制渐变背景
        Color color1 = generateRandomColor(100, 200);
        Color color2 = generateRandomColor(50, 150);
        GradientPaint gradient = new GradientPaint(0, 0, color1, WIDTH, HEIGHT, color2);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 添加半透明圆形
        for (int i = 0; i < 8; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            int size = random.nextInt(40) + 20;
            
            Color circleColor = generateRandomColor(100, 255, 80);
            g2d.setColor(circleColor);
            g2d.fill(new Ellipse2D.Double(x, y, size, size));
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 风格2：抽象波浪
     */
    private static BufferedImage generateAbstractWaves() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 深色背景
        Color bgColor = generateRandomColor(30, 80);
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 绘制波浪线
        g2d.setStroke(new BasicStroke(3));
        for (int wave = 0; wave < 5; wave++) {
            Color waveColor = generateRandomColor(100, 255, 100);
            g2d.setColor(waveColor);
            
            int baseY = 30 + wave * 25;
            int[] xPoints = new int[WIDTH];
            int[] yPoints = new int[WIDTH];
            
            for (int x = 0; x < WIDTH; x++) {
                xPoints[x] = x;
                yPoints[x] = (int)(baseY + Math.sin(x * 0.05 + wave) * 15);
            }
            
            g2d.drawPolyline(xPoints, yPoints, WIDTH);
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 风格3：几何图案
     */
    private static BufferedImage generateGeometricPattern() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 浅色背景
        Color bgColor = generateRandomColor(200, 255);
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 绘制三角形网格
        g2d.setStroke(new BasicStroke(1));
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 8; j++) {
                int x = i * 20;
                int y = j * 20;
                
                Color triColor = generateRandomColor(100, 200, 60);
                g2d.setColor(triColor);
                
                // 绘制小三角形
                int[] xPoints = {x, x + 20, x + 10};
                int[] yPoints = {y, y, y + 17};
                g2d.fillPolygon(xPoints, yPoints, 3);
            }
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 风格4：自然灵感（类似极光效果）
     */
    private static BufferedImage generateNatureInspired() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 深色背景（夜空）
        g2d.setColor(new Color(20, 30, 50));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 绘制光带效果
        for (int band = 0; band < 3; band++) {
            Color bandColor = generateRandomColor(50, 150, 40);
            g2d.setColor(bandColor);
            
            int startY = 40 + band * 30;
            for (int x = 0; x < WIDTH; x += 2) {
                int height = (int)(Math.sin(x * 0.03 + band) * 20 + 30);
                g2d.fillRect(x, startY, 2, height);
            }
        }
        
        // 添加星星点缀
        g2d.setColor(Color.WHITE);
        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT / 2);
            g2d.fillOval(x, y, 2, 2);
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 生成随机颜色（RGB模式）
     */
    private static Color generateRandomColor(int min, int max) {
        int r = random.nextInt(max - min + 1) + min;
        int g = random.nextInt(max - min + 1) + min;
        int b = random.nextInt(max - min + 1) + min;
        return new Color(r, g, b);
    }
    
    /**
     * 生成随机颜色（带透明度）
     */
    private static Color generateRandomColor(int min, int max, int alpha) {
        int r = random.nextInt(max - min + 1) + min;
        int g = random.nextInt(max - min + 1) + min;
        int b = random.nextInt(max - min + 1) + min;
        return new Color(r, g, b, alpha);
    }
}
