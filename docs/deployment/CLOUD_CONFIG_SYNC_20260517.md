# 云端配置同步记录

## 📅 同步时间
**2026-05-17 12:03**

---

## ✅ 已同步的配置文件

### 从云端下载的配置文件

| 文件 | 云端路径 | 本地路径（IdeaProjects） | 大小 | 状态 |
|------|---------|------------------------|------|------|
| Dockerfile.backend | `~/myblog-deploy/Dockerfile.backend` | `/home/leo/IdeaProjects/myblog/Dockerfile.backend` | 437B | ✅ 已同步 |
| Dockerfile.frontend | `~/myblog-deploy/Dockerfile.frontend` | `/home/leo/IdeaProjects/myblog/Dockerfile.frontend` → WebstormProjects | 1.8KB | ✅ 已同步+软链接 |
| docker-compose.yml | `~/myblog-deploy/docker-compose.yml` | `/home/leo/IdeaProjects/myblog/docker-compose.yml` | 1.3KB | ✅ 已同步 |
| docker-compose.prod.yml | `~/myblog-deploy/docker-compose.prod.yml` | `/home/leo/IdeaProjects/myblog/docker-compose.prod.yml` | 3.6KB | ✅ 已同步 |
| docker-compose.ssl.yml | `~/myblog-deploy/docker-compose.ssl.yml` | `/home/leo/IdeaProjects/myblog/docker-compose.ssl.yml` | 3.1KB | ✅ 已同步 |
| frontend/apache.conf | `~/myblog-deploy/frontend/apache.conf` | `/home/leo/IdeaProjects/myblog/frontend/apache.conf` → WebstormProjects | 5.6KB | ✅ 已同步+软链接 |

---

## 🔗 软链接配置

### 当前软链接状态

```bash
# IdeaProjects → WebstormProjects
Dockerfile.frontend -> /home/leo/WebstormProjects/myblog/Dockerfile.frontend
frontend/apache.conf -> /home/leo/WebstormProjects/myblog/frontend/apache.conf
```

**优势**：
- ✅ 单一数据源（WebstormProjects）
- ✅ 自动同步
- ✅ 避免配置割裂

---

## 📋 配置内容验证

### 1. Dockerfile.frontend - SSL 支持

```dockerfile
✅ 启用 SSL 模块 (mod_ssl, mod_socache_shmcb)
✅ 添加 Listen 443 指令
✅ 暴露 80 和 443 端口
✅ 包含自定义 VirtualHost 配置
✅ 安装 Certbot（Let's Encrypt）
```

### 2. apache.conf - HTTPS 配置

```apache
✅ HTTP VirtualHost (80端口) - 重定向到 HTTPS
✅ HTTPS VirtualHost (443端口) - SSL 配置
✅ ACME 挑战路径支持
✅ ServerName: myblog.com
✅ ServerAlias: www.myblog.com
✅ SSL 证书路径: /etc/letsencrypt/live/myblog.com/
```

### 3. docker-compose.yml - 服务配置

```yaml
✅ PostgreSQL 14-alpine
✅ Redis 7-alpine
✅ MinIO (elestio/minio:latest)
✅ 后端服务
✅ 前端服务（80 + 443 端口）
```

---

## 🔄 同步流程

### 从云端到本地

```bash
# 1. 下载配置文件
scp ubuntu@113.31.105.30:~/myblog-deploy/Dockerfile.* .
scp ubuntu@113.31.105.30:~/myblog-deploy/docker-compose*.yml .
scp ubuntu@113.31.105.30:~/myblog-deploy/frontend/apache.conf frontend/

# 2. 同步到 WebstormProjects
cp Dockerfile.frontend /home/leo/WebstormProjects/myblog/
cp frontend/apache.conf /home/leo/WebstormProjects/myblog/frontend/

# 3. 重新创建软链接
ln -sf /home/leo/WebstormProjects/myblog/Dockerfile.frontend .
ln -sf /home/leo/WebstormProjects/myblog/frontend/apache.conf frontend/
```

---

## ⚠️ 注意事项

### 1. 临时证书

当前云端使用**自签名证书**用于测试：
- 位置：`~/myblog-deploy/certbot/conf/live/myblog.com/`
- 有效期：7天
- **需要替换为 Let's Encrypt 正式证书**

### 2. MinIO 镜像修复

云端已修复 MinIO 镜像路径：
```yaml
# 修复前
image: 04eh77udwizz5vqdbk.xuanyuan.run/library/minio:latest

# 修复后
image: 04eh77udwizz5vqdbk.xuanyuan.run/elestio/minio:latest
```

### 3. 前端容器状态

```
容器名称: myblog-frontend
镜像: myblog-frontend:ssl
端口: 80, 443
状态: 运行中
SSL: 已启用（临时证书）
```

---

## 📝 下一步操作

### 1. 申请 Let's Encrypt 正式证书

参考文档：[LETSENCRYPT_SSL_SETUP.md](./LETSENCRYPT_SSL_SETUP.md)

```bash
ssh ubuntu@113.31.105.30
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com
```

### 2. 更新 Git 仓库

```bash
# WebstormProjects
cd /home/leo/WebstormProjects/myblog
git add Dockerfile.frontend frontend/apache.conf docker-compose*.yml
git commit -m "feat: 添加 SSL 支持和云端配置同步"
git push

# IdeaProjects（软链接不会被 Git 跟踪）
cd /home/leo/IdeaProjects/myblog
# 无需提交，配置文件通过软链接同步
```

### 3. 验证 HTTPS 访问

```bash
# 测试 HTTP 重定向
curl -I http://113.31.105.30

# 测试 HTTPS（临时证书）
curl -kI https://113.31.105.30
```

---

## 📊 配置对比

| 项目 | 修改前 | 修改后 |
|------|--------|--------|
| **前端端口** | 仅 80 | 80 + 443 |
| **SSL 支持** | ❌ | ✅ |
| **HTTP→HTTPS** | ❌ | ✅ 自动重定向 |
| **证书类型** | 无 | 临时自签名（待替换） |
| **配置同步** | 割裂 | 软链接统一 |

---

## 🔍 验证命令

### 检查云端配置

```bash
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && ls -la Dockerfile.* docker-compose*.yml frontend/*.conf"
```

### 检查本地配置

```bash
ls -la /home/leo/IdeaProjects/myblog/Dockerfile.* /home/leo/IdeaProjects/myblog/docker-compose*.yml
ls -la /home/leo/IdeaProjects/myblog/frontend/apache.conf
```

### 验证软链接

```bash
readlink /home/leo/IdeaProjects/myblog/Dockerfile.frontend
readlink /home/leo/IdeaProjects/myblog/frontend/apache.conf
```

---

**同步完成时间**: 2026-05-17 12:03  
**同步人员**: Leo  
**下次同步**: 当云端配置变更时
