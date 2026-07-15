# 项目文件整理总结

## 整理日期
2026-05-13

## 整理内容

### 1. 文档整理 (docs/)

所有 Markdown 文档已从项目根目录移至 `docs/` 目录，并按类别分组：

#### API 文档 (docs/api/)
- 用户认证与授权 API
- 文章管理 API  
- 评论系统 API
- 收藏功能 API
- 搜索功能 API
- 分类和标签 API
- 主页内容 API
- 粒子偏好设置 API
- 共 21 个 API 文档

#### 部署文档 (docs/deployment/)
- DEPLOYMENT.md
- CLOUD_DEPLOYMENT_GUIDE.md
- DOCKER_DEPLOYMENT_FILES.md
- DOCKER_ENVIRONMENT.md
- CICD_QUICKSTART.md
- GITEA_CICD_GUIDE.md
- QUICK_START_DEPLOY.md
- 共 7 个部署文档

#### 功能模块文档 (docs/features/)
- AVATAR_HISTORY_MANAGEMENT.md
- AVATAR_UPLOAD_GUIDE.md
- DATA_MASKING_COMPLETE.md
- DEVOPS_SUMMARY.md
- JAR_REFACTOR_GUIDE.md
- PROJECT_INTRODUCTION.md
- TODO.md
- 共 7 个功能文档

#### 修复和更新文档 (docs/fixes/)
- API_FIX_POST_CREATE.md
- API_FIX_TAG_VALIDATION.md
- COMMENT_API_FIX.md
- COMMENT_BUG_FIX.md
- LONG_ID_FIX_COMPLETE.md
- LONG_ID_SERIALIZATION_FIX.md
- LONG_TO_STRING_COMPLETE_FIX.md
- SECURITY_FIX_CATEGORY_API.md
- SECURITY_FIX_POST_DETAIL_API.md
- PAGINATION_UPDATE.md
- BUILD_SUCCESS.md
- 共 11 个修复文档

#### 开发指南 (docs/guides/)
- QUICK_START.md
- JAVA_VERSION_MANAGEMENT.md
- JAVA21_READY.md
- JAVASCRIPT_BIGINT_FIX.md
- HELP.md
- 共 5 个指南文档

#### 其他文档
- 疑惑.md - 项目开发过程中的疑问和思考

### 2. 脚本文件整理 (scripts/)

所有 shell 脚本已从项目根目录移至 `scripts/` 目录，并按用途分类：

#### 部署脚本 (scripts/deploy/)
- deploy.sh
- check-deployment.sh

#### 开发脚本 (scripts/dev/)
- start-local.sh
- stop-local.sh

#### 环境设置脚本 (scripts/setup/)
- setup-gitea-ci.sh
- setup-java21.sh
- setup-server.sh
- use-java21.sh

#### 测试脚本 (scripts/tests/)
- test_comment_api.sh
- test_favorite_api.sh
- test_particle_preference_api.sh
- test_public_posts_api.sh
- test_search.sh

### 3. SQL 文件整理

SQL 相关文件已统一存放在 `sql/` 目录：
- check_posts.sql
- pgsql.sql
- create_audio_table.sql
- init-db.sh
- init-minio.sh
- myblog_backup.dump

### 4. .gitignore 更新

更新了 `.gitignore` 文件以：
- 忽略临时文件（*.tar.gz, *.tar）
- 忽略敏感信息文件（apifoxtoken.txt）
- 忽略配置文件（.gitignore.docker, .sdkmanrc）
- 添加文档目录说明

### 5. 创建的文档

- `docs/README.md` - 文档目录说明
- `scripts/README.md` - 脚本目录说明
- `PROJECT_ORGANIZATION.md` - 本整理总结文档

## 整理效果

### 整理前
- 项目根目录有 40+ 个 .md 文件
- 项目根目录有 10+ 个 .sh 脚本文件
- 文件杂乱，难以查找和维护

### 整理后
- 项目根目录干净整洁，只有必要的配置和构建文件
- 所有文档按类别组织在 docs/ 目录下
- 所有脚本按用途分类在 scripts/ 目录下
- 每个子目录都有 README 说明
- 易于维护和扩展

## 后续维护建议

1. **新增文档**：根据文档类型添加到对应的子目录
2. **新增脚本**：根据脚本用途添加到对应的子目录
3. **定期清理**：定期检查和清理过时的文档和脚本
4. **更新索引**：在相应目录的 README 中更新文件列表

## 注意事项

- 所有原有的 Docker 配置文件保持不变
- 构建文件（build.gradle, settings.gradle 等）保持不变
- 源代码结构未做任何修改
- Git 历史记录完整保留
