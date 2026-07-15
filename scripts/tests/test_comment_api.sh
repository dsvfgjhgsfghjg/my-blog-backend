#!/bin/bash

###############################################################################
# 评论系统 API 测试脚本
# 用途：测试评论系统的各个接口
###############################################################################

BASE_URL="http://localhost:8080/api"
TOKEN=""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  评论系统 API 测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查服务是否运行
echo -e "${YELLOW}步骤 1: 检查服务是否运行...${NC}"
if curl -s http://localhost:8080 > /dev/null; then
    echo -e "${GREEN}✓ 服务正在运行${NC}"
else
    echo -e "${RED}✗ 服务未运行，请先启动应用${NC}"
    exit 1
fi

# 登录获取Token
echo ""
echo -e "${YELLOW}步骤 2: 登录获取Token...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/users/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}✗ 登录失败，请检查用户名密码${NC}"
    echo "响应: $LOGIN_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ 登录成功${NC}"
echo ""

# 测试1：获取文章列表（找到一篇文章ID）
echo -e "${YELLOW}测试 1: 获取文章列表...${NC}"
POSTS_RESPONSE=$(curl -s "$BASE_URL/posts/public?pageNum=1&pageSize=1")
POST_ID=$(echo $POSTS_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$POST_ID" ]; then
    echo -e "${RED}✗ 没有找到文章${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 找到文章 ID: $POST_ID${NC}"
echo ""

# 测试2：发表评论
echo -e "${YELLOW}测试 2: 发表评论...${NC}"
CREATE_COMMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/comments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"postId\": $POST_ID,
    \"content\": \"这是一篇很好的文章！测试评论功能。\"
  }")

echo -e "${BLUE}响应:${NC}"
echo $CREATE_COMMENT_RESPONSE | python3 -m json.tool 2>/dev/null || echo $CREATE_COMMENT_RESPONSE

COMMENT_ID=$(echo $CREATE_COMMENT_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -n "$COMMENT_ID" ]; then
    echo -e "${GREEN}✓ 评论成功，评论ID: $COMMENT_ID${NC}"
else
    echo -e "${RED}✗ 评论失败${NC}"
fi
echo ""

# 测试3：获取文章评论列表
echo -e "${YELLOW}测试 3: 获取文章评论列表...${NC}"
GET_COMMENTS_RESPONSE=$(curl -s "$BASE_URL/comments/post?postId=$POST_ID&pageNum=1&pageSize=10")

echo -e "${BLUE}响应:${NC}"
echo $GET_COMMENTS_RESPONSE | python3 -m json.tool 2>/dev/null || echo $GET_COMMENTS_RESPONSE
echo ""

# 测试4：回复评论
if [ -n "$COMMENT_ID" ]; then
    echo -e "${YELLOW}测试 4: 回复评论...${NC}"
    REPLY_RESPONSE=$(curl -s -X POST "$BASE_URL/comments" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"postId\": $POST_ID,
        \"parentId\": $COMMENT_ID,
        \"content\": \"这是回复测试\"
      }")
    
    echo -e "${BLUE}响应:${NC}"
    echo $REPLY_RESPONSE | python3 -m json.tool 2>/dev/null || echo $REPLY_RESPONSE
    
    REPLY_ID=$(echo $REPLY_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ -n "$REPLY_ID" ]; then
        echo -e "${GREEN}✓ 回复成功，回复ID: $REPLY_ID${NC}"
    else
        echo -e "${RED}✗ 回复失败${NC}"
    fi
    echo ""
fi

# 测试5：获取我的评论
echo -e "${YELLOW}测试 5: 获取我的评论...${NC}"
MY_COMMENTS_RESPONSE=$(curl -s "$BASE_URL/comments/my?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN")

echo -e "${BLUE}响应:${NC}"
echo $MY_COMMENTS_RESPONSE | python3 -m json.tool 2>/dev/null || echo $MY_COMMENTS_RESPONSE
echo ""

# 测试6：删除评论
if [ -n "$REPLY_ID" ]; then
    echo -e "${YELLOW}测试 6: 删除评论...${NC}"
    DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/comments/$REPLY_ID" \
      -H "Authorization: Bearer $TOKEN")
    
    echo -e "${BLUE}响应:${NC}"
    echo $DELETE_RESPONSE | python3 -m json.tool 2>/dev/null || echo $DELETE_RESPONSE
    echo ""
fi

# 总结
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  测试完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}测试总结:${NC}"
echo -e "  ✓ 发表评论"
echo -e "  ✓ 获取评论列表"
echo -e "  ✓ 回复评论"
echo -e "  ✓ 获取我的评论"
echo -e "  ✓ 删除评论"
echo ""
echo -e "${BLUE}查看详细文档:${NC}"
echo -e "  cat COMMENT_API.md"
echo ""
