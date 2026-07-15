-- V24: 创建UA黑名单和安全防护表
-- 目的：拦截恶意User-Agent，记录安全事件，自动封禁IP

-- 1. UA黑名单表
CREATE TABLE IF NOT EXISTS t_ua_blacklist (
    id BIGINT PRIMARY KEY,
    pattern VARCHAR(200) NOT NULL UNIQUE, -- UA匹配模式（支持部分匹配）
    description VARCHAR(500), -- 描述
    threat_level VARCHAR(20) DEFAULT 'MEDIUM', -- 威胁等级：LOW/MEDIUM/HIGH/CRITICAL
    action_type VARCHAR(20) DEFAULT 'BLOCK', -- 动作类型：BLOCK/HONEYPOT
    is_enabled BOOLEAN DEFAULT TRUE, -- 是否启用
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2. 安全事件日志表
CREATE TABLE IF NOT EXISTS t_security_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL, -- 事件类型：UA_BLOCKED/IP_BANNED/HONEYPOT_TRIGGERED
    ip_address VARCHAR(50) NOT NULL, -- 客户端IP
    user_agent TEXT, -- User-Agent
    request_url VARCHAR(500), -- 请求URL
    request_method VARCHAR(10), -- 请求方法
    matched_pattern VARCHAR(200), -- 匹配的黑名单模式
    threat_level VARCHAR(20), -- 威胁等级
    action_taken VARCHAR(20), -- 采取的动作：BLOCKED/HONEYPOT
    trigger_count INTEGER DEFAULT 1, -- 触发次数
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_security_event_ip ON t_security_event(ip_address);
CREATE INDEX IF NOT EXISTS idx_security_event_time ON t_security_event(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_security_event_type ON t_security_event(event_type);

-- 3. IP封禁表（临时封禁）
CREATE TABLE IF NOT EXISTS t_ip_ban (
    id BIGINT PRIMARY KEY,
    ip_address VARCHAR(50) NOT NULL UNIQUE,
    reason VARCHAR(500), -- 封禁原因
    ban_until TIMESTAMP NOT NULL, -- 封禁到期时间
    trigger_count INTEGER DEFAULT 1, -- 触发次数
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ip_ban_expiry ON t_ip_ban(ban_until);

-- 插入默认UA黑名单规则
INSERT INTO t_ua_blacklist (id, pattern, description, threat_level, action_type) VALUES
(1, 'kali', 'Kali Linux 渗透测试系统', 'HIGH', 'HONEYPOT'),
(2, 'sqlmap', 'SQL注入扫描工具', 'CRITICAL', 'HONEYPOT'),
(3, 'nmap', '网络扫描工具', 'HIGH', 'HONEYPOT'),
(4, 'nikto', 'Web服务器扫描器', 'HIGH', 'HONEYPOT'),
(5, 'dirbuster', '目录爆破工具', 'MEDIUM', 'HONEYPOT'),
(6, 'w3af', 'Web应用攻击框架', 'HIGH', 'HONEYPOT'),
(7, 'metasploit', '渗透测试框架', 'CRITICAL', 'HONEYPOT'),
(8, 'python-requests', 'Python脚本库', 'LOW', 'BLOCK'),
(9, 'python-urllib', 'Python脚本库', 'LOW', 'BLOCK'),
(10, 'go-http-client', 'Go语言HTTP客户端', 'LOW', 'BLOCK'),
(11, 'java/', 'Java HTTP客户端', 'LOW', 'BLOCK'),
(12, 'curl/', '命令行工具', 'LOW', 'BLOCK'),
(13, 'wget/', '命令行下载工具', 'LOW', 'BLOCK')
ON CONFLICT (pattern) DO NOTHING;

-- 添加注释
COMMENT ON TABLE t_ua_blacklist IS 'User-Agent黑名单表';
COMMENT ON TABLE t_security_event IS '安全事件日志表';
COMMENT ON TABLE t_ip_ban IS 'IP临时封禁表';

-- 验证表创建成功
SELECT 
    table_name,
    pg_catalog.obj_description(('public.' || table_name)::regclass) as comment
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name IN ('t_ua_blacklist', 't_security_event', 't_ip_ban')
ORDER BY table_name;
