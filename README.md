# MyBlog

> 一个面向个人博主的现代化全栈博客系统，**安全优先**、**高内聚低耦合**、**前后端分离**。

🌐 **在线演示**: [https://leo-blog.top](https://leo-blog.top)

---

## 📦 快速安装（推荐）

MyBlog 提供类似 WordPress 的交互式 CLI 一键安装向导，无需手动配置环境变量和数据库。

```bash
# 下载 Release 包后，直接运行安装脚本
bash scripts/install.sh
```

安装过程引导您完成以下配置：

| 步骤 | 说明 |
|------|------|
| 1. 选择部署方式 | Docker Compose 编排部署（推荐）或宿主机直接部署 |
| 2. 环境检测 | 自动检测 Docker、Java、Node.js 等依赖 |
| 3. 镜像源配置 | 可选配置镜像加速地址 |
| 4. 数据库配置 | PostgreSQL 连接信息 + 连接测试 |
| 5. Redis 配置 | Redis 连接信息 |
| 6. MinIO 配置 | 对象存储连接信息 + 连接测试 |
| 7. JWT 配置 | JWT 密钥（可自动生成）和过期时间 |
| 8. 管理员账号 | 创建系统管理员（密码强度校验） |
| 9. 站点信息 | 站点名称、描述、域名、HTTPS |
| 10. 配置确认 | 确认所有配置，开始安装 |

> 📖 详细安装说明参见 [docs/install/INSTALL_GUIDE.md](docs/install/INSTALL_GUIDE.md)

---

## ✨ 项目简介

**MyBlog** 是基于 **Spring Boot 3.5.16 + Java 21** 构建的个人博客系统后端，采用前后端分离架构，提供完整的用户管理、文章发布、评论互动、多媒体存储、内容审核与多层次安全防护能力。系统已通过完整的渗透测试与安全加固，可作为个人博客 / 知识库 / 技术内容平台的开箱即用方案。

前端以独立容器部署（Apache 静态资源），后端提供完全 RESTful 风格的 API。

---

## 🚀 技术栈

### 后端核心
| 类别 | 技术 |
| --- | --- |
| 语言 / JDK | **Java 21**（Temurin LTS） |
| Web 框架 | **Spring Boot 3.5.16** + Spring Security |
| 持久层 | **MyBatis-Plus 3.5.5** |
| 关系数据库 | **PostgreSQL 18** |
| 缓存 | **Redis 7+** |
| 对象存储 | **MinIO** |
| 安全认证 | **JWT** (jjwt 0.12.3) + **TOTP 双因素认证** + **App 审批式双因素认证** |
| 加密通信 | **DH 2048-bit 密钥交换** + **AES-256/192/128-GCM** + **SM4-GCM 国密** |
| 实时通信 | **WebSocket**（加密通道 + 管理后台仪表盘） |
| AOP | Spring AOP（管理员 / TOTP / 审计日志切面） |
| 工具 | Lombok、Jsoup、Jsoup + Xerces（SVG 安全清洗） |
| 构建 | **Gradle 8.13**（阿里云镜像加速） |

### 部署 / 运维
- **Docker Compose** 多环境编排（local / prod / ssl）
- **Gitea + Gitea Runner** 自建 CI/CD
- **Let's Encrypt** 自动 HTTPS
- **Apache HTTPD** 反向代理 + 静态资源服务
- **Nginx**（可选）作为前端网关

### 前端
- 独立项目 / 独立容器
- 任意现代框架（推荐 Vue 3 + Vite），通过 RESTful API 与后端通信

---

## 🏗️ 系统架构

```
                ┌─────────────────────────────┐
                │   浏览器 (访客/博主/管理员)    │
                └──────────────┬──────────────┘
                               │ HTTPS
                ┌──────────────▼──────────────┐
                │  Apache (前端容器, 静态+代理) │
                └──────────────┬──────────────┘
                               │ HTTP (内网)
                ┌──────────────▼──────────────┐
                │   Spring Boot Backend       │
                │   (JWT / TOTP / AOP / ...)   │
                └──┬───────────┬───────────┬──┘
                   │           │           │
            ┌──────▼──┐   ┌────▼────┐  ┌───▼────┐
            │PostgreSQL│   │  Redis  │  │ MinIO  │
            │  数据    │   │  缓存    │  │ 对象存储│
            └─────────┘   └─────────┘  └────────┘
                            ▲
                            │ WebSocket (加密通道)
                    ┌───────┴────────┐
                    │  MyAuthenticator │
                    │  Android App    │
                    │  (审批式双因素)  │
                    └────────────────┘
```

所有服务统一通过 `docker-compose` 编排，并接入 Gitea Runner 实现自动化构建与部署。

---

## 🌟 核心特性

### 1. 用户与认证
- ✅ 用户注册 / 登录 / 注销（JWT + Token 黑名单）
- ✅ **TOTP 双因素认证**（Google Authenticator 兼容）+ 敏感操作二次校验
- ✅ **App 审批式双因素认证** — 通过 MyAuthenticator Android App 实现 3 选 1 挑战审批
- ✅ **WebSocket 加密通道** — DH 2048-bit 密钥交换 + AES-GCM/SM4-GCM 加密通信
- ✅ **设备持久化设备码（Device Secret）** — 服务端生成、BCrypt 存储、WebSocket 验证
- ✅ **设备绑定幂等化** — 同一设备重复绑定不创建重复记录，每用户上限 5 台
- ✅ **批量解绑设备 API** — 保留指定设备 / 完全重置
- ✅ 基于角色 / 能力（Ability）的 **RBAC 权限模型**（`@RequireAdmin` / `@RequireTotp` AOP 切面）
- ✅ **数据脱敏**（手机号、邮箱等）
- ✅ **渐进式账户锁定** + 登录速率限制（防爆破）
- ✅ **登录设备追踪**（IP、操作系统、浏览器、客户端类型）
- ✅ 头像历史管理 / 多端 Session 管理
- ✅ BCrypt 强密码哈希

### 2. 文章与内容
- ✅ 文章 CRUD + 软删除
- ✅ 分类 / 标签管理
- ✅ **Markdown 渲染**（含代码高亮、公式、图片）
- ✅ 全文搜索（模糊匹配）+ 高级筛选（分类 / 标签 / 状态 / 时间范围）
- ✅ 阅读量统计、置顶、SEO 友好 slug
- ✅ **文章审核工作流**（待审核 → 通过 / 拒绝 → 重新提交）+ 审计日志
- ✅ 文章正文 **MinIO 对象存储**（大数据量优化）

### 3. 评论与互动
- ✅ 嵌套多级回复 + `@用户` 提醒
- ✅ 评论点赞 / 取消点赞（防重复）
- ✅ 管理员删除 / 隐藏 / 审核
- ✅ 评论分页加载

### 4. 收藏与多媒体
- ✅ 文章收藏 / 取消收藏（防重复）
- ✅ 我的收藏列表
- ✅ 头像 / 封面 / 音频上传（MinIO）
- ✅ **文件类型白名单 + 魔数校验**
- ✅ **SVG 安全清洗**（防 XSS）

### 5. 博主信息
- ✅ 博主资料管理（昵称、简介、头像）
- ✅ 公开信息查询（无需登录）

### 6. 系统与扩展
- ✅ **粒子特效系统**（鼠标交互、可动态扩展）
- ✅ **系统配置中心**（数据库驱动，Redis 缓存，热更新）
- ✅ **能力（Ability）管理系统**（细粒度权限，可后台分配）
- ✅ **审计日志系统**（AOP 自动采集 + 设备信息 + Request ID 链路追踪）
- ✅ **全局异常处理**（统一响应格式，11 种异常类型处理器）
- ✅ **API 限流**（滑块验证码、评论限流、登录限流）

---

## 🔐 安全特性（重点）

MyBlog 在设计与实现上把**安全性**作为第一优先级，已通过完整渗透测试并修复了报告中的全部高危漏洞。

| 类别 | 实现 |
| --- | --- |
| **身份认证** | JWT（jjwt 0.12.3） + Token 黑名单 + Session 失效 |
| **多因素认证** | TOTP（Google Authenticator）+ App 审批式双因素（3 选 1 挑战） |
| **加密通信** | DH 2048-bit 密钥交换 + AES-256/192/128-GCM + SM4-GCM 国密 |
| **设备安全** | 服务端生成设备码（Device Secret）、BCrypt 存储、WebSocket 验证 |
| **暴力破解防护** | 渐进式账户锁定 + 登录速率限制 + 空密码保护 + 绑定频率限制 |
| **爬虫/扫描器防护** | UA 黑名单（13+ 条规则） + 蜜罐机制 + IP 自动封禁（24h） |
| **人机验证** | 滑块验证码（背景图随机生成 + MinIO 存储） |
| **XSS 防护** | 输入过滤 + 输出转义 + SVG 安全清洗 + CSP 头 |
| **CSRF 防护** | Spring Security 默认策略（结合 CORS 白名单） |
| **点击劫持** | `X-Frame-Options: DENY` + `frame-ancestors 'none'` |
| **MIME 嗅探** | `X-Content-Type-Options: nosniff` |
| **HTTPS 强制** | HSTS 1 年 + 子域名 + Preload |
| **引用信息控制** | `Referrer-Policy: strict-origin-when-cross-origin` |
| **浏览器能力控制** | `Permissions-Policy` 禁用摄像头 / 麦克风 / 定位 |
| **文件上传安全** | 白名单 + 魔数验证 + 大小限制 + SVG 清洗 |
| **数据保护** | 敏感字段脱敏 + BCrypt 强哈希 |
| **审计追溯** | AOP 自动审计日志 + Request ID 链路追踪 + 安全事件日志 |
| **响应头加固** | CSP / HSTS / X-Frame-Options / Referrer-Policy / Permissions-Policy |
| **数据库一致性** | 触发器保护审核状态 + `@Transactional` 事务 + 自动修复脚本 |

> 完整安全审计报告与修复记录参见 [docs/security/](docs/security/)。

---

## 📁 项目结构

```
myblog/
├── build.gradle / settings.gradle     # Gradle 构建脚本
├── docker-compose*.yml                # 多环境编排（local / prod / ssl）
├── Dockerfile / Dockerfile.*          # 镜像构建（postgres / backend / frontend / app）
├── config.yaml                        # Gitea Runner 配置
├── .env.example                       # 环境变量模板
├── .myblog_installed                  # 安装锁文件（安装后生成）
├── sql/                               # 数据库脚本与初始化数据
├── scripts/
│   ├── install.sh                     # 🆕 交互式一键安装脚本
│   ├── deploy/                        # 部署脚本
│   ├── dev/                           # 开发脚本
│   ├── setup/                         # 环境初始化脚本
│   └── tests/                         # 测试脚本
├── docs/
│   ├── install/                       # 🆕 安装指南
│   ├── api/                           # API 接口文档
│   ├── deployment/                    # 部署与 CI/CD 文档
│   ├── features/                      # 功能模块文档
│   ├── fixes/                         # 修复记录
│   ├── guides/                        # 开发指南
│   ├── security/                      # 安全审计与修复
│   └── frontend/                      # 前端集成指南
└── src/main/java/com/leo/myblog/
    ├── annotation/        # 自定义注解（@RequireAdmin, @RequireTotp）
    ├── aspect/            # AOP 切面（AdminAspect, TotpAspect, AuditLogAspect）
    ├── common/            # 通用响应（Result, PageResult）
    ├── config/            # 配置类（Security, Redis, MinIO, MyBatis-Plus, ...）
    ├── controller/        # 控制器层（20+ REST 控制器）
    ├── dto/               # 请求/响应 DTO
    ├── entity/            # 数据库实体（20+ 实体）
    ├── enums/             # 枚举定义
    ├── exception/         # 全局异常处理
    ├── filter/            # 过滤器（UA 安全、Request ID）
    ├── mapper/            # MyBatis-Plus Mapper
    ├── service/           # 业务逻辑层（40+ 服务）
    ├── task/              # 定时任务（安全清理）
    ├── util/              # 工具类（JWT、TOTP、UA 解析、滑块验证码、SVG 清洗）
    ├── utils/             # 辅助工具
    └── websocket/         # WebSocket 处理器（加密通道 + 管理后台仪表盘）
```

---

## 🚦 快速开始（手动部署）

### 环境要求
- **JDK**: 21+
- **Gradle**: 8.13+（项目自带 wrapper）
- **Docker**: 24+ & Docker Compose v2
- **PostgreSQL**: 18+
- **Redis**: 7+
- **MinIO**: 最新版

### 方式一：Docker Compose（推荐）

#### 1. 准备环境变量
```bash
cp .env.example .env
# 编辑 .env，修改密码、密钥等信息（生产环境务必使用强密钥）
```

#### 2. 启动本地开发环境
```bash
docker compose -f deploy/docker-compose.local.yml up -d
```
启动后：
- 前端：`http://localhost:8081`
- 后端 API：`http://localhost:8080`
- MinIO 控制台：`http://localhost:9001`（默认 `minioadmin / minioadmin`）
- PostgreSQL：`localhost:5432`

#### 3. 启动生产环境（含 Apache + 自动 HTTPS）
```bash
docker compose -f deploy/docker-compose.prod.yml up -d
```

### 方式二：从源码构建

```bash
# 1. 启动依赖（数据库、Redis、MinIO）
docker compose -f deploy/docker-compose.local.yml up -d postgres redis minio

# 2. 编译运行
./gradlew bootRun
# 或打包
./gradlew build
java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar
```

### 默认账号
首次启动后，可通过注册接口创建账号，数据库中第一条用户可通过 SQL 提升为管理员：
```sql
UPDATE t_user SET is_admin = TRUE WHERE username = 'your_username';
```

---

## 📱 App 双因素认证系统

MyBlog 提供一套完整的 **App 审批式双因素认证系统**，作为 TOTP 的替代方案。用户通过 **MyAuthenticator Android App** 绑定设备后，当有敏感操作需要审批时，App 会收到推送挑战，用户选择正确数字完成审批。

### 系统架构

```
┌─────────────────┐         REST API          ┌──────────────┐
│   Android App   │ ◄──────────────────────►  │   Backend    │
│  MyAuthenticator │    (绑定设备/获取 Token)    │    Server    │
│                 │                            │              │
│                 │ ◄────── WebSocket ────────►│              │
│                 │    (DH 加密通道 + 挑战推送)  │              │
└─────────────────┘                            └──────────────┘
```

### 核心流程

| 阶段 | 说明 |
|------|------|
| **设备绑定** | 密码绑定 / 扫码绑定 / 配对码绑定，获取设备 Token |
| **DH 密钥交换** | 2048-bit DH 密钥交换，协商 AES-GCM/SM4-GCM 加密密钥 |
| **加密通信** | 所有 WebSocket 消息通过加密通道传输 |
| **3 选 1 挑战** | 敏感操作时推送 3 个候选数字，App 选择正确数字完成审批 |
| **管理后台仪表盘** | 管理员可通过加密 WebSocket 通道查看仪表盘数据 |

### 支持的加密算法

| 算法 | 密钥长度 | 说明 |
|------|---------|------|
| AES-256-GCM | 256 bit | 默认算法，安全性最高 |
| AES-192-GCM | 192 bit | 兼容性较好 |
| AES-128-GCM | 128 bit | 性能最优 |
| SM4-GCM | 128 bit | 国密标准 |

### 设备管理

- **设备码（Device Secret）**：服务端生成，BCrypt 哈希存储，WebSocket 连接验证
- **设备绑定幂等化**：同一设备重复绑定不创建重复记录
- **设备上限**：每用户最多绑定 5 台设备
- **批量解绑**：支持保留指定设备或完全重置

> 📖 详细文档：
> - [Android 端开发指导文档](docs/api/APP_AUTH_ANDROID_GUIDE.md)（1233 行）
> - [加密算法实现指南](docs/guides/APP_CIPHER_ALGORITHM_IMPLEMENTATION_GUIDE.md)
> - [设备码（Device Secret）指南](docs/api/APP_DEVICE_SECRET_GUIDE.md)
> - [设备特征码绑定指南](docs/api/APP_DEVICE_FINGERPRINT_GUIDE.md)
> - [管理后台仪表盘 WebSocket API](docs/api/APP_ADMIN_DASHBOARD_WS_GUIDE.md)
> - [App 推送挑战系统配置指南](docs/features/APP_PUSH_SYSTEM_CONFIG_GUIDE.md)
> - [Vue3 前端设备管理指南](docs/api/VUE3_DEVICE_MANAGEMENT_GUIDE.md)
> - [Vue3 前端推送挑战处理指南](docs/api/VUE3_PUSH_CHALLENGE_GUIDE.md)

---

## 🐳 CI/CD 部署

项目使用自建 **Gitea + Gitea Runner** 实现自动化部署：

- 推送代码 → Gitea Runner 监听变更 → 自动构建 Docker 镜像 → 推送/部署
- 详细配置参见 [`docs/deployment/CICD_DEPLOYMENT_GUIDE.md`](docs/deployment/CICD_DEPLOYMENT_GUIDE.md)
- 快速开始：[`docs/deployment/CICD_QUICKSTART.md`](docs/deployment/CICD_QUICKSTART.md)

---

## 📚 文档索引

完整文档位于 [`docs/`](docs/) 目录，按类别组织：

| 类别 | 路径 | 说明 |
| --- | --- | --- |
| 🆕 安装指南 | [`docs/install/INSTALL_GUIDE.md`](docs/install/INSTALL_GUIDE.md) | 一键安装脚本使用说明 |
| 🆕 App 双因素认证 | [`docs/api/APP_AUTH_ANDROID_GUIDE.md`](docs/api/APP_AUTH_ANDROID_GUIDE.md) | Android 端开发指导（1233 行） |
| 🆕 加密算法 | [`docs/guides/APP_CIPHER_ALGORITHM_IMPLEMENTATION_GUIDE.md`](docs/guides/APP_CIPHER_ALGORITHM_IMPLEMENTATION_GUIDE.md) | AES-GCM / SM4-GCM 实现指南 |
| 🆕 设备管理 | [`docs/api/APP_DEVICE_SECRET_GUIDE.md`](docs/api/APP_DEVICE_SECRET_GUIDE.md) | 设备码（Device Secret）机制 |
| 🆕 管理后台仪表盘 | [`docs/api/APP_ADMIN_DASHBOARD_WS_GUIDE.md`](docs/api/APP_ADMIN_DASHBOARD_WS_GUIDE.md) | WebSocket 加密仪表盘 API |
| 🆕 推送挑战配置 | [`docs/features/APP_PUSH_SYSTEM_CONFIG_GUIDE.md`](docs/features/APP_PUSH_SYSTEM_CONFIG_GUIDE.md) | 推送挑战系统配置项 |
| 🆕 Vue3 前端集成 | [`docs/api/VUE3_DEVICE_MANAGEMENT_GUIDE.md`](docs/api/VUE3_DEVICE_MANAGEMENT_GUIDE.md) | Vue3 设备管理组件 |
| 🆕 Vue3 推送挑战 | [`docs/api/VUE3_PUSH_CHALLENGE_GUIDE.md`](docs/api/VUE3_PUSH_CHALLENGE_GUIDE.md) | Vue3 推送挑战处理组件 |
| 项目简介 | [`docs/features/PROJECT_INTRODUCTION.md`](docs/features/PROJECT_INTRODUCTION.md) | 项目详细说明 |
| 功能清单 | [`docs/features/COMPLETED_FEATURES.md`](docs/features/COMPLETED_FEATURES.md) | 已完成功能总览 |
| API 文档 | [`docs/api/`](docs/api/) | 所有 RESTful API 接口 |
| 部署文档 | [`docs/deployment/`](docs/deployment/) | Docker / CI/CD / SSL 配置 |
| 安全文档 | [`docs/security/`](docs/security/) | 安全审计、TOTP、UA 防护、SVG 清洗… |
| 前端集成 | [`docs/frontend/`](docs/frontend/) | 前端对接指南 |
| 修复记录 | [`docs/fixes/`](docs/fixes/) | 重要 bug 与安全修复 |
| 开发指南 | [`docs/guides/`](docs/guides/) | Java 21、大整数、Markdown 渲染… |
| 渗透测试 | [`docs/渗透测试/`](docs/渗透测试/) | 渗透测试报告与修复记录 |

---

## 🧪 测试

```bash
# 单元测试
./gradlew test

# API 集成测试（需先启动本地环境）
bash scripts/tests/test_public_posts_api.sh
bash scripts/tests/test_comment_api.sh
bash scripts/tests/test_favorite_api.sh
# ... 详见 scripts/tests/
```

---

## 🛠️ 常用脚本

| 脚本 | 用途 |
| --- | --- |
| 🆕 `scripts/install.sh` | **交互式一键安装** |
| `scripts/dev/start-local.sh` | 一键启动本地开发环境 |
| `scripts/dev/stop-local.sh` | 停止本地开发环境 |
| `scripts/deploy/deploy.sh` | 一键部署到服务器 |
| `scripts/deploy/deploy-ssl.sh` | SSL 一键部署 |
| `scripts/setup/setup-gitea-ci.sh` | 初始化 Gitea CI/CD |
| `scripts/setup/setup-gitea-runner.sh` | 安装 Gitea Runner |
| `scripts/setup/setup-java21.sh` | 安装 Java 21 |
| `scripts/fix_post_audit_consistency.sh` | 文章审核一致性修复 |
| `scripts/upload-captcha-to-minio.sh` | 同步验证码背景图到 MinIO |

---

## 🗺️ 路线图

- [x] **App 审批式双因素认证系统** — 已完成
- [x] **WebSocket 加密通道**（DH + AES-GCM/SM4-GCM）— 已完成
- [x] **设备持久化设备码（Device Secret）** — 已完成
- [x] **管理后台仪表盘 WebSocket API** — 已完成
- [ ] 消息通知系统（WebSocket）
- [ ] Elasticsearch 全文检索
- [ ] 点赞 / 浏览历史分析
- [ ] 后台管理 UI 增强
- [ ] 多语言 i18n
- [ ] PWA / 移动端优化

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进本项目。

开发规范：
1. 新功能需先在 `docs/features/` 中补充设计文档
2. API 遵循 RESTful 规范，统一使用 `Result<T>` 响应格式
3. 敏感信息必须脱敏
4. 重要操作需接入审计日志
5. 所有公共接口需进行权限校验

---

## 📄 许可证

本项目采用 **MIT 许可证** 开源，详见 [LICENSE](LICENSE) 文件。

---

## 👤 作者

**Leo** — 个人博客 [https://leo-blog.top](https://leo-blog.top)

---

> ⭐ 如果这个项目对你有帮助，欢迎 Star！
