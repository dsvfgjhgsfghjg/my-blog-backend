#!/bin/bash

# 登录设备信息记录功能测试脚本

echo "======================================"
echo "登录设备信息记录功能测试"
echo "======================================"
echo ""

# 1. 检查数据库表是否存在
echo "1. 检查 t_login_log 表..."
docker exec -i myblog-postgres psql -U Leoxiaoblog -d myblog -c "\dt t_login_log" | grep -q "t_login_log"
if [ $? -eq 0 ]; then
    echo "✅ t_login_log 表存在"
else
    echo "❌ t_login_log 表不存在"
    exit 1
fi

echo ""
echo "2. 查看表结构..."
docker exec -i myblog-postgres psql -U Leoxiaoblog -d myblog -c "\d t_login_log"

echo ""
echo "3. 检查索引..."
docker exec -i myblog-postgres psql -U Leoxiaoblog -d myblog -c "
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 't_login_log';
"

echo ""
echo "======================================"
echo "测试完成！"
echo "======================================"
echo ""
echo "下一步："
echo "1. 重启后端服务: docker-compose restart backend"
echo "2. 尝试登录系统"
echo "3. 查询登录日志: SELECT * FROM t_login_log ORDER BY login_time DESC LIMIT 5;"
