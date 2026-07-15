#!/bin/bash
# 快速修复数据库 Secrets

GITEA_URL="http://113.31.105.30:3000"
REPO="leoxiao/backend"

echo "=== Gitea Secrets 修复脚本 ==="
echo ""
read -p "Gitea 用户名: " GITEA_USER
read -s -p "Gitea 密码: " GITEA_PASS
echo ""

# 获取 Token
TOKEN=$(curl -s -X POST "${GITEA_URL}/api/v1/users/${GITEA_USER}/tokens" \
    -H "Content-Type: application/json" \
    -d '{"name":"fix-secrets","scopes":["write:repository"]}' \
    -u "${GITEA_USER}:${GITEA_PASS}" | jq -r '.sha1')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "❌ 认证失败"
    exit 1
fi

echo "✅ Token 获取成功"
echo ""

# 修复 DB_USER
echo "修复 DB_USER..."
curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${GITEA_URL}/api/v1/repos/${REPO}/actions/secrets/DB_USER" \
    -H "Authorization: token ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name":"DB_USER","data":"Leoxiaoblog"}'
echo " ✅ DB_USER = Leoxiaoblog"

# 修复 DB_PASSWORD
echo "修复 DB_PASSWORD..."
curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${GITEA_URL}/api/v1/repos/${REPO}/actions/secrets/DB_PASSWORD" \
    -H "Authorization: token ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name":"DB_PASSWORD","data":"your-password-here"}'
echo " ✅ DB_PASSWORD = your-password-here"

# 修复 MINIO_ROOT_USER
echo "修复 MINIO_ROOT_USER..."
curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${GITEA_URL}/api/v1/repos/${REPO}/actions/secrets/MINIO_ROOT_USER" \
    -H "Authorization: token ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name":"MINIO_ROOT_USER","data":"Leoxiao"}'
echo " ✅ MINIO_ROOT_USER = Leoxiao"

# 修复 MINIO_ROOT_PASSWORD
echo "修复 MINIO_ROOT_PASSWORD..."
curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${GITEA_URL}/api/v1/repos/${REPO}/actions/secrets/MINIO_ROOT_PASSWORD" \
    -H "Authorization: token ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name":"MINIO_ROOT_PASSWORD","data":"your-password-here"}'
echo " ✅ MINIO_ROOT_PASSWORD = your-password-here"

echo ""
echo "✅ 所有 Secrets 已修复！"
echo ""
echo "现在可以重新触发 CI/CD 构建了"
