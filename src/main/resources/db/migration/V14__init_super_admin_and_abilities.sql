-- V14: 初始化超级管理员和能力系统
-- 执行此脚本前，请确保已运行 V12__add_super_admin_and_ability_constraints.sql

-- 1. 将现有的 admin 用户设置为超级管理员（根据实际用户名调整）
UPDATE t_user SET is_super_admin = TRUE WHERE username = 'admin' AND is_super_admin = FALSE;

-- 2. 为超级管理员添加 'manage_admin_abilities' 能力
INSERT INTO t_ability (id, user_id, name, description, icon, sort_order, create_time)
SELECT 
    2054000000000000001,
    id,
    'manage_admin_abilities',
    '管理其他管理员的能力分配',
    '/icons/manage-abilities.svg',
    1,
    CURRENT_TIMESTAMP
FROM t_user 
WHERE username = 'admin'
ON CONFLICT (user_id, name) DO NOTHING;

-- 3. 添加一些常见的管理能力示例（不分配给任何人，仅供参考）
-- 这些能力需要通过能力管理接口手动分配给具体的管理员

COMMENT ON TABLE t_ability IS '
管理员能力说明：
- manage_users: 管理用户（创建、删除、禁用用户）
- manage_posts: 管理文章（审核、删除文章）
- manage_comments: 管理评论（审核、删除评论）
- manage_categories: 管理分类
- manage_tags: 管理标签
- manage_admin_abilities: 管理其他管理员的能力分配（特殊权限）
- view_audit_logs: 查看审计日志
- manage_system_config: 管理系统配置
';
