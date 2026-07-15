#!/bin/bash

# 测试 Markdown 换行符问题
# 验证文章内容中的换行符是否被正确保留

echo "=========================================="
echo "   测试 Markdown 换行符保留"
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

echo "📝 步骤 1: 登录获取 Token"
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/users/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ 登录失败"
    echo "响应：$LOGIN_RESPONSE"
    exit 1
fi

echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
echo ""

echo "📝 步骤 2: 创建包含多段落的 Markdown 文章"

# 创建一个包含明确换行符的 Markdown 内容
MARKDOWN_CONTENT='# 测试标题

这是第一段，包含一些文本。

## 二级标题

这是第二段，在另一个段落中。

- 列表项 1
- 列表项 2

```java
public class Test {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

这是最后一段。'

CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"title\": \"测试 Markdown 换行符\",
    \"content\": $(echo "$MARKDOWN_CONTENT" | jq -Rs .),
    \"categoryId\": 4,
    \"tagIds\": [],
    \"status\": 0
  }")

echo "创建响应：$CREATE_RESPONSE"

POST_ID=$(echo $CREATE_RESPONSE | jq -r '.data')

if [ "$POST_ID" = "null" ] || [ -z "$POST_ID" ]; then
    echo "❌ 创建文章失败"
    exit 1
fi

echo "✅ 文章创建成功，ID: $POST_ID"
echo ""

echo "📝 步骤 3: 查询文章详情，检查换行符"
DETAIL_RESPONSE=$(curl -s -X GET "${BASE_URL}/posts/${POST_ID}" \
  -H "Authorization: Bearer $TOKEN")

CONTENT=$(echo $DETAIL_RESPONSE | jq -r '.data.content')

echo "📄 文章内容:"
echo "$CONTENT"
echo ""

echo "🔍 检查换行符:"

# 统计换行符数量
NEWLINE_COUNT=$(echo "$CONTENT" | grep -c "^$")
echo "检测到的换行符数量：$NEWLINE_COUNT"

# 检查是否包含 Markdown 段落分隔
if echo "$CONTENT" | grep -q "^$"; then
    echo "✅ 检测到段落分隔（空行）"
else
    echo "❌ 未检测到段落分隔（空行）- 换行符可能丢失"
fi

# 检查是否包含标题
if echo "$CONTENT" | grep -q "^# "; then
    echo "✅ 检测到一级标题"
else
    echo "❌ 未检测到一级标题"
fi

if echo "$CONTENT" | grep -q "^## "; then
    echo "✅ 检测到二级标题"
else
    echo "❌ 未检测到二级标题"
fi

# 检查是否包含列表
if echo "$CONTENT" | grep -q "^- "; then
    echo "✅ 检测到列表项"
else
    echo "❌ 未检测到列表项"
fi

# 检查是否包含代码块
if echo "$CONTENT" | grep -q '```'; then
    echo "✅ 检测到代码块"
else
    echo "❌ 未检测到代码块"
fi

echo ""
echo "📊 完整内容（带换行符显示）:"
echo "$CONTENT" | cat -A | head -30
echo ""

echo "📝 步骤 4: 清理测试数据"
DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/posts/${POST_ID}" \
  -H "Authorization: Bearer $TOKEN")

echo "删除响应：$DELETE_RESPONSE"
echo ""

echo "=========================================="
echo "   测试完成！"
echo "=========================================="
