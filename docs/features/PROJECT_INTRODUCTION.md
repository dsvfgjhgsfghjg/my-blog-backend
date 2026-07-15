# MyBlog 项目简介

## 📖 项目概述

MyBlog 是一个基于 Spring Boot 3.2.5 和 Java 21 开发的现代化个人博客系统。该系统采用前后端分离架构，提供了完整的用户管理、文章发布、评论互动、多媒体存储等功能，旨在为博主提供一个功能丰富、安全可靠的个人内容管理平台。

## 🛠️ 技术栈

### 后端技术
- **核心框架**: Spring Boot 3.2.5
- **编程语言**: Java 21 (Temurin)
- **构建工具**: Gradle 8.13
- **数据库**: PostgreSQL 15+
- **ORM框架**: MyBatis-Plus 3.5.5
- **缓存服务**: Redis 7+
- **对象存储**: MinIO
- **安全认证**: Spring Security + JWT (jjwt 0.12.3)
- **数据验证**: Spring Validation
- **代码简化**: Lombok

### 前端技术
- 支持任意现代前端框架（React, Vue, Angular等）
- RESTful API 接口设计

## 🏗️ 系统架构

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│   Frontend  │◄──►│  Spring Boot │◄──►│ PostgreSQL  │
│  (Any Tech) │    │   Backend    │    │  Database   │
└─────────────┘    └──────────────┘    └─────────────┘
                          │
                   ┌──────┴──────┐
                   │             │
            ┌──────▼──┐   ┌────▼────┐
            │  Redis  │   │  MinIO  │
            │ Cache   │   │ Storage │
            └─────────┘   └─────────┘
```

## 🚀 核心功能模块

### 1. 用户管理系统
- ✅ 用户注册/登录（JWT认证）
- ✅ 用户信息管理（昵称、头像、密码修改）
- ✅ 管理员功能（用户列表、启用/禁用、重置密码、权限管理）
- ✅ 用户注销（Token黑名单机制）
- ✅ 登录信息记录（IP地址、时间统计）
- ✅ 数据脱敏保护（手机号、邮箱等敏感信息）

### 2. 博客文章系统
- ✅ 文章 CRUD（创建、查询、更新、删除）
- ✅ 文章分类管理
- ✅ 文章标签管理
- ✅ 分页查询与搜索功能
- ✅ 全文搜索与高级筛选
- ✅ 置顶功能与阅读量统计
- ✅ Markdown格式支持
- ✅ SEO友好URL（slug）

### 3. 评论互动系统
- ✅ 文章评论发表
- ✅ 嵌套回复功能（多级评论）
- ✅ 评论分页加载
- ✅ 评论点赞功能
- ✅ 评论管理（管理员可删除/隐藏）
- ✅ @用户功能

### 4. 多媒体管理
- ✅ 头像上传与管理（MinIO存储）
- ✅ 音频文件上传与播放
- ✅ 图片封面管理
- ✅ 文件类型安全校验
- ✅ 文件大小限制控制

### 5. 博主信息模块
- ✅ 博主资料管理
- ✅ 公开信息查询
- ✅ 个性化设置

## 🔐 安全特性

- **JWT认证**: 基于Token的身份验证机制
- **权限控制**: 基于角色的访问控制（RBAC）
- **数据脱敏**: 敏感信息自动脱敏显示
- **文件安全**: 上传文件类型白名单与魔数验证
- **SQL注入防护**: 使用参数化查询
- **XSS防护**: 输入过滤与输出转义
- **密码加密**: BCrypt强哈希加密

## 📊 数据库设计

### 核心数据表
- `t_user` - 用户信息表
- `t_post` - 文章信息表
- `t_category` - 文章分类表
- `t_tag` - 标签信息表
- `t_post_tag` - 文章标签关联表
- `t_comment` - 评论信息表
- `t_blogger_info` - 博主信息表
- `t_audio` - 音频文件表

## 🌐 API 接口

系统提供完整的RESTful API接口，包括：

### 用户接口
- `POST /api/users/register` - 用户注册
- `POST /api/users/login` - 用户登录
- `GET /api/users/me` - 获取当前用户信息
- `PUT /api/users/me` - 更新用户信息
- `POST /api/users/logout` - 用户注销

### 文章接口
- `POST /api/posts` - 创建文章
- `GET /api/posts/{id}` - 获取文章详情
- `PUT /api/posts/{id}` - 更新文章
- `DELETE /api/posts/{id}` - 删除文章
- `GET /api/posts/public` - 公开文章列表

### 评论接口
- `POST /api/comments` - 发表评论
- `GET /api/comments/post?postId={id}` - 获取文章评论
- `DELETE /api/comments/{id}` - 删除评论

### 管理接口
- `GET /api/users/admin/list` - 用户管理列表
- `PUT /api/users/admin/{id}/status` - 启用/禁用用户
- `PUT /api/comments/{id}/status` - 隐藏/显示评论

## 📁 项目结构

```
src/main/java/com/leo/myblog/
├── annotation/          # 自定义注解
├── aspect/              # AOP切面
├── common/              # 通用类
├── config/              # 配置类
├── controller/          # 控制器层
├── dto/                 # 数据传输对象
├── entity/              # 实体类
├── exception/           # 异常处理
├── mapper/              # 数据访问层
├── service/             # 业务逻辑层
├── util/                # 工具类
└── utils/               # 辅助工具类
```

## 🚦 开发状态

### 已完成功能
- [x] 用户系统（注册、登录、权限管理）
- [x] 文章系统（CRUD、分类、标签）
- [x] 评论系统（发表、回复、管理）
- [x] 多媒体存储（头像、音频）
- [x] 数据脱敏功能
- [x] 搜索功能增强
- [x] JAR包重构
- [x] Java多版本管理

### 计划中功能
- [ ] 点赞/收藏功能
- [ ] 消息通知系统
- [ ] Elasticsearch集成
- [ ] SEO优化
- [ ] 性能优化
- [ ] 后台管理系统增强

## 🏃‍♂️ 快速启动

### 环境要求
- Java 21+
- PostgreSQL 15+
- Redis 7+
- MinIO

### 启动步骤
1. 配置数据库连接信息
2. 执行数据库迁移脚本
3. 配置MinIO和Redis连接
4. 运行 `./gradlew bootRun`

详细启动指南请参考 [QUICK_START.md](QUICK_START.md)

## 📄 许可证

本项目仅供学习和个人使用。

## 🤝 贡献

欢迎提交Issue和Pull Request来改进这个项目。

---

**开发者**: Leo  
**最后更新**: 2026-05-07  
**版本**: v1.0.0