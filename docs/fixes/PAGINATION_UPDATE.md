# 前端公开文章API分页功能更新说明

## 更新内容

已将三个分类文章列表API改为分页接口，默认每页20篇文章。

## 修改的API

### 1. 软件开发分类
- **URL**: `GET /api/posts/category/software-development`
- **参数**: 
  - `pageNum`: 页码（可选，默认1）
  - `pageSize`: 每页数量（可选，默认20）

### 2. AI分类
- **URL**: `GET /api/posts/category/ai`
- **参数**: 
  - `pageNum`: 页码（可选，默认1）
  - `pageSize`: 每页数量（可选，默认20）

### 3. 网络安全分类
- **URL**: `GET /api/posts/category/cybersecurity`
- **参数**: 
  - `pageNum`: 页码（可选，默认1）
  - `pageSize`: 每页数量（可选，默认20）

## 响应格式变化

### 修改前（返回列表）
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { /* 文章1 */ },
    { /* 文章2 */ }
  ]
}
```

### 修改后（返回分页对象）
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      { /* 文章1 */ },
      { /* 文章2 */ }
    ],
    "total": 50,    // 总记录数
    "size": 20,     // 每页数量
    "current": 1,   // 当前页码
    "pages": 3      // 总页数
  }
}
```

## 使用示例

### 获取第1页（默认20条）
```
GET /api/posts/category/software-development
```

### 获取第2页，每页10条
```
GET /api/posts/category/software-development?pageNum=2&pageSize=10
```

### JavaScript调用示例
```javascript
// 获取第1页，20条/页
fetch('/api/posts/category/software-development?pageNum=1&pageSize=20')
  .then(res => res.json())
  .then(result => {
    if (result.code === 200) {
      const articles = result.data.records;  // 文章列表
      const total = result.data.total;        // 总数
      const pages = result.data.pages;        // 总页数
      
      // 渲染文章列表
      articles.forEach(article => {
        console.log(article.title);
      });
    }
  });
```

## 修改的文件

1. **PostService.java** - 接口方法签名修改
2. **PostServiceImpl.java** - 实现分页查询逻辑
3. **PostController.java** - 添加分页参数
4. **FRONTEND_POSTS_API.md** - API文档更新
5. **test_public_posts_api.sh** - 测试脚本更新

## 注意事项

1. 向后兼容：不传参数时默认返回第1页，每页20条
2. 分页参数验证：pageNum和pageSize都有默认值，空值或无效值会使用默认值
3. 性能建议：pageSize建议不超过100，避免单次请求数据量过大
4. 排序规则：按更新时间降序排列，最新内容优先
