#!/bin/bash

# 粒子特效 XSS 注入攻击测试脚本
# 验证后端是否能正确拒绝恶意输入

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "======================================"
echo "粒子特效 XSS 注入攻击测试"
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

# 测试用例数组
declare -a ATTACK_VECTORS=(
  '<script>alert("XSS")</script>'
  '"><img src=x onerror=alert(1)>'
  "javascript:alert('XSS')"
  '<svg onload=alert(1)>'
  '../../../etc/passwd'
  '"; DROP TABLE user_preferences; --'
  '<iframe src="javascript:alert(1)"></iframe>'
  'onmouseover=alert(1)'
  '\x3cscript\x3ealert(1)\x3c/script\x3e'
  '%3Cscript%3Ealert(1)%3C/script%3E'
)

echo "2. 开始 XSS 注入攻击测试..."
echo ""

FAILED_COUNT=0
PASSED_COUNT=0

for i in "${!ATTACK_VECTORS[@]}"; do
  VECTOR="${ATTACK_VECTORS[$i]}"
  TEST_NUM=$((i + 1))
  
  echo "测试 $TEST_NUM: ${VECTOR:0:50}..."
  
  # 尝试注入
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user/preferences/particle-mode" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"particleMode\":\"$VECTOR\"}")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  # 检查是否被拒绝（应该返回 400 或 500）
  if [[ "$HTTP_CODE" == "400" || "$HTTP_CODE" == "500" ]]; then
    if echo "$BODY" | grep -q "无效\|Invalid\|error"; then
      echo "  ✅ 已阻止（HTTP $HTTP_CODE）"
      PASSED_COUNT=$((PASSED_COUNT + 1))
    else
      echo "  ⚠️  返回错误码但消息不明确（HTTP $HTTP_CODE）"
      PASSED_COUNT=$((PASSED_COUNT + 1))
    fi
  elif [[ "$HTTP_CODE" == "200" ]]; then
    echo "  ❌ 严重漏洞！注入成功（HTTP $HTTP_CODE）"
    echo "     响应: $BODY"
    FAILED_COUNT=$((FAILED_COUNT + 1))
  else
    echo "  ⚠️  未知响应（HTTP $HTTP_CODE）"
    FAILED_COUNT=$((FAILED_COUNT + 1))
  fi
  
  echo ""
done

echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "总测试数: ${#ATTACK_VECTORS[@]}"
echo "✅ 通过: $PASSED_COUNT"
echo "❌ 失败: $FAILED_COUNT"
echo ""

if [ $FAILED_COUNT -eq 0 ]; then
  echo "🎉 所有 XSS 注入攻击均被成功阻止！"
  echo "后端白名单机制工作正常。"
  exit 0
else
  echo "⚠️  发现安全漏洞！需要立即修复。"
  exit 1
fi
