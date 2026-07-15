-- V14: 添加霜枫之思粒子特效
-- 更新 user_preferences 表的 particle_mode 字段注释，添加 frostMaple（霜枫之思）

COMMENT ON COLUMN user_preferences.particle_mode IS '粒子特效模式: stardust, ice, sakura, firefly, magicCircle, pixel, frostMaple';

-- 验证更新
SELECT column_name, data_type, character_maximum_length, 
       pg_catalog.col_description('user_preferences'::regclass::oid, ordinal_position) as comment
FROM information_schema.columns 
WHERE table_name = 'user_preferences' AND column_name = 'particle_mode';
