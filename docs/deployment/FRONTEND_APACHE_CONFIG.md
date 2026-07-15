# 前端 Apache 配置指南

## 📋 概述

本文档提供前端项目的 Apache HTTP Server 完整配置方案，包括：
- 静态文件服务
- 反向代理到后端API
- 端口配置（8081）
- HTTPS支持（可选）
- 性能优化

---

## 🚀 快速开始

### 1. 基础配置

**配置文件位置**：`/etc/httpd/conf.d/frontend.conf` (CentOS/RHEL) 或 `/etc/apache2/sites-available/frontend.conf` (Ubuntu/Debian)

```apache
# ============================================
# 前端应用配置 - MyBlog Frontend
# ============================================

# 监听8081端口
Listen 8081

# 虚拟主机配置
<VirtualHost *:8081>
    # 服务器管理员邮箱
    ServerAdmin admin@myblog.com
    
    # 网站根目录（前端构建后的dist目录）
    DocumentRoot "/var/www/myblog-frontend"
    
    # 服务器名称
    ServerName localhost
    ServerAlias 113.31.105.30
    
    # ============================================
    # 目录配置
    # ============================================
    <Directory "/var/www/myblog-frontend">
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
        
        # SPA路由支持 - 所有请求重定向到index.html
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>
    
    # ============================================
    # 日志配置
    # ============================================
    ErrorLog "/var/log/httpd/frontend-error.log"
    CustomLog "/var/log/httpd/frontend-access.log" combined
    
    # ============================================
    # 反向代理配置 - 转发API请求到后端
    # ============================================
    
    # 启用代理模块
    ProxyPreserveHost On
    
    # API请求转发到后端（运行在8080端口）
    ProxyPass /api http://localhost:8080/api
    ProxyPassReverse /api http://localhost:8080/api
    
    # WebSocket支持（如果需要）
    ProxyPass /ws ws://localhost:8080/ws
    ProxyPassReverse /ws ws://localhost:8080/ws
    
    # ============================================
    # 静态资源缓存优化
    # ============================================
    
    # 启用压缩
    <IfModule mod_deflate.c>
        AddOutputFilterByType DEFLATE text/html
        AddOutputFilterByType DEFLATE text/css
        AddOutputFilterByType DEFLATE application/javascript
        AddOutputFilterByType DEFLATE application/json
        AddOutputFilterByType DEFLATE image/svg+xml
        AddOutputFilterByType DEFLATE application/xml
    </IfModule>
    
    # 浏览器缓存策略
    <IfModule mod_expires.c>
        ExpiresActive On
        
        # HTML文件不缓存
        ExpiresByType text/html "access plus 0 seconds"
        
        # CSS和JS文件缓存1周
        ExpiresByType text/css "access plus 1 week"
        ExpiresByType application/javascript "access plus 1 week"
        
        # 图片缓存1个月
        ExpiresByType image/png "access plus 1 month"
        ExpiresByType image/jpeg "access plus 1 month"
        ExpiresByType image/gif "access plus 1 month"
        ExpiresByType image/svg+xml "access plus 1 month"
        
        # 字体文件缓存1个月
        ExpiresByType font/woff2 "access plus 1 month"
        ExpiresByType font/woff "access plus 1 month"
    </IfModule>
    
    # ============================================
    # 安全头配置
    # ============================================
    
    # 防止点击劫持
    Header always set X-Frame-Options "SAMEORIGIN"
    
    # XSS保护
    Header always set X-XSS-Protection "1; mode=block"
    
    # 内容类型嗅探保护
    Header always set X-Content-Type-Options "nosniff"
    
    # Referrer策略
    Header always set Referrer-Policy "strict-origin-when-cross-origin"
</VirtualHost>
```

---

## 🔧 安装和启用模块

### CentOS/RHEL

```bash
# 安装Apache
sudo yum install httpd -y

# 启用必要模块
sudo yum install mod_ssl mod_proxy mod_proxy_http mod_rewrite -y

# 启动Apache
sudo systemctl enable httpd
sudo systemctl start httpd
```

### Ubuntu/Debian

```bash
# 安装Apache
sudo apt update
sudo apt install apache2 -y

# 启用必要模块
sudo a2enmod proxy
sudo a2enmod proxy_http
sudo a2enmod rewrite
sudo a2enmod headers
sudo a2enmod expires
sudo a2enmod deflate
sudo a2enmod ssl

# 重启Apache
sudo systemctl restart apache2
```

---

## 📁 部署前端文件

### 1. 创建目录并设置权限

```bash
# 创建网站根目录
sudo mkdir -p /var/www/myblog-frontend

# 复制前端构建文件到该目录
# 假设前端项目在 /path/to/frontend/dist
sudo cp -r /path/to/frontend/dist/* /var/www/myblog-frontend/

# 设置正确的所有权和权限
sudo chown -R apache:apache /var/www/myblog-frontend  # CentOS
# 或
sudo chown -R www-data:www-data /var/www/myblog-frontend  # Ubuntu

sudo chmod -R 755 /var/www/myblog-frontend
```

### 2. 创建日志目录

```bash
sudo mkdir -p /var/log/httpd
sudo chown apache:apache /var/log/httpd  # CentOS
# 或
sudo chown www-data:www-data /var/log/httpd  # Ubuntu
```

---

## 🔐 HTTPS配置（推荐生产环境使用）

### 使用Let's Encrypt免费证书

```bash
# 安装Certbot
sudo yum install certbot python3-certbot-apache -y  # CentOS
# 或
sudo apt install certbot python3-certbot-apache -y  # Ubuntu

# 获取证书
sudo certbot --apache -d your-domain.com

# 自动续期
sudo crontab -e
# 添加以下行（每天凌晨2点检查续期）
0 2 * * * certbot renew --quiet
```

### 手动配置HTTPS

```apache
<VirtualHost *:443>
    ServerName your-domain.com
    
    # SSL配置
    SSLEngine on
    SSLCertificateFile /etc/letsencrypt/live/your-domain.com/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/your-domain.com/privkey.pem
    
    # 其他配置同HTTP版本...
    DocumentRoot "/var/www/myblog-frontend"
    
    # 强制HTTPS
    Header always set Strict-Transport-Security "max-age=31536000; includeSubDomains"
    
    # 反向代理
    ProxyPass /api https://localhost:8080/api
    ProxyPassReverse /api https://localhost:8080/api
</VirtualHost>

# HTTP自动跳转到HTTPS
<VirtualHost *:80>
    ServerName your-domain.com
    Redirect permanent / https://your-domain.com/
</VirtualHost>
```

---

## 🎯 Docker环境配置

如果使用Docker部署，创建 `httpd.conf`：

```apache
# Docker中的Apache配置
Listen 80

<VirtualHost *:80>
    DocumentRoot "/usr/local/apache2/htdocs"
    
    # SPA路由支持
    <Directory "/usr/local/apache2/htdocs">
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
        
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>
    
    # 反向代理到后端容器
    ProxyPreserveHost On
    ProxyPass /api http://myblog-backend:8080/api
    ProxyPassReverse /api http://myblog-backend:8080/api
    
    # 日志
    ErrorLog "/proc/self/fd/2"
    CustomLog "/proc/self/fd/1" combined
</VirtualHost>
```

**Dockerfile.frontend**:

```dockerfile
FROM httpd:2.4-alpine

# 复制前端文件
COPY dist/ /usr/local/apache2/htdocs/

# 复制自定义配置
COPY httpd.conf /usr/local/apache2/conf/httpd.conf

# 启用必要模块
RUN sed -i \
    -e 's/#LoadModule proxy_module/LoadModule proxy_module/' \
    -e 's/#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
    -e 's/#LoadModule rewrite_module/LoadModule rewrite_module/' \
    /usr/local/apache2/conf/httpd.conf

EXPOSE 80

CMD ["httpd-foreground"]
```

**docker-compose.yml** 片段：

```yaml
services:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.frontend
    container_name: myblog-frontend
    ports:
      - "8081:80"
    networks:
      - myblog-network
    depends_on:
      - backend
    restart: unless-stopped

networks:
  myblog-network:
    driver: bridge
```

---

## 🔍 故障排查

### 1. 检查Apache状态

```bash
# 查看服务状态
sudo systemctl status httpd  # CentOS
sudo systemctl status apache2  # Ubuntu

# 查看错误日志
sudo tail -f /var/log/httpd/frontend-error.log
```

### 2. 测试配置语法

```bash
sudo apachectl configtest
# 或
sudo httpd -t
```

### 3. 检查端口占用

```bash
# 查看8081端口是否被占用
sudo ss -tlnp | grep 8081
# 或
sudo netstat -tlnp | grep 8081
```

### 4. 测试反向代理

```bash
# 测试API代理是否正常
curl http://localhost:8081/api/health

# 应该返回后端的响应
```

### 5. 常见问题

**问题1：403 Forbidden**
```bash
# 检查文件权限
ls -la /var/www/myblog-frontend/

# 修复权限
sudo chown -R apache:apache /var/www/myblog-frontend
sudo chmod -R 755 /var/www/myblog-frontend
```

**问题2：SPA路由刷新404**
- 确保 `.htaccess` 文件或 `<Directory>` 配置中有Rewrite规则
- 检查 `mod_rewrite` 模块是否启用

**问题3：反向代理失败**
```bash
# 检查后端是否运行
curl http://localhost:8080/api/health

# 检查代理模块是否加载
apachectl -M | grep proxy
```

**问题4：CORS错误**
在后端Spring Boot配置中添加：

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:8081", "http://113.31.105.30:8081")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

---

## 📊 性能优化建议

### 1. 启用Gzip压缩

已在配置中包含，确保 `mod_deflate` 已启用。

### 2. 浏览器缓存

已配置静态资源缓存策略，可根据需要调整缓存时间。

### 3. 连接保持

```apache
# 在 VirtualHost 外添加
KeepAlive On
MaxKeepAliveRequests 100
KeepAliveTimeout 5
```

### 4. Worker MPM优化（高并发场景）

```apache
<IfModule mpm_worker_module>
    StartServers             3
    MinSpareThreads         75
    MaxSpareThreads        250
    ThreadsPerChild         25
    MaxRequestWorkers      400
    MaxConnectionsPerChild   0
</IfModule>
```

---

## 🔄 更新流程

### 前端更新步骤

```bash
# 1. 构建前端
cd /path/to/frontend
npm run build

# 2. 备份旧版本
sudo mv /var/www/myblog-frontend /var/www/myblog-frontend.backup

# 3. 部署新版本
sudo cp -r dist/* /var/www/myblog-frontend/

# 4. 设置权限
sudo chown -R apache:apache /var/www/myblog-frontend
sudo chmod -R 755 /var/www/myblog-frontend

# 5. 重启Apache（可选，通常不需要）
sudo systemctl reload httpd

# 6. 验证
curl http://localhost:8081/

# 7. 如果没问题，删除备份
sudo rm -rf /var/www/myblog-frontend.backup
```

---

## 📝 配置清单

部署前检查：

- [ ] Apache已安装并运行
- [ ] 必要模块已启用（proxy, proxy_http, rewrite, headers, expires, deflate）
- [ ] 前端文件已复制到 `/var/www/myblog-frontend`
- [ ] 文件权限正确设置
- [ ] 8081端口防火墙已开放
- [ ] 后端服务运行在8080端口
- [ ] 反向代理配置正确
- [ ] 日志目录存在且可写
- [ ] 配置语法测试通过（`apachectl configtest`）
- [ ] 可以访问 `http://服务器IP:8081`

---

## 🆘 技术支持

如遇到问题，请检查：
1. Apache错误日志：`/var/log/httpd/frontend-error.log`
2. 系统日志：`journalctl -u httpd -f`
3. 网络连接：`curl -v http://localhost:8081/`

---

**最后更新**：2026-05-14  
**适用版本**：Apache 2.4+  
**维护者**：MyBlog Team
