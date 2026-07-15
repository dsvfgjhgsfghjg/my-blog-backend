-- V21: 创建 App 绑定设备表
-- 用于存储用户绑定的移动设备信息（App 认证使用）

CREATE TABLE IF NOT EXISTS t_app_device (
    id              BIGINT       PRIMARY KEY,          -- 主键（雪花算法）
    user_id         BIGINT       NOT NULL,              -- 用户ID
    device_id       VARCHAR(128) NOT NULL,              -- 设备唯一标识（由客户端生成）
    device_name     VARCHAR(256),                       -- 设备名称（如 "Pixel 7"）
    device_type     VARCHAR(64)  DEFAULT 'ANDROID',     -- 设备类型（ANDROID / IOS / OTHER）
    public_key      TEXT,                               -- 客户端 DH 公钥（Base64 编码）
    cipher_pref     VARCHAR(32)  DEFAULT 'AES-256-GCM', -- 首选加密算法
    last_active_at  TIMESTAMP,                          -- 最后活跃时间
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP, -- 绑定时间
    CONSTRAINT fk_app_device_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT uk_app_device_device_id UNIQUE (device_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_app_device_user_id ON t_app_device(user_id);
