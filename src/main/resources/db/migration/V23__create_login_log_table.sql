-- V17: 创建登录日志表
-- 目的：记录用户登录设备信息（IP、客户端、操作系统）

CREATE TABLE IF NOT EXISTS t_login_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    device_type VARCHAR(20), -- DESKTOP, MOBILE, TABLET, UNKNOWN
    os_name VARCHAR(50), -- Windows, macOS, Linux, iOS, Android等
    os_version VARCHAR(50), -- 操作系统版本
    browser_name VARCHAR(50), -- Chrome, Firefox, Safari等
    browser_version VARCHAR(50), -- 浏览器版本
    user_agent TEXT, -- 原始User-Agent字符串
    login_time TIMESTAMP NOT NULL DEFAULT NOW(),
    login_result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS', -- SUCCESS, FAILED
    failure_reason VARCHAR(200), -- 失败原因
    
    CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_login_log_user_id ON t_login_log(user_id);
CREATE INDEX IF NOT EXISTS idx_login_log_username ON t_login_log(username);
CREATE INDEX IF NOT EXISTS idx_login_log_login_time ON t_login_log(login_time DESC);
CREATE INDEX IF NOT EXISTS idx_login_log_ip_address ON t_login_log(ip_address);

-- 添加注释
COMMENT ON TABLE t_login_log IS '用户登录日志表';
COMMENT ON COLUMN t_login_log.id IS '主键ID（雪花算法）';
COMMENT ON COLUMN t_login_log.user_id IS '用户ID';
COMMENT ON COLUMN t_login_log.username IS '用户名';
COMMENT ON COLUMN t_login_log.ip_address IS '登录IP地址';
COMMENT ON COLUMN t_login_log.device_type IS '设备类型：DESKTOP/MOBILE/TABLET/UNKNOWN';
COMMENT ON COLUMN t_login_log.os_name IS '操作系统名称';
COMMENT ON COLUMN t_login_log.os_version IS '操作系统版本';
COMMENT ON COLUMN t_login_log.browser_name IS '浏览器名称';
COMMENT ON COLUMN t_login_log.browser_version IS '浏览器版本';
COMMENT ON COLUMN t_login_log.user_agent IS '原始User-Agent字符串';
COMMENT ON COLUMN t_login_log.login_time IS '登录时间';
COMMENT ON COLUMN t_login_log.login_result IS '登录结果：SUCCESS/FAILED';
COMMENT ON COLUMN t_login_log.failure_reason IS '失败原因';

-- 验证表创建成功
SELECT 
    table_name,
    column_name,
    data_type,
    character_maximum_length,
    pg_catalog.col_description('t_login_log'::regclass::oid, ordinal_position) as comment
FROM information_schema.columns 
WHERE table_name = 't_login_log'
ORDER BY ordinal_position;
