#!/bin/bash
###############################################################################
# Java 21 安装脚本（国内镜像）
# 功能：下载 Adoptium/Temurin JDK 21 并配置用户级环境
# 适用：Fedora / CentOS / RHEL / Ubuntu / Debian 等 Linux 发行版
# 作者：MyBlog Team
# 日期：2026-07-15
###############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Java 21 安装脚本（国内镜像）${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# === 检测架构 ===
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ARCH_TAG="x64" ;;
    aarch64) ARCH_TAG="aarch64" ;;
    *)
        echo -e "${RED}不支持的架构: $ARCH${NC}"
        exit 1
        ;;
esac
echo -e "${BLUE}系统架构: ${ARCH_TAG}${NC}"

# === 获取最新 JDK 21 版本信息 ===
echo -e "${BLUE}[1/5] 获取 JDK 21 最新版本信息...${NC}"
API_URL="https://api.adoptium.net/v3/assets/feature_releases/21/ga?architecture=${ARCH_TAG}&image_type=jdk&jvm_impl=hotspot&os=linux&page=0&page_size=1&project=jdk&sort_method=DEFAULT&sort_order=DESC&vendor=eclipse"
API_RESP=$(curl -s "$API_URL")

# 解析版本号和下载链接
FULL_VERSION=$(echo "$API_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['version']['semver'])" 2>/dev/null || true)
if [ -z "$FULL_VERSION" ]; then
    echo -e "${RED}✗ 获取版本信息失败，使用默认版本 21.0.2+13${NC}"
    JAVA_VERSION="21.0.2"
    JAVA_BUILD="13"
    DOWNLOAD_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_${ARCH_TAG}_linux_hotspot_21.0.2_13.tar.gz"
else
    # 从 semver 提取版本号，如 "21.0.2+13" -> 21.0.2, 13
    JAVA_VERSION=$(echo "$FULL_VERSION" | cut -d'+' -f1)
    JAVA_BUILD=$(echo "$FULL_VERSION" | cut -d'+' -f2)
    DL_VERSION="${JAVA_VERSION}%2B${JAVA_BUILD}"
    DL_VERSION_UNDERSCORE="${JAVA_VERSION}_${JAVA_BUILD}"
    DOWNLOAD_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${DL_VERSION}/OpenJDK21U-jdk_${ARCH_TAG}_linux_hotspot_${DL_VERSION_UNDERSCORE}.tar.gz"
    echo -e "${GREEN}✓ 最新版本: JDK ${FULL_VERSION}${NC}"
fi

JAVA_DIR_NAME="jdk-${JAVA_VERSION}+${JAVA_BUILD}"
INSTALL_DIR="${HOME}/.local/java"
JAVA_HOME_DIR="${INSTALL_DIR}/${JAVA_DIR_NAME}"
TAR_FILE="OpenJDK21U-jdk_${ARCH_TAG}_linux_hotspot_${JAVA_VERSION}_${JAVA_BUILD}.tar.gz"

echo -e "${BLUE}安装目录: ${INSTALL_DIR}${NC}"
echo ""

# === 检查是否已安装 ===
if [ -f "${JAVA_HOME_DIR}/bin/java" ]; then
    INSTALLED_VER=$("${JAVA_HOME_DIR}/bin/java" -version 2>&1 | head -1)
    echo -e "${GREEN}✓ JDK 已安装: ${INSTALLED_VER}${NC}"
    echo -e "${GREEN}  路径: ${JAVA_HOME_DIR}${NC}"
    echo ""
    # 检查软链接和环境变量
    if [ ! -L "${HOME}/.local/bin/java" ]; then
        echo -e "${YELLOW}⚠ 软链接缺失，将重新配置${NC}"
    else
        echo -e "${GREEN}安装完成，无需额外操作${NC}"
        # 跳过安装流程，只做验证
        SKIP_INSTALL=true
    fi
else
    SKIP_INSTALL=false
fi

# === 安装依赖 ===
echo -e "${BLUE}[2/5] 检查依赖...${NC}"
if ! command -v curl &>/dev/null; then
    echo -e "${YELLOW}正在安装 curl...${NC}"
    if command -v dnf &>/dev/null; then
        sudo dnf install -y curl
    elif command -v apt &>/dev/null; then
        sudo apt install -y curl
    elif command -v yum &>/dev/null; then
        sudo yum install -y curl
    else
        echo -e "${RED}无法安装 curl，请手动安装${NC}"
        exit 1
    fi
fi
if ! command -v tar &>/dev/null; then
    echo -e "${YELLOW}正在安装 tar...${NC}"
    if command -v dnf &>/dev/null; then
        sudo dnf install -y tar
    elif command -v apt &>/dev/null; then
        sudo apt install -y tar
    elif command -v yum &>/dev/null; then
        sudo yum install -y tar
    else
        echo -e "${RED}无法安装 tar，请手动安装${NC}"
        exit 1
    fi
fi
if ! command -v python3 &>/dev/null; then
    echo -e "${YELLOW}python3 未安装，将尝试不使用 API...${NC}"
fi
echo -e "${GREEN}✓ 依赖检查通过${NC}"
echo ""

# 如果已安装且软链接正常，跳转到配置环境变量
if [ "$SKIP_INSTALL" = true ]; then
    echo -e "${BLUE}[3/5] 跳过下载和解压（已安装）${NC}"
    echo -e "${BLUE}[4/5] 跳过配置软链接（已配置）${NC}"
else
    # === 下载 JDK ===
    echo -e "${BLUE}[3/5] 下载 JDK 21...${NC}"
    echo -e "    下载地址: ${YELLOW}${DOWNLOAD_URL}${NC}"

    TMP_DIR=$(mktemp -d)
    cd "$TMP_DIR"

    if [ -f "/tmp/${TAR_FILE}" ]; then
        echo -e "${GREEN}✓ 检测到已缓存的文件: /tmp/${TAR_FILE}${NC}"
        cp "/tmp/${TAR_FILE}" .
    else
        echo -e "${YELLOW}正在从 GitHub Releases 下载...${NC}"
        if curl -L --progress-bar -o "${TAR_FILE}" "${DOWNLOAD_URL}"; then
            echo -e "${GREEN}✓ 下载完成${NC}"
            cp "${TAR_FILE}" "/tmp/${TAR_FILE}" 2>/dev/null || true
        else
            echo -e "${RED}✗ 下载失败${NC}"
            echo -e "${YELLOW}可尝试手动下载:${NC}"
            echo -e "  wget ${DOWNLOAD_URL}"
            cd / && rm -rf "$TMP_DIR"
            exit 1
        fi
    fi
    echo ""

    # === 解压安装 ===
    echo -e "${BLUE}[4/5] 解压并安装到 ${INSTALL_DIR}...${NC}"
    mkdir -p "$INSTALL_DIR"

    if [ -d "$JAVA_HOME_DIR" ]; then
        echo -e "${YELLOW}⚠ 检测到已存在的 JDK 目录，将覆盖${NC}"
        rm -rf "$JAVA_HOME_DIR"
    fi

    echo -e "${YELLOW}正在解压...${NC}"
    tar -xzf "${TAR_FILE}" -C "$INSTALL_DIR"

    # 验证解压后的目录名（可能带时间戳后缀）
    INSTALLED_DIR=$(ls "$INSTALL_DIR" | grep "jdk-${JAVA_VERSION}" | head -1)
    if [ -n "$INSTALLED_DIR" ] && [ "$INSTALLED_DIR" != "$JAVA_DIR_NAME" ]; then
        mv "${INSTALL_DIR}/${INSTALLED_DIR}" "$JAVA_HOME_DIR"
    fi

    cd / && rm -rf "$TMP_DIR"
    echo -e "${GREEN}✓ 安装到 ${JAVA_HOME_DIR}${NC}"
    echo ""

    # === 配置软链接 ===
    echo -e "${BLUE}[5/5] 配置用户级软链接...${NC}"
    mkdir -p "${HOME}/.local/bin"
    ln -sf "${JAVA_HOME_DIR}/bin/java" "${HOME}/.local/bin/java"
    ln -sf "${JAVA_HOME_DIR}/bin/javac" "${HOME}/.local/bin/javac"
    ln -sf "${JAVA_HOME_DIR}/bin/jar" "${HOME}/.local/bin/jar"
    ln -sf "${JAVA_HOME_DIR}/bin/javadoc" "${HOME}/.local/bin/javadoc"
    ln -sf "${JAVA_HOME_DIR}/bin/jshell" "${HOME}/.local/bin/jshell"
    echo -e "${GREEN}✓ 软链接已创建到 ~/.local/bin/${NC}"
    echo ""
fi

# === 配置 JAVA_HOME 环境变量 ===
echo -e "${BLUE}配置 JAVA_HOME 环境变量...${NC}"

if [ -n "$BASH_VERSION" ]; then
    SHELL_RC="$HOME/.bashrc"
elif [ -n "$ZSH_VERSION" ]; then
    SHELL_RC="$HOME/.zshrc"
else
    SHELL_RC="$HOME/.bashrc"
fi

# 检查 JAVA_HOME 是否已配置
JAVA_HOME_CONFIGURED=false
if grep -q "JAVA_HOME=" "$SHELL_RC" 2>/dev/null; then
    JAVA_HOME_CONFIGURED=true
fi

if [ "$JAVA_HOME_CONFIGURED" = true ]; then
    echo -e "${GREEN}✓ JAVA_HOME 已配置${NC}"
else
    echo "" >> "$SHELL_RC"
    echo "# Java 21 环境变量" >> "$SHELL_RC"
    echo "export JAVA_HOME=${JAVA_HOME_DIR}" >> "$SHELL_RC"
    echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> "$SHELL_RC"
    echo -e "${GREEN}✓ 已将 JAVA_HOME 写入 ${SHELL_RC}${NC}"
fi

# 确保 ~/.local/bin 在 PATH 中（软链接方式备用）
if ! grep -q 'HOME/.local/bin' "$SHELL_RC" 2>/dev/null; then
    echo "" >> "$SHELL_RC"
    echo '# 用户本地 bin' >> "$SHELL_RC"
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$SHELL_RC"
    echo -e "${GREEN}✓ 已将 ~/.local/bin 添加到 PATH${NC}"
fi

# 立即加载当前终端的环境变量
export JAVA_HOME="${JAVA_HOME_DIR}"
export PATH="${JAVA_HOME}/bin:${HOME}/.local/bin:${PATH}"
echo ""

# === 验证 ===
echo -e "${BLUE}验证 Java 安装...${NC}"
echo ""
echo -e "${YELLOW}java -version:${NC}"
java -version 2>&1
echo ""
echo -e "${YELLOW}javac -version:${NC}"
javac -version 2>&1
echo ""
echo -e "${YELLOW}JAVA_HOME:${NC}"
echo -e "  ${JAVA_HOME_DIR}"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Java 21 安装成功！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}请在新终端中执行以下命令加载环境变量（如需）:${NC}"
echo -e "  ${YELLOW}source ${SHELL_RC}${NC}"
echo ""
echo -e "${BLUE}构建项目:${NC}"
echo -e "  ${YELLOW}cd /home/leo/IdeaProjects/myblog && ./gradlew clean bootJar${NC}"
echo ""