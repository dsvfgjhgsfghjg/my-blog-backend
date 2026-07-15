# SSL 配置完成总结

## ✅ 已完成的配置

### 1. Dockerfile.frontend - 支持 SSL
- ✅ 启用 SSL 模块 (`mod_ssl`, `mod_socache_shmcb`)
- ✅ 创建 Let's Encrypt 证书目录
- ✅ 暴露 80 和 443 端口
- ✅ 使用 apache.conf 替代 httpd.conf

**文件**: [Dockerfile.frontend](file:///home/leo/IdeaProjects/myblog/Dockerfile.frontend)

---

### 2. Apache 配置 - apache.conf
- ✅ HTTP VirtualHost (80端口)
  - ACME 挑战路径支持
  - 自动重定向到 HTTPS
- ✅ HTTPS VirtualHost (443端口)
  - SSL 证书配置
  - 现代加密套件
  - HSTS 安全头
  - 反向代理到后端
  - SPA 路由支持

**文件**: [frontend/apache.conf](file:///home/leo/IdeaProjects/myblog/frontend/apache.conf)

---

### 3. Docker Compose 配置
- ✅ 前端容器映射 80 和 443 端口
- ✅ 挂载 Let's Encrypt 证书目录
  - `./certbot/conf:/etc/letsencrypt`
  - `./certbot/www:/var/www/certbot`
- ✅ 完整的后端服务配置

**文件**: [docker-compose.ssl.yml](file:///home/leo/IdeaProjects/myblog/docker-compose.ssl.yml)

---

### 4. 部署脚本
- ✅ 自动化上传配置文件
- ✅ 构建前端容器
- ✅ 启动服务
- ✅ 验证 HTTP 重定向

**文件**: [scripts/deploy/deploy-ssl.sh](file:///home/leo/IdeaProjects/myblog/scripts/deploy/deploy-ssl.sh)

---

### 5. 完整文档
- ✅ Let's Encrypt 证书申请指南
- ✅ 自动续期配置
- ✅ 常见问题排查
- ✅ 安全建议

**文件**: [docs/deployment/LETSENCRYPT_SSL_SETUP.md](file:///home/leo/IdeaProjects/myblog/docs/deployment/LETSENCRYPT_SSL_SETUP.md)

---

## 🚀 快速开始

### 方式 1: 使用部署脚本（推荐）

```bash
cd /home/leo/IdeaProjects/myblog
./scripts/deploy/deploy-ssl.sh
```

脚本会自动：
1. 上传配置文件到云端
2. 创建必要目录
3. 停止旧容器
4. 构建新前端容器
5. 启动所有服务
6. 验证 HTTP 访问

---

### 方式 2: 手动部署

```bash
# 1. 上传文件
scp docker-compose.ssl.yml ubuntu@113.31.105.30:~/myblog-deploy/
scp Dockerfile.frontend ubuntu@113.31.105.30:~/myblog-deploy/
scp frontend/apache.conf ubuntu@113.31.105.30:~/myblog-deploy/frontend/

# 2. SSH 连接
ssh ubuntu@113.31.105.30

# 3. 创建目录
cd ~/myblog-deploy
mkdir -p certbot/conf certbot/www frontend-logs logs

# 4. 停止旧容器
docker-compose down

# 5. 构建并启动
docker-compose -f docker-compose.ssl.yml build frontend
docker-compose -f docker-compose.ssl.yml up -d

# 6. 检查状态
docker ps | grep frontend
```

---

## 📝 下一步：申请 Let's Encrypt 证书

⚠️ **重要提醒**：
- Let's Encrypt 有速率限制：**每周最多 50 个证书**
- 建议先用 `--dry-run` 测试
- 需要有域名指向服务器 IP（或使用 IP 地址）

### 申请证书步骤

```bash
# SSH 连接到云端
ssh ubuntu@113.31.105.30

# 安装 Certbot
sudo apt update
sudo apt install certbot

# 测试模式（不消耗配额）
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com \
  --dry-run

# 正式申请
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com

# 复制证书到挂载目录
sudo cp /etc/letsencrypt/live/myblog.com/fullchain.pem \
  /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/
sudo cp /etc/letsencrypt/live/myblog.com/privkey.pem \
  /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/

# 重启前端容器
docker-compose -f docker-compose.ssl.yml restart frontend
```

详细步骤请参考：[LETSENCRYPT_SSL_SETUP.md](file:///home/leo/IdeaProjects/myblog/docs/deployment/LETSENCRYPT_SSL_SETUP.md)

---

## 🔍 验证配置

### 1. 检查端口映射

```bash
ssh ubuntu@113.31.105.30 "docker inspect myblog-frontend | jq '.[0].NetworkSettings.Ports'"
```

应该看到：
```json
{
  "80/tcp": [{"HostPort": "80"}],
  "443/tcp": [{"HostPort": "443"}]
}
```

---

### 2. 检查 SSL 模块

```bash
ssh ubuntu@113.31.105.30 "docker exec myblog-frontend httpd -M 2>/dev/null | grep ssl"
```

应该看到：
```
ssl_module (shared)
```

---

### 3. 测试 HTTP 重定向

```bash
curl -I http://113.31.105.30
```

应该返回：
```
HTTP/1.1 301 Moved Permanently
Location: https://113.31.105.30/
```

---

### 4. 测试 HTTPS（申请证书后）

```bash
curl -kI https://113.31.105.30
```

应该返回：
```
HTTP/1.1 200 OK
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

---

## 📊 配置对比

| 项目 | 修改前 | 修改后 |
|------|--------|--------|
| **端口** | 仅 80 | 80 + 443 |
| **SSL 模块** | ❌ 未启用 | ✅ 已启用 |
| **HTTPS 支持** | ❌ 不支持 | ✅ 支持 |
| **证书目录** | ❌ 无 | ✅ 已创建 |
| **HTTP→HTTPS** | ❌ 无 | ✅ 自动重定向 |
| **HSTS** | ❌ 无 | ✅ 已配置 |

---

## ⚠️ 注意事项

### 1. 速率限制
- Let's Encrypt 每周最多申请 **50 个证书**
- 同一域名每小时最多 **5 次失败尝试**
- 建议先用 `--staging` 或 `--dry-run` 测试

### 2. 证书有效期
- Let's Encrypt 证书有效期为 **90 天**
- 需要设置自动续期（见文档）
- 建议提前 30 天续期

### 3. 域名要求
- 如果使用域名，需要 DNS 解析到服务器 IP
- 如果使用 IP，需要手动验证所有权
- 建议使用域名（更专业）

### 4. 防火墙
确保云端服务器开放端口：
```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

---

## 🆘 故障排查

### 问题 1: 容器启动失败

```bash
# 查看日志
docker logs myblog-frontend

# 常见原因：
# - 证书文件不存在
# - 端口被占用
# - 配置文件语法错误
```

---

### 问题 2: HTTPS 无法访问

```bash
# 检查证书是否存在
ls -la /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/

# 检查证书权限
sudo chown -R 1000:1000 /home/ubuntu/myblog-deploy/certbot

# 重启容器
docker-compose -f docker-compose.ssl.yml restart frontend
```

---

### 问题 3: HTTP 不重定向

```bash
# 检查 Apache 配置
docker exec myblog-frontend cat /usr/local/apache2/conf/httpd.conf | grep -A 10 "VirtualHost.*80"

# 检查 Rewrite 模块
docker exec myblog-frontend httpd -M | grep rewrite
```

---

## 📚 相关文档

1. [Let's Encrypt SSL 配置指南](file:///home/leo/IdeaProjects/myblog/docs/deployment/LETSENCRYPT_SSL_SETUP.md)
2. [Docker Compose 配置](file:///home/leo/IdeaProjects/myblog/docker-compose.ssl.yml)
3. [Apache 配置文件](file:///home/leo/IdeaProjects/myblog/frontend/apache.conf)
4. [部署脚本](file:///home/leo/IdeaProjects/myblog/scripts/deploy/deploy-ssl.sh)

---

## ✅ 检查清单

部署前确认：

- [ ] 已上传所有配置文件到云端
- [ ] 已创建必要的目录结构
- [ ] 已停止旧容器
- [ ] 已重新构建前端容器
- [ ] 端口 80 和 443 已映射
- [ ] HTTP 重定向正常工作
- [ ] 已申请 Let's Encrypt 证书
- [ ] 证书已复制到正确位置
- [ ] HTTPS 访问正常
- [ ] 已配置自动续期

---

**配置完成时间**: 2026-05-17  
**配置人员**: Leo  
**下一步**: 手动申请 Let's Encrypt 证书
