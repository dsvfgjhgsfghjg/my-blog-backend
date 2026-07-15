#!/bin/bash

# 测试文章内容存储格式
# 验证 Markdown 内容是否被正确存储，而不是转换为 HTML

echo "=========================================="
echo "   测试文章内容存储格式"
echo "=========================================="
echo ""

# 从 .env 文件加载配置
if [ -f config/.env ]; then
    source config/.env
else
    echo "❌ 找不到 config/.env 文件"
    exit 1
fi

BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"

echo "📝 步骤1: 登录获取 Token"
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ 登录失败"
    echo "响应: $LOGIN_RESPONSE"
    exit 1
fi

echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
echo ""

echo "📝 步骤2: 创建一篇包含 Markdown 格式的文章"
CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "测试 Markdown 存储格式",
    "content": "# 一级标题\n\n## 二级标题\n\n这是**粗体**和*斜体*文本。\n\n- 列表项1\n- 列表项2\n\n```java\npublic class Test {}\n```\n\n[链接文本](https://example.com)",
    "categoryId": 4,
    "tagIds": [],
    "status": 0
  }')

POST_ID=$(echo $CREATE_RESPONSE | jq -r '.data')

if [ "$POST_ID" = "null" ] || [ -z "$POST_ID" ]; then
    echo "❌ 创建文章失败"
    echo "响应: $CREATE_RESPONSE"
    exit 1
fi

echo "✅ 文章创建成功，ID: $POST_ID"
echo ""

echo "📝 步骤3: 查询文章详情，检查内容格式"
DETAIL_RESPONSE=$(curl -s -X GET "${BASE_URL}/posts/${POST_ID}" \
  -H "Authorization: Bearer $TOKEN")

CONTENT=$(echo $DETAIL_RESPONSE | jq -r '.data.content')

echo "📄 文章内容预览（前500字符）:"
echo "$CONTENT" | head -c 500
echo ""
echo ""

echo "🔍 检查结果:"

# 检查是否包含 Markdown 语法
if echo "$CONTENT" | grep -q "^# "; then
    echo "✅ 检测到 Markdown 一级标题 (# )"
else
    echo "❌ 未检测到 Markdown 一级标题"
fi

if echo "$CONTENT" | grep -q "^## "; then
    echo "✅ 检测到 Markdown 二级标题 (##)"
else
    echo "❌ 未检测到 Markdown 二级标题"
fi

if echo "$CONTENT" | grep -q "\*\*"; then
    echo "✅ 检测到 Markdown 粗体 (**)"
else
    echo "❌ 未检测到 Markdown 粗体"
fi

if echo "$CONTENT" | grep -q "^\- "; then
    echo "✅ 检测到 Markdown 列表 (-)"
else
    echo "❌ 未检测到 Markdown 列表"
fi

if echo "$CONTENT" | grep -q '```'; then
    echo "✅ 检测到 Markdown 代码块 (\`\`\`)"
else
    echo "❌ 未检测到 Markdown 代码块"
fi

# 检查是否被转换为 HTML
if echo "$CONTENT" | grep -q "<h1>"; then
    echo "⚠️  警告：检测到 HTML h1 标签（Markdown 可能被转换为 HTML）"
else
    echo "✅ 未检测到 HTML h1 标签"
fi

if echo "$CONTENT" | grep -q "<h2>"; then
    echo "⚠️  警告：检测到 HTML h2 标签"
else
    echo "✅ 未检测到 HTML h2 标签"
fi

if echo "$CONTENT" | grep -q "<strong>"; then
    echo "⚠️  警告：检测到 HTML strong 标签"
else
    echo "✅ 未检测到 HTML strong 标签"
fi

echo ""
echo "📊 完整内容:"
echo "$CONTENT"
echo ""

echo "📝 步骤4: 清理测试数据"
DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/posts/${POST_ID}" \
  -H "Authorization: Bearer $TOKEN")

echo "删除响应: $DELETE_RESPONSE"
echo ""

echo "=========================================="
echo "   测试完成！"
echo "=========================================="
