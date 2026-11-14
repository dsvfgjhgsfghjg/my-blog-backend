# 文章对象存储 API 文档

## 📋 概述

本文档说明文章功能的对象存储架构和相关API接口。文章内容现在存储在MinIO对象存储中，数据库仅存储内容路径引用。

### 架构优势

- **性能提升**: 数据库不再存储大文本，查询速度显著提升
- **扩展性强**: MinIO支持分布式存储，易于水平扩展
- **安全性高**: 三层XSS防护机制
- **API兼容**: 完全向后兼容，前端无需修改

---

## 🔐 安全特性

### XSS防护（三层防御）

#### 第一层：服务端Jsoup白名单过滤

**允许的HTML标签**:
```
p, br, strong, em, u, s, blockquote, code, pre, hr
h1-h6, ul, ol, li, dl, dt, dd
a (href, title, target), img (src, alt, title, width, height)
table, thead, tbody, tr, th, td
```

**允许的协议**:
- `a[href]`: https, http, mailto
- `img[src]`: https, http, data

**禁止的内容**:
- ❌ `<script>` 标签
- ❌ `javascript:` 协议
- ❌ `on*` 事件处理器 (onclick, onerror等)
- ❌ `<iframe>`, `<object>`, `<embed>`
- ❌ CSS `expression()`

#### 第二层：危险模式检测

自动检测并拒绝包含以下模式的内容：
```java
"javascript:", "vbscript:", "expression\\(", 
"url\\(\\s*['\"]?\\s*javascript:"
```

#### 第三层：前端渲染建议

**推荐使用DOMPurify**:
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
```

---

## 📡 API 接口

### 1. 创建文章

**接口**: `POST /api/posts`

**权限**: 需要登录

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**:
```json
{
  "title": "我的第一篇文章",
  "slug": "my-first-post",
  "content": "# 标题\n\n这是文章内容，支持 **Markdown** 语法。\n\n- 列表项1\n- 列表项2\n\n```javascript\nconsole.log('Hello World');\n```",
  "categoryId": 1,
  "tagIds": [1, 2],
  "coverImage": "/images/cover.jpg",
  "status": 1,
  "isTop": false
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | ✅ | 文章标题（1-200字符） |
| slug | String | ❌ | URL标识（可选，自动生成） |
| content | String | ✅ | Markdown正文内容 |
| categoryId | Long | ❌ | 分类ID |
| tagIds | Array<Long> | ❌ | 标签ID列表 |
| coverImage | String | ❌ | 封面图片URL |
| status | Integer | ❌ | 状态：0-草稿, 1-已发布, 2-私密, 3-待审核（默认3） |
| isTop | Boolean | ❌ | 是否置顶（默认false） |

**响应示例**:
```json
{
  "code": 200,
  "message": "文章创建成功",
  "data": "2054020312340758529"
}
```

**处理流程**:
1. 验证分类和标签是否存在
2. 生成或验证slug唯一性
3. **XSS安全检查**（Jsoup白名单过滤）
4. 提取摘要和统计字数
5. 插入数据库获取文章ID
6. **上传内容到MinIO**（路径格式：`posts/{postId}/{uuid}.md`）
7. 更新数据库content_path字段
8. 创建审计记录
9. 绑定标签关系

**错误响应**:
```json
{
  "code": 400,
  "message": "文章内容包含不安全的内容，请移除脚本或危险标签",
  "data": null
}
```

---

### 2. 更新文章

**接口**: `PUT /api/posts/{postId}`

**权限**: 作者或拥有`edit_posts`能力的管理员

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数**:
- `postId`: 文章ID

**请求体**:
```json
{
  "title": "更新后的标题",
  "slug": "updated-slug",
  "content": "# 更新后的内容\n\n这是新的文章内容",
  "categoryId": 2,
  "tagIds": [3, 4],
  "coverImage": "/images/new-cover.jpg",
  "isTop": true
}
```

**注意**: 
- ❌ **不能通过此接口修改文章状态**（需通过审核流程）
- 如果传递`status`字段会被忽略或抛出异常

**响应示例**:
```json
{
  "code": 200,
  "message": "文章更新成功",
  "data": null
}
```

**处理流程**:
1. 验证文章存在性和权限
2. 验证分类和标签
3. **XSS安全检查**
4. **删除MinIO中的旧内容**
5. **上传新内容到MinIO**
6. 更新数据库content_path和其他字段
7. 重新绑定标签

---

### 3. 删除文章

**接口**: `DELETE /api/posts/{postId}`

**权限**: 作者或拥有`delete_posts`能力的管理员 + **TOTP双因素认证**

**请求头**:
```
Authorization: Bearer {token}
X-TOTP-Code: 123456
```

**路径参数**:
- `postId`: 文章ID

**响应示例**:
```json
{
  "code": 200,
  "message": "文章删除成功",
  "data": null
}
```

**处理流程**:
1. 验证文章存在性和权限
2. **删除MinIO中的文章内容**
3. 软删除数据库记录（isDeleted=true）

---

### 4. 获取文章详情

**接口**: `GET /api/posts/{postId}`

**权限**: 
- 公开文章（status=1）：所有人可见
- 非公开文章：仅作者和管理员可见

**请求头**:
```
Authorization: Bearer {token}  // 可选，用于访问非公开文章
```

**路径参数**:
- `postId`: 文章ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "2054020312340758529",
    "userId": "1234567890",
    "title": "我的第一篇文章",
    "slug": "my-first-post",
    "summary": "这是文章内容，支持 Markdown 语法。列表项1 列表项2...",
    "content": "# 标题\n\n这是文章内容，支持 **Markdown** 语法。\n\n- 列表项1\n- 列表项2\n\n```javascript\nconsole.log('Hello World');\n```\n\n---\n\n## 🛠️ 调试信息\n\n### 📝 文章信息\n\n| 字段 | 值 |\n|------|-----|\n| 文章ID | `2054020312340758529` |\n| 标题 | 我的第一篇文章 |\n| 作者ID | `1234567890` |\n| 分类ID | `1` |\n| 状态 | `1` (已发布) |\n| 是否置顶 | `否` |\n| 阅读量 | `42` |\n| 字数 | `156` |\n| 创建时间 | `2026-05-21T10:30:00` |\n| 更新时间 | `2026-05-21T10:30:00` |\n| 发布时间 | `2026-05-21T10:30:00` |\n| Slug | `my-first-post` |\n\n### 🌐 请求信息\n\n| 字段 | 值 |\n|------|-----|\n| 请求方法 | `GET` |\n| 请求路径 | `/api/posts/2054020312340758529` |\n| 查询参数 | 无 |\n| User-Agent | `Mozilla/5.0...` |\n| Referer | 无 |\n| 客户端IP | `192.168.1.100` |\n| 请求时间 | `2026-05-21T11:00:00` |",
    "wordCount": 156,
    "coverImage": "/images/cover.jpg",
    "status": 1,
    "isTop": false,
    "viewCount": "42",
    "createTime": "2026-05-21T10:30:00Z",
    "updateTime": "2026-05-21T10:30:00Z",
    "publishTime": "2026-05-21T10:30:00Z",
    "categoryId": "1",
    "categoryName": "软件开发",
    "authorUsername": "leo",
    "authorNickname": "Leo",
    "tags": [
      {
        "id": "1",
        "name": "Java",
        "color": "#FF5733"
      },
      {
        "id": "2",
        "name": "Spring Boot",
        "color": "#6DB33F"
      }
    ],
    "debugInfo": "## 🛠️ 调试信息\n\n..."
  }
}
```

**处理流程**:
1. 查询数据库获取文章元数据
2. 验证访问权限（私密文章检查）
3. 阅读量+1（公开文章且非作者访问时）
4. **从MinIO下载文章内容**
5. 附加调试信息到内容末尾
6. 获取分类、作者、标签信息
7. 返回完整响应

**注意**: 
- `content`字段包含原始Markdown内容 + 调试信息
- `debugInfo`字段单独提供调试信息（纯文本格式）

---

### 5. 获取公开文章列表

**接口**: `GET /api/posts/public`

**权限**: 无需登录（仅返回已发布且已通过审计的文章）

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | ❌ | 1 | 页码 |
| pageSize | Integer | ❌ | 10 | 每页数量 |
| categoryId | Long | ❌ | - | 分类ID筛选 |
| tagId | Long | ❌ | - | 标签ID筛选 |
| keyword | String | ❌ | - | 关键字搜索（标题+摘要） |
| searchContent | Boolean | ❌ | false | **已废弃**（内容在MinIO中，不支持数据库搜索） |
| startTime | LocalDateTime | ❌ | - | 开始时间 |
| endTime | LocalDateTime | ❌ | - | 结束时间 |
| minViewCount | Long | ❌ | - | 最小阅读量 |
| maxViewCount | Long | ❌ | - | 最大阅读量 |
| sortBy | String | ❌ | publish_time | 排序字段：create_time, publish_time, view_count |
| sortOrder | String | ❌ | desc | 排序方式：asc, desc |

**请求示例**:
```
GET /api/posts/public?pageNum=1&pageSize=20&categoryId=1&sortBy=publish_time&sortOrder=desc
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": "2054020312340758529",
        "userId": "1234567890",
        "title": "我的第一篇文章",
        "slug": "my-first-post",
        "summary": "这是文章内容，支持 Markdown 语法...",
        "wordCount": 156,
        "coverImage": "/images/cover.jpg",
        "status": 1,
        "isTop": true,
        "viewCount": "42",
        "createTime": "2026-05-21T10:30:00Z",
        "updateTime": "2026-05-21T10:30:00Z",
        "publishTime": "2026-05-21T10:30:00Z",
        "categoryId": "1",
        "categoryName": "软件开发",
        "authorUsername": "leo",
        "authorNickname": "Leo",
        "tags": [
          {"id": "1", "name": "Java"},
          {"id": "2", "name": "Spring Boot"}
        ]
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

**重要变更**:
- ⚠️ **全文搜索已禁用**: 由于文章内容存储在MinIO中，`searchContent`参数不再生效
- 搜索仅在标题和摘要中进行
- 如需全文搜索，建议使用Elasticsearch等搜索引擎

---

### 6. 获取管理后台文章列表

**接口**: `POST /api/posts/admin/list`

**权限**: 需要管理员权限（`@RequireAdmin`）

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**:
```json
{
  "pageNum": 1,
  "pageSize": 20,
  "status": 3,
  "categoryId": 1,
  "tagId": null,
  "keyword": "测试",
  "searchContent": false,
  "startTime": "2026-01-01T00:00:00",
  "endTime": "2026-12-31T23:59:59",
  "minViewCount": 0,
  "maxViewCount": null,
  "sortBy": "create_time",
  "sortOrder": "desc"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | ❌ | 状态筛选（0-草稿, 1-已发布, 2-私密, 3-待审核） |
| 其他字段 | - | ❌ | 同公开列表接口 |

**响应示例**: 同公开列表接口

**特殊规则**:
- 非管理员只能查看自己的文章
- 管理员可以查看所有文章
- 支持按状态筛选（包括待审核文章）

---

### 7. 迁移文章内容（管理员专用）

**接口**: `POST /api/posts/admin/migrate-content`

**权限**: 需要管理员权限

**用途**: 将数据库中现有的文章内容迁移到MinIO

**请求头**:
```
Authorization: Bearer {admin_token}
```

**请求体**: 无

**响应示例**:
```json
{
  "code": 200,
  "message": "文章迁移完成，成功迁移 50 篇",
  "data": 50
}
```

**使用场景**:
- 首次部署后迁移历史文章
- 数据库备份恢复后重新迁移

**注意事项**:
- 迁移过程是事务性的，失败会回滚
- 已迁移的文章不会重复迁移
- 建议在低峰期执行

---

## 🎨 前端开发指南

### React 组件示例

#### 1. 文章编辑器组件

```jsx
import React, { useState } from 'react';
import MDEditor from '@uiw/react-markdown-editor';
import axios from 'axios';

const ArticleEditor = () => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [categoryId, setCategoryId] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!title.trim() || !content.trim()) {
      alert('标题和内容不能为空');
      return;
    }

    setLoading(true);
    try {
      const response = await axios.post('/api/posts', {
        title,
        content,
        categoryId,
        status: 1, // 已发布
        isTop: false
      }, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });

      if (response.data.code === 200) {
        alert('文章创建成功！');
        // 跳转到文章详情页
        window.location.href = `/post/${response.data.data}`;
      }
    } catch (error) {
      if (error.response?.data?.message) {
        alert(`创建失败: ${error.response.data.message}`);
      } else {
        alert('创建失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="article-editor">
      <input
        type="text"
        placeholder="文章标题"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        maxLength={200}
      />
      
      <MDEditor
        value={content}
        onChange={setContent}
        preview="edit"
        height={600}
      />
      
      <button onClick={handleSubmit} disabled={loading}>
        {loading ? '发布中...' : '发布文章'}
      </button>
    </div>
  );
};

export default ArticleEditor;
```

#### 2. 文章详情组件（带XSS防护）

```jsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';
import DOMPurify from 'dompurify';
import marked from 'marked';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const ArticleDetail = ({ postId }) => {
  const [article, setArticle] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchArticle();
  }, [postId]);

  const fetchArticle = async () => {
    try {
      const token = localStorage.getItem('token');
      const headers = token ? { 'Authorization': `Bearer ${token}` } : {};
      
      const response = await axios.get(`/api/posts/${postId}`, { headers });
      
      if (response.data.code === 200) {
        setArticle(response.data.data);
      }
    } catch (error) {
      console.error('获取文章失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 方法1: 使用 react-markdown（推荐）
  const renderWithReactMarkdown = () => {
    if (!article) return null;
    
    return (
      <ReactMarkdown 
        remarkPlugins={[remarkGfm]}
        components={{
          code({ node, inline, className, children, ...props }) {
            return inline ? (
              <code className={className} {...props}>{children}</code>
            ) : (
              <pre className={className}>
                <code {...props}>{children}</code>
              </pre>
            );
          },
          a({ node, ...props }) {
            // 外部链接添加 rel 属性
            return <a {...props} rel="noopener noreferrer" target="_blank" />;
          }
        }}
      >
        {article.content}
      </ReactMarkdown>
    );
  };

  // 方法2: 使用 marked + DOMPurify（备选）
  const renderWithMarked = () => {
    if (!article) return null;
    
    const html = marked.parse(article.content);
    const cleanHtml = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: [
        'p', 'br', 'strong', 'em', 'u', 's', 'blockquote', 'code', 'pre', 'hr',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'ul', 'ol', 'li', 'dl', 'dt', 'dd',
        'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td'
      ],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'width', 'height', 'class'],
      ALLOWED_URI_REGEXP: /^(https?|mailto|data):/i
    });

    return <div dangerouslySetInnerHTML={{ __html: cleanHtml }} />;
  };

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!article) {
    return <div>文章不存在</div>;
  }

  return (
    <article className="article-detail">
      <h1>{article.title}</h1>
      
      <div className="article-meta">
        <span>作者: {article.authorNickname}</span>
        <span>发布时间: {new Date(article.publishTime).toLocaleString()}</span>
        <span>阅读量: {article.viewCount}</span>
        <span>字数: {article.wordCount}</span>
      </div>

      {article.coverImage && (
        <img src={article.coverImage} alt={article.title} />
      )}

      <div className="article-content">
        {renderWithReactMarkdown()}
      </div>

      <div className="article-tags">
        {article.tags?.map(tag => (
          <span key={tag.id} className="tag" style={{ backgroundColor: tag.color }}>
            {tag.name}
          </span>
        ))}
      </div>
    </article>
  );
};

export default ArticleDetail;
```

#### 3. 文章列表组件

```jsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';

const ArticleList = () => {
  const [articles, setArticles] = useState([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0
  });
  const [filters, setFilters] = useState({
    categoryId: null,
    keyword: ''
  });

  useEffect(() => {
    fetchArticles();
  }, [pagination.current, filters]);

  const fetchArticles = async () => {
    try {
      const params = {
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        ...filters
      };

      const response = await axios.get('/api/posts/public', { params });
      
      if (response.data.code === 200) {
        setArticles(response.data.data.records);
        setPagination(prev => ({
          ...prev,
          total: response.data.data.total,
          pages: response.data.data.pages
        }));
      }
    } catch (error) {
      console.error('获取文章列表失败:', error);
    }
  };

  const handleSearch = (keyword) => {
    setFilters(prev => ({ ...prev, keyword }));
    setPagination(prev => ({ ...prev, current: 1 }));
  };

  const handleCategoryFilter = (categoryId) => {
    setFilters(prev => ({ ...prev, categoryId }));
    setPagination(prev => ({ ...prev, current: 1 }));
  };

  return (
    <div className="article-list">
      {/* 搜索框 */}
      <div className="search-bar">
        <input
          type="text"
          placeholder="搜索文章..."
          value={filters.keyword}
          onChange={(e) => handleSearch(e.target.value)}
        />
      </div>

      {/* 文章列表 */}
      <div className="articles">
        {articles.map(article => (
          <article key={article.id} className="article-card">
            {article.coverImage && (
              <img src={article.coverImage} alt={article.title} />
            )}
            
            <h2>
              <Link to={`/post/${article.id}`}>
                {article.isTop && <span className="top-badge">置顶</span>}
                {article.title}
              </Link>
            </h2>
            
            <p className="summary">{article.summary}</p>
            
            <div className="meta">
              <span>作者: {article.authorNickname}</span>
              <span>阅读: {article.viewCount}</span>
              <span>字数: {article.wordCount}</span>
              <span>{new Date(article.publishTime).toLocaleDateString()}</span>
            </div>

            <div className="tags">
              {article.tags?.slice(0, 3).map(tag => (
                <span key={tag.id} className="tag">{tag.name}</span>
              ))}
            </div>
          </article>
        ))}
      </div>

      {/* 分页 */}
      <div className="pagination">
        <button 
          onClick={() => setPagination(p => ({ ...p, current: p.current - 1 }))}
          disabled={pagination.current === 1}
        >
          上一页
        </button>
        
        <span>第 {pagination.current} / {pagination.pages} 页</span>
        
        <button 
          onClick={() => setPagination(p => ({ ...p, current: p.current + 1 }))}
          disabled={pagination.current >= pagination.pages}
        >
          下一页
        </button>
      </div>
    </div>
  );
};

export default ArticleList;
```

---

### Vue 3 组件示例

```vue
<template>
  <div class="article-detail">
    <h1>{{ article.title }}</h1>
    
    <div class="article-meta">
      <span>作者: {{ article.authorNickname }}</span>
      <span>发布时间: {{ formatDate(article.publishTime) }}</span>
      <span>阅读量: {{ article.viewCount }}</span>
    </div>

    <!-- 使用 v-html + DOMPurify -->
    <div 
      class="article-content"
      v-html="sanitizedContent"
    ></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import axios from 'axios';
import DOMPurify from 'dompurify';
import marked from 'marked';

const props = defineProps({
  postId: {
    type: [String, Number],
    required: true
  }
});

const article = ref(null);

const sanitizedContent = computed(() => {
  if (!article.value) return '';
  
  const html = marked.parse(article.value.content);
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 
                   'blockquote', 'code', 'pre', 'img', 'a', 'strong', 'em'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title']
  });
});

const fetchArticle = async () => {
  try {
    const token = localStorage.getItem('token');
    const headers = token ? { 'Authorization': `Bearer ${token}` } : {};
    
    const response = await axios.get(`/api/posts/${props.postId}`, { headers });
    
    if (response.data.code === 200) {
      article.value = response.data.data;
    }
  } catch (error) {
    console.error('获取文章失败:', error);
  }
};

const formatDate = (dateStr) => {
  return new Date(dateStr).toLocaleString('zh-CN');
};

onMounted(() => {
  fetchArticle();
});
</script>
```

---

## 📊 性能优化建议

### 1. 内容缓存策略

```javascript
// 使用 localStorage 缓存文章内容
const getCachedArticle = (postId) => {
  const cached = localStorage.getItem(`article_${postId}`);
  if (cached) {
    const { content, timestamp } = JSON.parse(cached);
    // 缓存有效期 5 分钟
    if (Date.now() - timestamp < 5 * 60 * 1000) {
      return content;
    }
  }
  return null;
};

const cacheArticle = (postId, content) => {
  localStorage.setItem(`article_${postId}`, JSON.stringify({
    content,
    timestamp: Date.now()
  }));
};
```

### 2. 懒加载文章列表

```javascript
// 无限滚动加载
const loadMoreArticles = async () => {
  if (loading || !hasMore) return;
  
  setLoading(true);
  const response = await axios.get('/api/posts/public', {
    params: {
      pageNum: currentPage + 1,
      pageSize: 20
    }
  });
  
  setArticles(prev => [...prev, ...response.data.data.records]);
  setCurrentPage(prev => prev + 1);
  setHasMore(response.data.data.current < response.data.data.pages);
  setLoading(false);
};
```

### 3. 图片懒加载

```jsx
<img 
  src={article.coverImage} 
  alt={article.title}
  loading="lazy"
  decoding="async"
/>
```

---

## 🐛 常见问题

### Q1: 为什么搜索不到文章内容？

**A**: 由于文章内容存储在MinIO中，数据库级别的全文搜索已禁用。

**解决方案**:
1. 使用标题和摘要搜索（当前实现）
2. 集成Elasticsearch实现全文搜索
3. 定期同步内容到搜索引擎

### Q2: XSS过滤太严格怎么办？

**A**: 可以调整`PostServiceImpl`中的`POST_CONTENT_SAFELIST`配置，添加更多允许的标签和属性。

### Q3: 如何迁移历史文章？

**A**: 调用管理员接口：
```bash
curl -X POST http://localhost:8080/api/posts/admin/migrate-content \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Q4: MinIO连接失败怎么办？

**A**: 检查配置：
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: Leoxiao
  secret-key: your-password-here
  bucket-name: myblog
```

确保MinIO服务正常运行且bucket已创建。

---

## 📝 更新日志

### v1.0 (2026-05-21)
- ✨ 初始版本：文章对象存储重构
- 🔒 三层XSS防护机制
- 📦 MinIO集成
- 📄 完整API文档和前端示例

---

**文档版本**: 1.0  
**最后更新**: 2026-05-21  
**维护者**: Leo
