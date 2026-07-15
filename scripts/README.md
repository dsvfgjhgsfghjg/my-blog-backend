# Scripts 目录

本目录包含了项目中使用的所有脚本文件，按用途分类组织。

## 目录结构

### [deploy/](./deploy/) - 部署脚本
- `deploy.sh` - 主部署脚本
- `check-deployment.sh` - 部署检查脚本

### [setup/](./setup/) - 环境设置脚本
- `setup-gitea-ci.sh` - Gitea CI/CD 设置脚本
- `setup-java21.sh` - Java 21 环境设置脚本
- `setup-server.sh` - 服务器设置脚本
- `use-java21.sh` - 切换到 Java 21 的脚本

### [dev/](./dev/) - 开发相关脚本
- `start-local.sh` - 启动本地开发环境
- `stop-local.sh` - 停止本地开发环境

### [tests/](./tests/) - 测试脚本
- `test_comment_api.sh` - 评论 API 测试
- `test_favorite_api.sh` - 收藏功能测试
- `test_particle_preference_api.sh` - 粒子偏好设置测试
- `test_public_posts_api.sh` - 公开文章 API 测试
- `test_search.sh` - 搜索功能测试

## 其他文件

- `.gitignore.docker` - Docker 相关的 git 忽略规则
- `.sdkmanrc` - SDKMAN 配置文件
- `apifoxtoken.txt` - Apifox 认证令牌（已添加到 .gitignore）
- `*.tar.gz` - 部署包文件（临时文件）

## 使用说明

在执行脚本之前，请确保给予执行权限：
```bash
chmod +x scripts/**/*.sh
```

然后根据具体需求运行相应的脚本。
