#!/bin/bash

# 收藏功能API测试脚本

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "======================================"
echo "收藏功能 API 测试"
echo "======================================"
echo ""

# 1. 登录获取Token
echo "1. 登录获取Token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败，无法获取Token"
  echo "响应: $LOGIN_RESPONSE"
  exit 1
fi

echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
echo ""

# 2. 获取一篇文章的ID（使用公开文章列表）
echo "2. 获取文章列表..."
POSTS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/posts/public?pageNum=1&pageSize=1")
POST_ID=$(echo $POSTS_RESPONSE | grep -o '"id":"[0-9]*"' | head -1 | cut -d'"' -f4)

if [ -z "$POST_ID" ]; then
  echo "❌ 无法获取文章ID"
  exit 1
fi

echo "✅ 获取到文章ID: $POST_ID"
echo ""

# 3. 检查收藏状态（初始应该是未收藏）
echo "3. 检查收藏状态（初始状态）..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/posts/$POST_ID/favorite/status" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $STATUS_RESPONSE"
echo ""

# 4. 收藏文章
echo "4. 收藏文章..."
FAVORITE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts/$POST_ID/favorite" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $FAVORITE_RESPONSE"
echo ""

# 5. 再次检查收藏状态（应该已收藏）
echo "5. 检查收藏状态（收藏后）..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/posts/$POST_ID/favorite/status" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $STATUS_RESPONSE"
echo ""

# 6. 获取用户的收藏列表
echo "6. 获取用户收藏列表..."
FAVORITES_RESPONSE=$(curl -s -X GET "$BASE_URL/api/posts/favorites?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $FAVORITES_RESPONSE"
echo ""

# 7. 尝试重复收藏（应该失败）
echo "7. 尝试重复收藏（预期失败）..."
DUPLICATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts/$POST_ID/favorite" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $DUPLICATE_RESPONSE"
echo ""

# 8. 取消收藏
echo "8. 取消收藏..."
UNFAVORITE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/posts/$POST_ID/favorite" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $UNFAVORITE_RESPONSE"
echo ""

# 9. 再次检查收藏状态（应该未收藏）
echo "9. 检查收藏状态（取消收藏后）..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/posts/$POST_ID/favorite/status" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $STATUS_RESPONSE"
echo ""

# 10. 尝试取消未收藏的文章（应该失败）
echo "10. 尝试取消未收藏的文章（预期失败）..."
UNFAVORITE_AGAIN_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/posts/$POST_ID/favorite" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $UNFAVORITE_AGAIN_RESPONSE"
echo ""

echo "======================================"
echo "测试完成！"
echo "======================================"
