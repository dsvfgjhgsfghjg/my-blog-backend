#!/bin/bash

# 用户粒子特效偏好 API 测试脚本

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "======================================"
echo "用户粒子特效偏好 API 测试"
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

# 2. 获取当前粒子特效设置（首次应该返回默认值stardust）
echo "2. 获取当前粒子特效设置（预期返回默认值stardust）..."
GET_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $GET_RESPONSE"
echo ""

# 3. 设置粒子特效为 sakura
echo "3. 设置粒子特效为 sakura..."
SET_RESPONSE=$(curl -s -X POST "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"particleMode":"sakura"}')

echo "响应: $SET_RESPONSE"
echo ""

# 4. 再次获取，验证是否更新成功
echo "4. 再次获取粒子特效设置（预期返回sakura）..."
GET_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $GET_RESPONSE"
echo ""

# 5. 更新为 ice
echo "5. 更新粒子特效为 ice..."
SET_RESPONSE=$(curl -s -X POST "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"particleMode":"ice"}')

echo "响应: $SET_RESPONSE"
echo ""

# 6. 验证更新
echo "6. 验证更新后的值（预期返回ice）..."
GET_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $GET_RESPONSE"
echo ""

# 7. 测试无效的模式值（应该失败）
echo "7. 测试无效的模式值 invalid（预期失败）..."
INVALID_RESPONSE=$(curl -s -X POST "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"particleMode":"invalid"}')

echo "响应: $INVALID_RESPONSE"
echo ""

# 8. 删除粒子特效设置
echo "8. 删除粒子特效设置（恢复默认）..."
DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $DELETE_RESPONSE"
echo ""

# 9. 删除后获取，应该返回默认值
echo "9. 删除后获取粒子特效（预期返回默认值stardust）..."
GET_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $GET_RESPONSE"
echo ""

# 10. 尝试删除不存在的记录（应该失败）
echo "10. 尝试再次删除（预期失败，记录不存在）..."
DELETE_AGAIN_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $DELETE_AGAIN_RESPONSE"
echo ""

echo "======================================"
echo "测试完成！"
echo "======================================"
