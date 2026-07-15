# MyBlog 后端 Secret 配置 - 快速参考

> **5 分钟完成所有 Secret 配置**

---

## 🚀 快速开始（推荐方式）

### 方法 1: 使用配置助手脚本（最简单）

```bash
# 运行配置助手
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy

# 脚本会自动：
# ✓ 生成 SSH 密钥对
# ✓ 测试 SSH 连接
# ✓ 生成强密码
# ✓ Base64 编码私钥
# ✓ 输出所有 Secrets 配置
```

### 方法 2: 手动配置

#### 步骤 1: 生成 SSH 密钥

```bash
ssh-keygen -t ed25519 -C "gitea_deploy" -f ~/.ssh/gitea_deploy_key -N ""
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@113.31.105.30
```

#### 步骤 2: Base64 编码私钥

```bash
base64 -w 0 ~/.ssh/gitea_deploy_key
# 复制输出的字符串
```

#### 步骤 3: 生成密码

```bash
# 数据库密码
openssl rand -base64 32 | head -c 20

# MinIO 密码
openssl rand -base64 32 | head -c 20

# JWT Secret
openssl rand -base64 64
```

#### 步骤 4: 在 Gitea 中配置

进入 **Settings → Secrets**，添加以下 11 个 Secrets：

| Secret 名称 | 值 | 说明 |
|------------|-----|------|
| `DEPLOY_HOST` | `113.31.105.30` | 服务器 IP |
| `DEPLOY_USER` | `ubuntu` | SSH 用户 |
| `DEPLOY_PATH` | `/home/ubuntu/myblog-deploy` | 部署路径 |
| `DEPLOY_KEY` | `<Base64 私钥>` | ⚠️ 必须 Base64 编码 |
| `DB_USER` | `myblog_user` | 数据库用户 |
| `DB_PASSWORD` | `<强密码>` | 数据库密码 |
| `DB_NAME` | `myblog` | 数据库名 |
| `MINIO_ROOT_USER` | `admin` | MinIO 用户 |
| `MINIO_ROOT_PASSWORD` | `<强密码>` | MinIO 密码 |
| `MINIO_BUCKET_NAME` | `myblog` | Bucket 名称 |
| `JWT_SECRET` | `<随机字符串>` | 至少 32 字符 |

---

## ✅ 验证清单

配置完成后，检查：

- [ ] 所有 11 个 Secrets 已添加
- [ ] `DEPLOY_KEY` 是 Base64 字符串（不包含 `-----BEGIN`）
- [ ] 可以 SSH 无密码登录：`ssh -i ~/.ssh/gitea_deploy_key ubuntu@IP echo OK`
- [ ] 服务器上 Docker 正在运行
- [ ] `myblog_myblog-network` 网络存在

---

## 🔧 触发部署

```bash
git add .
git commit -m "ci: configure backend deployment"
git push origin master
```

然后在 Gitea 中查看 Actions 标签。

---

## 📊 监控部署

```bash
# SSH 登录服务器
ssh -i ~/.ssh/gitea_deploy_key ubuntu@YOUR_SERVER_IP

# 查看容器状态
docker ps | grep myblog

# 查看后端日志
docker logs myblog-backend -f

# 健康检查
curl http://localhost:18080/actuator/health
```

---

## ⚠️ 常见问题速查

### Load key: invalid format
```bash
# 重新 Base64 编码
base64 -w 0 ~/.ssh/gitea_deploy_key
# 更新 DEPLOY_KEY
```

### Permission denied
```bash
# 重新添加公钥
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@IP
```

### 端口冲突
```bash
# 检查工作流中的端口映射
# 修改 -p 18080:8080 为其他端口
```

---

## 📚 详细文档

完整指南请查看：[BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md)

---

**最后更新**: 2026-05-16
