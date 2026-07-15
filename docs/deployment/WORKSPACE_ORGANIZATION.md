# 工作区整理说明

## 📋 整理概述

本次整理将配置文件集中到 `config/` 目录，并优化了打包排除规则，使部署包更干净、更安全。

## 🎯 整理目标

1. ✅ **配置文件集中管理** - 所有配置文件统一存放在 `config/` 目录
2. ✅ **保持向后兼容** - 通过软链接确保现有脚本和命令继续可用
3. ✅ **简化打包流程** - 提供专用打包脚本，自动排除敏感文件
4. ✅ **增强安全性** - 敏感配置不会被提交到 Git 或包含在部署包中

## 📁 新的目录结构

```
myblog/
├── config/                    # 🆕 配置文件目录
│   ├── .env                   # 环境变量（敏感，不提交）
│   ├── .env.example           # 环境变量示例（可提交）
│   ├── config.yaml            # 应用配置
│   ├── docker-compose.yml     # Docker Compose 主配置
│   ├── docker-compose.local.yml    # 本地开发环境
│   ├── docker-compose.prod.yml     # 生产环境
│   ├── docker-compose.ssl.yml      # SSL/HTTPS 环境
│   └── secrets_config_*.txt        # 密钥文件（敏感，不提交）
│
├── src/                       # 源代码
├── scripts/                   # 脚本文件
│   └── package.sh             # 🆕 打包脚本
├── docs/                      # 文档
│   └── deployment/
│       └── CONFIG_MANAGEMENT.md  # 🆕 配置管理指南
│
├── .gitignore                 # Git 忽略规则（已更新）
├── .gitignore.package         # 🆕 打包排除规则
├── build.gradle               # Gradle 构建配置
└── ...                        # 其他项目文件
```

## 🔗 软链接映射

为了保持兼容性，项目根目录保留了配置文件的软链接：

| 软链接 | 指向 | 用途 |
|--------|------|------|
| `.env` | `config/.env` | 环境变量 |
| `.env.example` | `config/.env.example` | 环境变量示例 |
| `config.yaml` | `config/config.yaml` | 应用配置 |
| `docker-compose.yml` | `config/docker-compose.yml` | Docker Compose |
| `docker-compose.local.yml` | `config/docker-compose.local.yml` | 本地环境 |
| `docker-compose.prod.yml` | `config/docker-compose.prod.yml` | 生产环境 |
| `docker-compose.ssl.yml` | `config/docker-compose.ssl.yml` | SSL 环境 |

## 🚀 快速开始

### 1. 验证软链接

```bash
# 检查软链接是否正确
ls -la .env
ls -la docker-compose.yml

# 应该显示:
# .env -> config/.env
# docker-compose.yml -> config/docker-compose.yml
```

### 2. 配置环境变量

```bash
# 如果是首次使用，复制示例文件
cp config/.env.example config/.env

# 编辑配置
vim config/.env
```

### 3. 启动服务

由于有软链接，原有命令仍然有效：

```bash
# 本地开发
docker-compose -f docker-compose.local.yml up -d

# 生产环境
docker-compose -f docker-compose.prod.yml up -d
```

## 📦 打包部署

### 方法一：使用打包脚本（推荐）

```bash
# 运行打包脚本
./scripts/package.sh

# 生成的包会自动排除:
# - config/.env (敏感配置)
# - config/secrets_config_*.txt (密钥文件)
# - build/ (构建产物)
# - .git/ (版本控制)
# - docs/ (文档)
# - 等其他开发文件
```

### 方法二：手动打包

```bash
# 使用 rsync 排除指定文件
rsync -av \
    --exclude='config/.env' \
    --exclude='config/secrets_config_*.txt' \
    --exclude='.git' \
    --exclude='build' \
    --exclude='docs' \
    ./ /path/to/deploy/
```

### 部署步骤

```bash
# 1. 上传包到服务器
scp myblog-20260517_120000.tar.gz user@server:/opt/

# 2. 解压
cd /opt
tar -xzf myblog-20260517_120000.tar.gz
cd myblog-20260517_120000

# 3. 配置环境变量
cp config/.env.example config/.env
vim config/.env

# 4. 启动服务
docker-compose -f config/docker-compose.prod.yml up -d
```

## 🔒 安全改进

### Git 提交规则

```gitignore
# .gitignore 中已添加
config/
!config/.env.example
```

这意味着：
- ✅ `config/.env.example` 会被提交（作为模板）
- ❌ `config/.env` 不会被提交（包含密码等敏感信息）
- ❌ `config/secrets_config_*.txt` 不会被提交

### 打包排除规则

`.gitignore.package` 定义了打包时排除的文件：

```gitignore
# 敏感配置
config/.env
config/secrets_config_*.txt

# 开发文件
.git/
.idea/
build/
docs/
scripts/

# 日志和缓存
*.log
cache/
logs/
```

## 📝 修改清单

### 新增文件

1. `config/` - 配置文件目录
2. `scripts/package.sh` - 打包脚本
3. `.gitignore.package` - 打包排除规则
4. `docs/deployment/CONFIG_MANAGEMENT.md` - 配置管理文档
5. `docs/deployment/WORKSPACE_ORGANIZATION.md` - 本文件

### 修改文件

1. `.gitignore` - 添加 config/ 目录排除规则
2. 所有 docker-compose 文件 - 移动到 config/ 目录

### 创建软链接

- `.env` → `config/.env`
- `.env.example` → `config/.env.example`
- `config.yaml` → `config/config.yaml`
- `docker-compose*.yml` → `config/docker-compose*.yml`

## ❓ 常见问题

### Q1: 为什么要移动配置文件？

**A:** 
- 集中管理，便于维护
- 打包时可以轻松排除敏感文件
- 减少误提交敏感配置的风险

### Q2: 软链接会影响性能吗？

**A:** 
不会。软链接的性能开销可以忽略不计，而且保持了向后兼容性。

### Q3: 现有的 CI/CD 需要修改吗？

**A:** 
不需要。由于使用了软链接，原有的命令和脚本无需修改即可继续使用。

### Q4: 如何验证配置是否正确？

```bash
# 1. 检查软链接
ls -la .env docker-compose.yml

# 2. 测试 Docker Compose
docker-compose -f docker-compose.local.yml config

# 3. 检查 Git 状态
git status
# config/.env 应该显示为 untracked
```

### Q5: 如果不想用软链接怎么办？

**A:** 
可以直接使用 config/ 目录下的文件：

```bash
# 直接使用 config/ 中的文件
docker-compose -f config/docker-compose.prod.yml up -d

# 或者设置环境变量
export COMPOSE_FILE=config/docker-compose.prod.yml
docker-compose up -d
```

## 📚 相关文档

- [配置管理指南](CONFIG_MANAGEMENT.md)
- [Docker 部署指南](DOCKER_DEPLOYMENT.md)
- [安全审计报告](../security/SECURITY_AUDIT_REPORT_20260517.md)

## 🎯 下一步

1. ✅ 配置文件已整理完成
2. ⏳ 测试打包脚本
3. ⏳ 更新 CI/CD 配置（如需要）
4. ⏳ 团队培训新的工作流程

---

**整理时间**: 2026-05-17  
**整理人员**: AI Assistant  
**审核状态**: ⏳ 待审核
