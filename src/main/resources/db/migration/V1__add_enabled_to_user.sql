-- 为用户表添加 enabled 字段（软删除/启用禁用）
ALTER TABLE t_user ADD COLUMN enabled BOOLEAN DEFAULT TRUE;
COMMENT ON COLUMN t_user.enabled IS '是否启用（true-启用，false-禁用/软删除）';
