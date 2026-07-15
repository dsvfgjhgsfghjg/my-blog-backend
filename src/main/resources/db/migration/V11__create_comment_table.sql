-- 创建评论表
CREATE TABLE IF NOT EXISTS t_comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,              -- 文章ID
    user_id BIGINT NOT NULL,              -- 评论者ID
    parent_id BIGINT,                     -- 父评论ID（回复用，NULL表示顶级评论）
    reply_to_user_id BIGINT,              -- 被回复的用户ID（用于@功能）
    content TEXT NOT NULL,                -- 评论内容
    like_count INT DEFAULT 0,             -- 点赞数
    status SMALLINT DEFAULT 1,            -- 状态：1正常 0隐藏 -1已删除
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES t_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES t_comment(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_reply_to_user FOREIGN KEY (reply_to_user_id) REFERENCES t_user(id)
);

-- 添加注释
COMMENT ON TABLE t_comment IS '评论表';
COMMENT ON COLUMN t_comment.post_id IS '文章ID';
COMMENT ON COLUMN t_comment.user_id IS '评论者ID';
COMMENT ON COLUMN t_comment.parent_id IS '父评论ID（NULL表示顶级评论）';
COMMENT ON COLUMN t_comment.reply_to_user_id IS '被回复的用户ID';
COMMENT ON COLUMN t_comment.content IS '评论内容';
COMMENT ON COLUMN t_comment.like_count IS '点赞数';
COMMENT ON COLUMN t_comment.status IS '状态：1正常 0隐藏 -1已删除';
COMMENT ON COLUMN t_comment.create_time IS '创建时间';
COMMENT ON COLUMN t_comment.update_time IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_comment_post_id ON t_comment(post_id);
CREATE INDEX IF NOT EXISTS idx_comment_user_id ON t_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent_id ON t_comment(parent_id);
CREATE INDEX IF NOT EXISTS idx_comment_create_time ON t_comment(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_comment_status ON t_comment(status);
