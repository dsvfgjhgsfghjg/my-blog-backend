-- 创建博主信息表
CREATE TABLE IF NOT EXISTS t_blogger_info (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL DEFAULT '博主',
    avatar VARCHAR(500),
    email VARCHAR(200),
    github VARCHAR(500),
    introduction TEXT,
    tech_stack TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认记录（ID固定为1）
INSERT INTO t_blogger_info (id, name, create_time, update_time)
VALUES (1, '博主', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 添加注释
COMMENT ON TABLE t_blogger_info IS '博主信息表';
COMMENT ON COLUMN t_blogger_info.id IS '主键ID（固定为1）';
COMMENT ON COLUMN t_blogger_info.name IS '博主名字';
COMMENT ON COLUMN t_blogger_info.avatar IS '头像URL（MinIO对象路径）';
COMMENT ON COLUMN t_blogger_info.email IS '邮箱';
COMMENT ON COLUMN t_blogger_info.github IS 'GitHub地址';
COMMENT ON COLUMN t_blogger_info.introduction IS '简介';
COMMENT ON COLUMN t_blogger_info.tech_stack IS '技术栈（JSON格式）';
COMMENT ON COLUMN t_blogger_info.create_time IS '创建时间';
COMMENT ON COLUMN t_blogger_info.update_time IS '更新时间';
