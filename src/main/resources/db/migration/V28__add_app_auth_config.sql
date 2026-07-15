-- V22: 添加 App 双因素认证系统配置项
-- 用于控制 App 审批式双因素认证功能

INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order) VALUES
(2054000000000000221, 'security.app_auth.enabled', 'true', 'BOOLEAN', '是否启用 App 双因素认证', 'SECURITY', FALSE, TRUE, 18),
(2054000000000000222, 'security.app_auth.challenge_timeout', '60', 'NUMBER', 'App 认证挑战超时时间（秒）', 'SECURITY', FALSE, TRUE, 19),
(2054000000000000223, 'security.app_auth.preferred_cipher', 'AES-256-GCM', 'STRING', '首选加密算法（AES-256-GCM / SM4-GCM）', 'SECURITY', FALSE, TRUE, 20)
ON CONFLICT (config_key) DO NOTHING;
