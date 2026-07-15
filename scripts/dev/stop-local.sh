#!/bin/bash

echo "🛑 停止本地 MyBlog 测试环境..."

docker compose -f docker-compose.local.yml down

echo ""
echo "✅ 服务已停止"
echo ""
echo "💡 如果要完全清理（包括数据卷）："
echo "   docker compose -f docker-compose.local.yml down -v"
