-- V20: 添加登录IP封禁系统配置项
-- 用于支持"登录失败次数过多则自动封禁IP"功能

-- 插入登录IP封禁相关的系统配置
INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order) VALUES
(2054000000000000211, 'security.login.ban_ip_enabled', 'false', 'BOOLEAN', '是否启用登录失败IP封禁', 'SECURITY', FALSE, TRUE, 15),
(2054000000000000212, 'security.login.ban_ip_threshold', '20', 'NUMBER', '触发IP封禁的登录失败次数', 'SECURITY', FALSE, TRUE, 16),
(2054000000000000213, 'security.login.ban_ip_duration_hours', '24', 'NUMBER', 'IP封禁时长（小时）', 'SECURITY', FALSE, TRUE, 17)
ON CONFLICT (config_key) DO NOTHING;
