package com.leo.myblog.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.security.SecureRandom;

/**
 * 旋转验证码工具类
 *
 * 设计说明：
 * - 将图片裁剪为圆形，再随机旋转一定角度（-35° ~ 35°）
 * - 返回给前端的是一个圆形区域内包含图片、圆形区域外透明的旋转后图片
 * - 前端只需直接显示这张图片，下面放一个滑块
 * - 用户滑动滑块旋转图片，使其回正
 * - 服务端验证用户旋转的角度是否与原始旋转角度抵消
 * - 基于行为分析（滑动轨迹、时间）判断是否为真人操作
 * - 允许一定角度偏差（±6°）
 */
@Slf4j
public class SliderCaptchaUtils {

    /**
     * 默认画布尺寸（正方形边长）
     */
    public static final int DEFAULT_CANVAS_SIZE = 310;

    /**
     * 允许的角度偏差（度）
     */
    public static final int ALLOW_ANGLE_DEVIATION = 6;

    /**
     * 最小随机旋转角度（度）
     */
    public static final int MIN_ROTATION = -180;

    /**
     * 最大随机旋转角度（度）
     */
    public static final int MAX_ROTATION = 180;

    private static final Random RANDOM = new SecureRandom();

    /**
     * 生成指定范围内的随机整数
     */
    public static int getRandomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    /**
     * 生成指定范围内的随机浮点数
     */
    public static double getRandomDouble(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }

    /**
     * 生成随机旋转角度（度），确保绝对值不低于 75°
     * 返回值：服务端对原始图片旋转的角度
     * 用户需要将这个角度"旋回"才能看到正向的图片
     */
    public static double generateRandomRotationAngle() {
        double angle;
        // 从 [-180, -75] 或 [75, 180] 两个区间中随机选一个取值
        if (RANDOM.nextBoolean()) {
            // 顺时针旋转：75° ~ 180°
            angle = getRandomDouble(75, 180);
        } else {
            // 逆时针旋转：-180° ~ -75°
            angle = getRandomDouble(-180, -75);
        }
        // 保留一位小数
        return Math.round(angle * 10.0) / 10.0;
    }

    /**
     * 将图片裁剪为圆形（圆形区域内保留图片，区域外透明）
     *
     * @param image 原始图片（必须是正方形）
     * @return 圆形裁剪后的图片（PNG透明格式，尺寸不变）
     */
    public static BufferedImage cropToCircle(BufferedImage image) {
        int size = Math.min(image.getWidth(), image.getHeight());
        BufferedImage circular = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = circular.createGraphics();

        // 高质量抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 设置圆形裁剪区域
        Ellipse2D.Double circle = new Ellipse2D.Double(0, 0, size, size);
        g2d.setClip(circle);

        // 绘制图片到圆形区域内
        g2d.drawImage(image, 0, 0, size, size, null);
        g2d.dispose();

        return circular;
    }

    /**
     * 将图片旋转指定角度（度）
     * 旋转后图片尺寸会放大以容纳旋转内容，多余部分用透明填充
     * 最终输出正方形图片，未被原图覆盖的区域为透明
     *
     * @param image        原始图片（正方形）
     * @param angleDegrees 旋转角度（正值为顺时针）
     * @return 旋转后的正方形图片（PNG透明格式，尺寸可能放大）
     */
    public static BufferedImage rotateImage(BufferedImage image, double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        int w = image.getWidth();
        int h = image.getHeight();

        // 计算旋转后的外接矩形尺寸
        double sin = Math.abs(Math.sin(angleRad));
        double cos = Math.abs(Math.cos(angleRad));
        int newW = (int) Math.ceil(w * cos + h * sin);
        int newH = (int) Math.ceil(h * cos + w * sin);

        // 确保是正方形：取最大值
        int size = Math.max(newW, newH);

        // 创建正方形画布（支持透明）
        BufferedImage rotated = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();

        // 高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 清空为透明
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, size, size);
        g2d.setComposite(AlphaComposite.SrcOver);

        // 执行旋转：以画布中心为旋转中心，将原始图片居中旋转
        AffineTransform transform = new AffineTransform();
        transform.translate(size / 2.0, size / 2.0);
        transform.rotate(angleRad);
        transform.translate(-w / 2.0, -h / 2.0);

        g2d.drawImage(image, transform, null);
        g2d.dispose();

        return rotated;
    }

    /**
     * 将原始图片处理为旋转验证码所需格式：
     * 1. 裁剪为正方形（取短边居中裁剪）
     * 2. 缩放到标准尺寸
     * 3. 裁剪为圆形（圆形区域内保留图片，区域外透明）
     * 4. 随机旋转一定角度
     *
     * @param source       原始图片（可以是任意矩形）
     * @param targetSize   目标尺寸（正方形边长）
     * @param angleDegrees 旋转角度
     * @return 处理后的图片（圆形内容被旋转，透明背景的正方形PNG）
     */
    public static BufferedImage prepareRotationCaptchaImage(BufferedImage source, int targetSize, double angleDegrees) {
        // 1. 裁剪为正方形
        BufferedImage square = cropToSquare(source);
        // 2. 缩放到标准尺寸
        BufferedImage resized = resize(square, targetSize);
        // 3. 裁剪为圆形（区域外透明）
        BufferedImage circular = cropToCircle(resized);
        // 4. 旋转
        return rotateImage(circular, angleDegrees);
    }

    /**
     * 将图片居中裁剪为正方形
     */
    public static BufferedImage cropToSquare(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int min = Math.min(w, h);
        int x = (w - min) / 2;
        int y = (h - min) / 2;
        return image.getSubimage(x, y, min, min);
    }

    /**
     * 将图片缩放到指定尺寸
     */
    public static BufferedImage resize(BufferedImage image, int targetSize) {
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, targetSize, targetSize, null);
        g2d.dispose();
        return resized;
    }

    /**
     * 生成程序化背景图（正方形）
     */
    public static BufferedImage generateBackground() {
        BufferedImage image = new BufferedImage(DEFAULT_CANVAS_SIZE, DEFAULT_CANVAS_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 随机渐变背景
        Color startColor = new Color(getRandomInt(100, 220), getRandomInt(100, 220), getRandomInt(100, 220));
        Color endColor = new Color(getRandomInt(30, 150), getRandomInt(30, 150), getRandomInt(30, 150));
        GradientPaint gradient = new GradientPaint(0, 0, startColor,
                DEFAULT_CANVAS_SIZE, DEFAULT_CANVAS_SIZE, endColor);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, DEFAULT_CANVAS_SIZE, DEFAULT_CANVAS_SIZE);

        // 装饰图案
        drawDecorations(g2d);

        g2d.dispose();
        return image;
    }

    /**
     * 绘制装饰图案（在程序生成的背景上增加随机元素，提升视觉丰富度）
     */
    private static void drawDecorations(Graphics2D g2d) {
        // 绘制随机圆形
        for (int i = 0; i < 8; i++) {
            int x = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            int y = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            int size = getRandomInt(20, 80);
            Color color = new Color(RANDOM.nextInt(255), RANDOM.nextInt(255),
                    RANDOM.nextInt(255), 40);
            g2d.setColor(color);
            g2d.fillOval(x, y, size, size);
        }

        // 绘制随机线条
        for (int i = 0; i < 5; i++) {
            int x1 = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            int y1 = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            int x2 = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            int y2 = RANDOM.nextInt(DEFAULT_CANVAS_SIZE);
            Color color = new Color(RANDOM.nextInt(255), RANDOM.nextInt(255),
                    RANDOM.nextInt(255), 60);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * BufferedImage 转 Base64（PNG格式，支持透明）
     */
    public static String toBase64(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return "data:image/" + format + ";base64," + base64;
        } catch (IOException e) {
            log.error("图片转Base64失败", e);
            return null;
        }
    }
}
