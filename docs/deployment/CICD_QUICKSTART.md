# CI/CD 快速开始指南

## 🚀 5 分钟配置 CI/CD

### 第一步：启动 Runner

```bash
./setup-gitea-ci.sh
```

### 第二步：注册 Runner

1. 访问 http://localhost:3000 并登录 Gitea
2. 创建或进入你的仓库
3. 进入 **Settings → Actions**
4. 复制注册 token
5. 运行：
   ```bash
   docker exec -it gitea-runner act_runner register
   ```
6. 粘贴 token，其他选项保持默认

### 第三步：推送代码

```bash
git init
git add .
git commit -m "Initial commit with CI/CD"
git remote add origin http://localhost:3000/your-username/myblog.git
git push -u origin main
```

### 第四步：查看执行

访问 http://localhost:3000/your-username/myblog/actions 查看工作流执行状态

## 📋 工作流说明

### 推送到 main/master 分支
- ✅ 编译项目
- ✅ 运行测试
- ✅ 构建 Docker 镜像
- ✅ 部署到服务器

### 推送到 dev 分支
- ✅ 编译项目
- ✅ 部署到本地开发环境（端口 8081）

## 🔧 配置 Secrets

如果需要部署到远程服务器，在 Gitea 仓库设置中添加：

- `SERVER_HOST`: 服务器 IP
- `SERVER_USER`: 用户名
- `SERVER_PASSWORD`: 密码

## 📖 详细文档

查看 [GITEA_CICD_GUIDE.md](./GITEA_CICD_GUIDE.md) 了解完整配置

## 🎯 工作流程图

```
代码推送 → Gitea → Actions Runner → 构建 → 测试 → 部署
                                    ↓
                              Docker 镜像
                                    ↓
                              自动部署
```

## 💡 提示

- 首次使用需要等待 runner 下载依赖
- 可以在 Actions 标签页查看详细日志
- 支持并行执行多个工作流
