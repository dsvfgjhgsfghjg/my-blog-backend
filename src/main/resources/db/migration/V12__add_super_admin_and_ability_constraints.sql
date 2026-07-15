-- V12: 为 t_user 表添加 is_super_admin 字段
-- 用于标识超级管理员（可以管理其他管理员的能力）
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN t_user.is_super_admin IS '是否为超级管理员（可管理能力分配）';

-- 为 t_ability 表添加唯一约束（防止重复能力）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'uk_user_ability_name' 
        AND table_name = 't_ability'
    ) THEN
        ALTER TABLE t_ability ADD CONSTRAINT uk_user_ability_name UNIQUE (user_id, name);
    END IF;
END $$;

-- 添加索引优化查询性能
CREATE INDEX IF NOT EXISTS idx_ability_user_id ON t_ability(user_id);
CREATE INDEX IF NOT EXISTS idx_ability_name ON t_ability(name);

-- 添加注释
COMMENT ON TABLE t_ability IS '管理员能力表，存储每个管理员的具体能力';
COMMENT ON COLUMN t_ability.user_id IS '用户ID（关联 t_user.id）';
COMMENT ON COLUMN t_ability.name IS '能力名称（如：manage_users, manage_posts, manage_admin_abilities）';
COMMENT ON COLUMN t_ability.description IS '能力描述';
COMMENT ON COLUMN t_ability.icon IS '图标URL（存储在MinIO）';
COMMENT ON COLUMN t_ability.sort_order IS '排序（前端展示顺序）';
COMMENT ON COLUMN t_ability.create_time IS '创建时间';
