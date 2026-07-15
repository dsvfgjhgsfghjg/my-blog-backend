# Let's Encrypt SSL 证书配置指南

## 📋 前置条件

1. ✅ 前端容器已配置支持 SSL（443端口）
2. ✅ Apache 已启用 SSL 模块
3. ✅ 配置文件已准备好
4. ⚠️ **注意速率限制**：每周最多申请 50 个证书

---

## 🚀 部署步骤

### 步骤 1: 上传配置文件到云端

```bash
# 在本地执行
scp docker-compose.ssl.yml ubuntu@113.31.105.30:~/myblog-deploy/
scp Dockerfile.frontend ubuntu@113.31.105.30:~/myblog-deploy/
scp frontend/apache.conf ubuntu@113.31.105.30:~/myblog-deploy/frontend/

# 创建必要的目录
ssh ubuntu@113.31.105.30 "cd ~/myblog-deploy && mkdir -p certbot/conf certbot/www frontend-logs logs"
```

---

### 步骤 2: 构建并启动前端容器

```bash
# SSH 连接到云端
ssh ubuntu@113.31.105.30

# 进入部署目录
cd ~/myblog-deploy

# 停止旧容器
docker-compose down

# 重新构建前端容器
docker-compose -f docker-compose.ssl.yml build frontend

# 启动所有服务
docker-compose -f docker-compose.ssl.yml up -d

# 检查容器状态
docker ps | grep frontend
```

**预期输出**：
```
CONTAINER ID   IMAGE                  STATUS          PORTS
xxx            myblog-deploy-frontend Up 10 seconds   0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
```

---

### 步骤 3: 验证 HTTP 访问

```bash
# 测试 HTTP 是否正常
curl -I http://113.31.105.30

# 应该返回 301 重定向到 HTTPS
HTTP/1.1 301 Moved Permanently
Location: https://113.31.105.30/
```

---

### 步骤 4: 手动申请 Let's Encrypt 证书

⚠️ **重要提示**：
- Let's Encrypt 有速率限制：**每周最多 50 个证书**
- 建议使用 `--dry-run` 先测试
- 需要有域名指向服务器 IP

#### 方法 A: 使用 Certbot（推荐）

```bash
# 在云端服务器上安装 Certbot
sudo apt update
sudo apt install certbot

# 测试模式（不消耗配额）
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com \
  --dry-run

# 如果测试成功，正式申请
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com

# 或者使用 IP 地址（需要 DNS 验证）
sudo certbot certonly --manual \
  -d 113.31.105.30
```

#### 方法 B: 使用 Docker Certbot

```bash
# 运行 Certbot 容器
docker run -it --rm \
  -v /home/ubuntu/myblog-deploy/certbot/conf:/etc/letsencrypt \
  -v /home/ubuntu/myblog-deploy/certbot/www:/var/www/certbot \
  certbot/certbot certonly --webroot \
  --webroot-path /var/www/certbot \
  -d myblog.com \
  -d www.myblog.com
```

---

### 步骤 5: 复制证书到正确位置

```bash
# Certbot 会将证书保存在 /etc/letsencrypt/live/域名/
# 需要复制到挂载的目录

# 查看证书文件
ls -la /etc/letsencrypt/live/myblog.com/

# 应该看到：
# - fullchain.pem (证书链)
# - privkey.pem (私钥)
# - cert.pem (证书)
# - chain.pem (中间证书)

# 复制证书到挂载目录
sudo cp /etc/letsencrypt/live/myblog.com/fullchain.pem \
  /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/

sudo cp /etc/letsencrypt/live/myblog.com/privkey.pem \
  /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/

# 设置权限
sudo chown -R 1000:1000 /home/ubuntu/myblog-deploy/certbot
```

---

### 步骤 6: 重启前端容器

```bash
# 重启以加载新证书
docker-compose -f docker-compose.ssl.yml restart frontend

# 检查日志
docker logs myblog-frontend | tail -50

# 应该看到类似：
# [ssl:info] AH01964: Connection to child 0 established
# [ssl:info] AH02567: Server myblog.com:443:0 configured to listen on 0.0.0.0:443, IPv4
```

---

### 步骤 7: 验证 HTTPS 访问

```bash
# 测试 HTTPS
curl -kI https://113.31.105.30

# 应该返回 200 OK
HTTP/1.1 200 OK
Strict-Transport-Security: max-age=31536000; includeSubDomains

# 浏览器访问
# https://113.31.105.30
```

---

## 🔧 自动续期配置

Let's Encrypt 证书有效期为 **90 天**，需要定期续期。

### 方法 1: Cron 定时任务

```bash
# 编辑 crontab
crontab -e

# 添加每月检查续期的任务（每月 1 号凌晨 3 点）
0 3 1 * * /usr/bin/certbot renew --quiet --post-hook "docker restart myblog-frontend"
```

### 方法 2: Docker Compose 集成

创建 `certbot-renew.sh` 脚本：

```bash
#!/bin/bash
# 证书续期脚本

echo "开始检查证书续期..."

# 运行 Certbot 续期
docker run -it --rm \
  -v /home/ubuntu/myblog-deploy/certbot/conf:/etc/letsencrypt \
  -v /home/ubuntu/myblog-deploy/certbot/www:/var/www/certbot \
  certbot/certbot renew --quiet

# 如果续期成功，重启前端容器
if [ $? -eq 0 ]; then
  echo "证书续期成功，重启前端容器..."
  cd /home/ubuntu/myblog-deploy
  docker-compose -f docker-compose.ssl.yml restart frontend
  echo "完成！"
else
  echo "证书无需续期或续期失败"
fi
```

```bash
# 添加执行权限
chmod +x certbot-renew.sh

# 添加到 crontab（每 60 天检查一次）
0 3 */60 * * /home/ubuntu/myblog-deploy/certbot-renew.sh >> /var/log/certbot-renew.log 2>&1
```

---

## ⚠️ 常见问题

### Q1: 证书申请失败 - 连接超时

**原因**: 80 端口未开放或被防火墙阻止

**解决**:
```bash
# 检查防火墙
sudo ufw status
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 检查端口监听
netstat -tlnp | grep -E ':80|:443'
```

---

### Q2: 证书路径错误

**错误信息**:
```
SSLCertificateFile: file '/etc/letsencrypt/live/myblog.com/fullchain.pem' does not exist
```

**解决**:
```bash
# 检查证书是否存在
ls -la /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/

# 如果不存在，重新申请证书
# 确保目录结构正确
mkdir -p /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com
```

---

### Q3: 浏览器显示证书无效

**原因**: 
- 使用了自签名证书
- 证书域名与访问域名不匹配
- 证书已过期

**解决**:
```bash
# 检查证书详情
openssl x509 -in /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/fullchain.pem -text -noout

# 查看有效期
openssl x509 -enddate -noout -in /home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/fullchain.pem
```

---

### Q4: 速率限制 exceeded

**错误信息**:
```
Error creating new order :: too many certificates already issued for exact set of domains
```

**解决**:
- 等待一周后重试
- 使用 `--staging` 服务器测试（不消耗配额）
- 合并多个域名为一个证书

```bash
# 使用 Staging 环境测试
sudo certbot certonly --webroot \
  --webroot-path /home/ubuntu/myblog-deploy/certbot/www \
  -d myblog.com \
  -d www.myblog.com \
  --staging
```

---

## 📊 证书监控

### 检查证书到期时间

```bash
# 创建监控脚本
cat > check-cert-expiry.sh << 'EOF'
#!/bin/bash

CERT_FILE="/home/ubuntu/myblog-deploy/certbot/conf/live/myblog.com/fullchain.pem"

if [ -f "$CERT_FILE" ]; then
  EXPIRY_DATE=$(openssl x509 -enddate -noout -in "$CERT_FILE" | cut -d= -f2)
  EXPIRY_EPOCH=$(date -d "$EXPIRY_DATE" +%s)
  CURRENT_EPOCH=$(date +%s)
  DAYS_LEFT=$(( (EXPIRY_EPOCH - CURRENT_EPOCH) / 86400 ))
  
  echo "证书到期时间: $EXPIRY_DATE"
  echo "剩余天数: $DAYS_LEFT"
  
  if [ $DAYS_LEFT -lt 30 ]; then
    echo "⚠️ 警告: 证书将在 30 天内到期！"
    exit 1
  else
    echo "✅ 证书状态正常"
    exit 0
  fi
else
  echo "❌ 错误: 证书文件不存在"
  exit 2
fi
EOF

chmod +x check-cert-expiry.sh

# 运行检查
./check-cert-expiry.sh
```

---

## 🔒 安全建议

1. **定期更新证书** - 设置自动续期
2. **启用 HSTS** - 已在 apache.conf 中配置
3. **使用强加密套件** - 已禁用 TLS 1.0/1.1
4. **监控证书状态** - 设置到期提醒
5. **备份证书** - 定期备份 `/etc/letsencrypt` 目录

```bash
# 备份证书
tar czf letsencrypt-backup-$(date +%Y%m%d).tar.gz \
  /home/ubuntu/myblog-deploy/certbot/conf/
```

---

## 📞 需要帮助？

如果遇到问题，请提供：
1. `docker logs myblog-frontend` 的输出
2. Certbot 的错误信息
3. `openssl` 检查证书的结果

---

**最后更新**: 2026-05-17  
**参考文档**: 
- [Let's Encrypt 官方文档](https://letsencrypt.org/zh-cn/docs/)
- [速率限制说明](https://letsencrypt.org/zh-cn/docs/rate-limits/)
- [Certbot 用户指南](https://certbot.eff.org/docs/)
