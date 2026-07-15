#!/bin/bash

# 测试拥有 manage_admins 能力的普通管理员是否可以修改用户角色

echo "=== 测试拥有 manage_admins 能力的普通管理员修改用户角色 ==="
echo ""

BASE_URL="http://localhost:8080"

# 1. 登录超级管理员
echo "1. 登录超级管理员..."
SUPER_ADMIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')

echo "响应: $SUPER_ADMIN_RESPONSE"
SUPER_ADMIN_TOKEN=$(echo $SUPER_ADMIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "Super Admin Token: $SUPER_ADMIN_TOKEN"
echo ""

# 2. 创建一个普通用户
echo "2. 创建普通用户 testuser1..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser1","password":"Test@123456","nickname":"测试用户1"}')

echo "响应: $REGISTER_RESPONSE"
echo ""

# 3. 将普通用户设置为管理员（使用超级管理员）
echo "3. 将 testuser1 设置为管理员..."
TARGET_USER_ID=$(echo $REGISTER_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "目标用户ID: $TARGET_USER_ID"

SET_ADMIN_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/users/admin/$TARGET_USER_ID/role?isAdmin=true" \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN")

echo "响应: $SET_ADMIN_RESPONSE"
echo ""

# 4. 为新管理员分配 manage_admins 能力
echo "4. 为 testuser1 分配 manage_admins 能力..."
ABILITY_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/admin/$TARGET_USER_ID/abilities" \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '[{"name":"manage_admins","description":"管理管理员","sortOrder":1}]')

echo "响应: $ABILITY_RESPONSE"
echo ""

# 5. 用新管理员登录
echo "5. 用 testuser1 登录..."
ADMIN_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser1","password":"Test@123456"}')

echo "响应: $ADMIN_LOGIN_RESPONSE"
ADMIN_TOKEN=$(echo $ADMIN_LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "Admin Token: $ADMIN_TOKEN"
echo ""

# 6. 创建另一个普通用户
echo "6. 创建普通用户 testuser2..."
REGISTER_RESPONSE2=$(curl -s -X POST "$BASE_URL/api/users/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser2","password":"Test@123456","nickname":"测试用户2"}')

echo "响应: $REGISTER_RESPONSE2"
TARGET_USER_ID_2=$(echo $REGISTER_RESPONSE2 | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "目标用户ID: $TARGET_USER_ID_2"
echo ""

# 7. 用拥有 manage_admins 能力的管理员将 testuser2 设置为管理员
echo "7. 用 testuser1 (拥有 manage_admins 能力) 将 testuser2 设置为管理员..."
UPDATE_ROLE_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/users/admin/$TARGET_USER_ID_2/role?isAdmin=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

echo "响应: $UPDATE_ROLE_RESPONSE"
echo ""

# 检查是否成功
if echo "$UPDATE_ROLE_RESPONSE" | grep -q '"code":200'; then
    echo "✅ 测试通过：拥有 manage_admins 能力的管理员可以修改用户角色"
else
    echo "❌ 测试失败：拥有 manage_admins 能力的管理员无法修改用户角色"
    echo "错误信息: $UPDATE_ROLE_RESPONSE"
fi

echo ""
echo "=== 测试完成 ==="
