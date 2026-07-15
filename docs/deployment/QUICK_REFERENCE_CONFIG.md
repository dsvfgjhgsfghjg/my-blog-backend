# 配置文件管理 - 快速参考

## 📁 配置文件位置

所有配置文件都在 `config/` 目录：

```bash
config/
├── .env                    # 环境变量（编辑这个）
├── .env.example            # 示例模板
├── config.yaml             # 应用配置
└── docker-compose*.yml     # Docker 配置
```

## 🔧 常用命令

### 配置环境变量

```bash
# 首次使用，复制示例
cp config/.env.example config/.env

# 编辑配置
vim config/.env
```

### 启动服务

```bash
# 本地开发
docker-compose -f docker-compose.local.yml up -d

# 生产环境  
docker-compose -f docker-compose.prod.yml up -d

# SSL/HTTPS
docker-compose -f docker-compose.ssl.yml up -d
```

### 查看日志

```bash
docker-compose logs -f
docker-compose logs backend
docker-compose logs frontend
```

### 停止服务

```bash
docker-compose down
docker-compose down -v  # 同时删除数据卷
```

## 📦 打包部署

### 一键打包

```bash
./scripts/package.sh
```

生成的包：`myblog-YYYYMMDD_HHMMSS.tar.gz`

### 部署步骤

```bash
# 1. 上传
scp myblog-*.tar.gz user@server:/opt/

# 2. 解压
ssh user@server
cd /opt
tar -xzf myblog-*.tar.gz
cd myblog-*

# 3. 配置
cp config/.env.example config/.env
vim config/.env

# 4. 启动
docker-compose -f config/docker-compose.prod.yml up -d
```

## 🔒 安全提醒

### ✅ 要做

- [ ] 使用强密码
- [ ] 定期更换密钥
- [ ] 不提交 `.env` 到 Git
- [ ] 备份配置文件

### ❌ 不要做

- [ ] 不要提交 `config/.env`
- [ ] 不要分享密钥文件
- [ ] 不要在公共仓库存储密码
- [ ] 不要使用默认密码

## 🆘 常见问题

### Q: 软链接失效怎么办？

```bash
# 重新创建软链接
ln -sf config/.env .env
ln -sf config/docker-compose.yml docker-compose.yml
```

### Q: 如何验证配置？

```bash
# 检查 Docker Compose 配置
docker-compose -f config/docker-compose.prod.yml config

# 检查环境变量
cat config/.env | grep -v "^#" | grep -v "^$"
```

### Q: 打包时排除了哪些文件？

```bash
# 查看排除规则
cat .gitignore.package
```

主要排除：
- `config/.env` (敏感配置)
- `build/` (构建产物)
- `.git/` (版本控制)
- `docs/` (文档)

## 📞 需要帮助？

查看详细文档：
- [配置管理指南](CONFIG_MANAGEMENT.md)
- [工作区整理说明](WORKSPACE_ORGANIZATION.md)
- [完整部署指南](DOCKER_DEPLOYMENT.md)

---

**最后更新**: 2026-05-17  
**打印此页作为快速参考**
