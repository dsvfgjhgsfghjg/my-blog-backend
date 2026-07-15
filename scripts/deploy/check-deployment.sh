#!/bin/bash

# MyBlog 部署检查脚本
# 用于验证所有服务是否正常运行

set -e

echo "========================================="
echo "  MyBlog 部署健康检查"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker Compose 文件是否存在
if [ ! -f "docker-compose.prod.yml" ]; then
    echo -e "${RED}错误: docker-compose.prod.yml 文件不存在${NC}"
    exit 1
fi

# 检查容器状态
echo -e "${YELLOW}检查容器状态...${NC}"
CONTAINERS=$(docker-compose -f docker-compose.prod.yml ps --services 2>/dev/null)

if [ -z "$CONTAINERS" ]; then
    echo -e "${RED}错误: 没有运行中的容器${NC}"
    echo -e "${YELLOW}提示: 请先运行 ./deploy.sh 部署应用${NC}"
    exit 1
fi

for container in $CONTAINERS; do
    STATUS=$(docker-compose -f docker-compose.prod.yml ps $container | tail -n +2 | awk '{print $NF}')
    if [[ $STATUS == *"Up"* ]]; then
        echo -e "${GREEN}✓ $container: 运行中${NC}"
    else
        echo -e "${RED}✗ $container: $STATUS${NC}"
    fi
done
echo ""

# 检查端口监听
echo -e "${YELLOW}检查端口监听...${NC}"
PORTS=(80 8080 5432 6379 9000 9001)
for port in "${PORTS[@]}"; do
    if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
        echo -e "${GREEN}✓ 端口 $port: 监听中${NC}"
    else
        echo -e "${YELLOW}⚠ 端口 $port: 未监听${NC}"
    fi
done
echo ""

# 测试后端 API
echo -e "${YELLOW}测试后端 API...${NC}"
if command -v curl &> /dev/null; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" == "200" ]; then
        echo -e "${GREEN}✓ 后端 API: 正常 (HTTP $HTTP_CODE)${NC}"
    else
        echo -e "${YELLOW}⚠ 后端 API: 异常 (HTTP $HTTP_CODE)${NC}"
        echo -e "${YELLOW}提示: 等待应用启动或检查日志${NC}"
    fi
else
    echo -e "${YELLOW}⚠ curl 未安装，跳过 API 测试${NC}"
fi
echo ""

# 测试前端
echo -e "${YELLOW}测试前端服务...${NC}"
if command -v curl &> /dev/null; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:80/ 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" == "200" ]; then
        echo -e "${GREEN}✓ 前端服务: 正常 (HTTP $HTTP_CODE)${NC}"
    else
        echo -e "${YELLOW}⚠ 前端服务: 异常 (HTTP $HTTP_CODE)${NC}"
    fi
else
    echo -e "${YELLOW}⚠ curl 未安装，跳过前端测试${NC}"
fi
echo ""

# 检查磁盘空间
echo -e "${YELLOW}检查磁盘空间...${NC}"
DISK_USAGE=$(df -h / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -lt 80 ]; then
    echo -e "${GREEN}✓ 磁盘使用率: ${DISK_USAGE}%${NC}"
else
    echo -e "${RED}⚠ 磁盘使用率: ${DISK_USAGE}% (建议清理)${NC}"
fi
echo ""

# 检查内存使用
echo -e "${YELLOW}检查内存使用...${NC}"
if command -v free &> /dev/null; then
    MEMORY_USAGE=$(free | grep Mem | awk '{printf "%.0f", $3/$2 * 100}')
    if [ "$MEMORY_USAGE" -lt 80 ]; then
        echo -e "${GREEN}✓ 内存使用率: ${MEMORY_USAGE}%${NC}"
    else
        echo -e "${YELLOW}⚠ 内存使用率: ${MEMORY_USAGE}% (较高)${NC}"
    fi
else
    echo -e "${YELLOW}⚠ free 命令不可用，跳过内存检查${NC}"
fi
echo ""

# 显示访问信息
echo "========================================="
echo -e "${GREEN}  检查完成！${NC}"
echo "========================================="
echo ""
echo "服务访问地址："
echo "  - 前端页面:     http://$(curl -s ifconfig.me 2>/dev/null || echo '你的服务器IP')/"
echo "  - 后端 API:     http://$(curl -s ifconfig.me 2>/dev/null || echo '你的服务器IP'):8080/"
echo "  - MinIO Console: http://$(curl -s ifconfig.me 2>/dev/null || echo '你的服务器IP'):9001/"
echo ""
echo "查看详细日志:"
echo "  docker-compose -f docker-compose.prod.yml logs -f"
echo ""
