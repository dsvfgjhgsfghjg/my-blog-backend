-- ============================================
-- 修复缺失的数据库字段
-- 执行此脚本添加 is_super_admin 字段
-- ============================================

-- 1. 添加 is_super_admin 字段到 t_user 表
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN t_user.is_super_admin IS '是否为超级管理员（可管理能力分配）';

-- 2. 为现有的 admin 用户设置为超级管理员（可选）
-- UPDATE t_user SET is_super_admin = TRUE WHERE username = 'admin';

-- 3. 验证字段已添加
SELECT column_name, data_type, column_default 
FROM information_schema.columns 
WHERE table_name = 't_user' AND column_name = 'is_super_admin';
