# 数据库字段缺失修复指南

## 问题描述

启动应用时出现以下错误：

```
ERROR: column "is_super_admin" does not exist
```

**原因**: `t_user` 表中缺少 `is_super_admin` 字段，但代码中的 `User` 实体类已经包含该字段。

---

## 解决方案

### 方法1: 使用 pgAdmin 或 DBeaver（推荐）

1. 打开数据库管理工具（pgAdmin、DBeaver等）
2. 连接到 PostgreSQL 数据库
3. 执行以下SQL：

```sql
-- 添加 is_super_admin 字段
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN t_user.is_super_admin IS '是否为超级管理员（可管理能力分配）';

-- （可选）将 admin 用户设置为超级管理员
UPDATE t_user SET is_super_admin = TRUE WHERE username = 'admin';
```

4. 重启应用

---

### 方法2: 使用 Docker 中的 psql

如果你的 PostgreSQL 运行在 Docker 中：

```bash
# 进入 PostgreSQL 容器
docker exec -it <postgres_container_name> psql -U Leoxiaoblog -d myblog

# 执行SQL
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN t_user.is_super_admin IS '是否为超级管理员（可管理能力分配）';

# 退出
\q
```

---

### 方法3: 使用项目中的 SQL 脚本

我已经创建了修复脚本：`sql/fix_missing_is_super_admin.sql`

**如果你有 psql 客户端**：

```bash
psql -h localhost -U Leoxiaoblog -d myblog -f sql/fix_missing_is_super_admin.sql
```

---

### 方法4: 安装 psql 客户端后执行

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql-client

# 然后执行
psql -h localhost -U Leoxiaoblog -d myblog -f sql/fix_missing_is_super_admin.sql
```

---

## 验证修复

执行以下SQL验证字段已添加：

```sql
SELECT column_name, data_type, column_default 
FROM information_schema.columns 
WHERE table_name = 't_user' AND column_name = 'is_super_admin';
```

**预期输出**：

```
   column_name    | data_type | column_default 
------------------+-----------+----------------
 is_super_admin   | boolean   | false
```

---

## 相关迁移脚本

项目中已有完整的迁移脚本：

- **V10__add_super_admin_and_ability_constraints.sql**: 添加 `is_super_admin` 字段和约束
- **V11__init_super_admin_and_abilities.sql**: 初始化超级管理员和能力
- **V12__create_system_config_table.sql**: 创建系统配置表

建议后续配置 Flyway 来自动管理数据库迁移。

---

## 配置 Flyway（可选，推荐）

### 1. 添加依赖到 `build.gradle`

```groovy
dependencies {
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
}
```

### 2. 在 `application.yml` 中配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

### 3. 重启应用

Flyway 会自动执行所有未执行的迁移脚本。

---

## 常见问题

### Q1: 为什么会出现这个问题？

**A**: 因为代码中添加了新字段，但数据库没有同步更新。通常应该通过迁移脚本（如Flyway）来管理数据库schema变更。

### Q2: 会影响现有数据吗？

**A**: 不会。`ADD COLUMN IF NOT EXISTS` 是安全的，如果字段已存在则跳过。默认值为 `FALSE`，不会影响现有用户。

### Q3: 需要重启应用吗？

**A**: 是的。添加字段后需要重启应用让MyBatis-Plus重新加载表结构。

### Q4: 其他字段也会缺失吗？

**A**: 检查以下字段是否也存在：
- `last_login_ip`
- `last_login_time`
- `login_count`

如果缺失，执行：

```sql
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS last_login_ip VARCHAR(50);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS last_login_time TIMESTAMP;
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS login_count INT DEFAULT 0;
```

---

## 下一步

1. ✅ 执行上述SQL添加字段
2. ✅ 重启应用
3. ✅ 测试登录功能
4. 🔮 考虑配置 Flyway 自动管理数据库迁移

---

**创建日期**: 2026-05-13  
**状态**: ⚠️ 需要手动执行SQL
