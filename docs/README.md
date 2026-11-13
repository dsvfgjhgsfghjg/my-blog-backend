# MyBlog 项目文档

本目录包含了 MyBlog 项目的所有文档，按类别组织以便于查找和维护。

## 目录结构

### [engineering/](./engineering/) - 工程接手与协作规范
- 仓库边界、职责分工与安全不变量
- 开发、测试、发布和跨端交接流程
- 2026-11-12 安全审计基线
- 2026-11-13 安全维护窗口与回滚手册

### [api/](./api/) - API 文档
包含所有 API 接口的详细说明文档：
- 用户认证与授权 API
- 文章管理 API
- 评论系统 API
- 收藏功能 API
- 搜索功能 API
- 分类和标签 API
- 主页内容 API

### [deployment/](./deployment/) - 部署文档
包含项目部署相关的指南和说明：
- Docker 部署指南
- 云部署指南
- CI/CD 配置说明
- 环境设置文档

### [features/](./features/) - 功能模块文档
描述项目各个功能模块的设计和实现：
- 头像管理系统
- 数据脱敏功能
- DevOps 相关说明
- JAR 包重构指南
- 项目介绍

### [fixes/](./fixes/) - 修复和更新文档
记录各种 bug 修复和功能更新：
- API 修复说明
- 长整型 ID 序列化修复
- 安全修复
- 分页更新说明

### [guides/](./guides/) - 开发指南
提供开发和使用的指导文档：
- 快速开始指南
- Java 版本管理
- JavaScript BigInt 处理
- 帮助文档

## 其他文件

- `疑惑.md` - 项目开发过程中的疑问和思考
- `.gitignore.docker` - Docker 相关的 git 忽略规则
- `.sdkmanrc` - SDKMAN 配置文件

App 端需要的变更说明统一放在 [app-handoffs/](./app-handoffs/)，模板见
[engineering/APP_HANDOFF_TEMPLATE.md](./engineering/APP_HANDOFF_TEMPLATE.md)。

## 脚本文件

所有的 shell 脚本已移至项目根目录的 `scripts/` 目录中，包括：
- 部署脚本
- 测试脚本
- 环境设置脚本
- 本地开发脚本

## 维护说明

当添加新文档时，请将其放置在相应的子目录中，以保持项目结构的整洁。
