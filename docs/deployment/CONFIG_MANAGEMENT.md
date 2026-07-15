# 配置文件管理指南

## 📁 目录结构

所有配置文件已集中存放在 `config/` 目录下，方便管理和打包时排除。

```
config/
├── .env                    # 环境变量配置（敏感信息，不提交到 Git）
├── .env.example            # 环境变量示例（可提交到 Git）
├── config.yaml             # 应用配置文件
├── docker-compose.yml      # Docker Compose 配置
├── docker-compose.local.yml    # 本地开发环境
├── docker-compose.prod.yml     # 生产环境
├── docker-compose.ssl.yml      # SSL/HTTPS 环境
└── secrets_config_*.txt        # 密钥配置文件（敏感，不提交）
```

## 🔗 软链接

项目根目录保留了配置文件的软链接，确保现有脚本和命令仍然可用：

```bash
.env -> config/.env
.env.example -> config/.env.example
config.yaml -> config/config.yaml
docker-compose.yml -> config/docker-compose.yml
docker-compose.local.yml -> config/docker-compose.local.yml
docker-compose.prod.yml -> config/docker-compose.prod.yml
docker-compose.ssl.yml -> config/docker-compose.ssl.yml
```

## 📦 打包排除规则

### `.gitignore` 配置

已在 `.gitignore` 中添加：

```gitignore
# Config directory (contains sensitive configs)
config/
!config/.env.example
```

这意味着：
- ✅ `config/.env.example` 会被提交到 Git
- ❌ `config/.env` 和其他配置文件不会被提交

### `.gitignore.package` 配置

专门用于打包的排除规则文件，包含：

```gitignore
# 敏感配置文件（保留示例文件）
config/.env
config/secrets_config_*.txt
!.env.example
!config/.env.example
```

## 🚀 使用方法

### 1. 首次设置

```bash
# 复制示例配置文件
cp config/.env.example config/.env

# 编辑配置文件
vim config/.env
```

### 2. 使用打包脚本

```bash
# 运行打包脚本
./scripts/package.sh

# 生成的包将自动排除敏感配置
# 例如: myblog-20260517_120000.tar.gz
```

### 3. 手动打包

```bash
# 使用 rsync 排除敏感文件
rsync -av \
    --exclude='config/.env' \
    --exclude='config/secrets_config_*.txt' \
    --exclude='.git' \
    --exclude='build' \
    ./ /path/to/deploy/
```

### 4. Docker Compose 使用

由于使用了软链接，原有的命令仍然有效：

```bash
# 本地开发
docker-compose -f docker-compose.local.yml up -d

# 生产环境
docker-compose -f docker-compose.prod.yml up -d

# SSL 环境
docker-compose -f docker-compose.ssl.yml up -d
```

## 🔒 安全建议

### 1. 不要提交敏感文件

```bash
# ❌ 错误：提交包含密码的文件
git add config/.env
git commit -m "Add config"

# ✅ 正确：只提交示例文件
git add config/.env.example
git commit -m "Add config example"
```

### 2. 检查 Git 状态

```bash
# 确认敏感文件未被跟踪
git status

# 应该看到 config/.env 在 untracked files 中
```

### 3. 使用不同的环境配置

为不同环境创建独立的配置文件：

```bash
config/
├── .env.dev          # 开发环境
├── .env.test         # 测试环境
├── .env.staging      # 预发布环境
└── .env.prod         # 生产环境
```

在部署时选择对应的文件：

```bash
# 开发环境
cp config/.env.dev config/.env

# 生产环境
cp config/.env.prod config/.env
```

### 4. 密钥管理

对于特别敏感的密钥（如 JWT_SECRET、数据库密码）：

**方案 A：使用环境变量**

```bash
# 在服务器上设置环境变量
export JWT_SECRET="your-secret-key"
export DB_PASSWORD="your-password"

# 在 .env 文件中引用
JWT_SECRET=${JWT_SECRET}
DB_PASSWORD=${DB_PASSWORD}
```

**方案 B：使用密钥管理服务**

- AWS Secrets Manager
- HashiCorp Vault
- Azure Key Vault

## 📝 配置文件清单

### 必须配置的文件

| 文件 | 说明 | 是否提交 |
|------|------|---------|
| `config/.env.example` | 环境变量示例 | ✅ 是 |
| `config/.env` | 实际环境变量 | ❌ 否 |
| `config/config.yaml` | 应用配置 | ⚠️ 视情况 |

### 可选配置文件

| 文件 | 说明 | 用途 |
|------|------|------|
| `docker-compose.local.yml` | 本地开发 | 开发环境 |
| `docker-compose.prod.yml` | 生产环境 | 生产部署 |
| `docker-compose.ssl.yml` | SSL 配置 | HTTPS 部署 |

## 🛠️ 常见问题

### Q1: 为什么使用软链接？

**A:** 软链接保持了向后兼容性，现有的脚本和命令无需修改即可继续使用。

### Q2: 如何验证软链接是否正确？

```bash
# 查看软链接
ls -la .env
ls -la docker-compose.yml

# 应该显示类似:
# .env -> config/.env
# docker-compose.yml -> config/docker-compose.yml
```

### Q3: 打包后如何部署？

```bash
# 1. 上传包到服务器
scp myblog-20260517_120000.tar.gz user@server:/opt/

# 2. 解压
cd /opt
tar -xzf myblog-20260517_120000.tar.gz

# 3. 配置环境变量
cd myblog-20260517_120000
cp config/.env.example config/.env
vim config/.env

# 4. 启动服务
docker-compose -f config/docker-compose.prod.yml up -d
```

### Q4: 如何在 CI/CD 中使用？

在 CI/CD 流水线中：

```yaml
# 示例：Gitea Actions
steps:
  - name: Checkout code
    uses: actions/checkout@v3
  
  - name: Setup config
    run: |
      cp config/.env.example config/.env
      echo "DB_PASSWORD=${{ secrets.DB_PASSWORD }}" >> config/.env
      echo "JWT_SECRET=${{ secrets.JWT_SECRET }}" >> config/.env
  
  - name: Build and deploy
    run: ./scripts/package.sh
```

## 📚 相关文档

- [部署指南](../docs/deployment/)
- [Docker 配置](../docs/deployment/DOCKER_DEPLOYMENT.md)
- [安全审计](../docs/security/SECURITY_AUDIT_REPORT_20260517.md)

---

**最后更新**: 2026-05-17  
**维护者**: MyBlog Team
