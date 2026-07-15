# 本地Gitea推送和后端部署指南

## 📅 创建时间：2026-05-14

本指南指导您如何将代码推送到本地Gitea仓库，并通过CI/CD自动部署新的后端系统。

---

## 🎯 部署流程概览

```
1. 提交代码到Git → 2. 推送到Gitea → 3. 触发CI/CD → 4. 自动构建 → 5. 自动部署
```

---

## 📋 前置条件检查

### 1. 确认服务状态

```bash
# 检查Gitea是否运行
docker ps | grep gitea

# 检查Docker网络
docker network ls | grep myblog

# 检查数据库和Redis
docker ps | grep -E "postgres|redis"
```

**预期输出**：
- ✅ myblog-gitea 正在运行（端口3000、222）
- ✅ myblog-myblog-network 网络存在
- ✅ myblog-postgres 和 myblog-redis 正在运行

---

## 🚀 部署步骤

### 步骤1：配置Git远程仓库

#### 1.1 访问Gitea Web界面

在浏览器中打开：
```
http://localhost:3000
```

使用您的Gitea账号登录（默认管理员账号通常是 `leoxiao`）。

#### 1.2 创建新仓库

1. 点击右上角 **"+"** → **"新建仓库"**
2. 填写信息：
   - **仓库名称**: `myblog`
   - **可见性**: 私有（推荐）
   - **初始化选项**: ❌ 不勾选任何选项（我们已有代码）
3. 点击 **"创建仓库"**

#### 1.3 添加Git远程地址

在终端执行：

```bash
cd /home/leo/IdeaProjects/myblog

# 添加Gitea远程仓库（HTTP方式）
git remote add origin http://localhost:3000/leoxiao/myblog.git

# 或者使用SSH方式（推荐）
git remote add origin ssh://git@localhost:222/leoxiao/myblog.git

# 验证远程仓库
git remote -v
```

**预期输出**：
```
origin  ssh://git@localhost:222/leoxiao/myblog.git (fetch)
origin  ssh://git@localhost:222/leoxiao/myblog.git (push)
```

---

### 步骤2：提交并推送代码

#### 2.1 查看当前更改

```bash
# 查看所有修改的文件
git status

# 查看具体改动
git diff
```

#### 2.2 添加并提交

```bash
# 添加所有文件
git add .

# 提交更改
git commit -m "feat: 增强审计日志系统 - 添加设备信息展示和空值处理

- 新增设备信息字段（deviceType、osName、browserName）
- 所有空字段统一显示为N/A
- 从extraData解析登录设备信息
- 完善AuditLogResponse响应结构
- 创建AUDIT_LOG_ENHANCEMENT.md文档"
```

#### 2.3 推送到Gitea

```bash
# 首次推送（设置上游分支）
git push -u origin master

# 或者如果是main分支
git push -u origin main
```

**如果提示需要认证**：
- HTTP方式：输入Gitea用户名和密码
- SSH方式：确保已配置SSH密钥

---

### 步骤3：监控CI/CD执行

#### 3.1 查看Actions执行状态

1. 访问Gitea仓库页面：`http://localhost:3000/leoxiao/myblog`
2. 点击顶部导航栏的 **"Actions"** 标签
3. 查看最新的工作流执行状态

**执行流程**：
```
✅ build（构建JAR包）
  ↓
✅ test（运行单元测试）
  ↓
✅ deploy（构建Docker镜像）
  ↓
✅ deploy-to-server（部署到服务器）
```

#### 3.2 查看详细日志

点击每个Job可以查看详细的执行日志：

- **build**: 查看Gradle编译输出
- **test**: 查看测试结果
- **deploy**: 查看Docker镜像构建过程
- **deploy-to-server**: 查看部署命令执行

---

### 步骤4：验证部署结果

#### 4.1 检查容器状态

```bash
# 查看后端容器
docker ps | grep myblog-backend

# 查看容器日志
docker logs -f myblog-backend --tail 100
```

**预期输出**：
```
Started MyblogApplication in X.XXX seconds
```

#### 4.2 测试API接口

```bash
# 测试健康检查
curl http://localhost:8080/api/health

# 测试文章列表
curl http://localhost:8080/api/posts/public?pageNum=1&pageSize=5

# 测试审计日志（需要管理员Token）
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/api/system/audit-logs?pageNum=1&pageSize=5
```

#### 4.3 验证新功能

测试审计日志的设备信息字段：

```bash
# 获取审计日志详情
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/api/system/audit-logs/1234567890 | jq .
```

**预期响应包含**：
```json
{
  "deviceType": "DESKTOP",
  "osName": "Windows 10",
  "browserName": "Chrome 120.0",
  "userAgent": "Mozilla/5.0...",
  "ipAddress": "192.168.1.100"
}
```

---

## 🔧 常见问题排查

### 问题1：推送失败 - 认证错误

**症状**：
```
fatal: Authentication failed for 'http://localhost:3000/...'
```

**解决方案**：

**方法1：使用SSH（推荐）**
```bash
# 生成SSH密钥（如果没有）
ssh-keygen -t ed25519 -C "your_email@example.com"

# 复制公钥
cat ~/.ssh/id_ed25519.pub

# 在Gitea中添加SSH密钥：
# 设置 → SSH密钥 → 添加密钥
```

**方法2：使用Personal Access Token**
```bash
# 在Gitea生成Token：
# 设置 → 应用 → 生成新令牌

# 使用Token推送
git remote set-url origin http://leoxiao:YOUR_TOKEN@localhost:3000/leoxiao/myblog.git
git push -u origin master
```

---

### 问题2：CI/CD未触发

**症状**：推送后Actions没有执行

**排查步骤**：

1. **检查分支名称**
   ```bash
   git branch
   # 确保是 master 或 main 分支
   ```

2. **检查workflow文件**
   ```bash
   cat .gitea/workflows/ci-cd.yml
   # 确认on.push.branches包含当前分支
   ```

3. **手动触发**
   - 在Gitea Actions页面点击 **"运行工作流"**

---

### 问题3：部署失败 - Docker网络不存在

**症状**：
```
Error response from daemon: network myblog_myblog-network not found
```

**解决方案**：

```bash
# 创建Docker网络
docker network create myblog_myblog-network

# 验证网络
docker network ls | grep myblog
```

---

### 问题4：容器启动失败 - 数据库连接错误

**症状**：
```
org.postgresql.util.PSQLException: Connection refused
```

**排查步骤**：

1. **检查PostgreSQL容器**
   ```bash
   docker ps | grep postgres
   docker logs myblog-postgres
   ```

2. **检查网络连接**
   ```bash
   docker inspect myblog-backend | grep NetworkMode
   docker network inspect myblog_myblog-network
   ```

3. **重启容器**
   ```bash
   docker restart myblog-postgres
   docker restart myblog-backend
   ```

---

### 问题5：端口冲突

**症状**：
```
Error starting userland proxy: bind tcp 0.0.0.0:8080: bind: address already in use
```

**解决方案**：

```bash
# 查找占用端口的进程
sudo lsof -i :8080

# 停止旧的后端容器
docker stop myblog-backend
docker rm myblog-backend

# 重新启动
docker run -d --name myblog-backend ...
```

---

## 📊 部署验证清单

完成部署后，请逐项检查：

- [ ] Gitea仓库已创建并可访问
- [ ] 代码已成功推送到Gitea
- [ ] CI/CD工作流执行成功（所有Job绿色✓）
- [ ] Docker镜像构建成功
- [ ] 后端容器正在运行
- [ ] 数据库迁移脚本已执行（V18、V19）
- [ ] API接口可正常访问
- [ ] 审计日志返回设备信息字段
- [ ] 空字段显示为"N/A"
- [ ] 前端可以正常调用新API

---

## 🔄 后续更新流程

下次代码更新时，只需执行：

```bash
# 1. 提交更改
git add .
git commit -m "描述你的更改"

# 2. 推送到Gitea（自动触发CI/CD）
git push

# 3. 等待自动部署完成（约3-5分钟）
# 4. 验证部署结果
```

---

## 📝 环境变量说明

部署时使用的关键环境变量：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://myblog-postgres:5432/myblog` | 数据库连接 |
| `SPRING_DATASOURCE_USERNAME` | `Leoxiaoblog` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | `your-password-here` | 数据库密码 |
| `SPRING_DATA_REDIS_HOST` | `myblog-redis` | Redis主机 |
| `MINIO_ENDPOINT` | `http://myblog-minio:9000` | MinIO地址 |
| `MINIO_ACCESS_KEY` | `Leoxiao` | MinIO访问密钥 |
| `MINIO_SECRET_KEY` | `your-password-here` | MinIO秘密密钥 |

⚠️ **安全提示**：生产环境建议使用Docker Secrets或Vault管理敏感信息。

---

## 🎯 快速参考命令

```bash
# === Git操作 ===
git status                          # 查看状态
git add .                           # 添加所有文件
git commit -m "消息"                # 提交
git push                            # 推送
git log --oneline -5               # 查看最近5次提交

# === Docker操作 ===
docker ps                           # 查看运行中的容器
docker logs myblog-backend -f      # 查看后端日志
docker restart myblog-backend      # 重启后端
docker stop myblog-backend         # 停止后端
docker rm myblog-backend           # 删除后端容器

# === 测试API ===
curl http://localhost:8080/api/health                                    # 健康检查
curl http://localhost:8080/api/posts/public?pageNum=1&pageSize=5        # 文章列表
curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/system/audit-logs  # 审计日志

# === 数据库检查 ===
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog -c "\dt"  # 查看表
docker exec -it myblog-postgres psql -U Leoxiaoblog -d myblog -c "SELECT COUNT(*) FROM t_audit_log;"  # 统计日志数
```

---

## 📞 需要帮助？

如果遇到问题，请检查：

1. **CI/CD日志**：Gitea Actions页面查看详细错误
2. **容器日志**：`docker logs myblog-backend`
3. **数据库日志**：`docker logs myblog-postgres`
4. **本文档的"常见问题排查"章节**

---

**维护者**: Leo  
**最后更新**: 2026-05-14  
**版本**: v1.0.0
