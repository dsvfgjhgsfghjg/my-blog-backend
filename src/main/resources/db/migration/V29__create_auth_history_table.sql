-- V23: 创建认证历史表
-- 用于记录 App 双因素认证的挑战历史

CREATE TABLE IF NOT EXISTS t_auth_history (
    id              BIGINT       PRIMARY KEY,              -- 主键（雪花算法）
    user_id         BIGINT       NOT NULL,                  -- 用户ID
    device_id       VARCHAR(128),                           -- 响应设备 ID
    device_name     VARCHAR(256),                           -- 设备名称
    challenge_id    VARCHAR(64)  NOT NULL,                  -- 挑战 ID
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',-- 状态: pending/approved/rejected/expired
    numbers         JSON,                                   -- 3 个候选数字 [231, 845, 672]
    requested_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 挑战发起时间
    responded_at    TIMESTAMP,                              -- 用户响应时间
    CONSTRAINT fk_auth_history_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT uk_auth_history_challenge_id UNIQUE (challenge_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_auth_history_user_id ON t_auth_history(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_history_user_id_status ON t_auth_history(user_id, status);
CREATE INDEX IF NOT EXISTS idx_auth_history_requested_at ON t_auth_history(requested_at DESC);
