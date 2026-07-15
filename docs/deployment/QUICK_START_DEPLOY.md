# MyBlog 快速部署参考卡

## 🚀 一键部署（3步完成）

```bash
# 1. 上传文件到服务器 ~/my-app/ 目录
# 2. 创建 .env 配置文件
# 3. 运行部署脚本
cd ~/my-app && ./deploy.sh
```

## 📁 文件清单

确保以下文件在 `~/my-app/` 目录：

```
✓ docker-compose.prod.yml      # Docker 编排配置
✓ Dockerfile.backend           # 后端 Dockerfile
✓ Dockerfile.frontend          # 前端 Dockerfile
✓ .env                         # 环境变量（需创建）
✓ backend/myblog-0.0.1-SNAPSHOT.jar  # 后端 JAR 包
✓ frontend/dist/               # Vue 打包文件
✓ frontend/httpd.conf          # Apache 配置
✓ myblog_backup.dump           # 数据库备份（可选）
```

## 🔑 关键配置

### 创建 .env 文件

```bash
cat > .env << EOF
POSTGRES_USER=Leoxiaoblog
POSTGRES_PASSWORD=你的强密码
POSTGRES_DB=myblog
MINIO_ROOT_USER=Leoxiao
MINIO_ROOT_PASSWORD=你的强密码
JWT_SECRET=$(openssl rand -base64 64)
SPRING_PROFILES_ACTIVE=prod
EOF
```

### 生成 JWT 密钥

```bash
openssl rand -base64 64
```

## 🌐 访问地址

| 服务 | URL |
|------|-----|
| 前端 | http://服务器IP/ |
| 后端 API | http://服务器IP:8080/ |
| MinIO Console | http://服务器IP:9001/ |

## 🛠️ 常用命令

```bash
# 查看状态
docker-compose -f docker-compose.prod.yml ps

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f backend

# 重启服务
docker-compose -f docker-compose.prod.yml restart

# 停止服务
docker-compose -f docker-compose.prod.yml down

# 健康检查
./check-deployment.sh
```

## 🔄 更新应用

### 更新后端
```bash
cp new.jar backend/myblog-0.0.1-SNAPSHOT.jar
docker-compose -f docker-compose.prod.yml build backend
docker-compose -f docker-compose.prod.yml up -d backend
```

### 更新前端
```bash
rm -rf frontend/dist/*
cp -r vue-project/dist/* frontend/dist/
docker-compose -f docker-compose.prod.yml build frontend
docker-compose -f docker-compose.prod.yml up -d frontend
```

## 💾 数据备份

```bash
# 备份数据库
docker exec myblog-postgres pg_dump -U Leoxiaoblog myblog > backup_$(date +%Y%m%d).sql

# 备份 MinIO
tar -czf minio-backup-$(date +%Y%m%d).tar.gz minio-data/
```

## 🐛 故障排查

```bash
# 后端启动失败
docker-compose -f docker-compose.prod.yml logs backend

# 数据库连接问题
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog

# Redis 连接问题
docker exec -it myblog-redis redis-cli ping

# 查看资源使用
docker stats
```

## 🔒 安全提醒

- ⚠️ 修改所有默认密码
- ⚠️ 使用强 JWT 密钥
- ⚠️ 配置防火墙（只开放 80, 443, 8080）
- ⚠️ 定期更新系统和镜像
- ⚠️ 不要将 .env 文件提交到 Git

## 📊 系统要求

- CPU: 2核心+
- 内存: 2GB+
- 磁盘: 20GB+
- 系统: Ubuntu 20.04+ / CentOS 7+
- Docker: 20.10+
- Docker Compose: 2.0+

---

**详细文档**: 查看 DEPLOYMENT.md
