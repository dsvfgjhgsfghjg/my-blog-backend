#!/bin/bash
# ============================================
# MyBlog 发行包打包脚本
# 预编译后端 JAR，生成后端与部署模板的 tar.gz。
# 前端由独立仓库和独立镜像发布。
# 目标机器只需安装 Docker + Docker Compose
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  MyBlog 发行包打包工具${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# ========== 配置 ==========
PROJECT_NAME="myblog"
VERSION=$(date +%Y%m%d_%H%M%S)
PACKAGE_NAME="${PROJECT_NAME}-release-${VERSION}"
BACKEND_DIR="$(pwd)"                          # 后端项目目录
TEMP_DIR="/tmp/${PACKAGE_NAME}"

echo -e "${YELLOW}📦 发行包信息${NC}"
echo "  包名: ${PACKAGE_NAME}"
echo "  后端目录: ${BACKEND_DIR}"
echo ""

# ========== 检查目录 ==========
if [ ! -f "${BACKEND_DIR}/build.gradle" ]; then
    echo -e "${RED}❌ 错误: 请在后端项目根目录运行此脚本${NC}"
    exit 1
fi

# ========== 步骤1: 清理 ==========
echo -e "${YELLOW}[1/4] 清理旧的构建产物...${NC}"
cd "${BACKEND_DIR}"
rm -rf build dist 2>/dev/null || true
echo -e "${GREEN}✅ 清理完成${NC}"
echo ""

# ========== 步骤2: 构建后端 JAR ==========
echo -e "${YELLOW}[2/4] 构建后端 (Gradle bootJar)...${NC}"
cd "${BACKEND_DIR}"
./gradlew bootJar -x test 2>&1 || {
    echo -e "${RED}❌ 后端构建失败${NC}"
    exit 1
}
echo -e "${GREEN}✅ 后端构建完成${NC}"
echo ""

# ========== 步骤3: 创建打包目录 ==========
echo -e "${YELLOW}[3/4] 创建发行包目录...${NC}"
rm -rf "${TEMP_DIR}" 2>/dev/null || true
mkdir -p "${TEMP_DIR}"

# 复制后端 JAR
JAR_FILE=$(ls "${BACKEND_DIR}/build/libs/"*-SNAPSHOT.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}❌ 找不到构建好的 JAR 文件${NC}"
    exit 1
fi
mkdir -p "${TEMP_DIR}/build/libs"
cp "${JAR_FILE}" "${TEMP_DIR}/build/libs/"
echo -e "  ${GREEN}✅${NC} JAR: $(basename "${JAR_FILE}")"

# 复制 Dockerfile 和 deploy 模板
cd "${BACKEND_DIR}"
cp -r deploy "${TEMP_DIR}/"
cp Dockerfile.backend "${TEMP_DIR}/"
cp Dockerfile.migrations "${TEMP_DIR}/"
echo -e "  ${GREEN}✅${NC} deploy/ + Dockerfile"

# 复制 .env.example
cp .env.example "${TEMP_DIR}/"
echo -e "  ${GREEN}✅${NC} .env.example"

# 复制验证码背景图片
if [ -d "${BACKEND_DIR}/captcha-images" ]; then
    mkdir -p "${TEMP_DIR}/captcha-images"
    cp -r "${BACKEND_DIR}/captcha-images/"* "${TEMP_DIR}/captcha-images/" 2>/dev/null || true
    echo -e "  ${GREEN}✅${NC} 验证码背景图 (captcha-images/)"
fi

# 复制 schema 和迁移辅助脚本
mkdir -p "${TEMP_DIR}/sql"
if [ -d "${BACKEND_DIR}/sql" ]; then
    cp "${BACKEND_DIR}/sql/"*.sql "${TEMP_DIR}/sql/" 2>/dev/null || true
    cp "${BACKEND_DIR}/sql/"*.sh "${TEMP_DIR}/sql/" 2>/dev/null || true
    echo -e "  ${GREEN}✅${NC} SQL 初始化脚本"
fi

# 复制脚本
mkdir -p "${TEMP_DIR}/scripts"
cp "${BACKEND_DIR}/scripts/deploy.sh" "${TEMP_DIR}/scripts/"
cp "${BACKEND_DIR}/scripts/install.sh" "${TEMP_DIR}/scripts/"
cp "${BACKEND_DIR}/scripts/uninstall.sh" "${TEMP_DIR}/scripts/"
# 复制 captcha 上传脚本
if [ -f "${BACKEND_DIR}/scripts/upload-captcha-to-minio.sh" ]; then
    cp "${BACKEND_DIR}/scripts/upload-captcha-to-minio.sh" "${TEMP_DIR}/scripts/"
fi
echo -e "  ${GREEN}✅${NC} 部署/卸载脚本"

# 复制 README
if [ -f "${BACKEND_DIR}/docs/install/INSTALL_GUIDE.md" ]; then
    mkdir -p "${TEMP_DIR}/docs/install"
    cp "${BACKEND_DIR}/docs/install/INSTALL_GUIDE.md" "${TEMP_DIR}/docs/install/"
    echo -e "  ${GREEN}✅${NC} 安装文档"
fi

echo ""

# ========== 步骤4: 打包压缩 ==========
echo -e "${YELLOW}[4/4] 打包压缩...${NC}"
cd /tmp
tar -czf "${PACKAGE_NAME}.tar.gz" "${PACKAGE_NAME}/"
cd - > /dev/null

# 移动到当前目录
mv "/tmp/${PACKAGE_NAME}.tar.gz" "${BACKEND_DIR}/"

echo -e "${GREEN}✅ 打包完成${NC}"
echo ""

# ========== 显示结果 ==========
PACKAGE_SIZE=$(du -h "${BACKEND_DIR}/${PACKAGE_NAME}.tar.gz" | cut -f1)
echo -e "${CYAN}════════════════════════════════════════${NC}"
echo -e "${CYAN}  📦 发行包已生成${NC}"
echo -e "${CYAN}════════════════════════════════════════${NC}"
echo ""
echo -e "  文件: ${PACKAGE_NAME}.tar.gz"
echo -e "  大小: ${PACKAGE_SIZE}"
echo ""
echo -e "${CYAN}发布包内容:${NC}"
tar -tzf "${BACKEND_DIR}/${PACKAGE_NAME}.tar.gz" | head -30
echo "  ..."
echo ""

# 清理临时目录
rm -rf "${TEMP_DIR}"

echo -e "${GREEN}════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ 打包成功！${NC}"
echo ""
echo -e "${YELLOW}目标机器部署步骤:${NC}"
echo "  1. 将 ${PACKAGE_NAME}.tar.gz 复制到目标机器"
echo "  2. 解压: tar -xzf ${PACKAGE_NAME}.tar.gz"
echo "  3. 进入目录: cd ${PACKAGE_NAME}"
echo "  4. 编辑配置: cp .env.example .env && vim .env"
echo "  5. 一键部署: bash scripts/deploy.sh"
echo ""
echo -e "  ${GREEN}或手动部署:${NC}"
echo -e "  docker compose -f deploy/docker-compose.prod.yml up -d"
echo ""
