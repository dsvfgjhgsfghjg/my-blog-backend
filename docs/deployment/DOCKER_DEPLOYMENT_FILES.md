# MyBlog Docker 部署文件说明

## 📦 已创建的文件清单

### 1. Docker 配置文件

#### `docker-compose.prod.yml`
- **用途**: Docker Compose 生产环境编排配置
- **包含服务**:
  - PostgreSQL 18 (数据库)
  - Redis 7-alpine (缓存)
  - MinIO RELEASE.2025-09-07T16-13-09Z (对象存储)
  - Spring Boot Backend (Java 21 应用)
  - Apache HTTPD (Vue 前端)
- **特性**:
  - 使用 Docker 内部网络连接
  - 健康检查机制
  - 数据卷持久化
  - 环境变量配置支持

#### `Dockerfile.backend`
- **用途**: Spring Boot 后端应用镜像构建
- **特点**:
  - 多阶段构建（减小镜像体积）
  - 基于 Eclipse Temurin 21 JRE Alpine
  - 非 root 用户运行（安全）
  - 健康检查配置

#### `Dockerfile.frontend`
- **用途**: Vue 前端 Apache 服务器镜像构建
- **特点**:
  - 基于 httpd:2.4-alpine
  - 启用 rewrite、deflate、expires 模块
  - 支持 Vue Router 历史模式
  - Gzip 压缩和缓存控制

### 2. 配置文件

#### `.env`
- **用途**: 环境变量配置（敏感信息）
- **包含**:
  - 数据库用户名/密码
  - MinIO 访问密钥
  - JWT 密钥
  - Spring Profile
- **⚠️ 重要**: 不要提交到版本控制系统

#### `frontend/httpd.conf`
- **用途**: Apache HTTPD 自定义配置
- **功能**:
  - Vue Router 历史模式支持（URL 重写）
  - Gzip 压缩
  - 静态资源缓存控制
  - CORS 支持

### 3. 部署脚本

#### `deploy.sh`
- **用途**: 自动化部署脚本
- **功能**:
  - 检查 Docker 环境
  - 清理旧容器
  - 构建镜像
  - 启动服务
  - 检查状态
  - 显示访问信息
- **使用**: `chmod +x deploy.sh && ./deploy.sh`

#### `setup-server.sh`
- **用途**: 服务器初始化脚本
- **功能**:
  - 安装 Docker 和 Docker Compose
  - 配置防火墙
  - 创建部署目录
  - 系统更新
- **使用**: `sudo chmod +x setup-server.sh && sudo ./setup-server.sh`

#### `check-deployment.sh`
- **用途**: 部署健康检查脚本
- **功能**:
  - 检查容器状态
  - 验证端口监听
  - 测试 API 响应
  - 检查系统资源
- **使用**: `chmod +x check-deployment.sh && ./check-deployment.sh`

### 4. 文档

#### `DEPLOYMENT.md`
- **用途**: 完整部署指南
- **内容**:
  - 详细部署步骤
  - 配置说明
  - 运维命令
  - 故障排查
  - 安全建议
  - 备份策略

#### `QUICK_START_DEPLOY.md`
- **用途**: 快速参考卡片
- **内容**:
  - 一键部署步骤
  - 常用命令速查
  - 访问地址列表
  - 故障排查要点

#### `DOCKER_DEPLOYMENT_FILES.md` (本文件)
- **用途**: 文件说明文档

### 5. 其他文件

#### `.gitignore.docker`
- **用途**: Git 忽略规则示例
- **建议**: 合并到项目根目录的 `.gitignore`

## 🎯 部署架构

```
┌─────────────────────────────────────────┐
│         Docker Bridge Network           │
│                                         │
│  ┌──────────┐    ┌──────────┐          │
│  │ Frontend │───▶│ Backend  │          │
│  │ (Apache) │    │(Spring   │          │
│  │ Port 80  │    │ Boot)    │          │
│  └──────────┘    │Port 8080 │          │
│                  └────┬─────┘          │
│                       │                 │
│              ┌────────┼────────┐       │
│              ▼        ▼        ▼       │
│         ┌────────┐┌──────┐┌────────┐  │
│         │PostgreSQL│Redis │ MinIO  │  │
│         │Port 5432│:6379 │:9000/1 │  │
│         └────────┘└──────┘└────────┘  │
└─────────────────────────────────────────┘
         ▲                    ▲
         │                    │
    用户访问             管理界面
   (Port 80)          (Port 9001)
```

## 🚀 快速开始

### 在本地准备文件

1. **构建后端 JAR 包**
```bash
./gradlew bootJar
cp build/libs/myblog-0.0.1-SNAPSHOT.jar ~/my-app/backend/
```

2. **构建前端 dist**
```bash
cd vue-project
npm run build
cp -r dist/* ~/my-app/frontend/dist/
```

3. **复制 Docker 配置文件**
```bash
cp docker-compose.prod.yml ~/my-app/
cp Dockerfile.backend ~/my-app/
cp Dockerfile.frontend ~/my-app/
cp frontend/httpd.conf ~/my-app/frontend/
```

4. **创建 .env 文件**
```bash
cd ~/my-app
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

5. **上传到服务器**
```bash
# 使用 scp 或 rsync
scp -r ~/my-app/* user@your-server:~/my-app/
```

### 在服务器上部署

```bash
# 1. SSH 登录服务器
ssh user@your-server

# 2. 进入项目目录
cd ~/my-app

# 3. 运行部署脚本
chmod +x deploy.sh
./deploy.sh

# 4. 检查部署状态
./check-deployment.sh
```

## 📊 资源需求

| 组件 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| PostgreSQL | 0.5核 | 512MB | 5GB+ |
| Redis | 0.2核 | 128MB | 1GB |
| MinIO | 0.3核 | 256MB | 10GB+ |
| Backend | 1核 | 512MB | 1GB |
| Frontend | 0.2核 | 64MB | 500MB |
| **总计** | **2.2核** | **1.5GB** | **17GB+** |

**推荐配置**: 2核 CPU, 2GB 内存, 20GB 磁盘

## 🔧 环境变量说明

### PostgreSQL
- `POSTGRES_USER`: 数据库用户名
- `POSTGRES_PASSWORD`: 数据库密码
- `POSTGRES_DB`: 数据库名称

### MinIO
- `MINIO_ROOT_USER`: 管理员用户名
- `MINIO_ROOT_PASSWORD`: 管理员密码

### Spring Boot
- `SPRING_DATASOURCE_URL`: 数据库连接 URL
- `SPRING_DATA_REDIS_HOST`: Redis 主机
- `MINIO_ENDPOINT`: MinIO API 地址
- `JWT_SECRET`: JWT 签名密钥
- `SPRING_PROFILES_ACTIVE`: Spring Profile

## 🛡️ 安全最佳实践

1. **修改默认密码**
   - 所有密码至少 16 位
   - 包含大小写字母、数字、特殊字符

2. **生成强 JWT 密钥**
```bash
openssl rand -base64 64
```

3. **配置防火墙**
```bash
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 8080/tcp
ufw enable
```

4. **定期更新**
```bash
# 更新系统
apt update && apt upgrade -y

# 更新 Docker 镜像
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

5. **备份策略**
   - 每日数据库备份
   - 每周完整备份
   - 异地备份存储

## 📝 维护命令速查

```bash
# 查看日志
docker-compose -f docker-compose.prod.yml logs -f [service]

# 重启服务
docker-compose -f docker-compose.prod.yml restart [service]

# 进入容器
docker exec -it myblog-backend sh
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog

# 备份数据库
docker exec myblog-postgres pg_dump -U Leoxiaoblog myblog > backup.sql

# 清理空间
docker system prune -a --volumes
```

## 🆘 常见问题

### Q: 后端无法连接数据库？
A: 检查 PostgreSQL 容器是否正常运行，确认环境变量配置正确

### Q: 前端页面空白？
A: 检查 Apache 配置，确保 Vue Router 重写规则生效

### Q: MinIO 无法上传文件？
A: 检查存储桶是否创建，权限配置是否正确

### Q: 内存不足？
A: 调整 JVM 参数，限制各容器内存使用

## 📞 获取帮助

详细文档请查看:
- `DEPLOYMENT.md` - 完整部署指南
- `QUICK_START_DEPLOY.md` - 快速参考

---

**祝部署顺利！** 🎉
