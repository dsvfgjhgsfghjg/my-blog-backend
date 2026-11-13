#!/bin/bash
# ============================================================
# MyBlog 交互式一键安装脚本 v2.0
# Docker Compose 部署模式，所有依赖服务均在容器内
# 日志文件: logs/install_YYYYMMDD_HHMMSS.log
# ============================================================
set -e

# ========== 全局配置 ==========
INSTALL_LOCK_FILE=".myblog_installed"
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="${SCRIPT_DIR}/logs/install_$(date '+%Y%m%d_%H%M%S').log"

# ========== 颜色定义 ==========
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
NC='\033[0m'

# ========== 工具函数 ==========
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; echo "[INFO] $1" >> "$LOG_FILE"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; echo "[WARN] $1" >> "$LOG_FILE"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; echo "[ERROR] $1" >> "$LOG_FILE"; }
log_step()  {
    echo -e "\n${CYAN}═══════════════════════════════════════════${NC}" | tee -a "$LOG_FILE"
    echo -e "${CYAN}  $1${NC}" | tee -a "$LOG_FILE"
    echo -e "${CYAN}═══════════════════════════════════════════${NC}\n" | tee -a "$LOG_FILE"
}

# 同时输出到终端和日志
log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

read_password() {
    local prompt=$1 var_name=$2 value="" confirm=""
    while true; do
        read -s -p "$(echo -e "${BLUE}${prompt}${NC}: ")" value
        echo ""
        [ -z "$value" ] && echo -e "${RED}密码不能为空${NC}" && continue
        read -s -p "$(echo -e "${BLUE}确认密码${NC}: ")" confirm
        echo ""
        [ "$value" != "$confirm" ] && echo -e "${RED}两次密码不一致${NC}" && continue
        break
    done
    eval "$var_name=\"$value\""
    echo "[INPUT] ${prompt}: ******" >> "$LOG_FILE"
}

generate_random() {
    tr -dc 'A-Za-z0-9!@#$%^&*()_+-=' < /dev/urandom 2>/dev/null | head -c 64 || openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 64
}

generate_snowflake_id() {
    echo "$(date +%s%3N)${RANDOM}${RANDOM}" | cut -c1-18
}

PASSWORD_RULES="密码要求：至少8位，包含大小写字母、数字和特殊字符"
validate_password() {
    local pass=$1 errors=""
    [ ${#pass} -lt 8 ] && errors="${errors}  ❌ 长度不足8位\n"
    ! echo "$pass" | grep -q '[a-z]' && errors="${errors}  ❌ 缺少小写字母\n"
    ! echo "$pass" | grep -q '[A-Z]' && errors="${errors}  ❌ 缺少大写字母\n"
    ! echo "$pass" | grep -q '[0-9]' && errors="${errors}  ❌ 缺少数字\n"
    ! echo "$pass" | grep -q '[^a-zA-Z0-9]' && errors="${errors}  ❌ 缺少特殊字符\n"
    if [ -n "$errors" ]; then
        echo -e "密码不符合要求:\n${errors}"
        return 1
    fi
    return 0
}

generate_bcrypt_hash() {
    local password=$1
    local hash=""
    
    # 方法1: 使用 htpasswd（apache2-utils）
    if command -v htpasswd &>/dev/null; then
        hash=$(htpasswd -bnBC 10 "" "$password" 2>/dev/null | cut -d: -f2 | head -1)
        if [ -n "$hash" ]; then
            # htpasswd 输出 $2y$，Spring Security 需要 $2a$
            echo "${hash/\$2y\$/\$2a\$}"
            return 0
        fi
    fi
    
    # 方法2: Docker 运行 Alpine + htpasswd（最可靠）
    if command -v docker &>/dev/null; then
        hash=$(docker run --rm alpine:3.18 sh -c "
            apk add -q apache2-utils >/dev/null 2>&1
            htpasswd -bnBC 10 '' '${password}' 2>/dev/null | cut -d: -f2
        " 2>/dev/null | head -1)
        if [ -n "$hash" ]; then
            echo "${hash/\$2y\$/\$2a\$}"
            return 0
        fi
    fi
    
    # 方法3: 使用 Python bcrypt
    if python3 -c "import bcrypt" 2>/dev/null; then
        hash=$(python3 -c "import bcrypt,sys;print(bcrypt.hashpw(sys.argv[1].encode(),bcrypt.gensalt(rounds=10)).decode())" "$password" 2>/dev/null | head -1)
        [ -n "$hash" ] && echo "$hash" && return 0
    fi
    
    # 全部失败
    echo ""
    return 1
}

# ========== 步骤1: 欢迎 ==========
show_banner() {
    clear
    echo -e "${CYAN}"
    echo '╔══════════════════════════════════════════════════════╗'
    echo '║              MyBlog 一键安装向导 v2.0                ║'
    echo '║         基于 Docker Compose，本地容器化部署           ║'
    echo '╚══════════════════════════════════════════════════════╝'
    echo -e "${NC}"
    echo "安装日志: ${LOG_FILE}"
    mkdir -p "$(dirname "$LOG_FILE")"
    echo "===== MyBlog 安装日志 $(date) =====" > "$LOG_FILE"
    echo "脚本路径: ${SCRIPT_DIR}" >> "$LOG_FILE"
    echo "系统信息: $(uname -a)" >> "$LOG_FILE"
    read -p "$(echo -e "${YELLOW}按 Enter 继续...${NC}")"
}

# ========== 步骤2: 环境检测 ==========
check_env() {
    log_step "[1/8] 环境检测"
    
    if ! command -v docker &>/dev/null; then
        log_error "Docker 未安装！"; exit 1
    fi
    log_info "✅ Docker: $(docker --version)"
    
    # 兼容 docker compose (v2) 和 docker-compose (v1)
    if docker compose version &>/dev/null; then
        export COMPOSE_CMD="docker compose"
        log_info "✅ Docker Compose: $(docker compose version)"
    elif docker-compose --version &>/dev/null; then
        export COMPOSE_CMD="docker-compose"
        log_info "✅ Docker Compose: $(docker-compose --version)"
    else
        log_error "Docker Compose 未安装！"; exit 1
    fi
    
    # curl 用于检查后端健康
    if ! command -v curl &>/dev/null; then
        log_warn "curl 未安装，将跳过后端健康检查"
    fi
    
    read -p "$(echo -e "${YELLOW}按 Enter 继续...${NC}")"
}

# ========== 步骤3: SSL 选择 ==========
prompt_ssl() {
    log_step "[2/8] HTTPS 配置"
    echo -e "  ${YELLOW}1) HTTP 模式（本地测试/无证书）${NC}"
    echo -e "  ${GREEN}2) HTTPS 模式（需要 SSL 证书）${NC}"
    echo ""
    read -p "$(echo -e "${BLUE}请输入 [1/2]${NC}: ")" SITE_HTTPS
    case $SITE_HTTPS in
        2|y|Y) SITE_HTTPS="y"; log_info "使用 HTTPS" ;;
        *) SITE_HTTPS="n"; log_info "使用 HTTP" ;;
    esac
}

# ========== 步骤4: 密码配置 ==========
prompt_passwords() {
    log_step "[3/8] 密码配置"
    echo "所有密码留空将自动生成 24 位随机密码"
    echo ""
    
    DB_PASSWORD_INPUT="n"
    read -s -p "$(echo -e "${BLUE}PostgreSQL 密码（留空自动生成）${NC}: ")" DB_PASSWORD
    echo ""
    if [ -z "$DB_PASSWORD" ]; then
        DB_PASSWORD=$(openssl rand -base64 18)
        DB_PASSWORD_INPUT="y"
        echo "✅ PostgreSQL 密码已自动生成"
    fi
    echo "[INPUT] PostgreSQL 密码: ******" >> "$LOG_FILE"
    
    REDIS_PASSWORD_INPUT="n"
    read -s -p "$(echo -e "${BLUE}Redis 密码（留空自动生成）${NC}: ")" REDIS_PASSWORD
    echo ""
    if [ -z "$REDIS_PASSWORD" ]; then
        REDIS_PASSWORD=$(openssl rand -base64 18)
        REDIS_PASSWORD_INPUT="y"
        echo "✅ Redis 密码已自动生成"
    fi
    echo "[INPUT] Redis 密码: ******" >> "$LOG_FILE"
    
    MINIO_PASSWORD_INPUT="n"
    read -s -p "$(echo -e "${BLUE}MinIO Root 密码（留空自动生成）${NC}: ")" MINIO_PASSWORD
    echo ""
    if [ -z "$MINIO_PASSWORD" ]; then
        MINIO_PASSWORD=$(openssl rand -base64 18)
        MINIO_PASSWORD_INPUT="y"
        echo "✅ MinIO 密码已自动生成"
    fi
    echo "[INPUT] MinIO 密码: ******" >> "$LOG_FILE"
    
    JWT_SECRET=$(openssl rand -base64 48)
}

# ========== 步骤5: 管理员账号 ==========
prompt_admin() {
    log_step "[4/8] 管理员账号"
    while true; do
        read -p "$(echo -e "${BLUE}管理员用户名${NC}: ")" ADMIN_USERNAME
        [ -n "$ADMIN_USERNAME" ] && break
    done
    echo "[INPUT] 管理员用户名: ${ADMIN_USERNAME}" >> "$LOG_FILE"
    
    while true; do
        read -p "$(echo -e "${BLUE}管理员邮箱${NC}: ")" ADMIN_EMAIL
        echo "$ADMIN_EMAIL" | grep -qE '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$' && break
        echo -e "${RED}邮箱格式无效${NC}"
    done
    echo "[INPUT] 管理员邮箱: ${ADMIN_EMAIL}" >> "$LOG_FILE"
    
    echo -e "${YELLOW}${PASSWORD_RULES}${NC}"
    while true; do
        read_password "管理员密码" ADMIN_PASSWORD
        validate_password "$ADMIN_PASSWORD" && break
    done
}

# ========== 步骤6: 站点信息 ==========
prompt_site() {
    log_step "[5/8] 站点信息"
    read -p "$(echo -e "${BLUE}站点名称${NC}: ")" SITE_NAME
    read -p "$(echo -e "${BLUE}站点域名 (例如 leo-blog.top)${NC}: ")" SITE_DOMAIN
    [ -z "$SITE_DOMAIN" ] && SITE_DOMAIN="localhost"
    echo "[INPUT] 站点名称: ${SITE_NAME}" >> "$LOG_FILE"
    echo "[INPUT] 站点域名: ${SITE_DOMAIN}" >> "$LOG_FILE"
}

# ========== 步骤7: 确认 ==========
show_summary() {
    log_step "[6/9] 安装确认"
    log "  ${GREEN}部署方式:${NC}   Docker Compose"
    log "  ${GREEN}HTTPS:${NC}       $([ "$SITE_HTTPS" = "y" ] && echo "启用" || echo "禁用 - HTTP")"
    log "  ${GREEN}站点名称:${NC}    ${SITE_NAME}"
    log "  ${GREEN}站点域名:${NC}    ${SITE_DOMAIN}"
    log "  ${GREEN}管理员:${NC}      ${ADMIN_USERNAME} / ${ADMIN_EMAIL}"
    echo ""
    read -p "$(echo -e "${BLUE}输入 YES 开始安装${NC}: ")" confirm
    echo "[INPUT] 确认: ${confirm}" >> "$LOG_FILE"
    if [ "$confirm" != "YES" ]; then
        echo "已取消"
        exit 0
    fi
}

# ========== 步骤8: 镜像源配置 ==========
prompt_mirror() {
    log_step "[ 可选 ] 轩辕镜像源"
    echo "轩辕镜像可以加速 Docker 镜像下载（国内环境推荐）。"
    echo "选择后将交由轩辕安装脚本完成配置，安装完成后继续 MyBlog 部署。"
    echo ""
    read -p "$(echo -e "${BLUE}是否启用轩辕镜像？(y/n)${NC}: ")" USE_MIRROR
    case $USE_MIRROR in
        y|Y|yes|YES)
            echo ""
            echo "${YELLOW}正在下载并执行轩辕镜像安装脚本...${NC}"
            echo "---- 轩辕镜像配置开始 ----" >> "$LOG_FILE"
            echo "执行命令: bash <(wget -qO- https://xuanyuan.cloud/docker.sh)" >> "$LOG_FILE"
            # 临时关闭 set -e，避免 wget 或轩辕脚本失败时整个脚本退出
            set +e
            bash <(wget -qO- https://xuanyuan.cloud/docker.sh) 2>&1 | tee -a "$LOG_FILE"
            local mirror_exit=$?
            set -e
            echo "---- 轩辕镜像配置结束（退出码: ${mirror_exit}）----" >> "$LOG_FILE"
            if [ $mirror_exit -eq 0 ]; then
                echo "${GREEN}[INFO]${NC} ✅ 轩辕镜像安装完成"
            else
                echo "${YELLOW}[WARN]${NC} ⚠️  轩辕镜像安装退出码: ${mirror_exit}，用户可能取消了安装，继续 MyBlog 部署"
            fi
            echo ""
            echo "${YELLOW}按 Enter 继续 MyBlog 安装...${NC}"
            read -r
            ;;
        *)
            echo "将使用 Docker Hub 官方源"
            ;;
    esac
}

# ========== 步骤9: 执行安装 ==========
execute_install() {
    log_step "[8/9] 执行安装"
    
    # ---- 1. 生成 .env ----
    echo ""
    echo "${YELLOW}[1/6] 生成 .env 配置...${NC}"
    cat > "$SCRIPT_DIR/.env" << EOF
POSTGRES_USER=myblog
POSTGRES_PASSWORD=${DB_PASSWORD}
POSTGRES_DB=myblog

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=${MINIO_PASSWORD}

JWT_SECRET=${JWT_SECRET}

SPRING_PROFILES_ACTIVE=prod
EOF
    cat "$SCRIPT_DIR/.env" >> "$LOG_FILE"
    echo "${GREEN}[INFO]${NC} ✅ .env 已生成"
    
    # ---- 2. 配置前端 ----
    echo ""
    echo "${YELLOW}[2/6] 配置前端...${NC}"
    if [ "$SITE_HTTPS" = "n" ] && [ -f "$SCRIPT_DIR/frontend/apache.no-ssl.conf" ]; then
        cp "$SCRIPT_DIR/frontend/apache.conf" "$SCRIPT_DIR/frontend/apache.conf.ssl.bak" 2>/dev/null || true
        cp "$SCRIPT_DIR/frontend/apache.no-ssl.conf" "$SCRIPT_DIR/frontend/apache.conf"
        echo "使用的 Apache 配置: frontend/apache.no-ssl.conf (HTTP 无 SSL)"
    else
        echo "使用的 Apache 配置: frontend/apache.conf (SSL)"
    fi
    
    # ---- 3. 启动 Docker 服务 ----
    echo ""
    echo "${YELLOW}[3/6] 启动 Docker 服务...${NC}"
    cd "$SCRIPT_DIR"
    echo "清理可能遗留的旧容器..."
    for old in myblog-postgres myblog-redis myblog-minio myblog-backend myblog-frontend; do
        if docker ps -a --format '{{.Names}}' | grep -q "^${old}$"; then
            echo "  移除旧容器: ${old}"
            docker rm -f "${old}" 2>/dev/null || true
        fi
    done
    
    echo "===== Docker Compose Build & Up ====="
    echo "===== Docker Compose Build & Up =====" >> "$LOG_FILE"
    set +e
    docker compose -f deploy/docker-compose.prod.yml up -d --build 2>&1 | tee -a "$LOG_FILE"
    local compose_exit=${PIPESTATUS[0]}
    set -e
    echo "docker compose exit code: ${compose_exit}" >> "$LOG_FILE"
    if [ $compose_exit -ne 0 ]; then
        echo "${RED}[ERROR]${NC} ❌ Docker Compose 构建/启动失败（退出码: ${compose_exit}）！终止安装"
        echo "${YELLOW}请检查日志后重新运行脚本${NC}"
        exit 1
    fi
    
    echo "===== Docker PS ====="
    docker ps --filter "name=myblog-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>&1 | tee -a "$LOG_FILE"
    
    # 获取 Compose 创建的实际网络名（关键修复！）
    local network_name="myblog-network"
    # 尝试从 docker network ls 中找到实际网络
    local actual_network=$(docker network ls --format '{{.Name}}' | grep -i "myblog.*network" | head -1)
    if [ -n "$actual_network" ]; then
        network_name="$actual_network"
        echo "检测到 Docker 网络: ${network_name}" >> "$LOG_FILE"
    fi
    
    for svc in postgres redis minio backend frontend; do
        if ! docker ps --filter "name=myblog-${svc}" --filter "status=running" --format "{{.Names}}" | grep -q "myblog-${svc}"; then
            echo "${RED}[ERROR]${NC} ❌ 容器 myblog-${svc} 未运行！终止安装"
            echo "===== myblog-${svc} 容器日志 ====="
            docker logs myblog-${svc} --tail 30 2>&1 || echo "(容器不存在或无日志)"
            echo "========================="
            exit 1
        fi
        echo "  ${GREEN}✅${NC} myblog-${svc} 运行中"
    done
    echo "${GREEN}[INFO]${NC} ✅ 所有服务已启动，等待就绪..."
    sleep 15
    
    # ---- 4. 检查后端 ----
    echo ""
    echo "${YELLOW}[4/6] 检查后端服务...${NC}"
    HEALTHY=false
    for i in $(seq 1 15); do
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health 2>/dev/null || echo "failed")
        echo "  第 ${i} 次检查: HTTP ${http_code}" >> "$LOG_FILE"
        echo -n "."
        if [ "$http_code" = "200" ]; then
            HEALTHY=true
            break
        fi
        sleep 2
    done
    echo ""
    if [ "$HEALTHY" = true ]; then
        echo "${GREEN}[INFO]${NC} ✅ 后端服务就绪"
    else
        echo "${RED}[ERROR]${NC} ❌ 后端服务未就绪，终止安装"
        echo "===== 后端容器日志 ====="
        docker logs myblog-backend --tail 50 2>&1 | tee -a "$LOG_FILE"
        echo "===== 尝试查看数据库连接状态 ====="
        docker compose -f deploy/docker-compose.prod.yml exec -T postgres psql -U myblog -d myblog -c "SELECT 1 as connected;" 2>&1 | tee -a "$LOG_FILE" || echo "数据库无法连接"
        exit 1
    fi
    
    # ---- 5. MinIO 初始化 ----
    echo ""
    echo "${YELLOW}[5/7] 初始化 MinIO 存储...${NC}"
    set +e
    
    # 查找实际网络名
    local mc_network=$(docker network ls --format '{{.Name}}' | grep "myblog.*network" | head -1)
    [ -z "$mc_network" ] && mc_network="myblog-network"
    echo "  使用网络: ${mc_network}" >> "$LOG_FILE"
    
    # 拉取 mc 镜像（后台静默拉取）
    docker pull minio/mc:latest >/dev/null 2>&1 &
    
    local mc_cmd="docker run --rm --network ${mc_network} -v ${SCRIPT_DIR}/captcha-images:/images:ro minio/mc:latest"
    
    echo "  创建 captcha-backgrounds bucket..."
    $mc_cmd alias set myminio http://minio:9000 "${MINIO_ROOT_USER:-minioadmin}" "${MINIO_PASSWORD}" --api S3v4 >/tmp/mc.log 2>&1
    local mc_alias_exit=$?
    cat /tmp/mc.log >> "$LOG_FILE"
    
    if [ $mc_alias_exit -eq 0 ]; then
        $mc_cmd mb myminio/captcha-backgrounds --ignore-existing >> "$LOG_FILE" 2>&1
        $mc_cmd anonymous set public myminio/captcha-backgrounds >> "$LOG_FILE" 2>&1
        echo "  ✓ bucket 已就绪"
        
        # 上传验证码图片
        if [ -d "$SCRIPT_DIR/captcha-images" ]; then
            echo "  上传验证码背景图片 (${mc_cmd})..."
            # 直接用 mc cp 从挂载的 /images 目录上传
            $mc_cmd cp /images/bg_001.jpg myminio/captcha-backgrounds/ >/dev/null 2>&1
            if [ $? -eq 0 ]; then
                # 批量上传所有图片
                $mc_cmd cp --recursive /images/ myminio/captcha-backgrounds/ >> "$LOG_FILE" 2>&1
                echo "  ✅ 验证码背景图上传完成"
            else
                echo "  ⚠️ mc cp 失败，尝试 docker cp 方案..."
                # 备用方案：用 docker cp 到容器内
                docker exec myblog-minio mkdir -p /data/captcha-backgrounds/ 2>/dev/null
                docker cp "$SCRIPT_DIR/captcha-images/." myblog-minio:/data/captcha-backgrounds/ 2>/dev/null
                echo "  ✅ 已通过文件系统复制图片到 MinIO"
            fi
        fi
    else
        echo "  ⚠️  mc 连接 MinIO 失败，尝试备用方案..."
        # 备用方案：直接通过文件系统
        docker exec myblog-minio mkdir -p /data/captcha-backgrounds/ 2>/dev/null
        if [ -d "$SCRIPT_DIR/captcha-images" ]; then
            docker cp "$SCRIPT_DIR/captcha-images/." myblog-minio:/data/captcha-backgrounds/ 2>/dev/null
            echo "  ✅ 已通过文件系统复制图片到 MinIO"
        fi
        echo "  ✓ bucket 已就绪（文件系统模式）"
    fi
    rm -f /tmp/mc.log
    set -e
    
    # ---- 6. 创建管理员 ----
    echo ""
    echo "${YELLOW}[6/7] 创建管理员账号...${NC}"
    
    echo "  生成密码哈希..."
    local bcrypt_hash=$(generate_bcrypt_hash "$ADMIN_PASSWORD")
    if [ -z "$bcrypt_hash" ]; then
        echo "${YELLOW}[WARN]${NC} ⚠️  无法生成密码哈希，将在数据库中设置临时密码，稍后请重置"
        bcrypt_hash='$2a$10$dummyhashforpasswordreset'
    fi
    echo "  哈希: ${bcrypt_hash:0:30}..."
    
    local user_id=$(generate_snowflake_id)
    echo "  用户 ID: ${user_id}"
    
    cat > /tmp/myblog_init.sql << SQL
INSERT INTO t_user (id, username, password, nickname, email, is_admin, is_super_admin, enabled, create_time)
VALUES (${user_id}, '${ADMIN_USERNAME}', '${bcrypt_hash}', '管理员', '${ADMIN_EMAIL}', TRUE, TRUE, TRUE, NOW())
ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, is_admin = TRUE, is_super_admin = TRUE, enabled = TRUE;

INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order)
VALUES (${user_id}, 'install.locked', 'true', 'BOOLEAN', '系统安装锁定', 'SYSTEM', false, false, 0)
ON CONFLICT (config_key) DO UPDATE SET config_value = 'true';

INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order)
VALUES ($(generate_snowflake_id), 'site.name', '${SITE_NAME}', 'STRING', '网站名称', 'BASIC', TRUE, TRUE, 1)
ON CONFLICT (config_key) DO UPDATE SET config_value = '${SITE_NAME}';

INSERT INTO t_system_config (id, config_key, config_value, config_type, description, category, is_public, is_editable, sort_order)
VALUES ($(generate_snowflake_id), 'site.description', '${SITE_DESC:-MyBlog 个人博客}', 'STRING', '网站描述', 'BASIC', TRUE, TRUE, 2)
ON CONFLICT (config_key) DO UPDATE SET config_value = '${SITE_DESC:-MyBlog 个人博客}';
SQL
    echo "  SQL 脚本已生成，共 $(wc -l < /tmp/myblog_init.sql) 行" >> "$LOG_FILE"
    
    set +e
    echo "  执行 SQL（使用 docker compose exec 确保连接到正确网络）..."
    cat /tmp/myblog_init.sql | docker compose -f deploy/docker-compose.prod.yml exec -T postgres psql -U myblog -d myblog -v ON_ERROR_STOP=1 2>&1 | tee -a "$LOG_FILE"
    local sql_exit=${PIPESTATUS[0]}
    echo "  psql 退出码: ${sql_exit}" >> "$LOG_FILE"
    
    # 验证用户是否创建成功
    echo "  验证用户创建..."
    docker compose -f deploy/docker-compose.prod.yml exec -T postgres psql -U myblog -d myblog -c "SELECT id, username, nickname, is_admin FROM t_user;" 2>&1 | tee -a "$LOG_FILE"
    local verify_exit=$?
    
    rm -f /tmp/myblog_init.sql
    set -e
    
    if [ $sql_exit -eq 0 ] && [ $verify_exit -eq 0 ]; then
        echo "${GREEN}[INFO]${NC} ✅ 管理员账号创建并验证成功"
    else
        echo "${RED}[ERROR]${NC} ❌ 管理员创建失败, 请安装后手动在数据库创建用户"
        echo "  备用命令: docker compose exec postgres psql -U myblog -d myblog -c \"INSERT INTO t_user ...\""
    fi
    
    # ---- 7. 写入安装锁 ----
    echo ""
    echo "${YELLOW}[7/7] 写入安装锁...${NC}"
    cat > "$SCRIPT_DIR/$INSTALL_LOCK_FILE" << EOF
INSTALLED=true
INSTALL_DATE=$(date '+%Y-%m-%d %H:%M:%S')
INSTALL_MODE=docker
SITE_NAME=${SITE_NAME}
SITE_DOMAIN=${SITE_DOMAIN}
ADMIN_USER=${ADMIN_USERNAME}
EOF
    cat "$SCRIPT_DIR/$INSTALL_LOCK_FILE" >> "$LOG_FILE"
    echo "${GREEN}[INFO]${NC} ✅ 安装完成！"
}

# ========== 完成 ==========
show_complete() {
    log_step "[8/8] 安装成功"
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║           🎉 MyBlog 安装成功！                      ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  前端:     http://${SITE_DOMAIN}/"
    echo -e "  后端 API: http://${SITE_DOMAIN}:18080/api/"
    echo -e "  MinIO:    http://${SITE_DOMAIN}:9001/"
    echo ""
    echo -e "  ${GREEN}管理员账号:${NC}"
    echo -e "    用户名: ${ADMIN_USERNAME}"
    echo -e "    密  码: ${ADMIN_PASSWORD}"
    echo ""
    echo -e "  ${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${YELLOW}⚠️  以下凭据请及时保存！${NC}"
    echo -e "  ${YELLOW}   关闭终端后将无法再次查看！${NC}"
    echo -e "  ${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "  ${GREEN}PostgreSQL${NC}:"
    echo -e "    用户名: myblog"
    echo -e "    密  码: ${DB_PASSWORD}"
    echo -e "    数据库: myblog"
    echo ""
    echo -e "  ${GREEN}Redis${NC}:"
    echo -e "    地址: localhost:6379"
    echo -e "    密  码: ${REDIS_PASSWORD}"
    echo ""
    echo -e "  ${GREEN}MinIO${NC}:"
    echo -e "    用户名: minioadmin"
    echo -e "    密  码: ${MINIO_PASSWORD}"
    echo -e "    控制台: http://localhost:9001/"
    echo ""
    echo -e "  ${GREEN}JWT Secret${NC}: ${JWT_SECRET}"
    echo ""
    echo -e "  ${YELLOW}这些信息已保存在安装日志中: ${LOG_FILE}${NC}"
    echo ""
    echo -e "  管理命令:"
    echo -e "    日志:   docker compose -f deploy/docker-compose.prod.yml logs -f"
    echo -e "    停止:   docker compose -f deploy/docker-compose.prod.yml down"
    echo -e "    卸载:   bash scripts/uninstall.sh"
    echo ""
    echo -e "  ${YELLOW}安装日志: ${LOG_FILE}${NC}"
    echo ""
}

# ========== 主流程 ==========
main() {
    show_banner
    check_env
    prompt_ssl
    prompt_mirror
    prompt_passwords
    prompt_admin
    prompt_site
    show_summary
    execute_install
    show_complete
}

# 捕获中断信号
trap 'echo -e "\n${RED}安装被中断${NC}"; echo "[ERROR] 安装被中断于 $(date)" >> "$LOG_FILE"; exit 1' INT TERM

main
