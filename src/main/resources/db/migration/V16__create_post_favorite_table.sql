-- V16: 创建文章收藏表
CREATE TABLE IF NOT EXISTS t_post_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_post UNIQUE (user_id, post_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_post FOREIGN KEY (post_id) REFERENCES t_post(id) ON DELETE CASCADE
);

COMMENT ON TABLE t_post_favorite IS '文章收藏表';
COMMENT ON COLUMN t_post_favorite.id IS '主键ID';
COMMENT ON COLUMN t_post_favorite.user_id IS '用户ID';
COMMENT ON COLUMN t_post_favorite.post_id IS '文章ID';
COMMENT ON COLUMN t_post_favorite.create_time IS '收藏时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_favorite_user_id ON t_post_favorite(user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_post_id ON t_post_favorite(post_id);
