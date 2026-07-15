package com.leo.myblog.entity;

import lombok.Data;

/**
 * 旋转验证码实体（替代原有滑块拼图验证码）
 *
 * 新方案设计：
 * - 服务端将一张图片随机旋转一定角度（-35° ~ 35°）
 * - 前端把图片修剪为圆形显示
 * - 用户通过下方的滑块旋转图片，将图片旋回正位
 * - 验证时比对用户旋转角度是否与原始角度抵消（允许 ±6° 偏差）
 * - 同时基于行为分析（滑动轨迹、时间等）判断是否为真人
 */
@Data
public class SliderCaptcha {

    /**
     * 随机字符串（用于标识本次验证）
     */
    private String nonceStr;

    /**
     * 旋转后的图片 base64（PNG格式，支持透明背景）
     * 前端需要将此图片修剪为圆形并显示
     * 图片已被服务端随机旋转，看起来不是正的
     */
    private String rotatedImageSrc;

    /**
     * 画布尺寸（正方形边长，像素）
     * 建议前端将图片显示为当前尺寸
     */
    private Integer canvasSize;

    /**
     * 图片来源提示（用于前端显示，不暴露具体存储技术）
     * 可能的值：
     * - "cloud_storage": 来自云端存储
     * - "local_cache": 来自本地缓存
     * - "generated": 程序生成
     */
    private String imageSourceHint;
}
