-- V10: 为用户表添加手机号和邮箱字段
ALTER TABLE t_user 
ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
ADD COLUMN IF NOT EXISTS email VARCHAR(100);

-- 添加注释
COMMENT ON COLUMN t_user.phone IS '手机号码';
COMMENT ON COLUMN t_user.email IS '电子邮箱';

-- 为手机号字段添加索引（便于查询）
CREATE INDEX IF NOT EXISTS idx_user_phone ON t_user(phone) WHERE phone IS NOT NULL;
