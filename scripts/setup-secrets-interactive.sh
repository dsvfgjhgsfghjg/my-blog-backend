#!/bin/bash
# 批量配置 Gitea Actions Secrets

GITEA_URL="http://113.31.105.30:3000"
REPO_OWNER="leoxiao"
REPO_NAME="backend"

echo "=== Gitea Actions Secrets 配置脚本 ==="
echo ""
echo "请提供以下信息："
read -p "Gitea 用户名: " GITEA_USER
read -s -p "Gitea 密码: " GITEA_PASSWORD
echo ""

# 获取 API Token
echo "正在获取 API Token..."
TOKEN=$(curl -s -X POST "${GITEA_URL}/api/v1/users/${GITEA_USER}/tokens" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"ci-cd-token-$(date +%s)\",\"scopes\":[\"write:repository\",\"read:user\"]}" \
    -u "${GITEA_USER}:${GITEA_PASSWORD}" | jq -r '.sha1')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "❌ 获取 Token 失败，请检查用户名和密码"
    exit 1
fi

echo "✅ Token 获取成功"
echo ""

# 设置 Secret 函数
set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    if [ -z "$secret_value" ]; then
        echo "⚠️  $secret_name 为空，跳过"
        return
    fi
    
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
        "${GITEA_URL}/api/v1/repos/${REPO_OWNER}/${REPO_NAME}/actions/secrets/${secret_name}" \
        -H "Authorization: token ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"${secret_name}\",\"data\":\"${secret_value}\"}")
    
    if [ "$RESPONSE" = "204" ] || [ "$RESPONSE" = "201" ]; then
        echo "✅ $secret_name 配置成功"
    else
        echo "❌ $secret_name 配置失败 (HTTP $RESPONSE)"
    fi
}

# Base64 编码函数
base64_encode() {
    echo -n "$1" | base64 -w 0
}

echo "=== 开始配置 Secrets ==="
echo ""

# 1. DEPLOY_KEY (SSH 私钥)
read -p "SSH 私钥路径 (默认: ~/.ssh/gitea_deploy_key): " SSH_KEY_PATH
SSH_KEY_PATH=${SSH_KEY_PATH:-~/.ssh/gitea_deploy_key}
SSH_KEY_PATH=$(eval echo $SSH_KEY_PATH)

if [ -f "$SSH_KEY_PATH" ]; then
    DEPLOY_KEY=$(base64_encode "$(cat $SSH_KEY_PATH)")
    set_secret "DEPLOY_KEY" "$DEPLOY_KEY"
else
    echo "⚠️  SSH 私钥文件不存在: $SSH_KEY_PATH"
    read -p "是否手动输入 SSH 私钥? (y/n): " INPUT_KEY
    if [ "$INPUT_KEY" = "y" ]; then
        echo "请粘贴 SSH 私钥内容（以 -----END 结尾后按 Ctrl+D）:"
        SSH_KEY_CONTENT=$(cat)
        DEPLOY_KEY=$(base64_encode "$SSH_KEY_CONTENT")
        set_secret "DEPLOY_KEY" "$DEPLOY_KEY"
    fi
fi

# 2. 部署服务器信息
read -p "部署服务器 IP (默认: 113.31.105.30): " DEPLOY_HOST
DEPLOY_HOST=${DEPLOY_HOST:-113.31.105.30}
set_secret "DEPLOY_HOST" "$DEPLOY_HOST"

read -p "部署用户 (默认: ubuntu): " DEPLOY_USER
DEPLOY_USER=${DEPLOY_USER:-ubuntu}
set_secret "DEPLOY_USER" "$DEPLOY_USER"

read -p "部署路径 (默认: /home/ubuntu/myblog-deploy): " DEPLOY_PATH
DEPLOY_PATH=${DEPLOY_PATH:-/home/ubuntu/myblog-deploy}
set_secret "DEPLOY_PATH" "$DEPLOY_PATH"

# 3. 数据库配置
read -p "数据库名 (默认: myblog): " DB_NAME
DB_NAME=${DB_NAME:-myblog}
set_secret "DB_NAME" "$DB_NAME"

read -p "数据库用户 (默认: Leoxiaoblog): " DB_USER
DB_USER=${DB_USER:-Leoxiaoblog}
set_secret "DB_USER" "$DB_USER"

read -s -p "数据库密码: " DB_PASSWORD
echo ""
set_secret "DB_PASSWORD" "$DB_PASSWORD"

# 4. MinIO 配置
read -p "MinIO Root User (默认: minioadmin): " MINIO_ROOT_USER
MINIO_ROOT_USER=${MINIO_ROOT_USER:-minioadmin}
set_secret "MINIO_ROOT_USER" "$MINIO_ROOT_USER"

read -s -p "MinIO Root Password: " MINIO_ROOT_PASSWORD
echo ""
set_secret "MINIO_ROOT_PASSWORD" "$MINIO_ROOT_PASSWORD"

read -p "MinIO Bucket Name (默认: myblog): " MINIO_BUCKET_NAME
MINIO_BUCKET_NAME=${MINIO_BUCKET_NAME:-myblog}
set_secret "MINIO_BUCKET_NAME" "$MINIO_BUCKET_NAME"

# 5. JWT Secret (自动生成)
JWT_SECRET=$(openssl rand -hex 32)
set_secret "JWT_SECRET" "$JWT_SECRET"

echo ""
echo "=== 配置完成 ==="
echo ""
echo "请访问以下页面验证配置："
echo "${GITEA_URL}/${REPO_OWNER}/${REPO_NAME}/settings/actions/secrets"
echo ""
echo "⚠️  重要提示："
echo "1. MINIO_ROOT_PASSWORD 不能为空"
echo "2. DEPLOY_KEY 必须是 Base64 编码的 SSH 私钥"
echo "3. 所有 Secrets 配置完成后，重新触发 CI/CD"
