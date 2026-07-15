-- 添加主页三个主要分类
INSERT INTO t_category (name, description, sort_order) VALUES 
('软件开发', '软件开发相关文章，包括前端、后端、移动开发等', 3),
('AI', '人工智能、机器学习、深度学习等相关文章', 4),
('网络安全', '网络安全、渗透测试、安全防护等相关文章', 5)
ON CONFLICT (name) DO NOTHING;
