-- ========================================
-- 文章存储迁移：从数据库迁移到 MinIO
-- ========================================
-- 说明：
-- 1. 将 t_post 表的 content 字段重命名为 content_path
-- 2. content_path 存储 MinIO 对象路径，格式: posts/{postId}/{uuid}.md
-- 3. 原有 content 数据需要手动迁移到 MinIO（通过 Java 服务）
-- 
-- 执行前请务必备份数据库！
-- ========================================

BEGIN;

-- 步骤1: 添加 content_path 字段
ALTER TABLE t_post ADD COLUMN IF NOT EXISTS content_path VARCHAR(500);

-- 步骤2: 为已有文章设置默认值（空字符串）
UPDATE t_post SET content_path = '' WHERE content_path IS NULL;

-- 步骤3: 添加注释
COMMENT ON COLUMN t_post.content_path IS 'Markdown正文内容路径（MinIO对象路径），格式: posts/{postId}/{uuid}.md';

-- 步骤4: （可选）如果确认所有数据已迁移到 MinIO，可以删除旧的 content 字段
-- 警告：执行此操作前必须确保所有文章内容已成功迁移到 MinIO！
-- ALTER TABLE t_post DROP COLUMN IF EXISTS content;

COMMIT;

-- ========================================
-- 回滚脚本（如果需要）
-- ========================================
/*
BEGIN;

-- 恢复 content 字段（如果有备份）
-- ALTER TABLE t_post ADD COLUMN content TEXT;

-- 删除 content_path 字段
ALTER TABLE t_post DROP COLUMN IF EXISTS content_path;

COMMIT;
*/
