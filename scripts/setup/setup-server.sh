#!/bin/bash

# MyBlog 服务器初始化脚本
# 此脚本用于在新的 Ubuntu 服务器上快速安装所需软件

set -e

echo "========================================="
echo "  MyBlog 服务器初始化"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}请使用 sudo 运行此脚本${NC}"
    exit 1
fi

# 更新系统
echo -e "${YELLOW}更新系统包...${NC}"
apt-get update
apt-get upgrade -y
echo -e "${GREEN}✓ 系统更新完成${NC}"
echo ""

# 安装必要工具
echo -e "${YELLOW}安装必要工具...${NC}"
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    wget \
    vim \
    git \
    openssl
echo -e "${GREEN}✓ 工具安装完成${NC}"
echo ""

# 安装 Docker
echo -e "${YELLOW}安装 Docker...${NC}"
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | bash
    echo -e "${GREEN}✓ Docker 安装完成${NC}"
else
    echo -e "${GREEN}✓ Docker 已安装${NC}"
fi
echo ""

# 启动 Docker 服务
echo -e "${YELLOW}启动 Docker 服务...${NC}"
systemctl start docker
systemctl enable docker
echo -e "${GREEN}✓ Docker 服务已启动${NC}"
echo ""

# 安装 Docker Compose
echo -e "${YELLOW}安装 Docker Compose...${NC}"
if ! command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep 'tag_name' | cut -d\" -f4)
    curl -L "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    echo -e "${GREEN}✓ Docker Compose 安装完成${NC}"
else
    echo -e "${GREEN}✓ Docker Compose 已安装${NC}"
fi
echo ""

# 配置防火墙
echo -e "${YELLOW}配置防火墙...${NC}"
if command -v ufw &> /dev/null; then
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw allow 8080/tcp
    ufw allow 9000/tcp
    ufw allow 9001/tcp
    echo "y" | ufw enable
    echo -e "${GREEN}✓ 防火墙配置完成${NC}"
else
    echo -e "${YELLOW}⚠ UFW 未安装，跳过防火墙配置${NC}"
fi
echo ""

# 创建部署目录
echo -e "${YELLOW}创建部署目录...${NC}"
mkdir -p ~/my-app/backend
mkdir -p ~/my-app/frontend/dist
echo -e "${GREEN}✓ 目录创建完成${NC}"
echo ""

# 显示下一步操作
echo "========================================="
echo -e "${GREEN}  服务器初始化完成！${NC}"
echo "========================================="
echo ""
echo "下一步操作："
echo "1. 将项目文件上传到 ~/my-app/ 目录"
echo "2. 创建 .env 配置文件（参考 DEPLOYMENT.md）"
echo "3. 运行部署脚本: cd ~/my-app && ./deploy.sh"
echo ""
echo "重要提醒："
echo "- 请修改 .env 文件中的默认密码"
echo "- 生成强 JWT 密钥: openssl rand -base64 64"
echo "- 确保 myblog_backup.dump 文件在 ~/my-app/ 目录下"
echo ""
