# 文章对象存储重构说明

## 📋 概述

本次重构将文章内容从数据库迁移到 MinIO 对象存储，并增强了 XSS 安全防护。

### 主要变更

1. **存储方式变更**: 文章 Markdown 内容从 PostgreSQL 数据库迁移到 MinIO 对象存储
2. **XSS 防护增强**: 使用 Jsoup 白名单过滤危险的 HTML 标签和属性
3. **前端直接使用**: 前端可以直接从后端 API 获取文章内容，无需额外处理

---

## 🏗️ 架构设计

### 数据流

```
用户提交文章
    ↓
后端接收 (PostController)
    ↓
XSS 安全检查 (Jsoup 白名单过滤)
    ↓
上传到 MinIO (PostStorageService)
    ↓
保存路径到数据库 (content_path)
    ↓
返回文章 ID
```

### 读取流程

```
用户请求文章详情
    ↓
后端查询数据库 (获取 content_path)
    ↓
从 MinIO 下载内容 (PostStorageService)
    ↓
附加调试信息
    ↓
返回给前端
```

---

## 📁 文件结构

### 新增文件

```
src/main/java/com/leo/myblog/
├── config/
│   └── MinioConfig.java                    # MinIO 配置类
├── service/
│   ├── PostStorageService.java             # 文章内容存储服务
│   └── PostContentMigrationService.java    # 数据迁移服务
└── controller/
    └── PostAdminController.java            # 管理员接口（迁移触发）

sql/
└── migrate_post_content_to_minio.sql       # 数据库迁移脚本
```

### 修改文件

```
src/main/java/com/leo/myblog/
├── entity/
│   └── Post.java                           # 添加 contentPath 字段
└── service/
    └── PostServiceImpl.java                # 集成 MinIO 存储和 XSS 防护
```

---

## 🔒 安全特性

### 1. XSS 防护（三层防御）

#### 第一层：Jsoup 白名单过滤

```java
private static final Safelist POST_CONTENT_SAFELIST = new Safelist()
    .addTags("p", "br", "strong", "em", "u", "s", "blockquote", "code", "pre", "hr")
    .addTags("h1", "h2", "h3", "h4", "h5", "h6")
    .addTags("ul", "ol", "li", "dl", "dt", "dd")
    .addTags("a", "img", "table", "thead", "tbody", "tr", "th", "td")
    .addAttributes("a", "href", "title", "target")
    .addAttributes("img", "src", "alt", "title", "width", "height")
    .addProtocols("a", "href", "https", "http", "mailto")
    .addProtocols("img", "src", "https", "http", "data")
    .removeProtocols("a", "href", "javascript", "vbscript")
    .removeProtocols("img", "src", "javascript", "vbscript");
```

**允许的标签**:
- 文本格式化: `p`, `br`, `strong`, `em`, `u`, `s`
- 标题: `h1` - `h6`
- 列表: `ul`, `ol`, `li`, `dl`, `dt`, `dd`
- 引用: `blockquote`
- 代码: `code`, `pre`
- 链接: `a` (仅 https, http, mailto)
- 图片: `img` (仅 https, http, data)
- 表格: `table`, `thead`, `tbody`, `tr`, `th`, `td`

**禁止的内容**:
- ❌ `<script>` 标签
- ❌ `javascript:` 协议
- ❌ `on*` 事件处理器 (onclick, onerror 等)
- ❌ `<iframe>`, `<object>`, `<embed>`
- ❌ CSS `expression()`

#### 第二层：危险模式检测

```java
String[] dangerousPatterns = {
    "javascript:",       // javascript: 协议
    "vbscript:",         // vbscript: 协议
    "expression\\(",    // CSS expression
    "url\\(\\s*['\"]?\\s*javascript:"  // url(javascript:...)
};
```

#### 第三层：前端渲染防护

**建议前端使用 DOMPurify**:

```javascript
import DOMPurify from 'dompurify'
import marked from 'marked'

const renderContent = (content) => {
  const html = marked.parse(content)
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 
                   'blockquote', 'code', 'pre', 'img', 'a', 'strong', 'em'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title']
  })
}

// 在模板中使用
<div v-html="renderContent(post.content)"></div>
```

### 2. 目录穿越防护

```java
private void validateObjectPath(String objectPath) {
    // 检查是否包含 ".." （目录穿越）
    if (objectPath.contains("..")) {
        throw new SecurityException("非法的对象路径");
    }
    
    // 只允许字母、数字、下划线、连字符、斜杠和点
    if (!objectPath.matches("^[a-zA-Z0-9_\\-/.]+$")) {
        throw new SecurityException("对象路径包含非法字符");
    }
    
    // 确保路径在 posts 目录下
    if (!objectPath.startsWith("posts/")) {
        throw new SecurityException("对象路径必须在 posts 目录下");
    }
}
```

---

## 🚀 部署步骤

### 1. 执行数据库迁移

```bash
# 备份数据库
pg_dump -U Leoxiaoblog myblog > backup_before_migration.sql

# 执行迁移脚本
psql -U Leoxiaoblog -d myblog -f sql/migrate_post_content_to_minio.sql
```

### 2. 迁移现有文章内容

```bash
# 启动应用后，调用迁移接口
curl -X POST http://localhost:8080/api/posts/admin/migrate-content \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 3. 验证迁移结果

```bash
# 检查数据库中 content_path 字段
psql -U Leoxiaoblog -d myblog -c "SELECT id, title, content_path FROM t_post LIMIT 10;"

# 检查 MinIO 中的文件
mc ls myminio/myblog/posts/
```

### 4. 测试文章功能

```bash
# 创建新文章
curl -X POST http://localhost:8080/api/posts \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "测试文章",
    "content": "# 测试\n\n这是一篇测试文章",
    "categoryId": 1,
    "status": 1
  }'

# 获取文章详情
curl http://localhost:8080/api/posts/ARTICLE_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 📊 性能优化

### 优势

1. **数据库负载降低**: 大文本不再存储在数据库中
2. **查询速度提升**: 列表查询不需要加载大文本
3. **存储扩展性**: MinIO 支持分布式存储，易于扩展
4. **CDN 友好**: 可以配合 CDN 加速内容分发

### 缓存策略（可选）

```java
// 可以在 PostStorageService 中添加 Redis 缓存
@Cacheable(value = "post:content", key = "#objectPath")
public String downloadContent(String objectPath) {
    // ...
}
```

---

## 🔧 配置说明

### application.yml

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: Leoxiao
  secret-key: your-password-here
  bucket-name: myblog
```

### MinIO Bucket 权限

```bash
# 设置 bucket 为私有（推荐）
mc anonymous set private myminio/myblog

# 或者设置为公开读取（如果需要直接访问）
mc anonymous set public myminio/myblog
```

---

## ⚠️ 注意事项

### 1. 全文搜索限制

由于文章内容存储在 MinIO 中，**不支持数据库级别的全文搜索**。

**解决方案**:
- 使用 Elasticsearch 或其他搜索引擎
- 仅在标题和摘要中搜索
- 定期同步内容到搜索引擎

### 2. 数据一致性

- 文章创建时，先插入数据库，再上传 MinIO
- 如果 MinIO 上传失败，会回滚数据库记录
- 删除文章时，先删除 MinIO 内容，再软删除数据库记录

### 3. 备份策略

```bash
# 备份 MinIO 数据
mc mirror myminio/myblog /backup/minio/myblog

# 备份数据库
pg_dump -U Leoxiaoblog myblog > backup.sql
```

---

## 🐛 故障排查

### 问题1: 文章内容加载失败

**症状**: 文章详情返回 "文章内容加载失败"

**排查**:
```bash
# 1. 检查 MinIO 连接
mc ls myminio/myblog/posts/

# 2. 检查日志
docker logs myblog-backend | grep "加载文章内容失败"

# 3. 验证 content_path 是否正确
psql -U Leoxiaoblog -d myblog -c "SELECT id, content_path FROM t_post WHERE id = ARTICLE_ID;"
```

### 问题2: XSS 过滤过度

**症状**: 合法的 Markdown 语法被过滤

**排查**:
```bash
# 查看日志中的清洗前后长度
docker logs myblog-backend | grep "Markdown 内容安全检查通过"

# 调整 Safelist 配置
# 编辑 PostServiceImpl.java 中的 POST_CONTENT_SAFELIST
```

### 问题3: 迁移失败

**症状**: 迁移接口返回错误

**排查**:
```bash
# 1. 检查 MinIO 权限
mc admin info myminio

# 2. 检查数据库连接
psql -U Leoxiaoblog -d myblog -c "SELECT count(*) FROM t_post;"

# 3. 查看详细错误日志
docker logs myblog-backend | grep "文章迁移失败"
```

---

## 📝 API 变更

### 无变化

前端 API **完全兼容**，无需修改前端代码：

- `POST /api/posts` - 创建文章
- `PUT /api/posts/{id}` - 更新文章
- `GET /api/posts/{id}` - 获取文章详情
- `DELETE /api/posts/{id}` - 删除文章

### 新增接口

- `POST /api/posts/admin/migrate-content` - 迁移文章内容（管理员专用）

---

## 🎯 后续优化建议

1. **添加 CDN 支持**: 为 MinIO 配置 CDN 加速
2. **实现增量迁移**: 支持增量迁移而非全量迁移
3. **添加内容版本控制**: 保留历史版本
4. **集成搜索引擎**: 使用 Elasticsearch 实现全文搜索
5. **添加内容压缩**: 对大文章进行压缩存储

---

**更新日期**: 2026-05-21  
**版本**: 1.0  
**作者**: Leo
