#!/bin/bash

# MyBlog 云端部署脚本
# 使用方法: chmod +x deploy.sh && ./deploy.sh

set -e  # 遇到错误立即退出

echo "========================================="
echo "  MyBlog 应用部署脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker 是否安装
check_docker() {
    echo -e "${YELLOW}检查 Docker 环境...${NC}"
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker 未安装，请先安装 Docker${NC}"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo -e "${RED}Docker Compose 未安装，请先安装 Docker Compose${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Docker 环境检查通过${NC}"
    echo ""
}

# 停止并清理旧容器
cleanup() {
    echo -e "${YELLOW}停止并清理旧容器...${NC}"
    docker-compose -f docker-compose.prod.yml down || true
    echo -e "${GREEN}✓ 清理完成${NC}"
    echo ""
}

# 构建镜像
build() {
    echo -e "${YELLOW}开始构建 Docker 镜像...${NC}"
    docker-compose -f docker-compose.prod.yml build --no-cache
    echo -e "${GREEN}✓ 镜像构建完成${NC}"
    echo ""
}

# 启动服务
start() {
    echo -e "${YELLOW}启动所有服务...${NC}"
    docker-compose -f docker-compose.prod.yml up -d
    echo -e "${GREEN}✓ 服务启动完成${NC}"
    echo ""
}

# 检查服务状态
check_status() {
    echo -e "${YELLOW}检查服务状态...${NC}"
    sleep 10  # 等待服务启动
    docker-compose -f docker-compose.prod.yml ps
    echo ""
    
    echo -e "${YELLOW}查看后端日志（最近50行）:${NC}"
    docker-compose -f docker-compose.prod.yml logs --tail=50 backend
    echo ""
}

# 显示访问信息
show_info() {
    echo "========================================="
    echo -e "${GREEN}  部署完成！${NC}"
    echo "========================================="
    echo ""
    echo "服务访问地址："
    echo "  - 前端页面:     http://你的服务器IP/"
    echo "  - 后端 API:     http://你的服务器IP:8080/"
    echo "  - MinIO Console: http://你的服务器IP:9001/"
    echo "  - MinIO API:    http://你的服务器IP:9000/"
    echo ""
    echo "数据库信息："
    echo "  - PostgreSQL:   端口 5432"
    echo "  - Redis:        端口 6379"
    echo ""
    echo "常用命令："
    echo "  - 查看日志:     docker-compose -f docker-compose.prod.yml logs -f"
    echo "  - 停止服务:     docker-compose -f docker-compose.prod.yml down"
    echo "  - 重启服务:     docker-compose -f docker-compose.prod.yml restart"
    echo "  - 查看状态:     docker-compose -f docker-compose.prod.yml ps"
    echo ""
}

# 主流程
main() {
    check_docker
    cleanup
    build
    start
    check_status
    show_info
}

# 执行主流程
main
