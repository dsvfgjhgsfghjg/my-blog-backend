# 数据库迁移在 CI/CD 流程中的集成方案

> **创建日期**: 2026-06-09
> **状态**: ✅ 方案已确定并实施

---

## 📋 现状分析

### 当前 CI/CD 工作流

```
代码推送 → Build (Gradle) → Test (PostgreSQL) → Deploy (Docker 镜像) → 部署到服务器
                                                                              ↓
                                                                        启动新容器
                                                                        (旧容器直接停止)
```

### 当前数据库管理存在的问题

| 问题 | 描述 | 风险等级 |
|------|------|---------|
| ❌ 无迁移框架 | `src/main/resources/db/migration/` 下有 Flyway 风格的 SQL 文件，但**未集成 Flyway/Liquibase**，SQL 不会自动执行 | ⭐⭐⭐⭐⭐ |
| ❌ 版本冲突 | 存在同版本号重复文件：V9、V10、V11、V12、V13、V15 各有两条记录 | ⭐⭐⭐⭐ |
| ❌ CI/CD 无迁移步骤 | 部署流程中没有任何数据库迁移环节 | ⭐⭐⭐⭐⭐ |
| ❌ 不支持回滚 | 数据库变更无法撤销 | ⭐⭐⭐ |
| ❌ 手动运维 | 表结构变更需要 SSH 登录手动执行 SQL | ⭐⭐⭐⭐ |

---

## 🎯 目标

在 CI/CD 流程中自动处理数据库迁移，确保：

1. **代码推送时自动执行数据库迁移**（新增表、修改字段等）
2. **迁移版本可追溯**，避免重复执行
3. **支持回滚**（降级部署时）
4. **生产环境与 CI 测试环境保持同步**

---

## 🏗️ 实施架构

### 方案选择：Flyway ✅

项目已使用 Flyway 命名规范（`V{version}__{description}.sql`），选择 Flyway 是自然延续。

```
┌──────────────────────────────────────────────────────────────────┐
│                    Gitea Actions CI/CD Pipeline                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Build → Test → [新增] 执行 Migration → Deploy → Health Check    │
│                    ↓                                              │
│           ┌──────────────────┐                                    │
│           │   Flyway CLI     │                                    │
│           │   (Docker 容器)  │                                    │
│           └──────────────────┘                                    │
│                    ↓                                              │
│           ┌──────────────────┐                                    │
│           │   PostgreSQL     │                                    │
│           │   (生产数据库)   │                                    │
│           └──────────────────┘                                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 📝 实施步骤

### 步骤 1：修复现有迁移脚本的版本冲突

当前存在以下冲突文件，需要重命名：

| 原文件 | 新文件 |
|--------|--------|
| ~~V9__add_phone_and_email_to_user.sql~~ | V9__add_phone_and_email_to_user.sql ✅ (保留) |
| ~~V9__create_audit_log_table.sql~~ | V9_1__create_audit_log_table.sql |
| ~~V10__add_super_admin_and_ability_constraints.sql~~ | V10__add_super_admin_and_ability_constraints.sql ✅ (保留) |
| ~~V10__create_comment_table.sql~~ | V10_1__create_comment_table.sql |
| ~~V11__create_comment_like_table.sql~~ | V11__create_comment_like_table.sql ✅ (保留) |
| ~~V11__init_super_admin_and_abilities.sql~~ | V11_1__init_super_admin_and_abilities.sql |
| ~~V12__create_post_favorite_table.sql~~ | V12__add_post_favorite_table.sql ✅ (保留) |
| ~~V12__create_system_config_table.sql~~ | V12_1__create_system_config_table.sql |
| ~~V13__add_post_audit_system.sql~~ | V13__add_post_audit_system.sql ✅ (保留) |
| ~~V13__create_user_preferences_table.sql~~ | V13_1__create_user_preferences_table.sql |
| ~~V15__add_post_audit_consistency_trigger.sql~~ | V15__add_post_audit_consistency_trigger.sql ✅ (保留) |
| ~~V15__create_particle_effect_table.sql~~ | V15_1__create_particle_effect_table.sql |

> **注意**：如果这些 SQL 已在生产环境手动执行过，需要手动清理 `flyway_schema_history` 表。

### 步骤 2：在 build.gradle 中添加 Flyway 依赖

```gradle
// Flyway 数据库迁移
implementation 'org.flywaydb:flyway-core'
runtimeOnly 'org.flywaydb:flyway-database-postgresql'
```

### 步骤 3：配置 application.yml

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    # 如果迁移脚本已手动执行过，需要 baseline 或手动清理
    # out-of-order: true  # 允许乱序执行
```

### 步骤 4：更新 CI/CD 工作流

#### 4.1 测试阶段 - 自动执行迁移

测试环境的 PostgreSQL 容器启动后，测试前 Flyway 会自动执行迁移。

#### 4.2 部署阶段 - 新增迁移步骤

在部署流水线中，**在启动新容器之前**，先执行数据库迁移：

```yaml
- name: Run database migrations
  run: |
    # 使用 Flyway 命令行工具执行迁移
    docker run --rm \
      --network myblog-deploy_myblog-network \
      -v $(pwd)/src/main/resources/db/migration:/flyway/sql:ro \
      flyway/flyway:10-alpine \
      -url=jdbc:postgresql://myblog-postgres:5432/${{ secrets.DB_NAME }} \
      -user=${{ secrets.DB_USER }} \
      -password=${{ secrets.DB_PASSWORD }} \
      -locations=filesystem:/flyway/sql \
      migrate
```

### 步骤 5：迁移验证步骤

迁移后验证数据库是否就绪：

```yaml
- name: Verify migration status
  run: |
    docker run --rm \
      --network myblog-deploy_myblog-network \
      flyway/flyway:10-alpine \
      -url=jdbc:postgresql://myblog-postgres:5432/${{ secrets.DB_NAME }} \
      -user=${{ secrets.DB_USER }} \
      -password=${{ secrets.DB_PASSWORD }} \
      info
```

---

## 🔄 完整的新工作流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Gitea Actions Pipeline                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  Step 1: Build                                                                │
│  ├── Checkout code                                                           │
│  ├── Setup JDK 21                                                           │
│  ├── Build with Gradle                                                      │
│  └── Upload artifact                                                        │
│                                                                               │
│  Step 2: Test                                                                │
│  ├── Start PostgreSQL (test) + Redis                                        │
│  ├── Download artifact                                                      │
│  ├── [新增] Flyway 自动执行迁移 (测试数据库)                                │
│  ├── Run tests                                                              │
│  └── Stop services                                                          │
│                                                                               │
│  Step 3: Build Docker Image                                                  │
│  ├── Download artifact                                                      │
│  ├── Build Docker image                                                     │
│  ├── Save Docker image                                                      │
│  └── Upload Docker image artifact                                           │
│                                                                               │
│  Step 4: [新增] 数据库迁移                                                    │
│  ├── 使用 Flyway Docker CLI                                                 │
│  ├── 连接生产数据库                                                         │
│  ├── 执行所有待处理的迁移                                                   │
│  └── 验证迁移状态                                                          │
│                                                                               │
│  Step 5: 部署应用                                                            │
│  ├── 停止旧容器                                                            │
│  ├── 启动新容器 (含 Flyway 自动迁移)                                        │
│  └── 健康检查                                                              │
│                                                                               │
│  Step 6: [新增] 迁移后验证                                                    │
│  ├── 检查应用日志                                                          │
│  └── 验证数据库连接正常                                                    │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔧 实施内容清单

### 已完成的修改

- [x] **docs/deployment/DATABASE_MIGRATION_IN_CICD.md** - 本方案文档
- [x] **build.gradle** - 添加 Flyway 依赖
- [x] **application.yml** - 添加 Flyway 配置
- [x] **`.gitea/workflows/backend-ci-cd.yml`** - 添加数据库迁移步骤
- [x] **`.gitea/workflows/deploy-dev.yml`** - 添加开发环境迁移步骤
- [x] **Dockerfile.backend** - 在 ENTRYPOINT 前集成 Flyway
- [x] **sql/init_oauth2.sql** → 迁移至 `V19__init_oauth2_tables.sql`
- [x] **`sql/` 目录说明文档** - 区分手动脚本与自动迁移

### 数据库更新开发流程（新增数据/表的标准流程）

当需要新增数据库变更时：

```
1. 在 src/main/resources/db/migration/ 下创建新版本迁移文件
   ├── 命名规范: V{版本号}__{描述}.sql
   ├── 版本号: 当前最大版本 + 1（当前为 V18，下一个为 V19）
   └── 内容: 只包含 DDL/DML 变更（CREATE TABLE、ALTER TABLE、INSERT 等）

2. 提交代码并推送到 Git
   └── CI/CD 自动执行迁移

3. 验证迁移结果
   └── 检查 flyway_schema_history 表
```

---

## ⚠️ 迁移注意事项

### 1. 生产环境首次部署 Flyway

生产环境已有数据，首次迁移需注意：

```sql
-- 如果已手动执行过某些迁移，需要标记为已执行
INSERT INTO flyway_schema_history 
  (installed_rank, version, description, type, script, checksum, installed_by, 
   installed_on, execution_time, success)
VALUES
  (1, '1', 'add enabled to user', 'SQL', 'V1__add_enabled_to_user.sql', 
   <checksum>, 'postgres', NOW(), 0, true);
```

或者使用 `baseline-on-migrate: true` 配合 `baseline-version` 配置。

### 2. 迁移脚本编写规范

```sql
-- ✅ 好的迁移
CREATE TABLE IF NOT EXISTS t_new_table (...);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS new_column VARCHAR(255);

-- ❌ 避免以下写法
DROP TABLE t_existing_table;  -- 数据丢失
ALTER TABLE t_user DROP COLUMN important_column;  -- 破坏性变更
INSERT INTO t_user VALUES (...);  -- 插入数据考虑用条件判断
```

### 3. 回滚策略

Flyway 不支持自动回滚，需要手动编写回滚脚本：

```sql
-- V19__add_new_feature.sql (迁移)
ALTER TABLE t_user ADD COLUMN new_feature VARCHAR(255);

-- V19__add_new_feature.sql.rollback (回滚)
ALTER TABLE t_user DROP COLUMN new_feature;
```

回滚命令：
```bash
docker run --rm flyway/flyway:10-alpine \
  -url=jdbc:postgresql://host:5432/db \
  -user=user -password=pass \
  undo
```

---

## 📊 数据库更新流程概览

```
开发者需要新增数据库变更（表、字段、数据）
                    │
                    ▼
┌──────────────────────────────────────┐
│ 1. 创建迁移文件                       │
│    src/main/resources/db/migration/   │
│    V19__add_new_table.sql             │
│    (遵循命名规范，保证版本号递增)      │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ 2. 本地测试验证                       │
│    ./gradlew flywayMigrate            │
│    或启动应用自动执行                  │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ 3. 推送代码到 Git                     │
│    git add .                          │
│    git commit -m "feat: add xxx"      │
│    git push origin main               │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ 4. CI/CD 自动执行                     │
│    ├── Build (编译通过)               │
│    ├── Test (测试数据库自动迁移)      │
│    ├── [新增] 生产数据库迁移           │
│    ├── Deploy (启动新容器)            │
│    └── Health Check (验证成功)        │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ 5. 验证部署结果                       │
│    检查 flyway_schema_history 表      │
│    确认迁移记录已写入                 │
└──────────────────────────────────────┘
```

---

## 🔧 开发环境新增迁移的快速参考

```bash
# 1. 创建新的迁移文件
touch src/main/resources/db/migration/V$(ls src/main/resources/db/migration/ | grep -oP 'V\K[0-9]+' | sort -n | tail -1 + 1)__your_description.sql

# 2. 编写 SQL 变更内容
# 3. 本地启动应用测试迁移
./gradlew bootRun

# 4. 查看迁移状态
# 连接数据库后查询：
SELECT version, description, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
```

---

## 📚 相关文档

- [CICD_DEPLOYMENT_GUIDE.md](./CICD_DEPLOYMENT_GUIDE.md) - CI/CD 完整指南
- [BACKEND_DEVOPS_SUMMARY.md](./BACKEND_DEVOPS_SUMMARY.md) - DevOps 配置总结
- [Flyway 官方文档](https://documentation.redmine.org/flyway/)
- [Spring Boot Flyway 集成](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool)
