#!/bin/bash

# Gitea CI/CD 快速配置脚本

echo "=========================================="
echo "Gitea CI/CD 快速配置"
echo "=========================================="
echo ""

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker 未运行，请先启动 Docker"
    exit 1
fi

# 检查 Gitea 容器是否运行
if ! docker ps | grep -q myblog-gitea; then
    echo "❌ Gitea 容器未运行"
    echo "请先运行: docker compose up -d"
    exit 1
fi

echo "✅ Gitea 正在运行"
echo ""

# 创建 Gitea Runner
echo "📦 创建 Gitea Actions Runner..."
docker run -d \
  --name gitea-runner \
  --network myblog_myblog-network \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e GITEA_INSTANCE_URL=http://myblog-gitea:3000 \
  -e GITEA_RUNNER_REGISTRATION_TOKEN="" \
  -e CONFIG_FILE=/config.yaml \
  gitea/act_runner:nightly-dind

if [ $? -eq 0 ]; then
    echo "✅ Runner 容器已创建"
else
    echo "❌ Runner 容器创建失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "下一步操作："
echo "=========================================="
echo ""
echo "1. 访问 Gitea: http://localhost:3000"
echo "2. 登录并创建仓库（如果还没有）"
echo "3. 进入仓库设置 → Actions，获取注册 token"
echo "4. 运行以下命令注册 runner："
echo ""
echo "   docker exec -it gitea-runner act_runner register"
echo ""
echo "5. 在 Gitea 中启用 Actions"
echo "6. 初始化 Git 仓库并推送代码"
echo ""
echo "   git init"
echo "   git add ."
echo "   git commit -m 'Initial commit'"
echo "   git remote add origin http://localhost:3000/your-username/myblog.git"
echo "   git push -u origin main"
echo ""
echo "=========================================="
echo "配置完成！查看 GITEA_CICD_GUIDE.md 了解更多"
echo "=========================================="
