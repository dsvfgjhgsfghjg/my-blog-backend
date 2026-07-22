-- 用户偏好设置表新增字段：默认登录方式
ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS default_login_method VARCHAR(50) NOT NULL DEFAULT 'PASSWORD',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;