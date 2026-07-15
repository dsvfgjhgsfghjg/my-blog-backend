# .gitignore 配置说明

## 概述

本文档说明了项目中 `.gitignore` 文件的配置规则和最佳实践。

## 已忽略的文件和目录

### 1. 构建和输出目录

- `build/` - Gradle构建输出
- `dist/` - 分发文件
- `out/` - IDE编译输出
- `bin/` - 二进制文件

**原因**: 这些是编译生成的文件，可以从源代码重新生成。

### 2. IDE配置文件

#### IntelliJ IDEA
- `.idea/` - IDE配置目录
- `*.iml` - 模块文件
- `*.iws` - 工作区文件
- `*.ipr` - 项目文件
- `.run/` - 运行配置

#### Eclipse/STS
- `.classpath`
- `.project`
- `.settings/`
- `.apt_generated`

#### VS Code
- `.vscode/`

**原因**: IDE配置是个人化的，不应该共享。每个开发者应该有自己的配置。

### 3. 环境和敏感信息

- `.env` - 环境变量文件（可能包含密码、密钥）
- `.env.local` - 本地环境配置
- `.env.*.local` - 特定环境的本地配置
- `apifoxtoken.txt` - API Token
- `*.pem` - 私钥文件
- `*.key` - 密钥文件
- `*.secret` - 机密文件

**原因**: ⚠️ **安全关键** - 这些文件包含敏感信息，绝对不能提交到版本控制。

### 4. Docker和数据目录

- `db-data/` - PostgreSQL数据
- `redis-data/` - Redis数据
- `minio-data/` - MinIO对象存储数据
- `gitea/` - Gitea数据
- `gitea-postgres/` - Gitea数据库
- `*.tar`, `*.tar.gz` - 打包文件

**原因**: 这些是运行时数据，体积大且可以重新生成。

### 5. Gradle

- `.gradle/` - Gradle缓存和配置
- 保留: `gradle/wrapper/gradle-wrapper.jar` (需要提交)

**原因**: Gradle缓存可以在首次构建时自动下载。

### 6. 临时文件

- `*.log` - 日志文件
- `*.tmp`, `*.temp` - 临时文件
- `*.swp`, `*.swo` - Vim交换文件
- `*~` - 备份文件
- `.DS_Store` - macOS文件夹配置
- `Thumbs.db` - Windows缩略图缓存

**原因**: 这些是编辑器或系统生成的临时文件。

### 7. 开发工具配置

- `.sdkmanrc` - SDKMAN配置
- `.gitignore.docker` - Docker专用的gitignore
- `config.md` - 配置文件说明
- `HELP.md` - Spring Boot生成的帮助文档

**原因**: 这些是开发工具的个人配置或自动生成的文档。

### 8. 重复的文档

以下文件在 `docs/` 目录下已有组织好的版本，根目录的应被忽略：

- `API_USER.md`
- `QUICK_START.md`
- `TODO.md`
- `pgsql.sql`
- `疑惑.md`

**原因**: 避免文档重复，统一在 `docs/` 目录下管理。

### 9. 部署包

- `deploy-package/` - 部署打包文件
- `frontend/` - 前端构建文件

**原因**: 这些是构建产物，应该通过CI/CD生成。

### 10. Docker Compose变体

- `docker-compose.yml.old` - 旧版本
- `docker-compose.new.yml` - 新版本

**原因**: 保持仓库整洁，只保留当前使用的配置文件。

### 11. Gitea CI/CD

- `.gitea/` - Gitea Actions数据

**原因**: 如果使用外部Gitea服务器，这些数据不应该提交。

## 应该提交的文件

### ✅ 必须提交

1. **源代码**
   - `src/main/java/**`
   - `src/main/resources/**`
   - `src/test/**`

2. **构建配置**
   - `build.gradle`
   - `settings.gradle`
   - `gradle/wrapper/gradle-wrapper.jar`
   - `gradlew`, `gradlew.bat`

3. **Docker配置**
   - `Dockerfile*`
   - `docker-compose.yml` (主配置文件)
   - `docker-compose.prod.yml` (生产环境)
   - `docker-compose.local.yml` (本地开发)

4. **文档**
   - `docs/**` (组织好的文档)
   - `README.md`
   - `PROJECT_ORGANIZATION.md`

5. **脚本**
   - `scripts/**`

6. **数据库迁移**
   - `sql/**`
   - `src/main/resources/db/migration/**`

7. **配置文件模板**
   - `.env.example` (如果有)
   - 不包含实际密钥的配置示例

### ❌ 不应提交

1. **敏感信息**
   - `.env` (使用 `.env.example` 代替)
   - 任何包含密码、密钥、Token的文件

2. **IDE配置**
   - `.idea/`
   - `*.iml`
   - `.vscode/`

3. **构建产物**
   - `build/`
   - `dist/`
   - `*.jar` (除了gradle-wrapper.jar)

4. **运行时数据**
   - `db-data/`
   - `redis-data/`
   - `minio-data/`

5. **日志和临时文件**
   - `*.log`
   - `*.tmp`

## 最佳实践

### 1. 环境变量管理

```bash
# 创建 .env.example 作为模板
cp .env .env.example

# 编辑 .env.example，移除所有真实值
# 例如：
# DB_PASSWORD=your_password_here  ->  DB_PASSWORD=

# 确保 .env 在 .gitignore 中
echo ".env" >> .gitignore

# 提交 .env.example
git add .env.example
git commit -m "Add environment configuration template"
```

### 2. 定期清理

```bash
# 检查是否有应该被忽略但已跟踪的文件
git ls-files | grep -E "(\.env|\.idea|build/)"

# 如果发现有误提交的文件，从Git中移除（但保留本地文件）
git rm -rf --cached .idea/
git rm --cached .env
```

### 3. 团队协作

- 在项目README中说明需要配置的 `.env` 文件
- 提供 `.env.example` 作为配置模板
- 使用CI/CD环境变量而不是提交 `.env` 文件

### 4. 安全检查

```bash
# 提交前检查是否有敏感信息
git diff --cached --name-only | xargs grep -l "password\|secret\|token" 2>/dev/null

# 使用工具扫描
# 安装 git-secrets 或 detect-secrets
```

## 常见问题

### Q1: 为什么 `.env` 不能提交？

**A**: `.env` 文件通常包含数据库密码、API密钥等敏感信息。如果提交到Git，这些信息会：
- 暴露给所有有仓库访问权限的人
- 永久保存在Git历史中（即使后来删除）
- 可能被恶意利用

**解决方案**: 
- 使用 `.env.example` 作为模板
- 在CI/CD中使用环境变量
- 使用密钥管理服务（如HashiCorp Vault）

### Q2: IDE配置真的不能共享吗？

**A**: 部分配置可以共享，但应该谨慎：
- ✅ 可以共享: 代码风格配置、检查规则
- ❌ 不应共享: 个人路径、个人偏好、本地运行配置

**建议**: 使用 `.editorconfig` 共享基本的代码风格设置。

### Q3: 如何忽略已经被跟踪的文件？

**A**: 
```bash
# 从Git跟踪中移除（保留本地文件）
git rm --cached <file>

# 添加到 .gitignore
echo "<file>" >> .gitignore

# 提交更改
git commit -m "Stop tracking <file>"
```

### Q4: Gradle wrapper jar为什么要提交？

**A**: `gradle-wrapper.jar` 是Gradle Wrapper的一部分，它确保所有开发者使用相同版本的Gradle，无需预先安装。这是Gradle官方推荐的做法。

## 更新历史

- **2026-05-13**: 初始版本，整理项目gitignore配置
- 添加了分类注释，提高可读性
- 明确了安全和非安全文件的界限

---

**维护者**: 项目开发团队  
**最后更新**: 2026-05-13
