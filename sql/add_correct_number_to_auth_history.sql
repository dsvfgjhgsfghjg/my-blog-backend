-- 为 t_auth_history 表添加 correct_number 列
-- 用于存储 3 选 1 挑战的正确答案（调试用）

ALTER TABLE t_auth_history
    ADD COLUMN IF NOT EXISTS correct_number INTEGER;
