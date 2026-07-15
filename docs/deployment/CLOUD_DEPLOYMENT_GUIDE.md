# MyBlog 云端部署完整指南

## 🎯 概述

本指南将帮助你在云端服务器上完整部署 MyBlog 应用，包括：
- ✅ PostgreSQL 18 数据库
- ✅ Redis 7 缓存
- ✅ MinIO 对象存储
- ✅ Spring Boot 后端（Java 21）
- ✅ Apache + Vue 前端

## 📋 前置准备

### 1. 服务器要求
- **操作系统**: Ubuntu 20.04+ / CentOS 7+
- **CPU**: 2核心或更高
- **内存**: 2GB 或更高
- **磁盘**: 20GB 或更高
- **网络**: 开放端口 80, 443, 8080

### 2. 本地准备文件

在你的开发机器上执行：

```bash
# 1. 构建后端 JAR 包
cd /home/leo/IdeaProjects/myblog
./gradlew bootJar

# 2. 创建部署目录
mkdir -p ~/my-app/backend
mkdir -p ~/my-app/frontend/dist

# 3. 复制 JAR 包
cp build/libs/myblog-0.0.1-SNAPSHOT.jar ~/my-app/backend/

# 4. 构建前端（在 Vue 项目目录）
cd your-vue-project
npm run build
cp -r dist/* ~/my-app/frontend/dist/

# 5. 复制 Docker 配置文件
cd /home/leo/IdeaProjects/myblog
cp docker-compose.prod.yml ~/my-app/
cp Dockerfile.backend ~/my-app/
cp Dockerfile.frontend ~/my-app/
cp frontend/httpd.conf ~/my-app/frontend/
cp deploy.sh ~/my-app/
cp check-deployment.sh ~/my-app/
cp DEPLOYMENT.md ~/my-app/
cp QUICK_START_DEPLOY.md ~/my-app/

# 6. 复制数据库备份（如果有）
cp myblog_backup.dump ~/my-app/

# 7. 创建 .env 文件
cd ~/my-app
cat > .env << 'EOF'
POSTGRES_USER=Leoxiaoblog
POSTGRES_PASSWORD=你的强密码_至少16位
POSTGRES_DB=myblog
MINIO_ROOT_USER=Leoxiao
MINIO_ROOT_PASSWORD=你的强密码_至少16位
JWT_SECRET=
SPRING_PROFILES_ACTIVE=prod
EOF

# 8. 生成 JWT 密钥并填入 .env
JWT_KEY=$(openssl rand -base64 64)
sed -i "s/JWT_SECRET=.*/JWT_SECRET=$JWT_KEY/" .env

# 9. 验证文件结构
tree -L 2 ~/my-app/
```

### 3. 上传到服务器

```bash
# 方法一：使用 scp
scp -r ~/my-app/* username@your-server-ip:~/my-app/

# 方法二：使用 rsync（推荐，支持断点续传）
rsync -avz --progress ~/my-app/ username@your-server-ip:~/my-app/

# 方法三：打包后传输
tar -czf my-app.tar.gz -C ~ my-app/
scp my-app.tar.gz username@your-server-ip:~
ssh username@your-server-ip "tar -xzf my-app.tar.gz"
```

## 🚀 服务器端部署

### 步骤 1: SSH 登录服务器

```bash
ssh username@your-server-ip
```

### 步骤 2: 初始化服务器（首次部署）

```bash
cd ~/my-app
chmod +x setup-server.sh
sudo ./setup-server.sh
```

这个脚本会：
- 安装 Docker 和 Docker Compose
- 配置防火墙
- 更新系统
- 创建必要的目录

### 步骤 3: 验证 .env 配置

```bash
cd ~/my-app
cat .env

# 确保：
# 1. 所有密码已修改为强密码
# 2. JWT_SECRET 已填写（至少 32 字符）
# 3. 没有使用默认密码
```

### 步骤 4: 执行部署

```bash
cd ~/my-app
chmod +x deploy.sh
./deploy.sh
```

部署脚本会自动：
1. 检查 Docker 环境
2. 停止旧容器（如果有）
3. 构建 Docker 镜像
4. 启动所有服务
5. 检查服务状态
6. 显示访问信息

### 步骤 5: 验证部署

```bash
# 运行健康检查
chmod +x check-deployment.sh
./check-deployment.sh

# 查看容器状态
docker-compose -f docker-compose.prod.yml ps

# 查看后端日志
docker-compose -f docker-compose.prod.yml logs -f backend
```

## 🌐 访问服务

部署成功后，通过浏览器访问：

| 服务 | URL | 说明 |
|------|-----|------|
| **前端网站** | `http://你的服务器IP/` | Vue 前端应用 |
| **后端 API** | `http://你的服务器IP:8080/` | RESTful API |
| **MinIO 控制台** | `http://你的服务器IP:9001/` | 对象存储管理 |
| **MinIO API** | `http://你的服务器IP:9000/` | 对象存储 API |

## ⚙️ 初始配置

### 1. MinIO 存储桶设置

1. 访问 `http://你的服务器IP:9001/`
2. 使用 `.env` 中的 `MINIO_ROOT_USER` 和 `MINIO_ROOT_PASSWORD` 登录
3. 点击 "Buckets" → "Create Bucket"
4. 创建名为 `myblog` 的存储桶
5. 设置访问策略（根据需要选择 Public 或 Private）

### 2. 数据库验证

```bash
# 进入 PostgreSQL 容器
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog

# 查看表列表
\dt

# 退出
\q
```

### 3. 测试 API

```bash
# 测试后端健康检查
curl http://localhost:8080/actuator/health

# 测试 API 端点
curl http://localhost:8080/api/posts
```

## 🔧 常用运维操作

### 查看日志

```bash
# 查看所有服务日志
docker-compose -f docker-compose.prod.yml logs -f

# 查看特定服务日志
docker-compose -f docker-compose.prod.yml logs -f backend
docker-compose -f docker-compose.prod.yml logs -f frontend
docker-compose -f docker-compose.prod.yml logs -f postgres
```

### 重启服务

```bash
# 重启所有服务
docker-compose -f docker-compose.prod.yml restart

# 重启特定服务
docker-compose -f docker-compose.prod.yml restart backend
```

### 停止服务

```bash
docker-compose -f docker-compose.prod.yml down
```

### 更新应用

#### 更新后端

```bash
# 1. 上传新的 JAR 包到服务器
scp myblog-new.jar username@server:~/my-app/backend/myblog-0.0.1-SNAPSHOT.jar

# 2. 重新构建并启动
cd ~/my-app
docker-compose -f docker-compose.prod.yml build backend
docker-compose -f docker-compose.prod.yml up -d backend
```

#### 更新前端

```bash
# 1. 上传新的 dist 文件
scp -r dist/* username@server:~/my-app/frontend/dist/

# 2. 重新构建并启动
cd ~/my-app
docker-compose -f docker-compose.prod.yml build frontend
docker-compose -f docker-compose.prod.yml up -d frontend
```

### 数据备份

```bash
# 备份 PostgreSQL
docker exec myblog-postgres pg_dump -U Leoxiaoblog myblog > backup_$(date +%Y%m%d_%H%M%S).sql

# 备份 MinIO 数据
tar -czf minio-backup-$(date +%Y%m%d_%H%M%S).tar.gz ~/my-app/minio-data/

# 下载备份到本地
scp username@server:~/my-app/backup_*.sql ./
```

### 恢复数据库

```bash
# 从备份恢复
cat backup.sql | docker exec -i myblog-postgres psql -U Leoxiaoblog -d myblog

# 或使用 pg_restore（如果是 custom 格式）
docker exec -i myblog-postgres pg_restore -U Leoxiaoblog -d myblog < backup.dump
```

## 🔒 安全加固

### 1. 配置防火墙

```bash
# 启用 UFW
sudo ufw enable

# 只开放必要端口
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS（如果配置 SSL）
sudo ufw allow 8080/tcp  # Backend API

# 限制数据库端口仅本地访问
sudo ufw deny 5432/tcp
sudo ufw deny 6379/tcp
sudo ufw deny 9000/tcp
sudo ufw deny 9001/tcp

# 查看规则
sudo ufw status
```

### 2. 配置 HTTPS（推荐）

使用 Let's Encrypt 免费证书：

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-apache

# 获取证书（需要域名）
sudo certbot --apache -d your-domain.com

# 自动续期
sudo crontab -e
# 添加：0 0 1 * * certbot renew --quiet
```

### 3. 定期更新

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 更新 Docker 镜像
cd ~/my-app
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

## 🐛 故障排查

### 问题 1: 容器无法启动

```bash
# 查看容器状态
docker-compose -f docker-compose.prod.yml ps

# 查看详细日志
docker-compose -f docker-compose.prod.yml logs [service-name]

# 检查资源使用
docker stats
```

### 问题 2: 后端无法连接数据库

```bash
# 检查 PostgreSQL 是否运行
docker ps | grep postgres

# 测试数据库连接
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog -c "SELECT 1;"

# 检查环境变量
docker exec myblog-backend env | grep SPRING_DATASOURCE
```

### 问题 3: 前端页面空白

```bash
# 检查 Apache 配置
docker exec myblog-frontend cat /usr/local/apache2/conf/httpd.conf

# 检查文件是否存在
docker exec myblog-frontend ls -la /usr/local/apache2/htdocs/

# 查看 Apache 日志
docker logs myblog-frontend
```

### 问题 4: 内存不足

```bash
# 查看内存使用
free -h
docker stats

# 限制容器内存（在 docker-compose.prod.yml 中添加）
# deploy:
#   resources:
#     limits:
#       memory: 512M
```

### 问题 5: 磁盘空间不足

```bash
# 检查磁盘使用
df -h
du -sh ~/my-app/*/data

# 清理 Docker 资源
docker system prune -a --volumes

# 清理日志
docker-compose -f docker-compose.prod.yml logs --tail=100 > last_logs.txt
```

## 📊 监控和维护

### 实时监控

```bash
# 实时查看容器资源使用
docker stats

# 查看系统负载
top
htop

# 查看磁盘 I/O
iotop
```

### 日志管理

```bash
# 限制日志大小（在 docker-compose.prod.yml 中添加）
# logging:
#   driver: "json-file"
#   options:
#     max-size: "10m"
#     max-file: "3"
```

### 健康检查

```bash
# 定期检查
./check-deployment.sh

# 设置定时任务
crontab -e
# 添加：*/5 * * * * cd ~/my-app && ./check-deployment.sh >> /var/log/myblog-health.log 2>&1
```

## 📝 检查清单

部署前确认：

- [ ] 服务器满足最低配置要求
- [ ] Docker 和 Docker Compose 已安装
- [ ] 所有文件已上传到服务器
- [ ] `.env` 文件已创建且密码已修改
- [ ] JWT_SECRET 已生成（至少 32 字符）
- [ ] 防火墙已配置
- [ ] 数据库备份文件已准备（如果需要）

部署后确认：

- [ ] 所有容器正常运行
- [ ] 前端页面可以访问
- [ ] 后端 API 响应正常
- [ ] 数据库连接成功
- [ ] Redis 连接成功
- [ ] MinIO 可以访问
- [ ] 已创建 MinIO 存储桶
- [ ] 日志无错误信息
- [ ] 已配置定期备份

## 📞 获取帮助

如遇到问题：

1. 查看日志：`docker-compose -f docker-compose.prod.yml logs -f`
2. 检查文档：`DEPLOYMENT.md`、`QUICK_START_DEPLOY.md`
3. 运行诊断：`./check-deployment.sh`
4. 查看常见问题：本文档"故障排查"章节

## 🎉 完成！

恭喜！你已经成功部署了 MyBlog 应用。

**下一步建议**：
1. 配置域名和 HTTPS
2. 设置自动备份
3. 配置监控告警
4. 优化性能参数
5. 定期安全更新

---

**祝你使用愉快！** 🚀
