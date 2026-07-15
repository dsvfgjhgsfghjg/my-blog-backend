# MyBlog 云端部署指南

## 📋 目录结构

```
~/my-app/
├── backend/                    # 后端应用目录
│   ├── Dockerfile             # 后端 Dockerfile（已废弃，使用根目录的 Dockerfile.backend）
│   └── myblog-0.0.1-SNAPSHOT.jar
├── frontend/                   # 前端应用目录
│   ├── dist/                  # Vue 打包后的文件
│   │   ├── assets/
│   │   ├── favicon.svg
│   │   ├── icons.svg
│   │   └── index.html
│   └── httpd.conf             # Apache 配置文件
├── myblog_backup.dump         # PostgreSQL 数据库备份
├── docker-compose.prod.yml    # Docker Compose 生产配置
├── Dockerfile.backend         # 后端 Dockerfile
├── Dockerfile.frontend        # 前端 Dockerfile
├── .env                       # 环境变量配置（需手动创建）
└── deploy.sh                  # 自动化部署脚本
```

## 🚀 快速部署

### 前置要求

1. **服务器要求**
   - Ubuntu 20.04+ / CentOS 7+
   - 至少 2GB RAM
   - 至少 20GB 磁盘空间
   - 已安装 Docker 和 Docker Compose

2. **安装 Docker**（如果未安装）
```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | bash
sudo usermod -aG docker $USER

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 部署步骤

#### 方法一：使用自动化脚本（推荐）

```bash
# 1. 进入项目目录
cd ~/my-app

# 2. 给部署脚本添加执行权限
chmod +x deploy.sh

# 3. 执行部署
./deploy.sh
```

#### 方法二：手动部署

```bash
# 1. 进入项目目录
cd ~/my-app

# 2. 停止旧容器（如果有）
docker-compose -f docker-compose.prod.yml down

# 3. 构建镜像
docker-compose -f docker-compose.prod.yml build

# 4. 启动服务
docker-compose -f docker-compose.prod.yml up -d

# 5. 查看日志
docker-compose -f docker-compose.prod.yml logs -f
```

## ⚙️ 配置说明

### 1. 环境变量配置 (.env)

在项目根目录创建 `.env` 文件：

```bash
# PostgreSQL 配置
POSTGRES_USER=Leoxiaoblog
POSTGRES_PASSWORD=你的密码
POSTGRES_DB=myblog

# MinIO 配置
MINIO_ROOT_USER=Leoxiao
MINIO_ROOT_PASSWORD=你的密码

# JWT 配置（生成强密钥）
JWT_SECRET=运行以下命令生成: openssl rand -base64 64

# Spring Profile
SPRING_PROFILES_ACTIVE=prod
```

**生成 JWT 密钥：**
```bash
openssl rand -base64 64
```

### 2. 数据库备份导入

如果你有数据库备份文件 `myblog_backup.dump`，它会自动在首次启动时导入。

**手动导入数据库：**
```bash
# 进入 PostgreSQL 容器
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog

# 或者从外部导入
docker exec -i myblog-postgres pg_restore -U Leoxiaoblog -d myblog < myblog_backup.dump
```

### 3. MinIO 初始化

首次启动后，访问 MinIO Console 创建存储桶：

1. 浏览器访问：`http://你的服务器IP:9001`
2. 使用 `.env` 中的 `MINIO_ROOT_USER` 和 `MINIO_ROOT_PASSWORD` 登录
3. 创建名为 `myblog` 的存储桶
4. 设置存储桶策略为公开读取（如果需要公开访问资源）

## 🔍 服务访问

部署成功后，可以通过以下地址访问服务：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://你的服务器IP/ | Vue 前端应用 |
| 后端 API | http://你的服务器IP:8080/ | Spring Boot API |
| MinIO Console | http://你的服务器IP:9001/ | 对象存储管理界面 |
| MinIO API | http://你的服务器IP:9000/ | 对象存储 API |
| PostgreSQL | 服务器IP:5432 | 数据库（建议仅内网访问） |
| Redis | 服务器IP:6379 | 缓存（建议仅内网访问） |

## 🛠️ 常用运维命令

### 查看服务状态
```bash
docker-compose -f docker-compose.prod.yml ps
```

### 查看日志
```bash
# 查看所有服务日志
docker-compose -f docker-compose.prod.yml logs -f

# 查看特定服务日志
docker-compose -f docker-compose.prod.yml logs -f backend
docker-compose -f docker-compose.prod.yml logs -f frontend
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
# 1. 将新的 JAR 包复制到 backend 目录
cp myblog-new.jar backend/myblog-0.0.1-SNAPSHOT.jar

# 2. 重新构建并启动
docker-compose -f docker-compose.prod.yml build backend
docker-compose -f docker-compose.prod.yml up -d backend
```

#### 更新前端
```bash
# 1. 将新的 dist 文件复制到 frontend/dist 目录
rm -rf frontend/dist/*
cp -r your-vue-project/dist/* frontend/dist/

# 2. 重新构建并启动
docker-compose -f docker-compose.prod.yml build frontend
docker-compose -f docker-compose.prod.yml up -d frontend
```

### 数据备份

#### 备份 PostgreSQL
```bash
docker exec myblog-postgres pg_dump -U Leoxiaoblog myblog > backup_$(date +%Y%m%d).sql
```

#### 备份 MinIO 数据
```bash
tar -czf minio-backup-$(date +%Y%m%d).tar.gz minio-data/
```

## 🔒 安全建议

1. **修改默认密码**
   - 修改 `.env` 文件中的所有密码
   - 使用强密码（至少 16 位，包含大小写字母、数字、特殊字符）

2. **防火墙配置**
```bash
# 只开放必要的端口
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS（如果配置了 SSL）
sudo ufw enable

# 限制数据库端口仅内网访问
sudo ufw allow from 172.16.0.0/12 to any port 5432
sudo ufw allow from 172.16.0.0/12 to any port 6379
```

3. **配置 HTTPS**（推荐）
   - 使用 Let's Encrypt 免费证书
   - 在前端配置反向代理或使用 Nginx

4. **定期更新**
   - 定期更新 Docker 镜像
   - 定期更新系统补丁

## 🐛 故障排查

### 后端启动失败
```bash
# 查看后端日志
docker-compose -f docker-compose.prod.yml logs backend

# 检查数据库连接
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog -c "\l"

# 检查 Redis 连接
docker exec -it myblog-redis redis-cli ping
```

### 前端无法访问后端
检查 Vue 前端的 API 基础 URL 配置，确保指向正确的后端地址：
```javascript
// 在 Vue 项目中配置
const API_BASE_URL = 'http://你的服务器IP:8080/api'
```

### 数据库导入失败
```bash
# 检查备份文件格式
file myblog_backup.dump

# 手动导入
docker exec -i myblog-postgres pg_restore -U Leoxiaoblog -d myblog --clean --if-exists < myblog_backup.dump
```

### MinIO 无法访问
```bash
# 检查 MinIO 容器状态
docker logs myblog-minio

# 检查存储桶是否创建
docker exec -it myblog-minio mc ls local/myblog
```

## 📊 监控和维护

### 查看资源使用情况
```bash
docker stats
```

### 清理无用资源
```bash
# 清理未使用的镜像
docker image prune -a

# 清理未使用的卷
docker volume prune

# 清理所有未使用的资源
docker system prune -a --volumes
```

## 📞 技术支持

如遇到问题，请检查：
1. Docker 日志：`docker-compose -f docker-compose.prod.yml logs`
2. 应用日志：查看各服务的日志输出
3. 网络连接：确保端口未被防火墙阻止
4. 资源配置：确保服务器有足够的内存和磁盘空间

---

**祝部署顺利！** 🎉
