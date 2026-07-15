#!/bin/bash

# 测试霜枫之思粒子特效 API

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "======================================"
echo "测试霜枫之思（frostMaple）粒子特效"
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

echo "✅ 登录成功"
echo ""

# 2. 设置粒子特效为 frostMaple（霜枫之思）
echo "2. 设置粒子特效为 frostMaple（霜枫之思）..."
SET_RESPONSE=$(curl -s -X POST "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"particleMode":"frostMaple"}')

echo "响应: $SET_RESPONSE"
echo ""

# 检查是否成功
if echo "$SET_RESPONSE" | grep -q '"code":200'; then
  echo "✅ 设置成功"
else
  echo "❌ 设置失败"
  exit 1
fi
echo ""

# 3. 获取当前粒子特效设置（验证是否设置为 frostMaple）
echo "3. 获取当前粒子特效设置（预期返回 frostMaple）..."
GET_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $GET_RESPONSE"
echo ""

# 检查返回值
if echo "$GET_RESPONSE" | grep -q '"particleMode":"frostMaple"'; then
  echo "✅ 验证成功：粒子特效已设置为 frostMaple"
else
  echo "❌ 验证失败：粒子特效未正确设置"
  exit 1
fi
echo ""

# 4. 测试无效值（应该被拒绝）
echo "4. 测试无效值（应该被拒绝）..."
INVALID_RESPONSE=$(curl -s -X POST "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"particleMode":"invalidValue"}')

echo "响应: $INVALID_RESPONSE"
echo ""

if echo "$INVALID_RESPONSE" | grep -q '"code":400\|"无效的粒子模式"'; then
  echo "✅ 验证成功：无效值被正确拒绝"
else
  echo "❌ 验证失败：无效值未被拒绝"
  exit 1
fi
echo ""

# 5. 恢复默认设置
echo "5. 恢复默认设置..."
DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/user/preferences/particle-mode" \
  -H "Authorization: Bearer $TOKEN")

echo "响应: $DELETE_RESPONSE"
echo ""

if echo "$DELETE_RESPONSE" | grep -q '"code":200'; then
  echo "✅ 恢复默认设置成功"
else
  echo "❌ 恢复默认设置失败"
  exit 1
fi
echo ""

echo "======================================"
echo "✅ 所有测试通过！"
echo "霜枫之思（frostMaple）粒子特效已成功添加"
echo "======================================"
