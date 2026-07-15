# 自托管 Gitea 实现 CI/CD 工作流与踩坑记录

## 前言

在搭建个人博客系统时，我选择了自托管 Gitea 作为代码仓库和 CI/CD 平台。整个过程中遇到了不少坑，从 Runner 配置、网络问题到 Docker 构建，每一个环节都值得记录。本文将详细分享从零开始搭建 Gitea Actions 的完整流程、遇到的所有问题及解决方案，**重点聚焦配置管理最佳实践和调试技巧**。

---

## 📋 核心原则：配置安全管理

### 三层配置管理体系

在开始之前，必须建立清晰的配置管理策略：

```
第1层: .env 文件（本地/服务器）
- 不提交到 Git
- 包含所有环境变量
- 使用 .env.example 作为模板

第2层: Gitea Secrets（CI/CD）
- 存储在 Gitea 仓库设置中
- 工作流通过 ${{ secrets.XXX }} 引用
- 支持 Base64 编码的多行文本

第3层: Docker Compose 环境变量
- 从 .env 文件读取
- 传递给容器
- 运行时动态注入
```

### ✅ 最佳实践清单

1. **永远不要硬编码敏感信息**

```yaml
# ❌ 错误：硬编码密码
-e DB_PASSWORD=my_secret_password

# ✅ 正确：使用变量
-e DB_PASSWORD=${DB_PASSWORD}
```

2. **.gitignore 必须包含**

```gitignore
.env
.env.*
*.secret
secrets_config_*.txt
```

3. **提供 .env.example 模板**

```bash
# .env.example
DB_USER=your_db_user
DB_PASSWORD=your_db_password
MINIO_ROOT_USER=your_minio_user
MINIO_ROOT_PASSWORD=your_minio_password
JWT_SECRET=your_jwt_secret
```

4. **Gitea Secrets 命名规范**
- 使用大写字母和下划线：`DB_PASSWORD`
- 添加前缀区分服务：`MINIO_ROOT_USER`
- 敏感密钥明确标注：`JWT_SECRET`

---

## 🛠️ 环境准备

### 技术栈

- **代码仓库**: Gitea (自托管)
- **CI/CD**: Gitea Actions
- **Runner**: self-hosted (Docker 容器)
- **后端**: Spring Boot + Java 21 + Gradle
- **前端**: Vue 3 + TypeScript + Vite
- **数据库**: PostgreSQL
- **缓存**: Redis
- **对象存储**: MinIO
- **容器化**: Docker + Docker Compose

---

## 1. 环境准备阶段

### 1.1 云端服务器初始化

**问题**: 全新 Ubuntu 服务器，没有任何 Docker 环境

**解决方案**:
```bash
# 创建初始化脚本 init-docker-compose.sh
# 自动检测 Docker Compose V1/V2
# 生成 docker-compose.prod.yml
# 创建所有必要的 Dockerfile
# 启动基础服务（PostgreSQL, Redis, MinIO）
```

**关键点**:
- ✅ Docker Compose V2 使用 `docker compose`（空格），不是 `docker-compose`（连字符）
- ✅ 所有镜像使用国内加速源：`04eh77udwizz5vqdbk.xuanyuan.run`
- ✅ 服务版本指定：
  - PostgreSQL: `14.23-bookworm`
  - Redis: `7.4.9-bookworm`
  - MinIO: `elestio/minio:latest`
  - Java: `ubuntu/jdk:21-24.04_stable`

### 1.2 端口规划

**问题**: Gitea 已占用标准端口（5432, 3000, 222）

**解决方案**: MyBlog 使用非标准端口
```yaml
postgres: 15432:5432    # 避免与 Gitea 的 5432 冲突
redis: 16379:6379       # 避免与其他服务冲突
minio: 19000:9000       # API 端口
       19001:9001       # Console 端口
frontend: 8081:80       # Web 访问端口
backend: 18080:8080     # API 端口
```

---

## 2. Gitea Actions 配置阶段

### 2.1 Runner 离线问题

**现象**: Gitea Actions Runner 显示"离线"状态

**原因**: 
- Runner 容器虽然运行，但服务本身可能崩溃
- 网络连接问题导致无法连接到 Gitea 服务器

**解决方案**:
```bash
# 重启 Runner
docker restart gitea-runner
sleep 30
docker logs gitea-runner --tail 20
```

**经验教训**: 
- ⚠️ Gitea Actions 自托管 Runner 稳定性不如 GitHub Actions
- ⚠️ 需要定期监控 Runner 状态
- 💡 对于个人项目，考虑使用简单的 deploy.sh 脚本替代

### 2.2 Secrets 配置问题

**问题**: Gitea Secrets 不支持多行文本

**现象**:
```
Load key "/root/.ssh/deploy_key": invalid format
```

**调试过程**:
```bash
# 添加调试信息查看密钥格式
echo "DEPLOY_KEY 长度: ${#DEPLOY_KEY}"
echo "文件行数: $(wc -l ~/.ssh/deploy_key)"
echo "文件大小: $(ls -lh ~/.ssh/deploy_key)"
```

**发现**: 
- 原始私钥应该是 7 行，411 字节
- Gitea Secrets 存储后变成 1 行，104 字节（被截断）

**解决方案**: 使用 Base64 编码

```bash
# 本地编码
base64 -w 0 ~/.ssh/gitea_local_key

# Gitea Secrets 中存储 Base64 字符串
# 工作流中解码
echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
chmod 600 ~/.ssh/deploy_key
```

### 2.3 SSH 公钥配置

**问题**: 私钥格式正确，但仍然 `Permission denied`

**原因**: 服务器上 `ubuntu` 用户的 `~/.ssh/authorized_keys` 中没有对应的公钥

**解决方案**:

#### 方法 1: 使用现有密钥（推荐）

如果你已经有 SSH 密钥（如 `~/.ssh/id_ed25519`）：

```bash
# 添加公钥到服务器
ssh-copy-id -i ~/.ssh/id_ed25519.pub ubuntu@113.31.105.30

# 测试连接
ssh -i ~/.ssh/id_ed25519 ubuntu@113.31.105.30 echo "Success"
```

#### 方法 2: 生成新密钥

```bash
# 生成专用密钥
ssh-keygen -t ed25519 -C "gitea_deploy" -f ~/.ssh/gitea_deploy_key -N ""

# 添加公钥到服务器
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@113.31.105.30

# 测试连接
ssh -i ~/.ssh/gitea_deploy_key ubuntu@113.31.105.30 echo "Success"
```

#### 方法 3: 手动添加

```bash
# 1. 复制公钥内容
cat ~/.ssh/your_key_name.pub

# 2. SSH 登录服务器
ssh ubuntu@113.31.105.30

# 3. 在服务器上执行
mkdir -p ~/.ssh
echo "<粘贴公钥内容>" >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
exit
```

**验证**:
```bash
ssh -i ~/.ssh/your_key_name ubuntu@113.31.105.30 echo "Success"
```

**详细文档**: [USE_EXISTING_SSH_KEY.md](./USE_EXISTING_SSH_KEY.md)

---

## 3. Docker 构建阶段

### 3.1 后端 JAR 文件缺失

**问题**: 
```
COPY target/*.jar app.jar
failed to solve: lstat /target: no such file or directory
```

**原因**: 后端项目还没有构建，没有生成 JAR 文件

**临时解决方案**: 
- 先只部署前端服务
- 后端稍后单独处理

```bash
docker compose up -d postgres redis minio frontend
```

### 3.2 配置文件路径错误

**问题**:
```
COPY frontend/httpd.conf /usr/local/apache2/conf/httpd.conf
failed: "/frontend/httpd.conf": not found
```

**原因**: 实际文件名是 `apache.conf`，不是 `httpd.conf`

**调试方法**:
```yaml
# 在工作流中添加文件列表调试
echo "Files in frontend/ directory:"
ls -la frontend/
```

**解决方案**: 修正文件名
```dockerfile
COPY frontend/apache.conf /usr/local/apache2/conf/extra/myblog.conf
```

### 3.3 Apache MPM 模块缺失

**问题**:
```
AH00534: httpd: Configuration error: No MPM loaded.
```

**原因**: 完全覆盖 `httpd.conf` 导致丢失核心配置（包括 MPM 模块加载）

**错误的做法**:
```dockerfile
# ❌ 不要这样做
COPY frontend/apache.conf /usr/local/apache2/conf/httpd.conf
```

**正确的做法**:
```dockerfile
# ✅ 保留默认 httpd.conf，只添加 VirtualHost 配置
COPY frontend/apache.conf /usr/local/apache2/conf/extra/myblog.conf
RUN echo "Include conf/extra/myblog.conf" >> /usr/local/apache2/conf/httpd.conf
```

---

## 4. SSH 认证阶段

### 4.1 完整的 SSH 密钥配置流程

**步骤清单**:

#### 选项 A: 使用现有的 SSH 密钥（推荐）

如果你已经有 SSH 密钥（如 `~/.ssh/id_ed25519`），可以直接使用：

1. **查看可用的密钥**
   ```bash
   ls -la ~/.ssh/id_* | grep -v ".pub$"
   # 常见密钥：id_rsa, id_ed25519, id_ecdsa
   ```

2. **测试密钥是否可以连接服务器**
   ```bash
   ssh -i ~/.ssh/your_key_name ubuntu@113.31.105.30 echo "SSH OK"
   ```

3. **如果连接失败，添加公钥到服务器**
   ```bash
   ssh-copy-id -i ~/.ssh/your_key_name.pub ubuntu@113.31.105.30
   ```

4. **Base64 编码私钥**
   ```bash
   base64 -w 0 ~/.ssh/your_key_name
   # 复制输出的字符串
   ```

5. **配置 Gitea Secrets**
   - DEPLOY_HOST: `113.31.105.30`
   - DEPLOY_USER: `ubuntu`
   - DEPLOY_PATH: `/home/ubuntu/myblog-deploy`
   - DEPLOY_KEY: `<Base64 编码的私钥>`

#### 选项 B: 生成新的专用密钥

如果想为 CI/CD 创建专用密钥：

1. **生成密钥对**
   ```bash
   ssh-keygen -t ed25519 -C "gitea_deploy" -f ~/.ssh/gitea_deploy_key -N ""
   # -N "" 表示无密码（CI/CD 需要）
   ```

2. **将公钥添加到服务器**
   ```bash
   ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@113.31.105.30
   ```

3. **测试本地 SSH 连接**
   ```bash
   ssh -i ~/.ssh/gitea_deploy_key ubuntu@113.31.105.30 echo "Success"
   ```

4. **Base64 编码私钥**
   ```bash
   base64 -w 0 ~/.ssh/gitea_deploy_key
   ```

5. **配置 Gitea Secrets**
   - DEPLOY_HOST: `113.31.105.30`
   - DEPLOY_USER: `ubuntu`
   - DEPLOY_PATH: `/home/ubuntu/myblog-deploy`
   - DEPLOY_KEY: `<Base64 编码的私钥>`

#### 选项 C: 使用配置助手脚本（最简单）

```bash
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

脚本会：
- ✓ 自动检测现有密钥或生成新密钥
- ✓ 测试 SSH 连接
- ✓ 检查 Docker 环境
- ✓ 生成强密码
- ✓ Base64 编码私钥
- ✓ 输出完整的 Secrets 配置清单

详细文档：[USE_EXISTING_SSH_KEY.md](./USE_EXISTING_SSH_KEY.md)

#### 工作流中解码并使用

无论使用哪种方式，在工作流中都需要解码：

```yaml
- name: Setup SSH
  run: |
    mkdir -p ~/.ssh
    echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
    chmod 600 ~/.ssh/deploy_key
    ssh-keyscan -H $DEPLOY_HOST >> ~/.ssh/known_hosts
```

---

## 5. Apache 配置阶段

### 5.1 VirtualHost 配置要点

**关键配置项**:

```apache
<VirtualHost *:80>
    # 文档根目录（与 Dockerfile 中的 COPY 路径一致）
    DocumentRoot "/usr/local/apache2/htdocs"
    
    <Directory "/usr/local/apache2/htdocs">
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
        
        # SPA 路由支持
        RewriteEngine On
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>
    
    # 日志输出到 stdout/stderr（Docker 标准做法）
    ErrorLog "/proc/self/fd/2"
    CustomLog "/proc/self/fd/1" combined
    
    # 反向代理到后端
    ProxyPreserveHost On
    ProxyPass /api http://localhost:8080/api
    ProxyPassReverse /api http://localhost:8080/api
    
    # 安全头
    Header always set X-Frame-Options "SAMEORIGIN"
    Header always set X-XSS-Protection "1; mode=block"
</VirtualHost>
```

### 5.2 必需的 Apache 模块

在 Dockerfile 中启用：
```dockerfile
RUN sed -i '/LoadModule rewrite_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule deflate_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule expires_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule proxy_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule proxy_http_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule headers_module/s/^#//g' /usr/local/apache2/conf/httpd.conf
```

---

## 6. 端口冲突问题

### 6.1 问题现象

```
Bind for 0.0.0.0:5432 failed: port is already allocated
```

**原因**: `docker compose up -d` 会重启所有服务，包括 PostgreSQL，但 5432 端口已被 Gitea 占用。

### 6.2 解决方案

使用 `--no-deps` 参数，只重启目标容器：

```bash
# ❌ 错误：会重启所有依赖服务
docker compose up -d frontend

# ✅ 正确：只重启 frontend，不影响其他服务
docker compose up -d --no-deps frontend
```

---

## 7. 最终成功方案

### 7.1 工作流配置 (.gitea/workflows/ci-cd.yml)

```yaml
name: Build and Deploy

on:
  push:
    branches: [master]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '20'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Build frontend
        run: npm run build
      
      - name: Deploy to Server
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
          DEPLOY_USER: ${{ secrets.DEPLOY_USER }}
          DEPLOY_KEY: ${{ secrets.DEPLOY_KEY }}
          DEPLOY_PATH: ${{ secrets.DEPLOY_PATH }}
        run: |
          # Setup SSH
          mkdir -p ~/.ssh
          echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key
          
          # Upload files
          ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} \
            "mkdir -p ${DEPLOY_PATH}/dist ${DEPLOY_PATH}/frontend"
          
          scp -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no \
            docker-compose.prod.yml ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/
          
          scp -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no \
            Dockerfile.frontend ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/
          
          scp -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no \
            frontend/apache.conf ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/frontend/
          
          scp -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no \
            -r dist/* ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/dist/
          
          # Build and restart
          ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} \
            "cd ${DEPLOY_PATH} && \
             docker compose -f docker-compose.prod.yml build --no-cache frontend && \
             docker compose -f docker-compose.prod.yml up -d --no-deps frontend"
          
          # Verify
          sleep 5
          HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${DEPLOY_HOST}:8081/)
          if [ "$HTTP_STATUS" = "200" ]; then
            echo "✅ Deployment successful!"
          else
            echo "❌ Deployment failed (HTTP $HTTP_STATUS)"
            exit 1
          fi
```

### 7.2 Dockerfile.frontend

```dockerfile
FROM 04eh77udwizz5vqdbk.xuanyuan.run/library/httpd:2.4.67-trixie

# 启用必要的 Apache 模块
RUN sed -i '/LoadModule rewrite_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule deflate_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule expires_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule proxy_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule proxy_http_module/s/^#//g' /usr/local/apache2/conf/httpd.conf && \
    sed -i '/LoadModule headers_module/s/^#//g' /usr/local/apache2/conf/httpd.conf

# 复制自定义 VirtualHost 配置
COPY frontend/apache.conf /usr/local/apache2/conf/extra/myblog.conf

# 包含自定义配置
RUN echo "" >> /usr/local/apache2/conf/httpd.conf && \
    echo "# MyBlog Custom Configuration" >> /usr/local/apache2/conf/httpd.conf && \
    echo "Include conf/extra/myblog.conf" >> /usr/local/apache2/conf/httpd.conf

# 复制前端文件
COPY dist/ /usr/local/apache2/htdocs/

EXPOSE 80
```

### 7.3 备选方案：deploy.sh 脚本

如果 Gitea Actions 不稳定，可以使用本地部署脚本：

```bash
#!/bin/bash
set -e

DEPLOY_USER="ubuntu"
DEPLOY_HOST="113.31.105.30"
DEPLOY_PATH="/home/ubuntu/myblog-deploy"
SSH_KEY="$HOME/.ssh/gitea_local_key"

echo "📦 Building frontend..."
npm run build

echo "📤 Uploading files..."
scp -i "$SSH_KEY" -r dist/* ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/dist/
scp -i "$SSH_KEY" Dockerfile.frontend ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/
scp -i "$SSH_KEY" frontend/apache.conf ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/frontend/

echo "🔄 Rebuilding container..."
ssh -i "$SSH_KEY" ${DEPLOY_USER}@${DEPLOY_HOST} \
  "cd ${DEPLOY_PATH} && \
   docker compose build --no-cache frontend && \
   docker compose up -d --no-deps frontend"

echo "✅ Deployment complete!"
echo "Visit: http://${DEPLOY_HOST}:8081/"
```

---

## 8. 后端 DevOps 配置（已完成）

### 8.1 工作流文件

已创建专用的后端部署工作流：`.gitea/workflows/backend-ci-cd.yml`

**主要改进**:
- ✅ SSH 私钥使用 Base64 编码（避免 Gitea Secrets 截断问题）
- ✅ 所有敏感信息通过 Secrets 管理（不硬编码）
- ✅ 端口使用 18080（避免与 Gitea 冲突）
- ✅ 健康检查验证部署成功
- ✅ 详细的日志输出和错误处理

### 8.2 Dockerfile.backend 优化

**更新内容**:
```dockerfile
# JVM 参数优化
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=prod

# 支持环境变量传递
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 8.3 Secret 配置指南

详细文档：
- 📖 [BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md) - 完整指南
- 🚀 [BACKEND_SECRETS_QUICKSTART.md](./BACKEND_SECRETS_QUICKSTART.md) - 快速参考
- 🔑 [USE_EXISTING_SSH_KEY.md](./USE_EXISTING_SSH_KEY.md) - 使用现有 SSH 密钥

**配置助手脚本**:
```bash
# 脚本会自动检测现有密钥或提示生成新密钥
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

**使用现有密钥的步骤**:

1. **查看你的 SSH 密钥**
   ```bash
   ls -la ~/.ssh/id_* | grep -v ".pub$"
   # 常见：id_rsa, id_ed25519, id_ecdsa
   ```

2. **测试连接**
   ```bash
   ssh -i ~/.ssh/id_ed25519 ubuntu@113.31.105.30 echo "OK"
   ```

3. **如果失败，添加公钥**
   ```bash
   ssh-copy-id -i ~/.ssh/id_ed25519.pub ubuntu@113.31.105.30
   ```

4. **运行配置助手**
   ```bash
   ./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
   # 选择选项 1（使用现有密钥）
   # 输入密钥路径：/home/leo/.ssh/id_ed25519
   ```

5. **复制输出的 Secrets，在 Gitea 中配置**

### 8.4 需要配置的 11 个 Secrets

| 类别 | Secret 名称 | 说明 |
|------|------------|------|
| SSH | `DEPLOY_HOST` | 服务器 IP |
| SSH | `DEPLOY_USER` | SSH 用户名 |
| SSH | `DEPLOY_PATH` | 部署路径 |
| SSH | `DEPLOY_KEY` | Base64 编码的私钥 ⚠️ |
| 数据库 | `DB_USER` | PostgreSQL 用户 |
| 数据库 | `DB_PASSWORD` | PostgreSQL 密码 |
| 数据库 | `DB_NAME` | 数据库名称 |
| MinIO | `MINIO_ROOT_USER` | MinIO 管理员 |
| MinIO | `MINIO_ROOT_PASSWORD` | MinIO 密码 |
| MinIO | `MINIO_BUCKET_NAME` | Bucket 名称 |
| JWT | `JWT_SECRET` | JWT 签名密钥 |

### 8.5 部署流程

```bash
# 1. 配置 Secrets（使用助手脚本或手动）
./scripts/setup/configure-backend-secrets.sh <IP> <USER> <PATH>

# 2. 触发部署
git push origin master

# 3. 监控部署
gitea → Actions → Build and Deploy Backend

# 4. 验证结果
ssh ubuntu@IP "docker logs myblog-backend --tail 50"
curl http://IP:18080/actuator/health
```

### 8.6 避坑要点

根据踩坑记录，以下问题已解决：

1. **SSH 私钥格式问题**
   - ❌ 旧方案：直接粘贴私钥（被截断）
   - ✅ 新方案：Base64 编码 + 工作流中解码

2. **敏感信息泄露**
   - ❌ 旧方案：硬编码在 `.env` 和工作流中
   - ✅ 新方案：全部通过 Gitea Secrets 管理

3. **端口冲突**
   - ❌ 旧方案：使用 8080（可能与 Gitea 冲突）
   - ✅ 新方案：使用 18080

4. **健康检查缺失**
   - ❌ 旧方案：无法确认部署是否成功
   - ✅ 新方案：自动健康检查 + HTTP 状态码验证

---

## 9. 给后端的建议

### 9.1 Docker 配置

**Dockerfile.backend**:
```dockerfile
FROM 04eh77udwizz5vqdbk.xuanyuan.run/ubuntu/jdk:21-24.04_stable

WORKDIR /app

# 复制 JAR 文件
COPY target/*.jar app.jar

EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.prod.yml 中添加 backend**:
```yaml
services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile.backend
    ports:
      - "18080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/myblog
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - myblog-network
```

### 9.2 CI/CD 工作流扩展

在后端构建完成后，添加部署步骤：

```yaml
- name: Build Backend
  run: |
    mvn clean package -DskipTests

- name: Deploy Backend
  run: |
    # 上传 JAR 文件
    scp -i ~/.ssh/deploy_key target/*.jar ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/target/
    
    # 重新构建并重启后端容器
    ssh -i ~/.ssh/deploy_key ${DEPLOY_USER}@${DEPLOY_HOST} \
      "cd ${DEPLOY_PATH} && \
       docker compose build --no-cache backend && \
       docker compose up -d --no-deps backend"
    
    # 等待后端启动
    sleep 30
    
    # 健康检查
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${DEPLOY_HOST}:18080/actuator/health)
    if [ "$HTTP_STATUS" = "200" ]; then
      echo "✅ Backend deployment successful!"
    else
      echo "❌ Backend deployment failed"
      exit 1
    fi
```

### 9.3 环境变量配置

在服务器上创建 `.env` 文件：
```bash
# 数据库配置
DB_USER=myblog_user
DB_PASSWORD=<强密码>
DB_NAME=myblog

# Redis 配置
REDIS_PASSWORD=<强密码>

# MinIO 配置
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=<强密码>

# JWT 配置
JWT_SECRET=<随机字符串>
```

### 9.4 数据库初始化

```bash
# 创建数据库
docker exec -it myblog-postgres psql -U postgres -c "CREATE DATABASE myblog;"
docker exec -it myblog-postgres psql -U postgres -c "CREATE USER myblog_user WITH PASSWORD '<password>';"
docker exec -it myblog-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE myblog TO myblog_user;"

# 导入数据（如果有备份）
docker exec -i myblog-postgres psql -U postgres -d myblog < backup.sql
```

### 9.5 注意事项

1. **端口映射**: 后端使用 18080，避免与 Gitea 或其他服务冲突
2. **网络连接**: 确保 backend 和 frontend 在同一 Docker 网络中
3. **健康检查**: 配置 Spring Boot Actuator 健康检查端点
4. **日志管理**: 使用 Docker logs 或 ELK 栈收集日志
5. **备份策略**: 定期备份 PostgreSQL 数据库和 MinIO 数据

---

## 📞 联系与支持

如有问题，请查阅：
- Gitea Actions 文档: https://docs.gitea.com/usage/actions
- Docker Compose 文档: https://docs.docker.com/compose/
- Apache HTTPD 文档: https://httpd.apache.org/docs/

---

**最后更新**: 2026-05-16  
**状态**: ✅ 前端和后端 CI/CD 均已成功配置
