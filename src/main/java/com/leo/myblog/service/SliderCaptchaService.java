package com.leo.myblog.service;

import com.leo.myblog.entity.SliderCaptcha;
import com.leo.myblog.util.SliderCaptchaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 旋转验证码服务（替代原有滑块拼图验证码）
 *
 * 新方案 —— 旋转验证码：
 * 1. 服务端从背景库取一张图片，随机旋转一个角度（-35° ~ 35°）
 * 2. 将旋转后的图片返回前端（PNG正方形，透明背景）
 * 3. 前端将图片修剪为圆形，下方提供一个滑块
 * 4. 用户滑动滑块旋转图片使其回正
 * 5. 用户旋转的角度 = 原始旋转角度的反向（即把图片旋正）
 * 6. 服务端验证用户旋转角度是否与原始角度相抵消（允许±6°偏差）
 * 7. 同时进行行为分析（轨迹、时间等）
 *
 * 安全措施：
 * 1. 真实旋转角度仅存储在 Redis，不返回给前端
 * 2. 行为分析：滑动轨迹抖动检测 + 时间阈值
 * 3. 一次性 nonce + 5分钟过期
 * 4. IP 级别速率限制（由 CaptchaRateLimitService 处理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SliderCaptchaService {

    private final StringRedisTemplate redisTemplate;
    private final CaptchaBackgroundService backgroundService;

    /**
     * Redis key 前缀
     */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:rotation:";

    /**
     * 验证码过期时间（5分钟）
     */
    private static final long CAPTCHA_EXPIRE_TIME = 5;

    /**
     * 生成旋转验证码
     *
     * 返回数据中不包含任何旋转角度信息，前端无法推导正确角度。
     * 前端得到一张被旋转过的图片，用户需要手动将其旋正。
     *
     * @return 旋转验证码实体
     */
    public SliderCaptcha generateCaptcha() {
        // 1. 获取背景图及其来源
        CaptchaBackgroundService.BackgroundWithSource backgroundWithSource =
            backgroundService.getRandomBackgroundWithSource();
        BufferedImage background = backgroundWithSource.getImage();
        String imageSourceHint = backgroundWithSource.getSourceHint();

        if (background == null) {
            log.debug("未找到背景图片，使用程序生成的背景");
            background = SliderCaptchaUtils.generateBackground();
            imageSourceHint = "generated";
        }

        // 2. 生成随机旋转角度
        double rotationAngle = SliderCaptchaUtils.generateRandomRotationAngle();
        log.debug("生成随机旋转角度: {}°", rotationAngle);

        // 3. 将图片旋转
        BufferedImage rotatedImage = SliderCaptchaUtils.prepareRotationCaptchaImage(
            background, SliderCaptchaUtils.DEFAULT_CANVAS_SIZE, rotationAngle
        );

        // 4. 转换为 Base64
        String rotatedImageSrc = SliderCaptchaUtils.toBase64(rotatedImage, "png");

        // 5. 生成唯一标识
        String nonceStr = UUID.randomUUID().toString().replace("-", "");

        // 6. 将真实旋转角度存入 Redis（格式: "rotationAngle"）
        String redisKey = CAPTCHA_KEY_PREFIX + nonceStr;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(rotationAngle),
                CAPTCHA_EXPIRE_TIME, TimeUnit.MINUTES);

        // 7. 构建响应对象（不包含任何角度信息）
        SliderCaptcha captcha = new SliderCaptcha();
        captcha.setNonceStr(nonceStr);
        captcha.setRotatedImageSrc(rotatedImageSrc);
        captcha.setCanvasSize(SliderCaptchaUtils.DEFAULT_CANVAS_SIZE);
        captcha.setImageSourceHint(imageSourceHint);

        log.debug("生成旋转验证码: nonceStr={}, source={}", nonceStr, imageSourceHint);

        return captcha;
    }

    /**
     * 验证旋转角度（角度验证 + 行为分析）
     *
     * 核心验证逻辑：
     * - 用户通过滑块将图片旋转了 userAngle 度
     * - 原始图片被服务端旋转了 rotationAngle 度
     * - 如果 userAngle ≈ -rotationAngle，说明图片已被旋正
     * - 允许 ±ALLOW_ANGLE_DEVIATION 度的偏差
     *
     * @param nonceStr      验证码标识
     * @param userAngle     用户旋转的角度（正值为顺时针旋转图片，即滑块向右滑动）
     *                      前端滑块向右/向上滑动为正值，向左/向下为负值
     *                      用户需要旋转的角度大约为 -rotationAngle
     * @param trajectory    滑动轨迹点列表（可选，用于行为分析）
     * @param totalTime     总耗时（毫秒，可选，用于行为分析）
     * @return 是否验证通过
     */
    public boolean verifyCaptcha(String nonceStr, Double userAngle,
                                  java.util.List<TrajectoryPoint> trajectory,
                                  Long totalTime) {
        if (nonceStr == null || userAngle == null) {
            log.warn("验证码参数为空: nonceStr={}", nonceStr);
            return false;
        }

        // 1. 从 Redis 获取验证码数据
        String redisKey = CAPTCHA_KEY_PREFIX + nonceStr;
        String captchaData = redisTemplate.opsForValue().get(redisKey);

        if (captchaData == null) {
            log.warn("验证码已过期或不存在: nonceStr={}", nonceStr);
            return false;
        }

        // 2. 删除已使用的验证码（防止重复使用）
        redisTemplate.delete(redisKey);

        // 3. 解析真实旋转角度
        double rotationAngle;
        try {
            rotationAngle = Double.parseDouble(captchaData);
        } catch (NumberFormatException e) {
            log.error("验证码数据格式错误: nonceStr={}, data={}", nonceStr, captchaData);
            return false;
        }

        // 4. 角度验证
        //    用户旋转角度 + 原始旋转角度 ≈ 0 时，说明图片已被旋正
        //    例如：原始旋转了 20°，用户需要旋转 -20°（向左滑），或提供 -20°的值
        //    计算实际偏移：combinedAngle = rotationAngle + userAngle
        //    理想情况下 combinedAngle ≈ 0
        double combinedAngle = rotationAngle + userAngle;
        double absCombined = Math.abs(combinedAngle);

        // 对角度做归一化处理，处理用户旋转超过360°的情况
        absCombined = normalizeAngle(absCombined);

        boolean anglePassed = absCombined <= SliderCaptchaUtils.ALLOW_ANGLE_DEVIATION;

        // 5. 行为验证（如果提供了轨迹和时间）
        boolean behaviorPassed = true;
        String behaviorInfo = "未提供";

        if (trajectory != null && !trajectory.isEmpty() && totalTime != null) {
            // 5.1 时间验证：太快来不及反应（少于 500ms 视为机器）
            if (totalTime < 500) {
                behaviorPassed = false;
                behaviorInfo = String.format("时间过短: %dms", totalTime);
                log.warn("❌ 旋转验证码验证失败（时间异常）: nonceStr={}, time={}ms", nonceStr, totalTime);
            }
            // 5.2 轨迹验证：至少有 5 个采样点
            else if (trajectory.size() < 5) {
                behaviorPassed = false;
                behaviorInfo = String.format("轨迹点过少: %d个", trajectory.size());
                log.warn("❌ 旋转验证码验证失败（轨迹异常）: nonceStr={}, points={}", nonceStr, trajectory.size());
            }
            // 5.3 轨迹特征验证（检查速度变化）
            else {
                boolean hasJitter = checkTrajectoryJitter(trajectory);
                if (!hasJitter) {
                    behaviorPassed = false;
                    behaviorInfo = "轨迹过于平滑（疑似机器）";
                    log.warn("❌ 旋转验证码验证失败（轨迹无抖动）: nonceStr={}", nonceStr);
                } else {
                    behaviorInfo = String.format("正常: time=%dms, points=%d", totalTime, trajectory.size());
                }
            }
        }

        // 6. 综合判断
        boolean passed = anglePassed && behaviorPassed;

        if (passed) {
            log.info("旋转验证码验证通过: nonceStr={}, behavior={}",
                    nonceStr, behaviorInfo);
        } else {
            log.warn("旋转验证码验证失败: nonceStr={}, anglePassed={}, behaviorPassed={}, info={}",
                    nonceStr, anglePassed, behaviorPassed, behaviorInfo);
        }

        return passed;
    }

    /**
     * 将角度归一化到 [0, 360) 范围
     */
    private double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle < 0) {
            angle += 360;
        }
        // 由于是验证"接近0"，如果 > 180，取 360 - angle 更合理
        if (angle > 180) {
            angle = 360 - angle;
        }
        return angle;
    }

    /**
     * 检查轨迹是否有自然的抖动（人类行为特征）
     *
     * 设计说明：
     * - 轨迹点的 x 值为角度（度），相邻点角度差值约为 0.5°~5°
     * - 30ms 间隔下，速度大约在 0.02~0.17 °/ms 之间
     * - 人类手指微小颤动会导致速度波动，阈值设为 0.01 即可检测到
     */
    private boolean checkTrajectoryJitter(java.util.List<TrajectoryPoint> trajectory) {
        if (trajectory.size() < 3) {
            return false;
        }

        // 计算相邻点的速度变化
        int jitterCount = 0;
        for (int i = 2; i < trajectory.size(); i++) {
            double speed1 = calculateSpeed(trajectory.get(i - 2), trajectory.get(i - 1));
            double speed2 = calculateSpeed(trajectory.get(i - 1), trajectory.get(i));

            // 阈值 0.01：速度变化超过 0.01 °/ms 即视为一次抖动
            // 例如：从 0.05 °/ms 变化到 0.04 °/ms 或 0.06 °/ms 即被检测到
            if (Math.abs(speed2 - speed1) > 0.01) {
                jitterCount++;
            }
        }

        double jitterRatio = (double) jitterCount / (trajectory.size() - 2);
        return jitterRatio >= 0.3;
    }

    /**
     * 计算两点之间的速度
     */
    private double calculateSpeed(TrajectoryPoint p1, TrajectoryPoint p2) {
        long timeDiff = p2.getT() - p1.getT();
        if (timeDiff <= 0) {
            return 0;
        }
        // 轨迹中的 x 表示当前旋转角度（度）
        // 使用 x 的变化量计算速度（度/毫秒）
        double distance = Math.abs(p2.getX() - p1.getX());
        return distance / timeDiff;
    }


    /**
     * 轨迹点 DTO（内部类）
     *
     * 注意：x 值表示当前旋转角度（度），可能为小数，因此使用 Double 类型。
     * 例如：-20.3 表示图片当前被逆时针旋转了约 20.3 度。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TrajectoryPoint {
        /**
         * 滑块当前旋转角度值（度），正为顺时针，负为逆时针
         * 使用 Double 类型以支持小数精度（例如：-20.3、15.7）
         */
        private Double x;

        /**
         * Y 坐标（保留兼容，不参与计算）
         */
        private Double y;

        /**
         * 时间戳（相对开始时间的毫秒数）
         */
        private Long t;
    }
}
