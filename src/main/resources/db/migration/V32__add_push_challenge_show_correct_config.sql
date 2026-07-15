-- V32: 添加推送挑战显示正确答案的调试配置项
-- 用于控制 App 推送挑战时是否在响应中返回正确答案（调试用，生产环境应关闭）

INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order)
VALUES (2054000000000000224, 'security.app_auth.push_challenge_show_correct', 'false', 'BOOLEAN', '推送挑战时显示正确答案（调试用，生产环境应关闭）', 'SECURITY', FALSE, TRUE, 21)
ON CONFLICT (config_key) DO NOTHING;
