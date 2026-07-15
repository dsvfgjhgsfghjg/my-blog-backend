# Git 忽略配置文件指南

## ✅ 当前配置状态

所有敏感配置文件已被正确配置为**不被 Git 跟踪**。

---

## 📋 已忽略的配置文件

### 1. config/ 目录（除示例外）

```gitignore
config/
!config/.env.example
```

**被忽略的文件**：
- ✅ `config/.env` - 环境变量（包含密码、密钥等）
- ✅ `config/config.yaml` - 应用配置
- ✅ `config/docker-compose*.yml` - Docker Compose 配置
- ✅ `config/secrets_config_*.txt` - 密钥配置文件

**保留跟踪的文件**：
- ✅ `config/.env.example` - 环境变量示例（作为模板）

### 2. 根目录配置文件

以下文件已从 Git 跟踪中移除：
- ✅ `.env` (软链接 → config/.env)
- ✅ `config.yaml` (软链接 → config/config.yaml)
- ✅ `docker-compose.yml` (软链接 → config/docker-compose.yml)
- ✅ `docker-compose.local.yml` (软链接 → config/docker-compose.local.yml)
- ✅ `docker-compose.prod.yml` (软链接 → config/docker-compose.prod.yml)
- ✅ `docker-compose.ssl.yml` (软链接 → config/docker-compose.ssl.yml)

### 3. 其他敏感文件

```gitignore
*.pem          # 私钥文件
*.key          # 密钥文件
*.secret       # 秘密文件
apifoxtoken.txt # API Token
```

---

## 🔍 验证配置

### 检查哪些文件被跟踪

```bash
# 查看所有被跟踪的配置文件
git ls-files | grep -E "config|\.env|docker-compose"

# 应该只看到：
# .env.example
```

### 检查未跟踪的文件

```bash
# 查看未跟踪的配置文件
git status --short | grep "??"

# 应该看到软链接文件显示为未跟踪
```

### 检查 Git 状态

```bash
git status
```

预期输出：
- 配置文件显示为 `??` (untracked)
- 不会出现在 "Changes to be committed" 中

---

## 🚫 如何防止误提交

### 方法 1: 使用 .gitignore（已配置）

`.gitignore` 已经正确配置，会自动忽略敏感文件。

### 方法 2: 提交前检查

```bash
# 提交前检查是否有敏感文件
git status

# 如果看到 config/.env 或其他敏感文件，不要提交
```

### 方法 3: 使用 pre-commit hook

创建 `.git/hooks/pre-commit` 脚本：

```bash
#!/bin/bash

# 检查是否尝试提交敏感文件
SENSITIVE_FILES=$(git diff --cached --name-only | grep -E "config/\.env$|secrets_config|\.pem$|\.key$")

if [ -n "$SENSITIVE_FILES" ]; then
    echo "❌ 错误: 检测到敏感文件被添加到提交中:"
    echo "$SENSITIVE_FILES"
    echo ""
    echo "请使用 'git reset HEAD <file>' 移除这些文件"
    exit 1
fi
```

---

## 📦 打包时的处理

### 自动排除

打包脚本 `scripts/package.sh` 会自动排除敏感配置：

```bash
./scripts/package.sh
```

生成的包不包含：
- ❌ `config/.env`
- ❌ `config/secrets_config_*.txt`
- ✅ `config/.env.example` (作为模板)

### 手动打包

如果使用 rsync：

```bash
rsync -av \
    --exclude='config/.env' \
    --exclude='config/secrets_config_*.txt' \
    --exclude='.git' \
    ./ /path/to/deploy/
```

---

## 🔄 部署流程

### 1. 上传部署包

```bash
scp myblog-20260517_120000.tar.gz user@server:/opt/
```

### 2. 解压

```bash
ssh user@server
cd /opt
tar -xzf myblog-20260517_120000.tar.gz
cd myblog-20260517_120000
```

### 3. 创建配置文件

```bash
# 复制示例文件
cp config/.env.example config/.env

# 编辑配置
vim config/.env
```

### 4. 启动服务

```bash
docker-compose -f config/docker-compose.prod.yml up -d
```

---

## ⚠️ 常见错误

### 错误 1: 误提交了 .env 文件

**症状**：
```bash
git add config/.env
git commit -m "Add config"
```

**修复**：
```bash
# 1. 从 Git 历史中移除
git rm --cached config/.env

# 2. 添加到 .gitignore（已完成）

# 3. 提交更改
git commit -m "Remove sensitive config from tracking"

# 4. 轮换密钥（重要！）
# 因为密钥可能已经泄露
```

### 错误 2: 软链接显示为修改

**症状**：
```bash
git status
# 显示: T config.yaml
```

**原因**：文件类型从普通文件变为软链接

**修复**：
```bash
# 从 Git 中移除
git rm --cached config.yaml

# 软链接会被 .gitignore 自动忽略
```

### 错误 3: .env.example 被忽略

**症状**：
```bash
git status
# .env.example 显示为 ??
```

**原因**：`.gitignore` 规则过于严格

**修复**：
确保 `.gitignore` 中有：
```gitignore
config/
!config/.env.example
```

注意顺序：先忽略 `config/`，然后例外 `!config/.env.example`

---

## 🔒 安全最佳实践

### 1. 永远不要提交真实配置

```bash
# ❌ 错误
git add config/.env
git commit -m "Add production config"

# ✅ 正确
git add config/.env.example
git commit -m "Add config template"
```

### 2. 使用不同的环境配置

```
config/
├── .env.dev      # 开发环境
├── .env.test     # 测试环境
├── .env.staging  # 预发布
└── .env.prod     # 生产环境
```

在 `.gitignore` 中添加：
```gitignore
config/.env*
!config/.env.example
```

### 3. 定期审计

```bash
# 检查是否有敏感文件被跟踪
git ls-files | grep -E "\.env$|secret|password|token|key"

# 应该返回空或只有示例文件
```

### 4. 使用 CI/CD Secrets

在 Gitea Actions 或其他 CI/CD 系统中：
- 使用 Secrets 存储敏感信息
- 不要在代码中硬编码
- 通过环境变量注入

---

## 📝 团队成员须知

### 首次克隆项目后

```bash
# 1. 复制配置模板
cp config/.env.example config/.env

# 2. 编辑配置
vim config/.env

# 3. 验证配置
docker-compose -f config/docker-compose.local.yml config
```

### 更新配置模板时

```bash
# 1. 更新示例文件
vim config/.env.example

# 2. 提交更改
git add config/.env.example
git commit -m "Update config template"
git push
```

### 收到配置更新后

```bash
# 1. 拉取最新代码
git pull

# 2. 检查是否有新的配置项
diff config/.env.example config/.env

# 3. 手动添加新配置项到本地 .env
vim config/.env
```

---

## 🆘 需要帮助？

### 检查配置是否正确

```bash
# 运行诊断脚本
./scripts/diagnose-git-config.sh
```

### 查看详细文档

- [配置管理指南](CONFIG_MANAGEMENT.md)
- [工作区整理说明](WORKSPACE_ORGANIZATION.md)
- [私钥清理报告](../security/PRIVATE_KEY_CLEANUP_REPORT.md)

---

**最后更新**: 2026-05-17  
**维护者**: MyBlog Team
