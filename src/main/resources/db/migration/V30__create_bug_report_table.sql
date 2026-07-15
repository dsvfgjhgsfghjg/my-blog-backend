-- V24: 创建 Bug 日志上报表
-- 用于存储 App 端上报的 Bug 日志
-- 日志内容存储在 MinIO，数据库只存元数据

CREATE TABLE IF NOT EXISTS t_bug_report (
    id              BIGINT       PRIMARY KEY,              -- 主键（雪花算法）
    user_id         BIGINT       NOT NULL,                  -- 上报用户 ID
    app_version     VARCHAR(32),                            -- App 版本号
    device_model    VARCHAR(128),                           -- 设备型号
    os_version      VARCHAR(32),                            -- 操作系统版本
    log_summary     VARCHAR(512),                           -- 日志摘要（前 200 字）
    minio_path      VARCHAR(512)  NOT NULL,                 -- MinIO 存储路径
    log_size        INT          NOT NULL DEFAULT 0,        -- 原始日志大小（字节）
    compressed_size INT          NOT NULL DEFAULT 0,        -- 压缩后大小（字节）
    status          VARCHAR(16)  NOT NULL DEFAULT 'unread', -- 状态: unread / read / resolved
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 上报时间
    resolved_at     TIMESTAMP,                              -- 解决时间
    resolve_note    TEXT,                                   -- 解决备注
    CONSTRAINT fk_bug_report_user FOREIGN KEY (user_id) REFERENCES t_user(id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_bug_report_user_id ON t_bug_report(user_id);
CREATE INDEX IF NOT EXISTS idx_bug_report_status ON t_bug_report(status);
CREATE INDEX IF NOT EXISTS idx_bug_report_created_at ON t_bug_report(created_at DESC);
