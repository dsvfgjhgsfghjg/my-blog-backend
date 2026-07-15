#!/bin/bash
###############################################################################
# MyBlog 开发环境全量安装脚本
# 适用：Fedora 44 (KDE)
# 功能：一键安装 Java 21 JDK + Docker 兼容层 + Node.js
# 注意：部分命令需要 sudo 权限，请准备好密码
# 日期：2026-07-15
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MyBlog 开发环境安装脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# ============================================================
# 1. Java 21 JDK
# ============================================================
echo -e "${BLUE}[1/4] Java 21 JDK${NC}"
if command -v java &>/dev/null && java -version 2>&1 | grep -q "21."; then
    echo -e "${GREEN}  ✓ 已安装${NC}"
else
    echo -e "${YELLOW}  → 需要安装: sudo dnf install -y java-21-openjdk-devel${NC}"
    NEED_JAVA=true
fi
echo ""

# ============================================================
# 2. Docker（通过 podman-docker 提供 docker 命令兼容）
# ============================================================
echo -e "${BLUE}[2/4] Docker 兼容层${NC}"
if command -v docker &>/dev/null; then
    echo -e "${GREEN}  ✓ docker 命令可用${NC}"
else
    if command -v podman &>/dev/null; then
        echo -e "${YELLOW}  → podman 已安装 (v$(podman --version 2>&1 | grep -oP '\d+\.\d+\.\d+'))，需要安装 podman-docker 提供 docker 命令:${NC}"
        echo -e "${YELLOW}     sudo dnf install -y podman-docker${NC}"
        NEED_PODMAN_DOCKER=true
    else
        echo -e "${RED}  ✗ podman 未安装，需要安装:${NC}"
        echo -e "${YELLOW}     sudo dnf install -y podman podman-docker${NC}"
        NEED_PODMAN=true
    fi
fi
# 检查 docker compose 插件
if ! command -v docker-compose &>/dev/null && ! docker compose version &>/dev/null 2>&1; then
    echo -e "${YELLOW}  → docker-compose 未安装，需要安装:${NC}"
    echo -e "${YELLOW}     sudo dnf install -y docker-compose${NC}"
    NEED_DOCKER_COMPOSE=true
else
    echo -e "${GREEN}  ✓ docker compose 可用${NC}"
fi
echo ""

# ============================================================
# 3. Node.js（前端构建用）
# ============================================================
echo -e "${BLUE}[3/4] Node.js${NC}"
if command -v node &>/dev/null; then
    echo -e "${GREEN}  ✓ Node.js $(node --version) 已安装${NC}"
else
    echo -e "${YELLOW}  → 未安装，需要安装（选用 LTS 版本 20.x）:${NC}"
    echo -e "${YELLOW}     方案A（推荐）— 使用 nvm:${NC}"
    echo -e "${YELLOW}       curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash${NC}"
    echo -e "${YELLOW}       nvm install 20${NC}"
    echo -e "${YELLOW}     方案B — dnf 安装:${NC}"
    echo -e "${YELLOW}       sudo dnf install -y nodejs${NC}"
    NEED_NODE=true
fi
echo ""

# ============================================================
# 4. 容器服务（PostgreSQL + Redis + MinIO）
# ============================================================
echo -e "${BLUE}[4/4] 容器服务${NC}"
echo -e "${YELLOW}  项目需要启动以下服务:${NC}"
echo -e "    • PostgreSQL 14（端口 5432）"
echo -e "    • Redis 7（端口 6379）"
echo -e "    • MinIO（端口 9000/9001）"
echo ""

echo -e "${YELLOW}  使用 podman-compose 启动方式:${NC}"
echo -e "    ${YELLOW}cd /home/leo/IdeaProjects/myblog${NC}"
echo -e "    ${YELLOW}podman-compose -f docker-compose.yml up -d${NC}"
echo ""
echo -e "${YELLOW}  注意: podman 默认以 rootless 模式运行，需要提前配置:${NC}"
echo -e "    ${YELLOW}systemctl --user enable podman.socket${NC}"
echo ""

# ============================================================
# 汇总
# ============================================================
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  需要手动执行的操作汇总${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ "$NEED_JAVA" = true ]; then
    echo -e "  ${YELLOW}1. 安装 Java 21 JDK:${NC}"
    echo -e "     sudo dnf install -y java-21-openjdk-devel"
    echo ""
fi

if [ "$NEED_PODMAN_DOCKER" = true ]; then
    echo -e "  ${YELLOW}2. 安装 docker 兼容层:${NC}"
    echo -e "     sudo dnf install -y podman-docker"
    echo ""
elif [ "$NEED_PODMAN" = true ]; then
    echo -e "  ${YELLOW}2. 安装 podman + docker 兼容层:${NC}"
    echo -e "     sudo dnf install -y podman podman-docker"
    echo ""
fi

if [ "$NEED_DOCKER_COMPOSE" = true ]; then
    echo -e "  ${YELLOW}3. 安装 docker-compose:${NC}"
    echo -e "     sudo dnf install -y docker-compose"
    echo ""
fi

if [ "$NEED_NODE" = true ]; then
    echo -e "  ${YELLOW}4. 安装 Node.js:${NC}"
    echo -e "     sudo dnf install -y nodejs"
    echo ""
fi

echo -e "  ${YELLOW}5. 启动容器服务:${NC}"
echo -e "     cd /home/leo/IdeaProjects/myblog"
echo -e "     podman-compose -f docker-compose.yml up -d"
echo ""

echo -e "  ${YELLOW}6. 构建并运行项目:${NC}"
echo -e "     cd /home/leo/IdeaProjects/myblog"
echo -e "     ./gradlew clean bootJar"
echo ""

echo -e "${GREEN}安装脚本已生成: scripts/setup/setup-full-dev-env.sh${NC}"
echo -e "${GREEN}可直接运行此脚本查看完整指南${NC}"
echo ""