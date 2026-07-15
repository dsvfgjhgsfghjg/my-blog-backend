# MyBlog 后端 DevOps 配置完成总结

> **完成时间**: 2026-05-16  
> **状态**: ✅ 已完成并经过踩坑验证

---

## 📋 完成的工作

### 1. CI/CD 工作流配置

**文件**: `.gitea/workflows/backend-ci-cd.yml`

**功能**:
- ✅ 自动构建 Spring Boot 应用（Gradle）
- ✅ 运行单元测试（PostgreSQL + Redis）
- ✅ 构建 Docker 镜像
- ✅ 自动部署到生产服务器
- ✅ 健康检查验证部署成功

**关键改进**:
```yaml
# SSH 私钥 Base64 编码（避免 Gitea Secrets 截断）
echo "${{ secrets.DEPLOY_KEY }}" | base64 -d > ~/.ssh/deploy_key

# 使用 Secrets 管理所有敏感信息
-e SPRING_DATASOURCE_PASSWORD=${{ secrets.DB_PASSWORD }}
-e JWT_SECRET=${{ secrets.JWT_SECRET }}

# 端口使用 18080（避免与 Gitea 冲突）
-p 18080:8080

# 健康检查
curl -s -o /dev/null -w '%{http_code}' http://localhost:18080/actuator/health
```

### 2. Dockerfile.backend 优化

**文件**: `Dockerfile.backend`

**更新内容**:
```dockerfile
# JVM 参数优化
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 支持环境变量传递
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**优势**:
- 内存使用更合理（容器感知）
- 可通过环境变量调整 JVM 参数
- 非 root 用户运行（安全性）

### 3. Secret 配置指南

创建了完整的文档体系：

#### 📖 详细指南
**文件**: `docs/deployment/BACKEND_SECRETS_GUIDE.md`
- 519 行完整文档
- 逐步配置说明
- 常见问题解答
- 安全最佳实践

#### 🚀 快速参考
**文件**: `docs/deployment/BACKEND_SECRETS_QUICKSTART.md`
- 5 分钟快速配置
- 配置清单
- 命令速查

#### 🔧 配置助手脚本
**文件**: `scripts/setup/configure-backend-secrets.sh`
- 自动生成 SSH 密钥
- 自动测试连接
- 自动生成强密码
- 自动 Base64 编码
- 输出配置摘要

---

## 🔐 需要配置的 11 个 Secrets

| # | Secret 名称 | 类型 | 示例值 | 重要性 |
|---|------------|------|--------|--------|
| 1 | `DEPLOY_HOST` | SSH | `113.31.105.30` | ⭐⭐⭐ |
| 2 | `DEPLOY_USER` | SSH | `ubuntu` | ⭐⭐⭐ |
| 3 | `DEPLOY_PATH` | SSH | `/home/ubuntu/myblog-deploy` | ⭐⭐ |
| 4 | `DEPLOY_KEY` | SSH | `<Base64 私钥>` | ⭐⭐⭐⭐⭐ |
| 5 | `DB_USER` | 数据库 | `myblog_user` | ⭐⭐⭐ |
| 6 | `DB_PASSWORD` | 数据库 | `<强密码>` | ⭐⭐⭐⭐⭐ |
| 7 | `DB_NAME` | 数据库 | `myblog` | ⭐⭐ |
| 8 | `MINIO_ROOT_USER` | MinIO | `admin` | ⭐⭐⭐ |
| 9 | `MINIO_ROOT_PASSWORD` | MinIO | `<强密码>` | ⭐⭐⭐⭐⭐ |
| 10 | `MINIO_BUCKET_NAME` | MinIO | `myblog` | ⭐⭐ |
| 11 | `JWT_SECRET` | JWT | `<随机字符串>` | ⭐⭐⭐⭐⭐ |

**特别注意**:
- ⚠️ `DEPLOY_KEY` 必须是 **Base64 编码**的私钥
- ⚠️ 所有密码必须是**强密码**（至少 16 字符）
- ⚠️ `JWT_SECRET` 长度至少 **32 字符**

---

## 🎯 避坑要点总结

根据踩坑记录，以下问题已在新配置中解决：

### ❌ 问题 1: SSH 私钥格式错误

**现象**:
```
Load key "/root/.ssh/deploy_key": invalid format
```

**原因**: Gitea Secrets 不支持多行文本，直接粘贴私钥会被截断

**解决方案**:
```bash
# Base64 编码私钥
base64 -w 0 ~/.ssh/gitea_deploy_key

# 工作流中解码
echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
chmod 600 ~/.ssh/deploy_key
```

### ❌ 问题 2: 敏感信息泄露

**现象**: 密码硬编码在 `.env` 和工作流文件中

**风险**: 
- Git 历史中可能泄露
- 多人协作时难以管理

**解决方案**:
- ✅ 所有敏感信息通过 Gitea Secrets 管理
- ✅ `.env` 文件加入 `.gitignore`
- ✅ 工作流中使用 `${{ secrets.* }}` 引用

### ❌ 问题 3: 端口冲突

**现象**:
```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

**原因**: Gitea 或其他服务占用了标准端口

**解决方案**:
- ✅ 后端使用 `18080` 端口
- ✅ PostgreSQL 使用 `15432`（如需外部访问）
- ✅ Redis 使用 `16379`（如需外部访问）
- ✅ MinIO 使用 `19000/19001`

### ❌ 问题 4: 无法确认部署成功

**现象**: 容器启动但应用未正常运行

**解决方案**:
```yaml
# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 部署后验证
HTTP_STATUS=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:18080/actuator/health)
if [ "$HTTP_STATUS" = "200" ]; then
  echo "✅ Backend deployment successful!"
else
  echo "❌ Backend deployment failed (HTTP $HTTP_STATUS)"
  exit 1
fi
```

### ❌ 问题 5: Docker 网络隔离

**现象**: 后端无法连接数据库/Redis/MinIO

**原因**: 容器不在同一 Docker 网络中

**解决方案**:
```yaml
# 确保所有容器在同一网络
--network myblog_myblog-network

# 使用服务名而非 localhost
SPRING_DATASOURCE_URL=jdbc:postgresql://myblog-postgres:5432/myblog
SPRING_DATA_REDIS_HOST=myblog-redis
MINIO_ENDPOINT=http://myblog-minio:9000
```

---

## 📚 相关文档

### 主要文档
1. **[CICD_DEPLOYMENT_GUIDE.md](./CICD_DEPLOYMENT_GUIDE.md)** - 完整踩坑记录
2. **[BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md)** - Secret 配置详细指南
3. **[BACKEND_SECRETS_QUICKSTART.md](./BACKEND_SECRETS_QUICKSTART.md)** - 快速参考

### 配置文件
1. **`.gitea/workflows/backend-ci-cd.yml`** - 后端 CI/CD 工作流
2. **`Dockerfile.backend`** - 后端 Docker 镜像配置
3. **`docker-compose.prod.yml`** - Docker Compose 生产配置
4. **`scripts/setup/configure-backend-secrets.sh`** - Secret 配置助手

---

## 🚀 快速开始

### 步骤 1: 运行配置助手

```bash
# 替换为你的实际服务器信息
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

脚本会：
- ✓ 生成 SSH 密钥对
- ✓ 测试 SSH 连接
- ✓ 生成强密码
- ✓ Base64 编码私钥
- ✓ 输出所有 Secrets 配置
- ✓ 保存到加密文件

### 步骤 2: 在 Gitea 中配置 Secrets

1. 打开 Gitea 仓库
2. 进入 **Settings → Secrets**
3. 添加脚本输出的 11 个 Secrets
4. 特别注意 `DEPLOY_KEY` 使用 Base64 编码的值

### 步骤 3: 触发部署

```bash
git add .
git commit -m "ci: configure backend deployment"
git push origin master
```

### 步骤 4: 监控部署

1. 访问 Gitea → **Actions**
2. 查看 **Build and Deploy Backend** 工作流
3. 点击最新运行记录查看实时日志

### 步骤 5: 验证结果

```bash
# SSH 登录服务器
ssh -i ~/.ssh/gitea_deploy_key ubuntu@YOUR_SERVER_IP

# 查看容器状态
docker ps | grep myblog-backend

# 查看日志
docker logs myblog-backend --tail 50

# 健康检查
curl http://localhost:18080/actuator/health
# 应该返回: {"status":"UP"}
```

---

## 🔍 故障排查

### 工作流失败

**检查点**:
1. Gitea Actions Runner 是否在线？
   ```bash
   docker ps | grep gitea-runner
   ```

2. Secrets 是否正确配置？
   - 进入 Settings → Secrets
   - 检查所有 11 个 Secrets 是否存在
   - 确认 `DEPLOY_KEY` 是 Base64 字符串

3. SSH 连接是否正常？
   ```bash
   ssh -i ~/.ssh/gitea_deploy_key ubuntu@IP echo OK
   ```

### 部署后应用无法访问

**检查点**:
1. 容器是否运行？
   ```bash
   docker ps | grep myblog-backend
   ```

2. 端口映射是否正确？
   ```bash
   docker port myblog-backend
   # 应该显示: 8080/tcp -> 0.0.0.0:18080
   ```

3. 应用日志是否有错误？
   ```bash
   docker logs myblog-backend --tail 100
   ```

4. 数据库/Redis/MinIO 是否运行？
   ```bash
   docker ps | grep -E "postgres|redis|minio"
   ```

5. 网络连接是否正常？
   ```bash
   docker network inspect myblog_myblog-network
   docker exec myblog-backend ping myblog-postgres
   ```

---

## 📊 架构概览

```
┌─────────────────────────────────────────────┐
│           Gitea Repository                  │
│  ┌───────────────────────────────────────┐  │
│  │  backend-ci-cd.yml (Workflows)        │  │
│  │  - Build (Gradle)                     │  │
│  │  - Test (PostgreSQL + Redis)          │  │
│  │  - Deploy (Docker)                    │  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │  Secrets (11 items)                   │  │
│  │  - DEPLOY_HOST/USER/PATH/KEY          │  │
│  │  - DB_USER/PASSWORD/NAME              │  │
│  │  - MINIO_ROOT_USER/PASSWORD/BUCKET    │  │
│  │  - JWT_SECRET                         │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                    │
                    │ SSH + Base64 Key
                    ▼
┌─────────────────────────────────────────────┐
│         Production Server                   │
│  ┌───────────────────────────────────────┐  │
│  │  Docker Network: myblog_myblog-network│  │
│  │                                       │  │
│  │  ┌──────────────┐  ┌──────────────┐  │  │
│  │  │  PostgreSQL  │  │    Redis     │  │  │
│  │  │  :5432       │  │    :6379     │  │  │
│  │  └──────────────┘  └──────────────┘  │  │
│  │                                       │  │
│  │  ┌──────────────┐  ┌──────────────┐  │  │
│  │  │    MinIO     │  │   Backend    │  │  │
│  │  │  :9000/:9001 │  │   :8080      │  │  │
│  │  └──────────────┘  └──────────────┘  │  │
│  │                        │              │  │
│  │                        │ Port Map     │  │
│  │                        ▼              │  │
│  │                  External: 18080      │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                    │
                    │ HTTP
                    ▼
┌─────────────────────────────────────────────┐
│         Frontend (Apache)                   │
│  Port: 8081                                 │
│  Proxy: /api → http://backend:8080/api     │
└─────────────────────────────────────────────┘
```

---

## ✅ 验收标准

部署成功的标志：

- [ ] Gitea Actions 工作流全部步骤通过（绿色 ✓）
- [ ] `myblog-backend` 容器正在运行
- [ ] `curl http://SERVER_IP:18080/actuator/health` 返回 `{"status":"UP"}`
- [ ] 后端可以连接 PostgreSQL、Redis、MinIO
- [ ] 前端可以通过 `/api` 访问后端接口
- [ ] 日志中没有 ERROR 或 Exception

---

## 🔄 后续维护

### 定期任务

1. **密钥轮换**（每 3-6 个月）
   ```bash
   # 生成新的 SSH 密钥
   ssh-keygen -t ed25519 -f ~/.ssh/gitea_deploy_key_new
   
   # 更新服务器公钥
   ssh-copy-id -i ~/.ssh/gitea_deploy_key_new.pub ubuntu@IP
   
   # 更新 Gitea Secrets
   base64 -w 0 ~/.ssh/gitea_deploy_key_new
   ```

2. **密码更新**（每 6 个月）
   ```bash
   # 生成新密码
   openssl rand -base64 32 | head -c 20
   
   # 更新数据库密码
   docker exec myblog-postgres psql -U postgres \
     -c "ALTER USER myblog_user WITH PASSWORD 'new_password';"
   
   # 更新 Gitea Secrets
   # 重启后端容器
   docker restart myblog-backend
   ```

3. **日志审查**（每周）
   ```bash
   docker logs myblog-backend --since 7d | grep -i "error\|exception"
   ```

4. **备份验证**（每月）
   ```bash
   # 数据库备份
   docker exec myblog-postgres pg_dump -U myblog_user myblog > backup.sql
   
   # MinIO 备份
   mc mirror local/myblog backup/myblog
   ```

---

## 📞 支持资源

- **Gitea Actions 文档**: https://docs.gitea.com/usage/actions
- **Docker 文档**: https://docs.docker.com/
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **项目 Issues**: Gitea → Issues 标签

---

**配置完成！🎉**

如有问题，请查阅详细文档或提交 Issue。
