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

-- TOTP 双因素认证模块开关
(2054000000000000205, 'security.totp.user_management', 'true', 'BOOLEAN', '用户管理操作需要TOTP（停用账户、重置密码、设置管理员等）', 'SECURITY', FALSE, TRUE, 5),
(2054000000000000206, 'security.totp.post_management', 'true', 'BOOLEAN', '文章管理操作需要TOTP（删除文章、文章审核）', 'SECURITY', FALSE, TRUE, 6),
(2054000000000000207, 'security.totp.tag_management', 'true', 'BOOLEAN', '标签管理操作需要TOTP（创建、更新、删除标签）', 'SECURITY', FALSE, TRUE, 7),
(2054000000000000208, 'security.totp.system_config', 'true', 'BOOLEAN', '系统配置操作需要TOTP（查看和修改系统设置）', 'SECURITY', FALSE, TRUE, 8),
(2054000000000000209, 'security.totp.audit_log', 'true', 'BOOLEAN', '审计日志查看需要TOTP（查看审计日志、详情、统计）', 'SECURITY', FALSE, TRUE, 9),
(2054000000000000210, 'security.totp.account_security', 'true', 'BOOLEAN', '账户安全操作需要TOTP（修改密码、注销账户）', 'SECURITY', FALSE, TRUE, 10),

(2054000000000000301, 'email.enabled', 'false', 'BOOLEAN', '是否启用邮件功能', 'EMAIL', FALSE, TRUE, 1),
(2054000000000000302, 'email.smtp.host', '', 'STRING', 'SMTP服务器地址', 'EMAIL', FALSE, TRUE, 2),
(2054000000000000303, 'email.smtp.port', '587', 'NUMBER', 'SMTP端口', 'EMAIL', FALSE, TRUE, 3),
(2054000000000000304, 'email.from.address', '', 'STRING', '发件人邮箱', 'EMAIL', FALSE, TRUE, 4),

(2054000000000000401, 'storage.type', 'minio', 'STRING', '存储类型（local/minio/oss）', 'STORAGE', FALSE, TRUE, 1),
(2054000000000000402, 'storage.max_file_size', '10485760', 'NUMBER', '最大文件大小（字节，默认10MB）', 'STORAGE', FALSE, TRUE, 2),
(2054000000000000403, 'storage.allowed_extensions', 'jpg,jpeg,png,gif,webp,pdf,doc,docx', 'STRING', '允许的文件扩展名', 'STORAGE', FALSE, TRUE, 3),

(2054000000000000501, 'post.per_page', '10', 'NUMBER', '每页文章数', 'POST', FALSE, TRUE, 1),
(2054000000000000502, 'comment.enabled', 'true', 'BOOLEAN', '是否启用评论', 'COMMENT', TRUE, TRUE, 2),
(2054000000000000503, 'comment.require_approval', 'false', 'BOOLEAN', '评论是否需要审核', 'COMMENT', FALSE, TRUE, 3),

-- 登录IP封禁配置
(2054000000000000211, 'security.login.ban_ip_enabled', 'false', 'BOOLEAN', '是否启用登录失败IP封禁', 'SECURITY', FALSE, TRUE, 15),
(2054000000000000212, 'security.login.ban_ip_threshold', '20', 'NUMBER', '触发IP封禁的登录失败次数', 'SECURITY', FALSE, TRUE, 16),
(2054000000000000213, 'security.login.ban_ip_duration_hours', '24', 'NUMBER', 'IP封禁时长（小时）', 'SECURITY', FALSE, TRUE, 17),

-- 存储维护配置
(2054000000000000601, 'storage.cleanup.orphan_images', 'true', 'BOOLEAN', '清理对象存储中的孤立垃圾图片（删除未被文章引用的无用图片，不碰历史头像等数据）', 'STORAGE', FALSE, TRUE, 10),

-- App 双因素认证配置
(2054000000000000221, 'security.app_auth.enabled', 'true', 'BOOLEAN', '是否启用 App 双因素认证', 'SECURITY', FALSE, TRUE, 18),
(2054000000000000222, 'security.app_auth.challenge_timeout', '60', 'NUMBER', 'App 认证挑战超时时间（秒）', 'SECURITY', FALSE, TRUE, 19),
(2054000000000000223, 'security.app_auth.preferred_cipher', 'AES-256-GCM', 'STRING', '首选加密算法（AES-256-GCM / SM4-GCM）', 'SECURITY', FALSE, TRUE, 20),
(2054000000000000224, 'security.app_auth.push_challenge_show_correct', 'false', 'BOOLEAN', '推送挑战时显示正确答案（调试用，生产环境应关闭）', 'SECURITY', FALSE, TRUE, 21)

ON CONFLICT (config_key) DO NOTHING;
