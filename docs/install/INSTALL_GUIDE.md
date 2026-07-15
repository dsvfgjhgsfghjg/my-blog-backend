# MyBlog 安装指南

## 概述

MyBlog 提供两种部署方式：

1. **Docker Compose 编排部署（推荐）** — 所有服务运行在 Docker 容器中，自动管理 PostgreSQL + Redis + MinIO + 后端 + 前端
2. **宿主机直接部署** — 仅数据库/缓存/存储使用 Docker，后端/前端直接在宿主机运行

安装脚本提供交互式 CLI 向导，类似 WordPress 的安装体验。

---

## 系统要求

### Docker Compose 模式

| 组件 | 要求 |
|------|------|
| Docker | 20.10+ |
| Docker Compose | v2.0+ |
| 内存 | 至少 2GB 可用内存 |
| 磁盘 | 至少 10GB 可用空间 |

### 宿主机模式

| 组件 | 要求 |
|------|------|
| Docker | 20.10+ |
| Java | JDK 21 (Temurin 推荐) |
| Node.js | 18+ |
| npm | 9+ |
| 内存 | 至少 1GB 可用内存 |
| 磁盘 | 至少 5GB 可用空间 |

---

## 快速开始

### 1. 下载 Release 包

从 Releases 页面下载最新版本的 `myblog-{version}.tar.gz`，解压后进入目录：

```bash
tar -xzf myblog-{version}.tar.gz
cd myblog-{version}
```

### 2. 运行安装脚本

```bash
bash scripts/install.sh
```

### 3. 按照向导提示完成安装

安装过程包含以下步骤：

| 步骤 | 说明 |
|------|------|
| 1. 选择部署方式 | Docker Compose 或 宿主机直接部署 |
| 2. 环境检测 | 自动检测 Docker、Java、Node.js 等依赖 |
| 3. 镜像源配置 | 可选配置轩辕镜像加速地址 |
| 4. 数据库配置 | PostgreSQL 连接信息 + 连接测试 |
| 5. Redis 配置 | Redis 连接信息 |
| 6. MinIO 配置 | 对象存储连接信息 + 连接测试 |
| 7. JWT 配置 | JWT 密钥（可自动生成）和过期时间 |
| 8. 管理员账号 | 创建系统管理员（密码强度校验） |
| 9. 站点信息 | 站点名称、描述、域名、HTTPS |
| 10. 配置确认 | 确认所有配置，开始安装 |

---

## 详细配置说明

### 数据库配置

需要提前创建好 PostgreSQL 数据库，安装脚本会测试连接。

```sql
-- 创建数据库（示例）
CREATE DATABASE myblog;
```

### MinIO 配置

需要提前准备好 MinIO 服务，安装脚本会测试连接。

- **端点**: MinIO 服务地址，例如 `http://192.168.1.100:9000`
- **Access Key / Secret Key**: MinIO 访问凭证
- **Bucket**: 存储桶名称，脚本会自动创建

### JWT 配置

- **JWT Secret**: 用于签发用户登录令牌，留空自动生成 64 位随机密钥
- **Token 过期时间**: 默认 24 小时

### 管理员账号

密码要求：
- 至少 8 位
- 包含小写字母
- 包含大写字母
- 包含数字
- 包含特殊字符

---

## 安装后操作

### 1. 配置 SSL 证书（如需 HTTPS）

```bash
# 使用 Let's Encrypt
certbot --nginx -d your-domain.com
```

详细参考: [Let's Encrypt SSL 配置](../deployment/LETSENCRYPT_SSL_SETUP.md)

### 2. 导入验证码背景图

```bash
bash scripts/upload-captcha-to-minio.sh
```

### 3. 查看运行状态

**Docker 模式:**
```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

**宿主机模式:**
```bash
cat logs/backend.log
```

### 4. 停止服务

**Docker 模式:**
```bash
docker compose -f docker-compose.prod.yml down
```

**宿主机模式:**
```bash
kill $(cat logs/backend.pid)
```

---

## 重新安装

如果系统已安装（检测到 `.myblog_installed` 文件），运行安装脚本时会提示确认：

```bash
bash scripts/install.sh
```

输入 `YES` 确认覆盖安装，或按其他键取消。

> **注意**: 重新安装会覆盖现有配置和数据，请提前备份。

---

## 常见问题

### Q: 安装脚本提示 Docker 未安装？

请先安装 Docker：

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | bash

# 安装 Docker Compose Plugin
apt install docker-compose-plugin
```

### Q: 数据库连接测试失败？

检查以下可能的问题：
1. 数据库主机地址是否正确
2. 端口是否开放（防火墙）
3. 用户名和密码是否正确
4. 数据库是否已创建

### Q: 如何生成 BCrypt 密码哈希？

安装脚本会自动处理，优先顺序：
1. Python3 `bcrypt` 库
2. Apache `htpasswd` 工具
3. 自动安装 `pip3 install bcrypt`

### Q: 安装后如何修改配置？

配置文件位于项目根目录的 `.env` 文件，修改后需要重启服务。

---

## 目录结构

```
myblog/
├── scripts/
│   └── install.sh          # 一键安装脚本
├── docs/
│   └── install/
│       └── INSTALL_GUIDE.md # 本安装文档
├── docker-compose.prod.yml  # Docker Compose 生产配置
├── docker-compose.yml       # Docker Compose 开发配置
├── .env                     # 环境变量（安装时生成）
├── .myblog_installed        # 安装锁文件
├── logs/                    # 日志目录
└── dist/                    # 前端构建产物
```

---

## 参考

- [Docker 安装指南](https://docs.docker.com/engine/install/)
- [Java 21 下载](https://adoptium.net/temurin/releases/)
- [Node.js 下载](https://nodejs.org/)
- [Let's Encrypt SSL 配置](../deployment/LETSENCRYPT_SSL_SETUP.md)
