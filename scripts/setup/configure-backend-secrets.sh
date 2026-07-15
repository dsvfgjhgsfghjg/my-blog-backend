#!/bin/bash

# MyBlog 后端 Secret 配置助手脚本
# 此脚本帮助你生成和验证所需的 Secrets

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MyBlog 后端 Secret 配置助手${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查参数
if [ $# -eq 0 ]; then
    echo -e "${YELLOW}用法:${NC}"
    echo "  $0 <server_ip> <ssh_user> [deploy_path]"
    echo ""
    echo "示例:"
    echo "  $0 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy"
    echo ""
    exit 1
fi

SERVER_IP=$1
SSH_USER=${2:-ubuntu}
DEPLOY_PATH=${3:-/home/ubuntu/myblog-deploy}

echo -e "${GREEN}配置信息:${NC}"
echo "  服务器 IP: $SERVER_IP"
echo "  SSH 用户: $SSH_USER"
echo "  部署路径: $DEPLOY_PATH"
echo ""

# 步骤 1: 检查 SSH 密钥
echo -e "${YELLOW}[1/6] 检查 SSH 密钥...${NC}"

# 首先尝试使用默认的 gitea_deploy_key
SSH_KEY_FILE="$HOME/.ssh/gitea_deploy_key"

if [ ! -f "$SSH_KEY_FILE" ]; then
    echo -e "${YELLOW}未找到默认密钥 ~/.ssh/gitea_deploy_key${NC}"
    echo ""
    echo -e "${YELLOW}请选择:${NC}"
    echo "  1. 使用现有的 SSH 密钥（推荐）"
    echo "  2. 生成新的密钥对"
    echo "  3. 指定密钥路径"
    echo ""
    read -p "请输入选项 (1/2/3): " choice
    
    case $choice in
        1)
            # 列出常见的 SSH 密钥
            echo ""
            echo -e "${YELLOW}找到以下 SSH 密钥:${NC}"
            ls -1 ~/.ssh/id_* 2>/dev/null | grep -v ".pub$" | while read key; do
                echo "  - $key"
            done
            echo ""
            read -p "请输入密钥完整路径: " custom_key
            if [ -f "$custom_key" ]; then
                SSH_KEY_FILE="$custom_key"
                echo -e "${GREEN}✓ 使用密钥: $SSH_KEY_FILE${NC}"
            else
                echo -e "${RED}✗ 密钥文件不存在${NC}"
                exit 1
            fi
            ;;
        2)
            ssh-keygen -t ed25519 -C "gitea_deploy_$(date +%Y%m%d)" -f "$SSH_KEY_FILE" -N ""
            echo -e "${GREEN}✓ SSH 密钥已生成: $SSH_KEY_FILE${NC}"
            ;;
        3)
            read -p "请输入密钥完整路径: " custom_key
            if [ -f "$custom_key" ]; then
                SSH_KEY_FILE="$custom_key"
                echo -e "${GREEN}✓ 使用密钥: $SSH_KEY_FILE${NC}"
            else
                echo -e "${RED}✗ 密钥文件不存在${NC}"
                exit 1
            fi
            ;;
        *)
            echo -e "${RED}✗ 无效选项${NC}"
            exit 1
            ;;
    esac
else
    echo -e "${GREEN}✓ 找到默认密钥: $SSH_KEY_FILE${NC}"
fi

# 步骤 2: 测试 SSH 连接
echo -e "${YELLOW}[2/6] 测试 SSH 连接...${NC}"
if ssh -i "$SSH_KEY_FILE" -o ConnectTimeout=5 -o StrictHostKeyChecking=no "$SSH_USER@$SERVER_IP" echo "SSH OK" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ SSH 连接成功${NC}"
else
    echo -e "${YELLOW}SSH 连接失败，是否添加公钥到服务器？(y/n)${NC}"
    read -r response
    if [[ "$response" =~ ^[Yy]$ ]]; then
        ssh-copy-id -i "$SSH_KEY_FILE.pub" "$SSH_USER@$SERVER_IP"
        echo -e "${GREEN}✓ 公钥已添加到服务器${NC}"
    else
        echo -e "${RED}✗ 无法继续，请手动配置 SSH${NC}"
        exit 1
    fi
fi

# 步骤 3: 检查 Docker 环境
echo -e "${YELLOW}[3/6] 检查服务器 Docker 环境...${NC}"
if ssh -i "$SSH_KEY_FILE" "$SSH_USER@$SERVER_IP" "docker --version" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Docker 已安装${NC}"
    
    # 检查网络
    if ssh -i "$SSH_KEY_FILE" "$SSH_USER@$SERVER_IP" "docker network ls | grep myblog_myblog-network" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Docker 网络 myblog_myblog-network 已存在${NC}"
    else
        echo -e "${YELLOW}⚠ Docker 网络不存在，将在部署时创建${NC}"
    fi
else
    echo -e "${RED}✗ 服务器上未安装 Docker${NC}"
    echo "请先在服务器上安装 Docker 和 Docker Compose"
    exit 1
fi

# 步骤 4: 生成强密码
echo -e "${YELLOW}[4/6] 生成安全凭证...${NC}"

# 数据库密码
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 20)
echo -e "${GREEN}生成的数据库密码:${NC} $DB_PASSWORD"

# MinIO 密码
MINIO_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 20)
echo -e "${GREEN}生成的 MinIO 密码:${NC} $MINIO_PASSWORD"

# JWT Secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo -e "${GREEN}生成的 JWT Secret:${NC} ${JWT_SECRET:0:40}..."

echo ""

# 步骤 5: Base64 编码 SSH 私钥
echo -e "${YELLOW}[5/6] 编码 SSH 私钥...${NC}"
DEPLOY_KEY_BASE64=$(base64 -w 0 "$SSH_KEY_FILE")
echo -e "${GREEN}✓ SSH 私钥已 Base64 编码${NC}"
echo -e "${YELLOW}长度: ${#DEPLOY_KEY_BASE64} 字符${NC}"
echo ""

# 步骤 6: 生成配置摘要
echo -e "${YELLOW}[6/6] 生成 Secret 配置摘要...${NC}"
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Gitea Secrets 配置清单${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo -e "${GREEN}SSH 部署相关:${NC}"
echo "  DEPLOY_HOST     = $SERVER_IP"
echo "  DEPLOY_USER     = $SSH_USER"
echo "  DEPLOY_PATH     = $DEPLOY_PATH"
echo "  DEPLOY_KEY      = (Base64 编码的私钥，见下方)"
echo ""

echo -e "${GREEN}数据库配置:${NC}"
echo "  DB_USER         = myblog_user"
echo "  DB_PASSWORD     = $DB_PASSWORD"
echo "  DB_NAME         = myblog"
echo ""

echo -e "${GREEN}MinIO 配置:${NC}"
echo "  MINIO_ROOT_USER     = admin"
echo "  MINIO_ROOT_PASSWORD = $MINIO_PASSWORD"
echo "  MINIO_BUCKET_NAME   = myblog"
echo ""

echo -e "${GREEN}JWT 配置:${NC}"
echo "  JWT_SECRET = $JWT_SECRET"
echo ""

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Base64 编码的 SSH 私钥${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "$DEPLOY_KEY_BASE64"
echo ""
echo -e "${BLUE}========================================${NC}"
echo ""

# 保存到文件
CONFIG_FILE="secrets_config_$(date +%Y%m%d_%H%M%S).txt"
cat > "$CONFIG_FILE" << EOF
MyBlog Backend Secrets Configuration
Generated: $(date)
Server: $SERVER_IP

========================================
SSH 部署相关
========================================
DEPLOY_HOST=$SERVER_IP
DEPLOY_USER=$SSH_USER
DEPLOY_PATH=$DEPLOY_PATH
DEPLOY_KEY=$(cat $SSH_KEY_FILE | base64 -w 0)

========================================
数据库配置
========================================
DB_USER=myblog_user
DB_PASSWORD=$DB_PASSWORD
DB_NAME=myblog

========================================
MinIO 配置
========================================
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=$MINIO_PASSWORD
MINIO_BUCKET_NAME=myblog

========================================
JWT 配置
========================================
JWT_SECRET=$JWT_SECRET

========================================
重要提示
========================================
1. 将此文件保存在安全的地方
2. 不要提交到版本控制系统
3. 配置完成后删除此文件或使用加密存储
4. 定期更换密码和密钥
EOF

chmod 600 "$CONFIG_FILE"
echo -e "${GREEN}✓ 配置已保存到: $CONFIG_FILE${NC}"
echo -e "${YELLOW}⚠  请妥善保管此文件，包含敏感信息！${NC}"
echo ""

# 提供下一步指导
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  下一步操作${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "1. 在 Gitea 仓库中配置 Secrets:"
echo "   - 进入 Settings → Secrets"
echo "   - 添加上述所有 11 个 Secrets"
echo "   - 特别注意：DEPLOY_KEY 使用 Base64 编码的值"
echo ""
echo "2. 验证配置:"
echo "   git push origin master"
echo ""
echo "3. 监控部署:"
echo "   - 访问 Gitea → Actions"
echo "   - 查看 Build and Deploy Backend 工作流"
echo ""
echo "4. 验证部署结果:"
echo "   ssh -i $SSH_KEY_FILE $SSH_USER@$SERVER_IP"
echo "   docker logs myblog-backend --tail 50"
echo "   curl http://localhost:18080/actuator/health"
echo ""

echo -e "${GREEN}配置助手执行完成！${NC}"
