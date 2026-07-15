-- 创建评论点赞记录表
CREATE TABLE IF NOT EXISTS t_comment_like (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,           -- 评论ID
    user_id BIGINT NOT NULL,              -- 点赞用户ID
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：一个用户对一条评论只能点赞一次
    CONSTRAINT uk_comment_user UNIQUE (comment_id, user_id),
    
    -- 外键约束
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES t_comment(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
);

-- 添加注释
COMMENT ON TABLE t_comment_like IS '评论点赞记录表';
COMMENT ON COLUMN t_comment_like.comment_id IS '评论ID';
COMMENT ON COLUMN t_comment_like.user_id IS '点赞用户ID';
COMMENT ON COLUMN t_comment_like.create_time IS '点赞时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_comment_like_comment_id ON t_comment_like(comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_like_user_id ON t_comment_like(user_id);
