#!/bin/bash

# ============================================
# Gitea 自动化部署脚本
# ============================================

set -e

echo "=========================================="
echo "开始部署 Gitea + PostgreSQL"
echo "=========================================="

# 配置变量
GITEA_IMAGE="04eh77udwizz5vqdbk.xuanyuan.run/gitea/gitea:latest"
POSTGRES_IMAGE="04eh77udwizz5vqdbk.xuanyuan.run/library/postgres:18-bookworm"
NETWORK_NAME="gitea-network"
POSTGRES_CONTAINER="gitea-postgres"
GITEA_CONTAINER="gitea-server"

# PostgreSQL 配置
DB_USER="gitea"
DB_PASSWORD="your-password-here"
DB_NAME="gitea"

# 数据目录
DATA_DIR="$HOME/gitea-data"
POSTGRES_DATA="$DATA_DIR/postgres"
GITEA_DATA="$DATA_DIR/gitea"

echo ""
echo "1. 创建数据目录..."
mkdir -p "$POSTGRES_DATA"
mkdir -p "$GITEA_DATA"
chmod -R 755 "$DATA_DIR"

echo ""
echo "2. 创建 Docker 网络..."
docker network create "$NETWORK_NAME" 2>/dev/null || echo "网络已存在"

echo ""
echo "3. 拉取镜像..."
docker pull "$POSTGRES_IMAGE"
docker pull "$GITEA_IMAGE"

echo ""
echo "4. 启动 PostgreSQL 容器..."
docker run -d \
  --name "$POSTGRES_CONTAINER" \
  --network "$NETWORK_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB="$DB_NAME" \
  -v "$POSTGRES_DATA:/var/lib/postgresql/data" \
  -p 5432:5432 \
  --restart unless-stopped \
  "$POSTGRES_IMAGE"

echo "等待 PostgreSQL 启动..."
sleep 5

# 检查 PostgreSQL 是否正常运行
if docker ps | grep -q "$POSTGRES_CONTAINER"; then
  echo "✓ PostgreSQL 启动成功"
else
  echo "✗ PostgreSQL 启动失败"
  exit 1
fi

echo ""
echo "5. 启动 Gitea 容器..."
docker run -d \
  --name "$GITEA_CONTAINER" \
  --network "$NETWORK_NAME" \
  -e USER_UID=1000 \
  -e USER_GID=1000 \
  -e GITEA__database__DB_TYPE=postgres \
  -e GITEA__database__HOST="$POSTGRES_CONTAINER:5432" \
  -e GITEA__database__NAME="$DB_NAME" \
  -e GITEA__database__USER="$DB_USER" \
  -e GITEA__database__PASSWD="$DB_PASSWORD" \
  -e GITEA__server__DOMAIN=113.31.105.30 \
  -e GITEA__server__HTTP_PORT=3000 \
  -e GITEA__server__ROOT_URL=http://113.31.105.30:3000/ \
  -e GITEA__server__SSH_DOMAIN=113.31.105.30 \
  -e GITEA__server__SSH_PORT=222 \
  -e GITEA__server__SSH_LISTEN_PORT=22 \
  -v "$GITEA_DATA:/data" \
  -p 3000:3000 \
  -p 222:22 \
  --restart unless-stopped \
  "$GITEA_IMAGE"

echo "等待 Gitea 启动..."
sleep 10

# 检查 Gitea 是否正常运行
if docker ps | grep -q "$GITEA_CONTAINER"; then
  echo "✓ Gitea 启动成功"
else
  echo "✗ Gitea 启动失败"
  echo "查看日志："
  docker logs "$GITEA_CONTAINER" --tail 20
  exit 1
fi

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "访问信息："
echo "  - Gitea Web: http://113.31.105.30:3000"
echo "  - SSH 端口: 222"
echo "  - PostgreSQL: localhost:5432"
echo ""
echo "数据库信息："
echo "  - 用户: $DB_USER"
echo "  - 密码: $DB_PASSWORD"
echo "  - 数据库: $DB_NAME"
echo ""
echo "数据目录："
echo "  - $DATA_DIR"
echo ""
echo "常用命令："
echo "  - 查看日志: docker logs -f $GITEA_CONTAINER"
echo "  - 停止服务: docker stop $GITEA_CONTAINER $POSTGRES_CONTAINER"
echo "  - 启动服务: docker start $POSTGRES_CONTAINER $GITEA_CONTAINER"
echo "  - 重启服务: docker restart $GITEA_CONTAINER $POSTGRES_CONTAINER"
echo ""
echo "首次访问需要初始化配置，请使用浏览器访问 http://113.31.105.30:3000"
echo "=========================================="
