#!/bin/bash
# ============================================
# MyBlog 卸载脚本
# 支持两种卸载模式：
#   1. 保留数据卸载 — 仅停止并删除容器，保留数据卷
#   2. 清除数据卸载 — 停止并删除容器、数据卷、镜像
# ============================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║        MyBlog 卸载工具                       ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# 检查 docker 和 docker-compose
if ! command -v docker &>/dev/null; then
    echo -e "${RED}❌ Docker 未安装，跳过 Docker 相关操作${NC}"
fi

# 检查 compose 文件
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.prod.yml"
if [ ! -f "$COMPOSE_FILE" ]; then
    echo -e "${YELLOW}⚠️  找不到 docker-compose.prod.yml${NC}"
    echo "   将尝试通过容器名直接删除容器"
    COMPOSE_FILE=""
fi

# ============================================
# 选择卸载模式
# ============================================
echo "请选择卸载模式："
echo ""
echo -e "  ${GREEN}1) 保留数据卸载${NC}"
echo "     停止并删除所有 Docker 容器"
echo "     保留 PostgreSQL、Redis、MinIO 的数据卷"
echo "     保留 Docker 镜像"
echo "     如需重新部署，数据不会丢失"
echo ""
echo -e "  ${RED}2) 清除数据卸载${NC}"
echo "     停止并删除所有 Docker 容器"
echo "     删除 PostgreSQL、Redis、MinIO 的数据卷（数据永久丢失！）"
echo "     删除已构建的 Docker 镜像"
echo "     删除安装锁文件和 .env 配置文件"
echo ""

while true; do
    read -p "$(echo -e "${BLUE}请输入数字 [1/2]${NC}: ")" UNINSTALL_MODE
    case $UNINSTALL_MODE in
        1) UNINSTALL_MODE="soft"; break;;
        2) UNINSTALL_MODE="clean"; break;;
        *) echo -e "${RED}请输入 1 或 2${NC}";;
    esac
done
echo ""

if [ "$UNINSTALL_MODE" = "clean" ]; then
    echo -e "${RED}⚠️  你选择了「清除数据卸载」，所有数据将永久丢失！${NC}"
    echo ""
    read -p "$(echo -e "${YELLOW}输入 YES 确认卸载，其他任意键取消: ${NC}")" confirm
    if [ "$confirm" != "YES" ]; then
        echo -e "${GREEN}卸载已取消${NC}"
        exit 0
    fi
fi

echo ""
echo -e "${YELLOW}开始卸载 MyBlog...${NC}"
echo ""

# ============================================
# 1. 停止并删除容器
# ============================================
echo -e "${YELLOW}[1/4] 停止并删除 Docker 容器...${NC}"

if [ -n "$COMPOSE_FILE" ]; then
    cd "$PROJECT_DIR"
    docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>&1 || true
    echo -e "${GREEN}✅ Docker Compose 服务已停止并删除${NC}"
else
    # 如果没有 compose 文件，通过容器名逐个删除
    for container in myblog-postgres myblog-redis myblog-minio myblog-backend myblog-frontend; do
        if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
            docker rm -f "$container" 2>/dev/null || true
            echo -e "  ${GREEN}✅${NC} 已删除容器: $container"
        fi
    done
fi

# ============================================
# 2. 删除数据卷（仅 clean 模式）
# ============================================
echo -e "${YELLOW}[2/4] 处理 Docker 数据卷...${NC}"

if [ "$UNINSTALL_MODE" = "clean" ]; then
    echo -e "  清除模式：删除所有数据卷..."
    for volume in myblog_postgres-data myblog_redis-data myblog_minio-data; do
        if docker volume ls --format '{{.Name}}' | grep -q "^${volume}$"; then
            docker volume rm "$volume" 2>/dev/null || true
            echo -e "  ${GREEN}✅${NC} 已删除数据卷: $volume"
        fi
    done
    # 也尝试不带前缀的卷名
    for volume in postgres-data redis-data minio-data; do
        if docker volume ls --format '{{.Name}}' | grep -q "^${volume}$"; then
            docker volume rm "$volume" 2>/dev/null || true
            echo -e "  ${GREEN}✅${NC} 已删除数据卷: $volume"
        fi
    done
    echo -e "${GREEN}✅ 数据卷已全部删除${NC}"
else
    echo -e "  保留模式：跳过数据卷删除"
    echo -e "${GREEN}✅ 数据卷已保留${NC}"
fi

# ============================================
# 3. 删除 Docker 镜像（仅 clean 模式）
# ============================================
echo -e "${YELLOW}[3/4] 处理 Docker 镜像...${NC}"

if [ "$UNINSTALL_MODE" = "clean" ]; then
    echo -e "  清除模式：删除 myblog 相关镜像..."
    # 删除通过 compose 构建的镜像
    docker images --format '{{.Repository}}:{{.Tag}}' | grep "^myblog" | while read -r img; do
        docker rmi "$img" 2>/dev/null || true
        echo -e "  ${GREEN}✅${NC} 已删除镜像: $img"
    done
    # 也删除 <none> 标签的 dangling 镜像
    docker image prune -f 2>/dev/null || true
    echo -e "${GREEN}✅ 镜像已清理${NC}"
else
    echo -e "  保留模式：跳过镜像删除"
    echo -e "${GREEN}✅ 镜像已保留${NC}"
fi

# ============================================
# 4. 删除本地文件
# ============================================
echo -e "${YELLOW}[4/4] 处理本地配置文件...${NC}"

cd "$PROJECT_DIR"

# 删除安装锁文件
if [ -f ".myblog_installed" ]; then
    rm -f ".myblog_installed"
    echo -e "  ${GREEN}✅${NC} 已删除安装锁文件 (.myblog_installed)"
fi

if [ "$UNINSTALL_MODE" = "clean" ]; then
    # 删除 .env 配置文件
    if [ -f ".env" ]; then
        rm -f ".env"
        echo -e "  ${GREEN}✅${NC} 已删除 .env 配置文件"
    fi
    # 恢复 Apache 配置备份（如果有）
    if [ -f "frontend/apache.conf.ssl.bak" ]; then
        mv "frontend/apache.conf.ssl.bak" "frontend/apache.conf" 2>/dev/null || true
        echo -e "  ${GREEN}✅${NC} 已恢复 Apache SSL 配置"
    fi
else
    echo -e "  保留模式：跳过配置文件删除"
fi

echo ""
echo -e "${CYAN}══════════════════════════════════════════════${NC}"
echo -e "${CYAN}  ✅ MyBlog 卸载完成！${NC}"
echo -e "${CYAN}══════════════════════════════════════════════${NC}"
echo ""

if [ "$UNINSTALL_MODE" = "soft" ]; then
    echo -e "${GREEN}数据卷已保留，重新部署后数据仍然可用。${NC}"
    echo ""
    echo "重新部署步骤："
    echo "  cd ${PROJECT_DIR}"
    echo "  bash scripts/deploy.sh"
    echo ""
    echo "或手动启动："
    echo "  docker compose -f docker-compose.prod.yml up -d --build"
else
    echo "如需重新部署，请重新解压发行包或执行："
    echo "  cd ${PROJECT_DIR}"
    echo "  bash scripts/deploy.sh"
fi
echo ""