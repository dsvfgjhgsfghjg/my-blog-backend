-- 博客文章模块数据库表结构

-- 1. 文章分类表
CREATE TABLE t_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_category IS '文章分类表';
COMMENT ON COLUMN t_category.name IS '分类名称';
COMMENT ON COLUMN t_category.description IS '分类描述';
COMMENT ON COLUMN t_category.sort_order IS '排序顺序';

-- 创建索引
CREATE INDEX idx_category_sort_order ON t_category(sort_order);

-- 2. 文章标签表
CREATE TABLE t_tag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(20),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_tag IS '文章标签表';
COMMENT ON COLUMN t_tag.name IS '标签名称';
COMMENT ON COLUMN t_tag.color IS '标签颜色（可选，用于前端展示）';

-- 创建索引
CREATE INDEX idx_tag_name ON t_tag(name);

-- 3. 文章表
CREATE TABLE t_post (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(200) UNIQUE,
    summary VARCHAR(500),
    content TEXT NOT NULL,
    word_count INT DEFAULT 0,
    cover_image VARCHAR(500),
    status SMALLINT DEFAULT 0,  -- 0:草稿, 1:已发布, 2:私密
    is_top BOOLEAN DEFAULT FALSE,
    view_count BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    publish_time TIMESTAMP,
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES t_category(id)
);

COMMENT ON TABLE t_post IS '文章表';
COMMENT ON COLUMN t_post.user_id IS '作者ID';
COMMENT ON COLUMN t_post.category_id IS '分类ID';
COMMENT ON COLUMN t_post.title IS '文章标题';
COMMENT ON COLUMN t_post.slug IS 'SEO友好的URL标识';
COMMENT ON COLUMN t_post.summary IS '文章摘要（前150字符）';
COMMENT ON COLUMN t_post.content IS 'Markdown正文';
COMMENT ON COLUMN t_post.word_count IS '字数统计';
COMMENT ON COLUMN t_post.cover_image IS '封面图片URL';
COMMENT ON COLUMN t_post.status IS '状态：0-草稿, 1-已发布, 2-私密';
COMMENT ON COLUMN t_post.is_top IS '是否置顶';
COMMENT ON COLUMN t_post.view_count IS '阅读量';
COMMENT ON COLUMN t_post.is_deleted IS '软删除标记';
COMMENT ON COLUMN t_post.publish_time IS '发布时间';

-- 创建索引
CREATE INDEX idx_post_user_id ON t_post(user_id);
CREATE INDEX idx_post_category_id ON t_post(category_id);
CREATE INDEX idx_post_status ON t_post(status);
CREATE INDEX idx_post_is_deleted ON t_post(is_deleted);
CREATE INDEX idx_post_create_time ON t_post(create_time DESC);
CREATE INDEX idx_post_publish_time ON t_post(publish_time DESC);
CREATE INDEX idx_post_slug ON t_post(slug);
CREATE INDEX idx_post_is_top ON t_post(is_top);

-- 4. 文章-标签关联表（多对多）
CREATE TABLE t_post_tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES t_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES t_tag(id) ON DELETE CASCADE
);

COMMENT ON TABLE t_post_tag IS '文章-标签关联表';

-- 创建索引
CREATE INDEX idx_post_tag_tag_id ON t_post_tag(tag_id);

-- 插入默认分类（可选）
INSERT INTO t_category (name, description, sort_order) VALUES 
('未分类', '默认分类', 0),
('技术', '技术相关文章', 1),
('生活', '生活随笔', 2)
ON CONFLICT (name) DO NOTHING;
