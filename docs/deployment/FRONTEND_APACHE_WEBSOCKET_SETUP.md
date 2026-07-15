# 前端 Apache 配置 WebSocket 代理步骤

## 背景

后端新增了 App 双因素认证功能，需要通过 WebSocket 与 App 通信。前端 Apache 需要配置 WebSocket 代理，将 `/ws/app/auth` 路径的请求转发到后端容器。

## 需要修改的文件

### 1. `frontend/apache.conf`

在 HTTPS VirtualHost（端口 443）的 `ProxyPass` 配置区域中，**已经添加了**以下 WebSocket 代理配置：

```apache
# ============================================
# WebSocket 代理 - App 双因素认证
# ============================================
# App 认证 WebSocket 端点（wss://leo-blog.top/ws/app/auth）
# 需要 mod_proxy_wstunnel 模块支持
ProxyPass /ws/app/auth ws://myblog-backend:8080/ws/app/auth
ProxyPassReverse /ws/app/auth ws://myblog-backend:8080/ws/app/auth

# WebSocket 心跳超时设置（App 可能长时间保持连接）
ProxyTimeout 300
Timeout 300
```

> **注意**：`myblog-backend` 是 Docker 内部网络中的后端容器名，前端容器通过 Docker 内部网络直接访问。

### 2. `Dockerfile.frontend`

**已经添加了** `mod_proxy_wstunnel` 模块的启用：

```dockerfile
# 启用 mod_proxy_wstunnel 模块（WebSocket 代理支持）
sed -i '/LoadModule proxy_wstunnel_module/s/^#//g' /usr/local/apache2/conf/httpd.conf
```

## 操作步骤

### 步骤 1：同步代码

确保前端仓库拉取最新的 `frontend/apache.conf` 和 `Dockerfile.frontend` 文件：

```bash
# 如果前端仓库有后端仓库的 submodule 或文件同步机制
git pull origin main

# 或者手动复制这两个文件到前端仓库对应位置
```

### 步骤 2：重新构建前端 Docker 镜像

```bash
# 在前端仓库目录下执行
docker build -t myblog-frontend:latest -f Dockerfile.frontend .
```

### 步骤 3：重新部署前端容器

```bash
# 停止旧容器
docker stop myblog-frontend || true
docker rm myblog-frontend || true

# 启动新容器（注意网络要跟后端在同一个网络）
docker run -d \
  --name myblog-frontend \
  --network myblog-deploy_myblog-network \
  -p 80:80 \
  -p 443:443 \
  --restart unless-stopped \
  myblog-frontend:latest
```

### 步骤 4：验证 WebSocket 代理是否生效

```bash
# 检查 Apache 是否加载了 wstunnel 模块
docker exec myblog-frontend httpd -M | grep proxy_wstunnel

# 应该输出类似：
# proxy_wstunnel_module (shared)
```

### 步骤 5：测试 WebSocket 连接

可以使用 `websocat` 或 `wscat` 工具测试：

```bash
# 安装测试工具
apt-get install -y websocat  # 或 npm install -g wscat

# 测试 WebSocket 连接（注意使用 wss://）
wscat -c wss://leo-blog.top/ws/app/auth
```

如果连接成功，会看到 WebSocket 连接建立，然后可以发送 `bind` 消息。

## 常见问题

### Q: 为什么不用 `wss://` 而是用 `ws://` 在 ProxyPass 中？

因为 Apache 到后端容器之间的通信是在 Docker 内部网络中，不需要加密。外部客户端通过 HTTPS（wss://）连接到 Apache，Apache 解密后通过内部网络用 ws:// 转发到后端。

### Q: 如何确认 WebSocket 代理配置正确？

检查 Apache 错误日志：
```bash
docker exec myblog-frontend tail -f /proc/self/fd/2
```

如果看到类似以下错误，说明 `mod_proxy_wstunnel` 没有启用：
```
AH01144: No protocol handler was valid for the URL /ws/app/auth (scheme 'ws')
```

### Q: 前端 CI/CD 需要做什么修改？

如果前端有独立的 CI/CD 流程（如 `.gitea/workflows/frontend-ci-cd.yml`），需要确保：
1. 构建镜像时使用最新的 `Dockerfile.frontend`
2. 部署时容器加入正确的 Docker 网络（`myblog-deploy_myblog-network`）
3. 容器能通过内部 DNS 解析到 `myblog-backend`

## 参考

- [Apache mod_proxy_wstunnel 文档](https://httpd.apache.org/docs/2.4/mod/mod_proxy_wstunnel.html)
- [后端 App 双因素认证 Android 开发指南](../api/APP_AUTH_ANDROID_GUIDE.md)
