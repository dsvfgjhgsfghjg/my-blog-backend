# DevOps 工作流完整配置总结

## ✅ 已完成配置

### 1. Docker 环境
- ✅ PostgreSQL 18 数据库
- ✅ Redis 7 缓存
- ✅ MinIO 对象存储
- ✅ Gitea 代码托管平台
- ✅ 所有服务在 `myblog_myblog-network` 网络中

### 2. CI/CD 工作流文件
- ✅ `.gitea/workflows/ci-cd.yml` - 生产环境部署
- ✅ `.gitea/workflows/deploy-dev.yml` - 开发环境部署

### 3. 配置文档
- ✅ `GITEA_CICD_GUIDE.md` - 详细配置指南
- ✅ `CICD_QUICKSTART.md` - 快速开始指南
- ✅ `DOCKER_ENVIRONMENT.md` - Docker 环境说明

### 4. 自动化脚本
- ✅ `setup-gitea-ci.sh` - 一键配置 Runner

## 🎯 CI/CD 工作流程

### 生产环境 (main/master 分支)

```
推送代码到 main/master
    ↓
触发 ci-cd.yml
    ↓
┌─────────────────┐
│  1. Build       │ ← 编译 Java 项目
└────────┬────────┘
         ↓
┌─────────────────┐
│  2. Test        │ ← 运行单元测试（PostgreSQL + Redis）
└────────┬────────┘
         ↓
┌─────────────────┐
│  3. Deploy      │ ← 构建 Docker 镜像
└────────┬────────┘
         ↓
┌─────────────────┐
│  4. Deploy to   │ ← 部署到生产服务器
│     Server      │
└─────────────────┘
```

### 开发环境 (dev 分支)

```
推送代码到 dev
    ↓
触发 deploy-dev.yml
    ↓
┌─────────────────┐
│  Build & Deploy │ ← 编译并部署到本地（端口 8081）
└─────────────────┘
```

## 📁 项目结构

```
myblog/
├── .gitea/
│   └── workflows/
│       ├── ci-cd.yml           # 生产环境工作流
│       └── deploy-dev.yml      # 开发环境工作流
├── docker-compose.yml          # Docker 编排配置
├── setup-gitea-ci.sh          # Runner 配置脚本
├── GITEA_CICD_GUIDE.md        # 详细配置指南
├── CICD_QUICKSTART.md         # 快速开始指南
├── DOCKER_ENVIRONMENT.md      # Docker 环境说明
└── DEVOPS_SUMMARY.md          # 本文件
```

## 🚀 快速启动步骤

### 1. 启动所有服务
```bash
docker compose up -d
```

### 2. 配置 Gitea Actions Runner
```bash
./setup-gitea-ci.sh
```

### 3. 注册 Runner
```bash
docker exec -it gitea-runner act_runner register
```

### 4. 初始化 Git 仓库
```bash
git init
git add .
git commit -m "Initial commit with CI/CD"
git remote add origin http://localhost:3000/your-username/myblog.git
git push -u origin main
```

### 5. 查看执行状态
访问 http://localhost:3000/your-username/myblog/actions

## 🔑 关键配置

### 镜像加速源
所有镜像使用轩辕加速：`04eh77udwizz5vqdbk.xuanyuan.run`

### 网络配置
所有容器在 `myblog_myblog-network` 网络中，可以通过服务名互相访问

### 端口映射
- Gitea: 3000 (Web), 222 (SSH)
- PostgreSQL: 5432
- Redis: 6379
- MinIO: 9000 (API), 9001 (Console)
- Backend (dev): 8081

### 环境变量
```yaml
数据库:
  POSTGRES_USER: Leoxiaoblog
  POSTGRES_PASSWORD: your-password-here
  POSTGRES_DB: myblog

MinIO:
  MINIO_ROOT_USER: Leoxiao
  MINIO_ROOT_PASSWORD: your-password-here
```

## 🛠️ 常用命令

### Docker 管理
```bash
# 查看所有容器
docker ps

# 查看日志
docker compose logs -f [service_name]

# 重启服务
docker compose restart [service_name]

# 停止所有服务
docker compose down
```

### Gitea Runner 管理
```bash
# 查看 Runner 状态
docker ps | grep gitea-runner

# 查看 Runner 日志
docker logs -f gitea-runner

# 重启 Runner
docker restart gitea-runner
```

### Git 操作
```bash
# 推送到开发分支（触发开发部署）
git checkout dev
git push origin dev

# 推送到主分支（触发生产部署）
git checkout main
git push origin main
```

## 📊 监控和调试

### 查看工作流执行
1. 访问 Gitea 仓库
2. 点击 "Actions" 标签
3. 查看执行历史和详细日志

### 常见问题排查

**Runner 未连接**
```bash
docker logs gitea-runner
docker restart gitea-runner
```

**工作流失败**
- 检查 Actions 标签页的日志
- 验证 secrets 配置
- 确认网络连接正常

**部署失败**
- 检查 SSH 连接
- 验证服务器配置
- 查看部署日志

## 🔐 安全建议

1. **使用 Secrets**
   - 不要在代码中硬编码密码
   - 在 Gitea Settings → Secrets 中配置敏感信息

2. **分支保护**
   - 启用 main 分支保护
   - 要求 PR 审查

3. **定期更新**
   - 更新依赖包
   - 更新 Docker 镜像
   - 更新 Gitea 版本

## 🎓 学习资源

- [Gitea Actions 官方文档](https://docs.gitea.com/usage/actions/)
- [GitHub Actions 参考](https://docs.github.com/en/actions)
- [Docker 最佳实践](https://docs.docker.com/develop/dev-best-practices/)

## 📞 需要帮助？

查看详细文档：
- 配置问题：`GITEA_CICD_GUIDE.md`
- 快速开始：`CICD_QUICKSTART.md`
- Docker 环境：`DOCKER_ENVIRONMENT.md`

---

**恭喜！你的 DevOps 工作流已经配置完成！** 🎉

现在你可以：
1. 推送代码自动触发 CI/CD
2. 在 Gitea 中查看执行状态
3. 享受自动化部署带来的便利
