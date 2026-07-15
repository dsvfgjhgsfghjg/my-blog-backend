# Gitea CI/CD 配置指南

## 概述

本项目使用 Gitea Actions 实现自动化 CI/CD 流程。Gitea Actions 兼容 GitHub Actions，可以使用相同的 workflow 语法。

## Workflow 文件

### 1. 生产环境部署 (ci-cd.yml)

**触发条件：**
- 推送到 `main` 或 `master` 分支
- 创建 Pull Request 到 `main` 或 `master` 分支

**工作流程：**
1. **Build**: 编译 Java 项目，生成 JAR 包
2. **Test**: 运行单元测试（需要 PostgreSQL 和 Redis）
3. **Deploy**: 构建 Docker 镜像
4. **Deploy to Server**: 部署到生产服务器

### 2. 开发环境部署 (deploy-dev.yml)

**触发条件：**
- 推送到 `dev` 分支

**工作流程：**
1. 编译项目
2. 构建 Docker 镜像
3. 部署到本地开发环境（端口 8081）

## 配置步骤

### 1. 启用 Gitea Actions

在 Gitea 仓库设置中启用 Actions：
1. 访问 http://localhost:3000/your-username/myblog
2. 进入 Settings → Actions
3. 启用 "Enable Actions"

### 2. 配置 Runner

Gitea 需要至少一个 runner 来执行工作流。你可以：

#### 选项 A：使用 act_runner（推荐）

```bash
# 下载 act_runner
docker run -d \
  --name gitea-runner \
  --network myblog_myblog-network \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e CONFIG_FILE=/config.yaml \
  gitea/act_runner:latest

# 注册 runner（需要在 Gitea 获取 token）
docker exec -it gitea-runner act_runner register
```

#### 选项 B：使用 Docker-in-Docker

在 docker-compose.yml 中添加 runner 服务：

```yaml
  gitea-runner:
    image: gitea/act_runner:nightly-dind
    container_name: gitea-runner
    privileged: true
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      CONFIG_FILE: /config.yaml
    networks:
      - myblog-network
    restart: unless-stopped
```

### 3. 配置 Secrets

在 Gitea 仓库设置中添加以下 secrets：

1. 进入 Settings → Secrets
2. 添加以下密钥：

```
SERVER_HOST=你的服务器IP地址
SERVER_USER=服务器用户名（如 root）
SERVER_PASSWORD=服务器密码
```

### 4. 初始化 Git 仓库

如果还没有初始化 Git 仓库：

```bash
git init
git add .
git commit -m "Initial commit with CI/CD"

# 添加远程仓库（替换为你的 Gitea 仓库地址）
git remote add origin http://localhost:3000/your-username/myblog.git
git push -u origin main
```

## 工作流说明

### 环境变量

所有 workflow 使用轩辕加速源拉取镜像：
```yaml
env:
  REGISTRY: 04eh77udwizz5vqdbk.xuanyuan.run
```

### 健康检查

PostgreSQL 和 Redis 服务都配置了健康检查，确保服务就绪后再执行测试。

###  artifact 管理

- 构建产物保留 1 天
- 自动清理旧的 artifacts

## 监控和调试

### 查看工作流执行

1. 访问 Gitea 仓库
2. 点击 "Actions" 标签
3. 查看工作流执行历史和日志

### 常见问题

**问题 1：Runner 未连接**
- 检查 runner 是否正常运行
- 确认网络连接正常

**问题 2：权限不足**
- 确保 runner 有访问 Docker socket 的权限
- 检查 Gitea 用户的权限设置

**问题 3：部署失败**
- 检查服务器 SSH 连接
- 验证 secrets 配置是否正确
- 查看部署日志

## 自定义工作流

你可以根据需要修改 `.gitea/workflows/` 目录下的文件：

- 添加代码质量检查
- 集成 SonarQube
- 添加通知（邮件、钉钉等）
- 自定义部署策略

## 最佳实践

1. **分支策略**
   - `main/master`: 生产环境
   - `dev`: 开发环境
   - `feature/*`: 功能分支

2. **安全建议**
   - 不要硬编码密码
   - 使用 secrets 管理敏感信息
   - 定期更新依赖

3. **性能优化**
   - 使用缓存加速构建
   - 并行执行独立任务
   - 限制 artifact 保留时间

## 快速开始

```bash
# 1. 提交代码触发 CI/CD
git add .
git commit -m "Trigger CI/CD"
git push

# 2. 在 Gitea 中查看执行状态
# 访问 http://localhost:3000/your-username/myblog/actions

# 3. 查看部署结果
docker ps | grep myblog-backend
```

## 参考资源

- [Gitea Actions 文档](https://docs.gitea.com/usage/actions/)
- [GitHub Actions 语法](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [act_runner 文档](https://gitea.com/gitea/act_runner)
