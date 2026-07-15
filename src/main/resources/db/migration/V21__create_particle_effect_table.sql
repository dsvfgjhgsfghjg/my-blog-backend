-- V21: 创建粒子特效配置表（白名单机制）

-- 创建粒子特效配置表
CREATE TABLE IF NOT EXISTS t_particle_effect (
    id BIGSERIAL PRIMARY KEY,
    effect_code VARCHAR(50) NOT NULL UNIQUE,      -- 特效代码（唯一标识）
    effect_name VARCHAR(100) NOT NULL,             -- 特效名称（中文显示）
    description TEXT,                              -- 特效描述
    is_enabled BOOLEAN DEFAULT TRUE,               -- 是否启用
    sort_order INT DEFAULT 0,                      -- 排序（前端展示顺序）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_effect_code UNIQUE (effect_code)
);

COMMENT ON TABLE t_particle_effect IS '粒子特效配置表（白名单）';
COMMENT ON COLUMN t_particle_effect.effect_code IS '特效代码（唯一标识，用于API传输和数据库存储）';
COMMENT ON COLUMN t_particle_effect.effect_name IS '特效名称（中文显示，用于前端展示）';
COMMENT ON COLUMN t_particle_effect.description IS '特效描述（可选）';
COMMENT ON COLUMN t_particle_effect.is_enabled IS '是否启用（false则用户不能选择此特效）';
COMMENT ON COLUMN t_particle_effect.sort_order IS '排序（前端展示顺序，数字越小越靠前）';

-- 插入默认的7种特效
INSERT INTO t_particle_effect (effect_code, effect_name, description, is_enabled, sort_order) VALUES
('stardust', '星尘', '闪烁的星尘粒子效果', TRUE, 1),
('ice', '冰晶', '晶莹剔透的冰晶飘落效果', TRUE, 2),
('sakura', '樱花', '浪漫的樱花飘落效果', TRUE, 3),
('firefly', '萤火虫', '夏夜萤火虫飞舞效果', TRUE, 4),
('magicCircle', '魔法阵', '神秘的魔法阵旋转效果', TRUE, 5),
('pixel', '像素', '复古像素风格粒子效果', TRUE, 6),
('frostMaple', '霜枫之思', '霜冻枫叶飘落效果', TRUE, 7)
ON CONFLICT (effect_code) DO NOTHING;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_effect_enabled ON t_particle_effect(is_enabled);
CREATE INDEX IF NOT EXISTS idx_effect_sort ON t_particle_effect(sort_order);

-- 验证插入
SELECT effect_code, effect_name, is_enabled, sort_order 
FROM t_particle_effect 
ORDER BY sort_order;
