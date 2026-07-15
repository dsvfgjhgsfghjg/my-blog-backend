#!/bin/bash

# UA安全拦截功能测试脚本

echo "======================================"
echo "UA安全拦截功能测试"
echo "======================================"
echo ""

BACKEND_URL="http://localhost:8080"

# 1. 测试空UA（应该被拦截）
echo "1. 测试空User-Agent..."
curl -s -o /dev/null -w "HTTP状态码: %{http_code}\n" \
  -H "User-Agent:" \
  "$BACKEND_URL/api/posts"
echo ""

# 2. 测试Kali UA（应该返回蜜罐数据）
echo "2. 测试Kali User-Agent（蜜罐）..."
RESPONSE=$(curl -s -w "\nHTTP状态码: %{http_code}" \
  -H "User-Agent: Mozilla/5.0 (Kali Linux)" \
  "$BACKEND_URL/api/posts")
echo "$RESPONSE" | head -5
echo ""

# 3. 测试sqlmap UA（应该返回蜜罐数据）
echo "3. 测试sqlmap User-Agent（蜜罐）..."
RESPONSE=$(curl -s -w "\nHTTP状态码: %{http_code}" \
  -H "User-Agent: sqlmap/1.5" \
  "$BACKEND_URL/api/users")
echo "$RESPONSE" | head -5
echo ""

# 4. 测试python-requests UA（应该被拦截）
echo "4. 测试python-requests User-Agent（拦截）..."
curl -s -o /dev/null -w "HTTP状态码: %{http_code}\n" \
  -H "User-Agent: python-requests/2.28.0" \
  "$BACKEND_URL/api/posts"
echo ""

# 5. 测试正常浏览器UA（应该正常访问）
echo "5. 测试正常浏览器User-Agent（应该成功）..."
curl -s -o /dev/null -w "HTTP状态码: %{http_code}\n" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
  "$BACKEND_URL/api/posts"
echo ""

# 6. 查看数据库中的黑名单规则
echo "6. 查看UA黑名单规则..."
docker exec -i myblog-postgres psql -U Leoxiaoblog -d myblog -c "
SELECT pattern, description, threat_level, action_type, is_enabled 
FROM t_ua_blacklist 
ORDER BY 
    CASE threat_level 
        WHEN 'CRITICAL' THEN 1 
        WHEN 'HIGH' THEN 2 
        WHEN 'MEDIUM' THEN 3 
        WHEN 'LOW' THEN 4 
    END;
"

echo ""
echo "======================================"
echo "测试完成！"
echo "======================================"
echo ""
echo "提示："
echo "- 蜜罐请求会返回200状态码和假数据"
echo "- 拦截请求会返回403状态码"
echo "- 同一IP多次违规会被自动封禁24小时"
