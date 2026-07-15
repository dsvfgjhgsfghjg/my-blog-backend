#!/bin/bash
# ============================================
# SSL 证书配置快速部署脚本
# ============================================

set -e

echo "=========================================="
echo "MyBlog SSL 证书配置部署"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否在正确的目录
if [ ! -f "docker-compose.ssl.yml" ]; then
    echo -e "${RED}错误: 请在项目根目录运行此脚本${NC}"
    exit 1
fi

echo -e "${YELLOW}步骤 1: 上传配置文件到云端...${NC}"
scp docker-compose.ssl.yml ubuntu@113.31.105.30:~/myblog-deploy/
scp Dockerfile.frontend ubuntu@113.31.105.30:~/myblog-deploy/
scp frontend/apache.conf ubuntu@113.31.105.30:~/myblog-deploy/frontend/
echo -e "${GREEN}✓ 文件上传完成${NC}"
echo ""

echo -e "${YELLOW}步骤 2: 在云端创建必要目录...${NC}"
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && mkdir -p certbot/conf certbot/www frontend-logs logs"
echo -e "${GREEN}✓ 目录创建完成${NC}"
echo ""

echo -e "${YELLOW}步骤 3: 停止旧容器...${NC}"
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && docker-compose down || true"
echo -e "${GREEN}✓ 旧容器已停止${NC}"
echo ""

echo -e "${YELLOW}步骤 4: 构建前端容器（支持 SSL）...${NC}"
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && docker-compose -f docker-compose.ssl.yml build frontend"
echo -e "${GREEN}✓ 前端容器构建完成${NC}"
echo ""

echo -e "${YELLOW}步骤 5: 启动所有服务...${NC}"
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && docker-compose -f docker-compose.ssl.yml up -d"
echo -e "${GREEN}✓ 服务已启动${NC}"
echo ""

echo -e "${YELLOW}步骤 6: 检查容器状态...${NC}"
ssh ubuntu@113.31.105.30 "docker ps | grep -E 'frontend|backend'"
echo ""

echo -e "${YELLOW}步骤 7: 验证 HTTP 访问...${NC}"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://113.31.105.30)
if [ "$HTTP_STATUS" = "301" ]; then
    echo -e "${GREEN}✓ HTTP 重定向正常 (301)${NC}"
else
    echo -e "${RED}✗ HTTP 重定向异常 (状态码: $HTTP_STATUS)${NC}"
fi
echo ""

echo "=========================================="
echo -e "${GREEN}部署完成！${NC}"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. SSH 连接到云端服务器申请 Let's Encrypt 证书"
echo "   ssh ubuntu@113.31.105.30"
echo ""
echo "2. 参考文档申请证书："
echo "   docs/deployment/LETSENCRYPT_SSL_SETUP.md"
echo ""
echo "3. 申请证书命令示例："
echo "   sudo certbot certonly --webroot \\"
echo "     --webroot-path /home/ubuntu/myblog-deploy/certbot/www \\"
echo "     -d myblog.com \\"
echo "     -d www.myblog.com"
echo ""
echo "4. 复制证书后重启前端容器："
echo "   docker-compose -f docker-compose.ssl.yml restart frontend"
echo ""
echo "⚠️  注意：Let's Encrypt 有速率限制（每周最多 50 个证书）"
echo "    建议先用 --dry-run 测试"
echo ""
