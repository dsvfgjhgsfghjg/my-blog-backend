-- 创建用户偏好设置表
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    particle_mode VARCHAR(20) NOT NULL DEFAULT 'stardust',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_id UNIQUE (user_id)
);

COMMENT ON TABLE user_preferences IS '用户偏好设置表';
COMMENT ON COLUMN user_preferences.id IS '主键ID';
COMMENT ON COLUMN user_preferences.user_id IS '用户ID';
COMMENT ON COLUMN user_preferences.particle_mode IS '粒子特效模式: stardust, ice, sakura, firefly, magicCircle, pixel';
COMMENT ON COLUMN user_preferences.created_at IS '创建时间';
COMMENT ON COLUMN user_preferences.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_particle_mode ON user_preferences(particle_mode);
