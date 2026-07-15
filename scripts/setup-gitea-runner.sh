#!/bin/bash

# ============================================
# Gitea Runner 自动化部署脚本
# ============================================

set -e

echo "=========================================="
echo "开始部署 Gitea Runner"
echo "=========================================="

# 配置变量
RUNNER_IMAGE="04eh77udwizz5vqdbk.xuanyuan.run/gitea/runner:1.0.3"
RUNNER_CONTAINER="gitea-runner"
NETWORK_NAME="gitea-network"
DATA_DIR="$HOME/gitea-data/runner"
CONFIG_FILE="$DATA_DIR/config.yaml"

# ---------------------------------------------------------------
# 解析 Runner Token（按优先级）
#   1) --token=<TOKEN>          命令行参数
#   2) 环境变量 GITEA_RUNNER_TOKEN
#   3) --token-file=<FILE>      从文件读取
#   4) 交互式输入（隐藏）
# ---------------------------------------------------------------
RUNNER_TOKEN=""
TOKEN_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --token=*)        RUNNER_TOKEN="${1#*=}"; shift ;;
    --token)          RUNNER_TOKEN="$2"; shift 2 ;;
    --token-file=*)   TOKEN_FILE="${1#*=}"; shift ;;
    --token-file)     TOKEN_FILE="$2"; shift 2 ;;
    -h|--help)
      cat <<'USAGE'
用法: setup-gitea-runner.sh [--token=<TOKEN>] [--token-file=<FILE>]

Runner Token 提供方式（按优先级）：
  1) --token=<TOKEN>          命令行参数
  2) 环境变量 GITEA_RUNNER_TOKEN
  3) --token-file=<FILE>      从文件读取（推荐，避免泄露到 shell 历史）
  4) 交互式输入（输入隐藏）

示例：
  bash setup-gitea-runner.sh --token=xxxxxxxxxx
  GITEA_RUNNER_TOKEN=xxx bash setup-gitea-runner.sh
  bash setup-gitea-runner.sh --token-file=/root/.gitea-runner-token

  # 把 token 保存到文件再调用
  echo "xxx" > /root/.gitea-runner-token && chmod 600 /root/.gitea-runner-token
  bash setup-gitea-runner.sh --token-file=/root/.gitea-runner-token
USAGE
      exit 0 ;;
    *)
      echo "未知参数: $1（使用 --help 查看帮助）"
      exit 1 ;;
  esac
done

# 环境变量回退
if [ -z "$RUNNER_TOKEN" ] && [ -n "$GITEA_RUNNER_TOKEN" ]; then
  RUNNER_TOKEN="$GITEA_RUNNER_TOKEN"
fi

# token 文件回退
if [ -z "$RUNNER_TOKEN" ] && [ -n "$TOKEN_FILE" ] && [ -f "$TOKEN_FILE" ]; then
  RUNNER_TOKEN="$(cat "$TOKEN_FILE")"
fi

# 交互式输入
if [ -z "$RUNNER_TOKEN" ]; then
  echo ""
  echo "请在 Gitea 管理面板中获取 Runner Token："
  echo "  1. 访问 http://113.31.105.30:3000"
  echo "  2. 站点管理 → Actions → Runners → 新增运行器"
  echo "  3. 复制显示的 Token"
  echo ""
  echo "也可以通过 --token / --token-file / 环境变量跳过此提示（见 --help）"
  echo ""
  read -s -p "请输入 Runner Token（输入隐藏）: " RUNNER_TOKEN
  echo ""
fi

if [ -z "$RUNNER_TOKEN" ]; then
  echo "错误：Token 不能为空！"
  exit 1
fi

echo ""
echo "1. 创建数据目录..."
mkdir -p "$DATA_DIR"

echo ""
echo "2. 拉取 Runner 镜像..."
docker pull "$RUNNER_IMAGE"

echo ""
echo "3. 生成 Runner 配置文件..."
cat > "$CONFIG_FILE" <<EOF
log:
  level: info

runner:
  name: myblog-native-runner
  file: .runner
  capacity: 1
  envs:
    DOCKER_HOST: unix:///var/run/docker.sock
  labels:
    - self-hosted
    - ubuntu-latest:docker://node:20-bullseye
    - ubuntu-22.04:docker://node:20-bullseye
    - ubuntu-24.04:docker://node:20-bullseye

cache:
  enabled: true
  dir: /data/cache

container:
  network: $NETWORK_NAME
  privileged: false
  dns:
    - 114.114.114.114
    - 8.8.8.8

host:
  network_mode: ""
EOF

echo "✓ 配置文件已生成：$CONFIG_FILE"

echo ""
echo "4. 启动 Gitea Runner 容器..."

# 停止并删除旧容器
docker stop "$RUNNER_CONTAINER" 2>/dev/null || true
docker rm "$RUNNER_CONTAINER" 2>/dev/null || true

# 启动新容器
docker run -d \
  --name "$RUNNER_CONTAINER" \
  --network "$NETWORK_NAME" \
  -e CONFIG_FILE=/data/config.yaml \
  -e GITEA_INSTANCE_URL=http://113.31.105.30:3000 \
  -e GITEA_RUNNER_REGISTRATION_TOKEN="$RUNNER_TOKEN" \
  -e GITEA_RUNNER_NAME=myblog-native-runner \
  -e GITEA_RUNNER_LABELS="ubuntu-latest:docker://node:20-bullseye,ubuntu-22.04:docker://node:20-bullseye" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$DATA_DIR:/data" \
  --restart unless-stopped \
  "$RUNNER_IMAGE"

echo "等待 Runner 注册..."
sleep 10

# 检查 Runner 是否正常运行
if docker ps | grep -q "$RUNNER_CONTAINER"; then
  echo "✓ Gitea Runner 启动成功"
else
  echo "✗ Gitea Runner 启动失败"
  echo "查看日志："
  docker logs "$RUNNER_CONTAINER" --tail 30
  exit 1
fi

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "Runner 信息："
echo "  - 名称: myblog-native-runner"
echo "  - 网络: $NETWORK_NAME"
echo "  - 标签: self-hosted, ubuntu-latest, ubuntu-22.04, ubuntu-24.04"
echo ""
echo "数据目录："
echo "  - $DATA_DIR"
echo ""
echo "常用命令："
echo "  - 查看日志: docker logs -f $RUNNER_CONTAINER"
echo "  - 查看状态: docker ps | grep $RUNNER_CONTAINER"
echo "  - 停止服务: docker stop $RUNNER_CONTAINER"
echo "  - 重启服务: docker restart $RUNNER_CONTAINER"
echo ""
echo "请在 Gitea 管理面板中确认 Runner 已上线！"
echo "=========================================="
