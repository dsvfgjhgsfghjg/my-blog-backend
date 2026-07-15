-- 查看软件开发分类下的所有已发布文章
SELECT 
    p.id::text as id_string,  -- 以字符串形式显示ID
    p.id,
    p.title,
    p.status,
    p.word_count,
    c.name as category_name,
    p.update_time
FROM t_post p
LEFT JOIN t_category c ON p.category_id = c.id
WHERE c.name = '软件开发' 
  AND p.status = 1 
  AND p.is_deleted = false
ORDER BY p.update_time DESC;

-- 查看所有分类的文章数量统计
SELECT 
    c.name as category_name,
    COUNT(p.id) as post_count
FROM t_category c
LEFT JOIN t_post p ON c.id = p.category_id AND p.status = 1 AND p.is_deleted = false
GROUP BY c.name
ORDER BY c.sort_order;
