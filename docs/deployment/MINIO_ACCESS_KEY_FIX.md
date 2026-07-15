# MinIO Access Key 大小写问题修复

## 🔍 问题描述

MinIO 的 Access Key **区分大小写**，云端和本地使用了不同的大小写：

- **本地开发**: `Leoxiao`（大写 L）
- **云端生产**: `leoxiao`（小写 l）❌ 之前配置错误

这导致后端无法连接到云端的 MinIO 服务。

---

## ✅ 已修复的配置

### 1. `.env` 文件（云端部署）

**修改前**:
```bash
MINIO_ROOT_USER=Leoxiao  # ❌ 错误：大写 L
```

**修改后**:
```bash
MINIO_ROOT_USER=leoxiao  # ✅ 正确：小写 l
```

**文件位置**: `/home/leo/IdeaProjects/myblog/.env`

---

### 2. `application.yml` 文件（本地开发）

**当前配置**（保持不变）:
```yaml
minio:
  access-key: Leoxiao  # 本地使用大写 L
```

**说明**: 本地 MinIO 容器使用大写 `Leoxiao`，所以本地配置不需要修改。

---

## ⚠️ 需要手动修复的配置

### Gitea Secrets 中的 MINIO_ROOT_USER

**访问地址**: 
```
http://113.31.105.30:3000/leoxiao/backend/settings/secrets/actions
```

**需要修改的 Secret**:
- **Secret 名称**: `MINIO_ROOT_USER`
- **当前值**: `Leoxiao` ❌
- **修改为**: `leoxiao` ✅

**操作步骤**:
1. 登录 Gitea
2. 进入仓库设置 → Secrets → Actions
3. 找到 `MINIO_ROOT_USER`
4. 点击编辑，将值从 `Leoxiao` 改为 `leoxiao`
5. 保存

---

## 🔄 验证步骤

### 1. 检查 MinIO 容器配置

```bash
# SSH 登录到云端服务器
ssh leo@113.31.105.30

# 查看 MinIO 容器的环境变量
docker exec myblog-minio env | grep MINIO_ROOT_USER

# 应该输出：MINIO_ROOT_USER=leoxiao
```

### 2. 检查后端容器日志

```bash
# 查看后端启动日志
docker logs myblog-backend --tail 100 | grep -i minio

# 应该没有认证错误
```

### 3. 测试 MinIO 连接

```bash
# 在后端容器中测试
docker exec myblog-backend curl http://myblog-minio:9000/minio/health/live

# 应该返回：OK
```

### 4. 重新触发部署

修改 Gitea Secrets 后，推送任意代码触发新的部署：

```bash
cd /home/leo/IdeaProjects/myblog
git commit --allow-empty -m "chore: trigger redeploy with fixed MinIO credentials"
git push origin master
```

---

## 📝 相关文档

- [MinIO 配置大小写敏感说明](../security/MINIO_CAPTCHA_SECURITY.md)
- [验证码图片上传到云端](CAPTCHA_IMAGES_UPLOAD_TO_CLOUD.md)
- [部署策略说明](DEPLOYMENT_STRATEGY.md)

---

## 💡 经验总结

### MinIO Access Key 大小写规则

| 环境 | Access Key | 说明 |
|------|-----------|------|
| 本地开发 | `Leoxiao` | 大写 L |
| 云端生产 | `leoxiao` | 小写 l |

**重要提醒**:
1. MinIO 的 Access Key **严格区分大小写**
2. 本地和云端使用了不同的 Access Key
3. 必须确保以下三处配置一致：
   - MinIO 容器的 `MINIO_ROOT_USER` 环境变量
   - `.env` 文件中的 `MINIO_ROOT_USER`
   - Gitea Secrets 中的 `MINIO_ROOT_USER`

---

**修复日期**: 2026-05-17  
**修复人员**: Leo
