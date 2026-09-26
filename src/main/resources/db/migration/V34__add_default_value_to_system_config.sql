-- V34: 为系统配置表添加默认值列，支持"重置为默认值"功能
--
-- 背景：t_system_config 只存当前值，默认值散落在各版本的种子迁移里，
--       导致 resetConfig() 无从得知该恢复到哪个值，只能抛"暂不支持重置配置"。
--
-- 约定：新增配置项的迁移在 INSERT 时须一并写入 default_value；
--       未定义 default_value（NULL）的配置项视为不可重置。

ALTER TABLE t_system_config ADD COLUMN IF NOT EXISTS default_value TEXT;
COMMENT ON COLUMN t_system_config.default_value IS '默认值（重置配置时使用，NULL 表示该配置不可重置）';

-- 回填已有配置项的默认值，取值与各版本种子数据（V15/V26/V28/V32）保持一致
UPDATE t_system_config c
SET default_value = v.default_value
FROM (VALUES
    -- V15: 基础配置
    ('site.name', 'MyBlog'),
    ('site.description', '一个现代化的博客系统'),
    ('site.logo', ''),
    ('site.favicon', ''),
    ('site.footer.text', '© 2026 MyBlog. All rights reserved.'),
    -- V15: 安全配置
    ('security.login.max_attempts', '5'),
    ('security.login.lock_duration', '180'),
    ('security.password.min_length', '6'),
    ('security.token.expiration', '86400000'),
    -- V15: 邮件配置
    ('email.enabled', 'false'),
    ('email.smtp.host', ''),
    ('email.smtp.port', '587'),
    ('email.from.address', ''),
    -- V15: 存储配置
    ('storage.type', 'minio'),
    ('storage.max_file_size', '10485760'),
    ('storage.allowed_extensions', 'jpg,jpeg,png,gif,webp,pdf,doc,docx'),
    -- V15: 文章与评论配置
    ('post.per_page', '10'),
    ('comment.enabled', 'true'),
    ('comment.require_approval', 'false'),
    -- V26: 登录 IP 封禁
    ('security.login.ban_ip_enabled', 'false'),
    ('security.login.ban_ip_threshold', '20'),
    ('security.login.ban_ip_duration_hours', '24'),
    -- V28: App 双因素认证
    ('security.app_auth.enabled', 'true'),
    ('security.app_auth.challenge_timeout', '60'),
    ('security.app_auth.preferred_cipher', 'AES-256-GCM'),
    -- V32: 推送挑战调试开关
    ('security.app_auth.push_challenge_show_correct', 'false')
) AS v(config_key, default_value)
WHERE c.config_key = v.config_key;