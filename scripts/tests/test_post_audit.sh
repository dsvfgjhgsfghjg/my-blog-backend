#!/bin/bash

# 文章审计系统测试脚本

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "文章审计系统功能测试"
echo "=========================================="
echo ""

# 1. 登录获取admin token
echo "1. 登录获取admin token..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/users/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    echo "❌ 登录失败"
    exit 1
fi
echo "✅ Admin Token获取成功"
echo ""

# 2. 创建文章（应该自动进入待审核状态）
echo "2. 创建文章（应该自动进入待审核状态 status=3）..."
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "测试审计功能文章",
    "content": "# 这是一篇测试文章\n\n用于测试文章审计系统功能。",
    "categoryId": 1,
    "tagIds": [],
    "status": 1
  }')

POST_ID=$(echo $CREATE_RESPONSE | jq -r '.data')
echo "创建响应: $CREATE_RESPONSE"
echo "文章ID: $POST_ID"
echo ""

# 3. 查看文章详情（作者应该能看到待审核状态）
echo "3. 查看文章详情（作者可见待审核文章）..."
DETAIL_RESPONSE=$(curl -s "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "文章详情: $(echo $DETAIL_RESPONSE | jq '.data.status')"
echo ""

# 4. 查看公开列表（待审核文章不应该出现在公开列表中）
echo "4. 查看公开文章列表（待审核文章不应出现）..."
PUBLIC_LIST=$(curl -s "$BASE_URL/api/posts/public?pageNum=1&pageSize=10")
echo "公开列表总数: $(echo $PUBLIC_LIST | jq '.data.total')"
HAS_TEST_POST=$(echo $PUBLIC_LIST | jq ".data.records[] | select(.id == \"$POST_ID\")")
if [ -z "$HAS_TEST_POST" ]; then
    echo "✅ 待审核文章未出现在公开列表中"
else
    echo "❌ 待审核文章错误地出现在公开列表中"
fi
echo ""

# 5. 获取待审核文章列表（审计员可见）
echo "5. 获取待审核文章列表（仅审计员）..."
PENDING_LIST=$(curl -s "$BASE_URL/api/posts/audit/pending" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "待审核列表: $(echo $PENDING_LIST | jq '.data.total') 篇文章"
echo ""

# 6. 审计通过文章
echo "6. 审计通过文章..."
AUDIT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts/audit/$POST_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "auditStatus": 1,
    "auditComment": "内容符合要求，予以通过"
  }')
echo "审计响应: $AUDIT_RESPONSE"
echo ""

# 7. 再次查看文章详情（应该已发布 status=1）
echo "7. 查看文章详情（审计通过后应为已发布状态）..."
DETAIL_AFTER_AUDIT=$(curl -s "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "文章状态: $(echo $DETAIL_AFTER_AUDIT | jq '.data.status')"
echo ""

# 8. 查看公开列表（已通过的文章应该出现）
echo "8. 查看公开文章列表（已通过的文章应出现）..."
PUBLIC_LIST_AFTER=$(curl -s "$BASE_URL/api/posts/public?pageNum=1&pageSize=10")
echo "公开列表总数: $(echo $PUBLIC_LIST_AFTER | jq '.data.total')"
HAS_TEST_POST_AFTER=$(echo $PUBLIC_LIST_AFTER | jq ".data.records[] | select(.id == \"$POST_ID\")")
if [ -n "$HAS_TEST_POST_AFTER" ]; then
    echo "✅ 已通过文章正确出现在公开列表中"
else
    echo "❌ 已通过文章未出现在公开列表中"
fi
echo ""

# 9. 查看审计记录
echo "9. 查看审计记录..."
AUDIT_RECORD=$(curl -s "$BASE_URL/api/posts/audit/$POST_ID/record" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "审计记录: $(echo $AUDIT_RECORD | jq '.data')"
echo ""

# 10. 尝试重复审计（应该失败）
echo "10. 尝试重复审计（应该失败）..."
RE_AUDIT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts/audit/$POST_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "auditStatus": 1,
    "auditComment": "再次审计"
  }')
echo "重复审计响应: $(echo $RE_AUDIT_RESPONSE | jq '.message')"
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="
