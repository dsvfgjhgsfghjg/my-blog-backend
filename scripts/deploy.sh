#!/bin/bash
# ============================================
# MyBlog 一键部署脚本（目标机器使用）
# 前提：目标机器已安装 Docker + Docker Compose
# ============================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SITE_HTTPS="n"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        MyBlog 一键部署脚本 v1.0              ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# 1. 检测 Docker
echo -e "${YELLOW}[1/5] 检测运行环境...${NC}"
if ! command -v docker &>/dev/null; then
    echo -e "${RED}❌ Docker 未安装！请先安装 Docker${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker: $(docker --version)${NC}"

if docker compose version &>/dev/null; then
    echo -e "${GREEN}✅ Docker Compose: $(docker compose version)${NC}"
else
    echo -e "${RED}❌ Docker Compose 未安装！${NC}"
    exit 1
fi

# 2. 询问是否启用 SSL
echo -e "${YELLOW}[2/5] HTTPS 配置...${NC}"
echo "请选择前端服务是否启用 HTTPS（SSL）："
echo ""
echo -e "  ${YELLOW}1) 不启用 HTTPS（HTTP 模式，适合本地测试）${NC}"
echo -e "  ${GREEN}2) 启用 HTTPS（需要 SSL 证书）${NC}"
echo ""
while true; do
    read -p "$(echo -e "${BLUE}请输入数字 [1/2]${NC}: ")" ssl_choice
    case $ssl_choice in
        1) SITE_HTTPS="n"; break;;
        2) SITE_HTTPS="y"; break;;
        *) echo -e "${RED}请输入 1 或 2${NC}";;
    esac
done
echo ""

# 3. 配置 .env
echo -e "${YELLOW}[3/5] 配置环境变量...${NC}"
cd "$PROJECT_DIR"

if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo -e "${GREEN}✅ 已从 .env.example 创建 .env 文件${NC}"
        echo -e "${YELLOW}⚠️  请编辑 .env 文件，修改数据库密码、JWT密钥等配置！${NC}"
        echo -e "   执行: vim ${PROJECT_DIR}/.env"
        echo ""
        read -p "$(echo -e "${YELLOW}编辑完成后按 Enter 继续...${NC}")"
    else
        echo -e "${RED}❌ 找不到 .env.example 文件${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}✅ .env 文件已存在${NC}"
fi

# 4. 根据 SSL 选择切换 Apache 配置
echo -e "${YELLOW}[4/5] 准备前端配置...${NC}"
cd "$PROJECT_DIR"
if [ "$SITE_HTTPS" = "n" ]; then
    if [ -f "frontend/apache.no-ssl.conf" ]; then
        cp "frontend/apache.conf" "frontend/apache.conf.ssl.bak" 2>/dev/null || true
        cp "frontend/apache.no-ssl.conf" "frontend/apache.conf"
        echo -e "${GREEN}✅ 已切换到 HTTP 模式（无 SSL）${NC}"
    else
        echo -e "${YELLOW}⚠️  找不到 apache.no-ssl.conf，使用默认配置${NC}"
    fi
else
    echo -e "${GREEN}✅ 使用 SSL 模式${NC}"
fi
echo ""

# 5. 构建并启动
echo -e "${YELLOW}[5/5] 构建并启动服务...${NC}"
echo -e "${GREEN}⏳ 首次启动将下载 Docker 镜像，请耐心等待...${NC}"
echo ""

cd "$PROJECT_DIR"
docker compose -f deploy/docker-compose.prod.yml up -d --build

echo ""
echo -e "${GREEN}✅ 服务已启动${NC}"

# 等待服务就绪
echo -e "等待服务就绪（约 30 秒）..."
sleep 10

# 检查后端健康
echo -e "正在检查后端服务..."
for i in $(seq 1 20); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health 2>/dev/null | grep -q "200"; then
        echo -e "${GREEN}✅ 后端服务已就绪${NC}"
        break
    fi
    if [ $i -eq 20 ]; then
        echo -e "${YELLOW}⚠️  后端服务可能尚未完全启动，请稍后检查${NC}"
    fi
    sleep 2
done

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        🎉 MyBlog 部署完成！                   ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
echo ""
echo -e "访问地址:"
echo -e "  前端:     http://localhost/"
echo -e "  后端 API: http://localhost:18080/api/"
echo -e "  MinIO:    http://localhost:9001/"
echo ""
echo -e "管理命令:"
echo -e "  查看日志:  docker compose -f deploy/docker-compose.prod.yml logs -f"
echo -e "  停止服务:  docker compose -f deploy/docker-compose.prod.yml down"
echo -e "  重启服务:  docker compose -f deploy/docker-compose.prod.yml restart"
echo -e "  查看状态:  docker compose -f deploy/docker-compose.prod.yml ps"
echo ""
echo -e "${YELLOW}注意:${NC}"
echo -e "  - 如需使用 HTTPS，请自行配置 SSL 证书"
echo -e "  - 如需导入验证码背景图，执行: bash scripts/upload-captcha-to-minio.sh"
echo ""
