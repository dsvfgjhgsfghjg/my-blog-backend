#!/bin/bash

echo "🚀 启动本地 MyBlog 测试环境..."

# 1. 构建后端 JAR
echo "📦 构建后端 JAR..."
./gradlew bootJar

# 2. 构建前端
echo "🎨 构建前端..."
cd ~/WebstormProjects/myblog
npm run build
cd -

# 3. 复制前端构建文件到项目根目录的 dist 文件夹
echo "📋 复制前端文件..."
rm -rf dist/
cp -r ~/WebstormProjects/myblog/dist ./dist/

# 4. 启动 Docker Compose
echo "🐳 启动容器..."
docker compose -f docker-compose.local.yml up -d

# 5. 等待服务启动
echo "⏳ 等待服务启动..."
sleep 15

# 6. 创建 MinIO Bucket
echo "🪣 创建 MinIO Bucket..."
docker exec myblog-minio-local mc alias set myminio http://localhost:9000 minioadmin minioadmin
docker exec myblog-minio-local mc mb myminio/myblog || echo "Bucket 已存在"

# 7. 导入数据库（如果有备份）
if [ -f "./myblog_backup.dump" ]; then
    echo "💾 导入数据库备份..."
    docker exec -i myblog-postgres-local pg_restore -U postgres -d myblog < ./myblog_backup.dump
fi

echo ""
echo "✅ 本地环境启动完成！"
echo ""
echo "📍 访问地址："
echo "   前端: http://localhost:8081"
echo "   后端 API: http://localhost:8080"
echo "   MinIO Console: http://localhost:9001 (minioadmin/minioadmin)"
echo ""
echo "📊 查看日志："
echo "   docker compose -f docker-compose.local.yml logs -f"
echo ""
echo "🛑 停止服务："
echo "   docker compose -f docker-compose.local.yml down"
