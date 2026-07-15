package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App 绑定设备实体
 * 用于存储用户绑定的移动设备信息（App 双因素认证使用）
 */
@Data
@TableName("t_app_device")
public class AppDevice {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 设备唯一标识（由客户端生成，如 Android ID）
     */
    private String deviceId;

    /**
     * 设备名称（如 "Pixel 7"）
     */
    private String deviceName;

    /**
     * 设备类型（ANDROID / IOS / OTHER）
     */
    private String deviceType;

    /**
     * 客户端 DH 公钥（Base64 编码）
     */
    private String publicKey;

    /**
     * 首选加密算法（如 AES-256-GCM、SM4-GCM）
     */
    private String cipherPref;

    /**
     * 设备密钥（BCrypt 哈希，服务端生成）
     * 用于设备身份验证，防止设备码被伪造
     */
    private String deviceSecret;

    /**
     * 设备密钥提示（前4位+后4位，如 a3f8...e5d6）
     * 用于 UI 展示，帮助用户识别设备
     */
    private String deviceSecretHint;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * 绑定时间
     */
    private LocalDateTime createdAt;
}
