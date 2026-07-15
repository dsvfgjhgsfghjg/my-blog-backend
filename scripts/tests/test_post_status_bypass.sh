#!/bin/bash

# 测试文章状态修改漏洞修复
# 验证待审核文章不能通过updatePost接口绕过审核

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "======================================"
echo "文章状态修改安全测试"
echo "======================================"
echo ""

# 登录获取Token
echo "1. 登录获取Token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败"
  exit 1
fi

echo "✅ 登录成功"
echo ""

# 创建一篇测试文章（会自动进入待审核状态）
echo "2. 创建测试文章（自动进入待审核状态）..."
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/posts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "测试文章-状态修改漏洞",
    "content": "# 测试内容\n这是一篇用于测试状态修改漏洞的文章。",
    "categoryId": 1,
    "tagIds": [],
    "status": 1
  }')

POST_ID=$(echo $CREATE_RESPONSE | grep -o '"data":[0-9]*' | grep -o '[0-9]*$')

if [ -z "$POST_ID" ]; then
  echo "❌ 创建文章失败"
  echo "响应: $CREATE_RESPONSE"
  exit 1
fi

echo "✅ 文章创建成功，ID: $POST_ID"
echo ""

# 查看文章当前状态（应该是待审核 status=3）
echo "3. 查看文章当前状态..."
DETAIL_RESPONSE=$(curl -s "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $TOKEN")

CURRENT_STATUS=$(echo $DETAIL_RESPONSE | grep -o '"status":[0-9]*' | grep -o '[0-9]*$')
echo "当前状态: $CURRENT_STATUS (3=待审核)"

if [ "$CURRENT_STATUS" != "3" ]; then
  echo "⚠️  警告：文章状态不是待审核，可能是历史文章"
fi
echo ""

# 尝试通过updatePost接口将待审核文章直接改为已发布
echo "4. 尝试通过updatePost接口将待审核文章直接改为已发布（应该被拒绝）..."
UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"测试文章-状态修改漏洞\",
    \"content\": \"# 测试内容\\n这是修改后的内容。\",
    \"categoryId\": 1,
    \"tagIds\": [],
    \"status\": 1
  }")

HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -n1)
BODY=$(echo "$UPDATE_RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "200" ]]; then
  echo "❌ 严重漏洞！待审核文章可以通过updatePost接口直接发布！"
  echo "   HTTP状态码: $HTTP_CODE"
  echo "   响应: $BODY"
  
  # 再次检查文章状态
  DETAIL_AFTER=$(curl -s "$BASE_URL/api/posts/$POST_ID" \
    -H "Authorization: Bearer $TOKEN")
  STATUS_AFTER=$(echo $DETAIL_AFTER | grep -o '"status":[0-9]*' | grep -o '[0-9]*$')
  echo "   文章当前状态: $STATUS_AFTER"
  
  if [ "$STATUS_AFTER" == "1" ]; then
    echo "   ❌ 确认漏洞：文章已被非法发布！"
  fi
  
  exit 1
else
  echo "✅ 安全防护生效！请求被拒绝"
  echo "   HTTP状态码: $HTTP_CODE"
  
  ERROR_MSG=$(echo $BODY | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
  echo "   错误信息: $ERROR_MSG"
  
  if echo "$ERROR_MSG" | grep -q "待审核\|审核流程"; then
    echo "   ✅ 错误提示正确"
  else
    echo "   ⚠️  错误提示不够明确"
  fi
fi
echo ""

# 验证文章状态仍然是待审核
echo "5. 验证文章状态仍然是待审核..."
DETAIL_VERIFY=$(curl -s "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $TOKEN")

STATUS_VERIFY=$(echo $DETAIL_VERIFY | grep -o '"status":[0-9]*' | grep -o '[0-9]*$')
echo "文章状态: $STATUS_VERIFY"

if [ "$STATUS_VERIFY" == "3" ]; then
  echo "✅ 文章状态未被篡改，仍然是待审核"
else
  echo "❌ 文章状态被非法修改为: $STATUS_VERIFY"
  exit 1
fi
echo ""

# 测试非管理员用户
echo "6. 使用普通用户账号测试（如果存在）..."
# 这里可以添加普通用户的测试逻辑
echo "   （跳过，需要普通用户账号）"
echo ""

echo "======================================"
echo "测试完成"
echo "======================================"
echo ""
echo "总结："
echo "- 待审核文章不能通过updatePost接口修改状态 ✅"
echo "- 必须通过专门的审核流程才能改变文章状态 ✅"
echo ""

# 清理：删除测试文章
echo "清理：删除测试文章..."
DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/posts/$POST_ID" \
  -H "Authorization: Bearer $TOKEN")
echo "删除响应: $DELETE_RESPONSE"
echo ""

exit 0
