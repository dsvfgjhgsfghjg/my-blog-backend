-- V17: 添加文章审计系统

-- 1. 更新文章状态注释，添加"待审核"状态
COMMENT ON COLUMN t_post.status IS '状态：0-草稿, 1-已发布, 2-私密, 3-待审核';

-- 2. 创建文章审计记录表
CREATE TABLE IF NOT EXISTS t_post_audit (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    auditor_id BIGINT,                    -- 审计员ID（NULL表示未审计）
    audit_status SMALLINT DEFAULT 0,      -- 审计状态：0-待审核, 1-已通过, 2-已拒绝
    audit_comment TEXT,                   -- 审计意见/拒绝原因
    audit_time TIMESTAMP,                 -- 审计时间
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_audit_post FOREIGN KEY (post_id) REFERENCES t_post(id),
    CONSTRAINT fk_post_audit_auditor FOREIGN KEY (auditor_id) REFERENCES t_user(id)
);

-- 3. 创建索引
CREATE INDEX IF NOT EXISTS idx_post_audit_post_id ON t_post_audit(post_id);
CREATE INDEX IF NOT EXISTS idx_post_audit_auditor_id ON t_post_audit(auditor_id);
CREATE INDEX IF NOT EXISTS idx_post_audit_status ON t_post_audit(audit_status);
CREATE INDEX IF NOT EXISTS idx_post_audit_create_time ON t_post_audit(create_time DESC);

-- 4. 添加注释
COMMENT ON TABLE t_post_audit IS '文章审计记录表';
COMMENT ON COLUMN t_post_audit.post_id IS '文章ID';
COMMENT ON COLUMN t_post_audit.auditor_id IS '审计员ID（NULL表示未审计）';
COMMENT ON COLUMN t_post_audit.audit_status IS '审计状态：0-待审核, 1-已通过, 2-已拒绝';
COMMENT ON COLUMN t_post_audit.audit_comment IS '审计意见/拒绝原因';
COMMENT ON COLUMN t_post_audit.audit_time IS '审计时间';

-- 5. 为所有已发布的文章创建审计记录（标记为已通过）
INSERT INTO t_post_audit (id, post_id, auditor_id, audit_status, audit_comment, audit_time, create_time, update_time)
SELECT 
    (EXTRACT(EPOCH FROM NOW()) * 1000000)::BIGINT + ROW_NUMBER() OVER ()::BIGINT,
    p.id,
    NULL,
    1,  -- 已通过
    '历史文章自动通过',
    p.publish_time,
    p.create_time,
    p.update_time
FROM t_post p
WHERE p.status = 1  -- 已发布的文章
AND NOT EXISTS (
    SELECT 1 FROM t_post_audit pa WHERE pa.post_id = p.id
);

-- 6. 为待审核的文章创建审计记录（如果有status=3的文章）
INSERT INTO t_post_audit (id, post_id, auditor_id, audit_status, audit_comment, audit_time, create_time, update_time)
SELECT 
    (EXTRACT(EPOCH FROM NOW()) * 1000000)::BIGINT + ROW_NUMBER() OVER ()::BIGINT + 100000,
    p.id,
    NULL,
    0,  -- 待审核
    NULL,
    NULL,
    p.create_time,
    p.update_time
FROM t_post p
WHERE p.status = 3  -- 待审核的文章
AND NOT EXISTS (
    SELECT 1 FROM t_post_audit pa WHERE pa.post_id = p.id
);
