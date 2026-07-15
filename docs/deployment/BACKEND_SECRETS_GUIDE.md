# MyBlog 后端 Secret 配置指南

> **重要提示**: 本文档指导你如何安全地配置 Gitea Actions Secrets，用于后端自动化部署。

---

## 📋 目录

1. [Secrets 概述](#secrets-概述)
2. [需要配置的 Secrets](#需要配置的-secrets)
3. [详细配置步骤](#详细配置步骤)
4. [SSH 密钥生成与配置](#ssh-密钥生成与配置)
5. [环境变量生成指南](#环境变量生成指南)
6. [验证配置](#验证配置)
7. [常见问题](#常见问题)

---

## Secrets 概述

根据踩坑记录，Gitea Secrets **不支持多行文本**，因此对于 SSH 私钥等 multiline 内容，需要使用 **Base64 编码**。

### ⚠️ 关键注意事项

1. **SSH 私钥必须 Base64 编码**
   ```bash
   # 错误做法：直接粘贴私钥（会被截断）
   # 正确做法：base64 -w 0 ~/.ssh/deploy_key
   
   # 在工作流中解码使用
   echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
   chmod 600 ~/.ssh/deploy_key
   ```

2. **不要硬编码敏感信息**
   - ❌ 不要在 `.env` 文件中提交到 Git
   - ❌ 不要在工作流文件中写死密码
   - ✅ 所有敏感信息通过 Secrets 管理

3. **端口规划**（避免与 Gitea 冲突）
   - 后端 API: `18080` (映射到容器内 8080)
   - PostgreSQL: `15432` (如果外部访问)
   - Redis: `16379` (如果外部访问)
   - MinIO API: `19000`, Console: `19001`

---

## 需要配置的 Secrets

在 Gitea 仓库设置中，需要添加以下 Secrets：

### 1. SSH 部署相关

| Secret 名称 | 说明 | 示例值 |
|------------|------|--------|
| `DEPLOY_HOST` | 服务器 IP 地址或域名 | `113.31.105.30` |
| `DEPLOY_USER` | SSH 登录用户名 | `ubuntu` |
| `DEPLOY_PATH` | 部署目录路径 | `/home/ubuntu/myblog-deploy` |
| `DEPLOY_KEY` | **Base64 编码的 SSH 私钥** | `(见下方生成步骤)` |

### 2. 数据库配置

| Secret 名称 | 说明 | 示例值 |
|------------|------|--------|
| `DB_USER` | PostgreSQL 用户名 | `myblog_user` |
| `DB_PASSWORD` | PostgreSQL 密码 | `<强密码>` |
| `DB_NAME` | 数据库名称 | `myblog` |

### 3. MinIO 对象存储配置

| Secret 名称 | 说明 | 示例值 |
|------------|------|--------|
| `MINIO_ROOT_USER` | MinIO 管理员用户名 | `admin` |
| `MINIO_ROOT_PASSWORD` | MinIO 管理员密码 | `<强密码>` |
| `MINIO_BUCKET_NAME` | MinIO Bucket 名称 | `myblog` |

### 4. JWT 安全配置

| Secret 名称 | 说明 | 示例值 |
|------------|------|--------|
| `JWT_SECRET` | JWT 签名密钥（至少 32 字符） | `<随机字符串>` |

---

## 详细配置步骤

### 步骤 1: 生成 SSH 密钥对（如果还没有）

```bash
# 在本地机器上执行
ssh-keygen -t ed25519 -C "your_email@example.com" -f ~/.ssh/gitea_deploy_key

# 会生成两个文件：
# ~/.ssh/gitea_deploy_key     (私钥)
# ~/.ssh/gitea_deploy_key.pub (公钥)
```

### 步骤 2: 将公钥添加到服务器

```bash
# 方法 1: 使用 ssh-copy-id（推荐）
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@113.31.105.30

# 方法 2: 手动添加
# 先登录服务器
ssh ubuntu@113.31.105.30

# 在服务器上执行
mkdir -p ~/.ssh
echo "<公钥内容>" >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
exit
```

### 步骤 3: 测试 SSH 连接

```bash
# 确保可以无密码登录
ssh -i ~/.ssh/gitea_deploy_key ubuntu@113.31.105.30 echo "Success"
```

如果输出 `Success`，说明 SSH 配置成功。

### 步骤 4: Base64 编码私钥

```bash
# 在本地机器上执行
base64 -w 0 ~/.ssh/gitea_deploy_key

# 会输出一长串 Base64 字符串，类似：
# LS0tLS1CRUdJTiBPUEVOU1NIIFBSSVZBVEUgS0VZLS0tLS0KYjNCbGJuTnphQzFyWlhrdGRqRUFB...
```

**复制整个输出字符串**（不要换行，不要遗漏任何字符）。

### 步骤 5: 在 Gitea 中配置 Secrets

1. 打开你的 Gitea 仓库
2. 进入 **Settings** → **Secrets**
3. 点击 **Add Secret**
4. 依次添加以下 Secrets：

#### SSH 相关
```
Name: DEPLOY_HOST
Value: 113.31.105.30

Name: DEPLOY_USER
Value: ubuntu

Name: DEPLOY_PATH
Value: /home/ubuntu/myblog-deploy

Name: DEPLOY_KEY
Value: <粘贴 Base64 编码的私钥字符串>
```

#### 数据库相关
```
Name: DB_USER
Value: myblog_user

Name: DB_PASSWORD
Value: <生成强密码，见下方指南>

Name: DB_NAME
Value: myblog
```

#### MinIO 相关
```
Name: MINIO_ROOT_USER
Value: admin

Name: MINIO_ROOT_PASSWORD
Value: <生成强密码>

Name: MINIO_BUCKET_NAME
Value: myblog
```

#### JWT 相关
```
Name: JWT_SECRET
Value: <生成随机字符串，见下方指南>
```

---

## SSH 密钥生成与配置

### 完整的 SSH 配置流程

```bash
# ============================================
# 1. 生成密钥对
# ============================================
ssh-keygen -t ed25519 -C "your_email@example.com" -f ~/.ssh/gitea_deploy_key
# 按回车使用默认设置（不需要 passphrase）

# ============================================
# 2. 查看公钥
# ============================================
cat ~/.ssh/gitea_deploy_key.pub
# 复制输出的内容

# ============================================
# 3. 添加公钥到服务器
# ============================================
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@YOUR_SERVER_IP

# ============================================
# 4. 测试连接
# ============================================
ssh -i ~/.ssh/gitea_deploy_key ubuntu@YOUR_SERVER_IP echo "SSH OK"

# ============================================
# 5. Base64 编码私钥
# ============================================
base64 -w 0 ~/.ssh/gitea_deploy_key
# 复制输出，粘贴到 Gitea Secrets 的 DEPLOY_KEY
```

### 验证 SSH 配置

```bash
# 在本地测试
ssh -i ~/.ssh/gitea_deploy_key ubuntu@YOUR_SERVER_IP \
  "docker ps && echo 'Docker accessible'"

# 应该能看到 Docker 容器列表和 "Docker accessible" 消息
```

---

## 环境变量生成指南

### 生成强密码

```bash
# 方法 1: 使用 openssl（推荐）
openssl rand -base64 32

# 方法 2: 使用 /dev/urandom
cat /dev/urandom | tr -dc 'a-zA-Z0-9!@#$%^&*' | fold -w 20 | head -n 1

# 示例输出: X9k#mP2$vL5nQ8wR3jF6hT1yB4cA7eD0
```

### 生成 JWT_SECRET

```bash
# JWT Secret 应该是一个随机的长字符串（至少 32 字符）
openssl rand -base64 64

# 示例输出: 
# kR8vN2mP5xL9wQ3jF6hT1yB4cA7eD0sG1iU4oK8nM2pV5zX9bC3fH6kJ0lO7rE
```

### 推荐的密码强度

- **长度**: 至少 16 字符
- **复杂度**: 包含大小写字母、数字、特殊字符
- **唯一性**: 不同服务使用不同密码

---

## 验证配置

### 1. 检查 Secrets 是否正确配置

在 Gitea 仓库中：
1. 进入 **Settings** → **Secrets**
2. 确认所有 11 个 Secrets 都已添加
3. 检查 `DEPLOY_KEY` 是否为 Base64 编码字符串（不应该包含 `-----BEGIN` 等字样）

### 2. 手动触发工作流测试

```bash
# 推送一个测试提交
git add .
git commit -m "test: trigger backend CI/CD"
git push origin master
```

然后在 Gitea 中：
1. 进入 **Actions** 标签
2. 查看 `Build and Deploy Backend` 工作流
3. 点击最新的运行记录
4. 检查每个步骤的执行情况

### 3. 验证部署结果

```bash
# SSH 登录服务器
ssh -i ~/.ssh/gitea_deploy_key ubuntu@YOUR_SERVER_IP

# 检查容器状态
docker ps | grep myblog-backend

# 查看日志
docker logs myblog-backend --tail 50

# 健康检查
curl http://localhost:18080/actuator/health

# 应该返回: {"status":"UP"}
```

### 4. 检查网络连接

```bash
# 确保 backend 容器在正确的网络中
docker network inspect myblog_myblog-network

# 应该能看到 myblog-backend、myblog-postgres、myblog-redis、myblog-minio 都在同一网络
```

---

## 常见问题

### Q1: Load key "/root/.ssh/deploy_key": invalid format

**原因**: Gitea Secrets 截断了多行私钥

**解决方案**:
```bash
# 重新 Base64 编码私钥
base64 -w 0 ~/.ssh/gitea_deploy_key

# 更新 Gitea Secrets 中的 DEPLOY_KEY

# 在工作流中确保正确解码
echo "$DEPLOY_KEY" | base64 -d > ~/.ssh/deploy_key
chmod 600 ~/.ssh/deploy_key
```

### Q2: Permission denied (publickey)

**原因**: 服务器上没有对应的公钥

**解决方案**:
```bash
# 重新添加公钥到服务器
ssh-copy-id -i ~/.ssh/gitea_deploy_key.pub ubuntu@YOUR_SERVER_IP

# 或者手动检查
ssh ubuntu@YOUR_SERVER_IP "cat ~/.ssh/authorized_keys | grep gitea_deploy"
```

### Q3: docker: Cannot connect to the Docker daemon

**原因**: 用户没有 Docker 权限

**解决方案**:
```bash
# 在服务器上执行
sudo usermod -aG docker ubuntu
# 重新登录 SSH 会话
exit
ssh ubuntu@YOUR_SERVER_IP
```

### Q4: 端口已被占用

**原因**: 其他服务占用了 18080 端口

**解决方案**:
```bash
# 检查端口占用
sudo lsof -i :18080

# 停止占用端口的服务
sudo systemctl stop <service_name>

# 或者修改工作流中的端口映射
# -p 18081:8080  (改为其他端口)
```

### Q5: 数据库连接失败

**原因**: 数据库容器未启动或网络配置错误

**解决方案**:
```bash
# 检查数据库容器状态
docker ps | grep postgres

# 检查网络连通性
docker exec myblog-backend ping myblog-postgres

# 查看后端日志
docker logs myblog-backend | grep "datasource"
```

### Q6: JWT_SECRET 太短导致启动失败

**原因**: JWT Secret 长度不足 32 字符

**解决方案**:
```bash
# 生成新的 JWT Secret
openssl rand -base64 64

# 更新 Gitea Secrets 中的 JWT_SECRET

# 重启容器
docker restart myblog-backend
```

---

## 安全最佳实践

### 1. 定期轮换密钥

```bash
# 每 3-6 个月更换一次
# 1. 生成新的 SSH 密钥
ssh-keygen -t ed25519 -C "new_key_$(date +%Y%m%d)" -f ~/.ssh/gitea_deploy_key_new

# 2. 替换服务器上的公钥
ssh-copy-id -i ~/.ssh/gitea_deploy_key_new.pub ubuntu@YOUR_SERVER_IP

# 3. 更新 Gitea Secrets
base64 -w 0 ~/.ssh/gitea_deploy_key_new

# 4. 测试新密钥
ssh -i ~/.ssh/gitea_deploy_key_new ubuntu@YOUR_SERVER_IP echo "OK"

# 5. 删除旧密钥
rm ~/.ssh/gitea_deploy_key ~/.ssh/gitea_deploy_key.pub
```

### 2. 限制 SSH 密钥权限

```bash
# 在服务器上编辑 /etc/ssh/sshd_config
sudo nano /etc/ssh/sshd_config

# 添加或修改以下配置
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys

# 重启 SSH 服务
sudo systemctl restart sshd
```

### 3. 监控部署日志

```bash
# 设置日志告警
docker logs -f myblog-backend | grep -i "error\|exception"

# 或使用日志管理工具（如 ELK Stack）
```

### 4. 备份 Secrets

```bash
# 安全地备份 Secrets（加密存储）
# 不要明文保存！

# 建议使用密码管理器（如 Bitwarden、1Password）
# 或加密文件
gpg --symmetric --cipher-algo AES256 secrets_backup.txt
```

---

## 快速检查清单

在开始部署前，确认以下事项：

- [ ] 已生成 SSH 密钥对
- [ ] 公钥已添加到服务器的 `~/.ssh/authorized_keys`
- [ ] 可以无密码 SSH 登录服务器
- [ ] 私钥已 Base64 编码
- [ ] 已在 Gitea 中配置所有 11 个 Secrets
- [ ] `DEPLOY_KEY` 是 Base64 字符串（不是原始私钥）
- [ ] 所有密码都是强密码（至少 16 字符）
- [ ] `JWT_SECRET` 长度至少 32 字符
- [ ] 服务器已安装 Docker 和 Docker Compose
- [ ] 服务器上有 `myblog_myblog-network` 网络
- [ ] PostgreSQL、Redis、MinIO 容器正在运行

---

## 下一步

配置完 Secrets 后：

1. **触发首次部署**:
   ```bash
   git push origin master
   ```

2. **监控部署过程**:
   - 访问 Gitea → Actions → Build and Deploy Backend
   - 查看实时日志

3. **验证部署**:
   ```bash
   curl http://YOUR_SERVER_IP:18080/actuator/health
   ```

4. **配置前端反向代理**（可选）:
   - 在 Apache/Nginx 中配置 `/api` 路由到后端
   - 或修改前端配置文件指向 `http://YOUR_SERVER_IP:18080`

---

**最后更新**: 2026-05-16  
**状态**: ✅ 文档已完成，等待配置 Secrets
