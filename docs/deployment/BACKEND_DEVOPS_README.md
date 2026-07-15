# MyBlog 后端 DevOps 配置指南

> 完整的后端自动化部署解决方案，基于 Gitea Actions + Docker

---

## 📚 文档导航

### 🚀 快速开始
- **[BACKEND_SECRETS_QUICKSTART.md](./BACKEND_SECRETS_QUICKSTART.md)** - 5 分钟完成 Secret 配置

### 📖 详细文档
- **[BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md)** - 完整的 Secret 配置指南（519 行）
- **[BACKEND_DEVOPS_SUMMARY.md](./BACKEND_DEVOPS_SUMMARY.md)** - 配置完成总结与验收标准
- **[CICD_DEPLOYMENT_GUIDE.md](./CICD_DEPLOYMENT_GUIDE.md)** - 完整踩坑记录（第 8 章为后端配置）

### 🔧 工具脚本
- **`scripts/setup/configure-backend-secrets.sh`** - Secret 配置助手（自动生成所有配置）

---

## 🎯 核心功能

✅ **自动化构建**: Gradle 编译 Spring Boot 应用  
✅ **自动化测试**: PostgreSQL + Redis 集成测试  
✅ **自动化部署**: SSH + Docker 一键部署  
✅ **健康检查**: Actuator 端点验证部署成功  
✅ **安全管理**: Gitea Secrets 管理所有敏感信息  
✅ **避坑优化**: Base64 编码 SSH 私钥、端口规划、网络隔离  

---

## 🚀 3 步完成配置

### 步骤 1: 运行配置助手

```bash
./scripts/setup/configure-backend-secrets.sh <服务器IP> <SSH用户> <部署路径>

# 示例：
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

### 步骤 2: 在 Gitea 中配置 Secrets

复制脚本输出的 11 个 Secrets，在 Gitea 仓库的 **Settings → Secrets** 中添加。

### 步骤 3: 触发部署

```bash
git push origin master
```

访问 Gitea → Actions 查看部署进度。

---

## 📋 需要配置的 11 个 Secrets

| 类别 | Secret | 说明 |
|------|--------|------|
| SSH | `DEPLOY_HOST` | 服务器 IP |
| SSH | `DEPLOY_USER` | SSH 用户名 |
| SSH | `DEPLOY_PATH` | 部署路径 |
| SSH | `DEPLOY_KEY` | ⚠️ Base64 编码的私钥 |
| 数据库 | `DB_USER` | PostgreSQL 用户 |
| 数据库 | `DB_PASSWORD` | PostgreSQL 密码 |
| 数据库 | `DB_NAME` | 数据库名称 |
| MinIO | `MINIO_ROOT_USER` | MinIO 管理员 |
| MinIO | `MINIO_ROOT_PASSWORD` | MinIO 密码 |
| MinIO | `MINIO_BUCKET_NAME` | Bucket 名称 |
| JWT | `JWT_SECRET` | JWT 签名密钥 |

---

## 🔍 验证部署

```bash
# 1. 检查容器状态
docker ps | grep myblog-backend

# 2. 查看日志
docker logs myblog-backend --tail 50

# 3. 健康检查
curl http://SERVER_IP:18080/actuator/health
# 应返回: {"status":"UP"}
```

---

## ⚠️ 重要提示

1. **SSH 私钥必须 Base64 编码**
   ```bash
   base64 -w 0 ~/.ssh/gitea_deploy_key
   ```

2. **不要硬编码敏感信息**
   - ❌ 不要在 `.env` 文件中提交到 Git
   - ❌ 不要在工作流中写死密码
   - ✅ 所有敏感信息通过 Secrets 管理

3. **端口规划**
   - 后端 API: `18080`（避免与 Gitea 冲突）
   - PostgreSQL: `15432`（外部访问）
   - Redis: `16379`（外部访问）
   - MinIO: `19000/19001`

---

## 🛠️ 故障排查

### 工作流失败？
- 检查 Gitea Runner 是否在线
- 验证 Secrets 配置是否正确
- 测试 SSH 连接是否正常

### 部署后无法访问？
- 检查容器是否运行：`docker ps`
- 查看应用日志：`docker logs myblog-backend`
- 验证网络连接：`docker network inspect myblog_myblog-network`

详细故障排查请查看 [BACKEND_DEVOPS_SUMMARY.md](./BACKEND_DEVOPS_SUMMARY.md)

---

## 📊 架构图

```
Gitea Repository (backend-ci-cd.yml + Secrets)
         │
         │ SSH + Base64 Key
         ▼
Production Server (Docker Network)
         ├─ PostgreSQL (:5432)
         ├─ Redis (:6379)
         ├─ MinIO (:9000/:9001)
         └─ Backend (:8080 → 18080)
                  │
                  │ HTTP
                  ▼
         Frontend (Apache :8081)
```

---

## 📞 获取帮助

- 📖 详细指南：[BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md)
- 🚀 快速参考：[BACKEND_SECRETS_QUICKSTART.md](./BACKEND_SECRETS_QUICKSTART.md)
- 📝 踩坑记录：[CICD_DEPLOYMENT_GUIDE.md](./CICD_DEPLOYMENT_GUIDE.md)
- ✅ 验收标准：[BACKEND_DEVOPS_SUMMARY.md](./BACKEND_DEVOPS_SUMMARY.md)

---

**准备好了吗？开始配置吧！** 🚀

```bash
./scripts/setup/configure-backend-secrets.sh YOUR_SERVER_IP ubuntu /home/ubuntu/myblog-deploy
```
