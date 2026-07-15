-- 创建系统配置表
CREATE TABLE IF NOT EXISTS t_system_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(50) DEFAULT 'STRING', -- STRING, NUMBER, BOOLEAN, JSON
    description TEXT,
    category VARCHAR(50), -- BASIC, SECURITY, EMAIL, STORAGE, etc.
    is_public BOOLEAN DEFAULT FALSE, -- 是否公开（前端可见）
    is_editable BOOLEAN DEFAULT TRUE, -- 是否可编辑
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 添加索引
CREATE INDEX idx_system_config_category ON t_system_config(category);
CREATE INDEX idx_system_config_key ON t_system_config(config_key);

-- 添加注释
COMMENT ON TABLE t_system_config IS '系统配置表';
COMMENT ON COLUMN t_system_config.config_key IS '配置键（唯一）';
COMMENT ON COLUMN t_system_config.config_value IS '配置值';
COMMENT ON COLUMN t_system_config.config_type IS '配置类型：STRING, NUMBER, BOOLEAN, JSON';
COMMENT ON COLUMN t_system_config.description IS '配置描述';
COMMENT ON COLUMN t_system_config.category IS '配置分类';
COMMENT ON COLUMN t_system_config.is_public IS '是否公开（前端可见）';
COMMENT ON COLUMN t_system_config.is_editable IS '是否可编辑';
COMMENT ON COLUMN t_system_config.sort_order IS '排序';

-- 插入默认系统配置
INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order) VALUES
(2054000000000000101, 'site.name', 'MyBlog', 'STRING', '网站名称', 'BASIC', TRUE, TRUE, 1),
(2054000000000000102, 'site.description', '一个现代化的博客系统', 'STRING', '网站描述', 'BASIC', TRUE, TRUE, 2),
(2054000000000000103, 'site.logo', '', 'STRING', '网站Logo URL', 'BASIC', TRUE, TRUE, 3),
(2054000000000000104, 'site.favicon', '', 'STRING', '网站Favicon URL', 'BASIC', TRUE, TRUE, 4),
(2054000000000000105, 'site.footer.text', '© 2026 MyBlog. All rights reserved.', 'STRING', '页脚文本', 'BASIC', TRUE, TRUE, 5),

(2054000000000000201, 'security.login.max_attempts', '5', 'NUMBER', '登录最大尝试次数', 'SECURITY', FALSE, TRUE, 1),
(2054000000000000202, 'security.login.lock_duration', '180', 'NUMBER', '登录锁定持续时间（秒）', 'SECURITY', FALSE, TRUE, 2),
(2054000000000000203, 'security.password.min_length', '6', 'NUMBER', '密码最小长度', 'SECURITY', FALSE, TRUE, 3),
(2054000000000000204, 'security.token.expiration', '86400000', 'NUMBER', 'Token过期时间（毫秒）', 'SECURITY', FALSE, FALSE, 4),

(2054000000000000301, 'email.enabled', 'false', 'BOOLEAN', '是否启用邮件功能', 'EMAIL', FALSE, TRUE, 1),
(2054000000000000302, 'email.smtp.host', '', 'STRING', 'SMTP服务器地址', 'EMAIL', FALSE, TRUE, 2),
(2054000000000000303, 'email.smtp.port', '587', 'NUMBER', 'SMTP端口', 'EMAIL', FALSE, TRUE, 3),
(2054000000000000304, 'email.from.address', '', 'STRING', '发件人邮箱', 'EMAIL', FALSE, TRUE, 4),

(2054000000000000401, 'storage.type', 'minio', 'STRING', '存储类型（local/minio/oss）', 'STORAGE', FALSE, TRUE, 1),
(2054000000000000402, 'storage.max_file_size', '10485760', 'NUMBER', '最大文件大小（字节，默认10MB）', 'STORAGE', FALSE, TRUE, 2),
(2054000000000000403, 'storage.allowed_extensions', 'jpg,jpeg,png,gif,webp,pdf,doc,docx', 'STRING', '允许的文件扩展名', 'STORAGE', FALSE, TRUE, 3),

(2054000000000000501, 'post.per_page', '10', 'NUMBER', '每页文章数', 'POST', FALSE, TRUE, 1),
(2054000000000000502, 'comment.enabled', 'true', 'BOOLEAN', '是否启用评论', 'COMMENT', TRUE, TRUE, 2),
(2054000000000000503, 'comment.require_approval', 'false', 'BOOLEAN', '评论是否需要审核', 'COMMENT', FALSE, TRUE, 3)

ON CONFLICT (config_key) DO NOTHING;
