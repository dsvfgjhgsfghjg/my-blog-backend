#!/bin/bash
# UA安全拦截系统完整测试脚本

BASE_URL="http://localhost:8080"
API_BASE="${BASE_URL}/api"

echo "=========================================="
echo "UA安全拦截系统测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试结果统计
PASS=0
FAIL=0

# 测试函数
test_request() {
    local test_name=$1
    local url=$2
    local ua=$3
    local expected_code=$4
    
    echo -e "${YELLOW}测试: ${test_name}${NC}"
    
    if [ -z "$ua" ]; then
        response=$(curl -s -w "\n%{http_code}" "${url}")
    else
        response=$(curl -s -w "\n%{http_code}" -H "User-Agent: ${ua}" "${url}")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "$expected_code" ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP ${http_code})"
        ((PASS++))
    else
        echo -e "${RED}✗ FAIL${NC} (期望: ${expected_code}, 实际: ${http_code})"
        echo "响应: ${body}"
        ((FAIL++))
    fi
    echo ""
}

echo "1. 正常浏览器访问（应该通过）"
echo "-------------------------------------------"
test_request "Chrome浏览器" \
    "${API_BASE}/posts" \
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
    "200"

test_request "Firefox浏览器" \
    "${API_BASE}/posts" \
    "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0" \
    "200"

echo ""
echo "2. 空UA测试（应该被拦截）"
echo "-------------------------------------------"
test_request "空User-Agent" \
    "${API_BASE}/posts" \
    "" \
    "403"

echo ""
echo "3. Kali Linux UA测试（蜜罐模式，返回200+假数据）"
echo "-------------------------------------------"
test_request "Kali Linux" \
    "${API_BASE}/posts" \
    "Mozilla/5.0 (X11; Linux x86_64; Kali GNU/Linux Rolling) AppleWebKit/537.36" \
    "200"

echo ""
echo "4. SQLMap工具测试（蜜罐模式，返回200+假数据）"
echo "-------------------------------------------"
test_request "SQLMap" \
    "${API_BASE}/posts" \
    "sqlmap/1.7.12#stable (https://sqlmap.org)" \
    "200"

echo ""
echo "5. Nmap扫描器测试（蜜罐模式，返回200+假数据）"
echo "-------------------------------------------"
test_request "Nmap" \
    "${API_BASE}/posts" \
    "Mozilla/5.0 (compatible; Nmap Scripting Engine; https://nmap.org/book/nse.html)" \
    "200"

echo ""
echo "6. Python脚本测试（应该被拦截）"
echo "-------------------------------------------"
test_request "python-requests" \
    "${API_BASE}/posts" \
    "python-requests/2.31.0" \
    "403"

test_request "python-urllib" \
    "${API_BASE}/posts" \
    "Python-urllib/3.11" \
    "403"

echo ""
echo "7. Java客户端测试（应该被拦截）"
echo "-------------------------------------------"
test_request "Java HTTP Client" \
    "${API_BASE}/posts" \
    "Java/17.0.2" \
    "403"

echo ""
echo "8. Go语言HTTP客户端测试（应该被拦截）"
echo "-------------------------------------------"
test_request "Go HTTP Client" \
    "${API_BASE}/posts" \
    "Go-http-client/1.1" \
    "403"

echo ""
echo "9. Nikto扫描器测试（应该被拦截）"
echo "-------------------------------------------"
test_request "Nikto" \
    "${API_BASE}/posts" \
    "Mozilla/5.00 (Nikto/2.1.6) (Evasions:None) (Test:map_codes)" \
    "403"

echo ""
echo "10. DirBuster扫描器测试（应该被拦截）"
echo "-------------------------------------------"
test_request "DirBuster" \
    "${API_BASE}/posts" \
    "DirBuster-1.0-RC1 (http://www.owasp.org/index.php/Category:OWASP_DirBuster_Project)" \
    "403"

echo ""
echo "=========================================="
echo "测试结果汇总"
echo "=========================================="
echo -e "通过: ${GREEN}${PASS}${NC}"
echo -e "失败: ${RED}${FAIL}${NC}"
echo "总计: $((PASS + FAIL))"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！${NC}"
else
    echo -e "${RED}部分测试失败，请检查配置${NC}"
fi

echo ""
echo "提示："
echo "- 蜜罐模式的请求会返回200和假数据，用于迷惑攻击者"
echo "- 拦截模式的请求会返回403拒绝访问"
echo "- 可以查看数据库 t_security_event 表了解详细的安全事件记录"
echo "- 可以查看Redis键 'security:banned:*' 了解被封禁的IP"
