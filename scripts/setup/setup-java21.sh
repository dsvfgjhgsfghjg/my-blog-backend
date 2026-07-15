#!/bin/bash

###############################################################################
# Java 多版本管理安装脚本
# 功能：安装 SDKMAN! 并配置 Java 21，实现多版本 Java 管理
# 作者：MyBlog Team
# 日期：2026-05-06
###############################################################################

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Java 多版本管理安装脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查是否已安装 SDKMAN
if [ -d "$HOME/.sdkman" ]; then
    echo -e "${GREEN}✓ SDKMAN! 已安装${NC}"
else
    echo -e "${YELLOW}正在安装 SDKMAN!...${NC}"
    
    # 安装必要的依赖
    echo -e "${BLUE}步骤 1: 安装依赖包...${NC}"
    sudo apt update
    sudo apt install -y zip unzip curl
    
    # 安装 SDKMAN
    echo -e "${BLUE}步骤 2: 安装 SDKMAN!...${NC}"
    curl -s "https://get.sdkman.io" | bash
    
    # 加载 SDKMAN
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    
    echo -e "${GREEN}✓ SDKMAN! 安装成功${NC}"
fi

# 确保 SDKMAN 可用
source "$HOME/.sdkman/bin/sdkman-init.sh" 2>/dev/null || true

# 安装 Java 21
echo ""
echo -e "${BLUE}步骤 3: 安装 Java 21...${NC}"

# 检查是否已安装 Java 21
if sdk list java | grep -q "21.*installed"; then
    echo -e "${GREEN}✓ Java 21 已安装${NC}"
else
    echo -e "${YELLOW}正在安装 Java 21 (Temurin)...${NC}"
    sdk install java 21.0.2-tem
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Java 21 安装成功${NC}"
    else
        echo -e "${RED}✗ Java 21 安装失败，尝试其他版本...${NC}"
        sdk install java 21.0.1-tem
    fi
fi

# 安装 Java 17（可选，用于兼容性）
echo ""
echo -e "${BLUE}步骤 4: 安装 Java 17（可选，用于兼容性）...${NC}"
read -p "是否安装 Java 17? (y/n, 默认 n): " install_java17
install_java17=${install_java17:-n}

if [[ $install_java17 =~ ^[Yy]$ ]]; then
    if sdk list java | grep -q "17.*installed"; then
        echo -e "${GREEN}✓ Java 17 已安装${NC}"
    else
        echo -e "${YELLOW}正在安装 Java 17...${NC}"
        sdk install java 17.0.9-tem
        echo -e "${GREEN}✓ Java 17 安装成功${NC}"
    fi
fi

# 设置默认 Java 版本为 21
echo ""
echo -e "${BLUE}步骤 5: 设置默认 Java 版本为 21...${NC}"
sdk default java 21.0.2-tem

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 默认 Java 版本已设置为 21${NC}"
else
    echo -e "${YELLOW}尝试设置其他 Java 21 版本...${NC}"
    sdk default java 21.0.1-tem
fi

# 验证安装
echo ""
echo -e "${BLUE}步骤 6: 验证安装...${NC}"
echo -e "${BLUE}当前 Java 版本:${NC}"
java -version

echo ""
echo -e "${BLUE}已安装的 Java 版本:${NC}"
sdk list java | grep "installed"

# 创建项目级别的 .sdkmanrc 文件
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo ""
echo -e "${BLUE}步骤 7: 配置项目级别的 Java 版本...${NC}"

cat > "$PROJECT_DIR/.sdkmanrc" << EOF
# MyBlog 项目 Java 版本配置
java=21.0.2-tem
EOF

echo -e "${GREEN}✓ 已创建项目配置文件 .sdkmanrc${NC}"

# 询问是否启用自动切换
echo ""
read -p "是否启用 SDKMAN 自动切换功能？(y/n, 推荐 y): " enable_auto_switch
enable_auto_switch=${enable_auto_switch:-y}

if [[ $enable_auto_switch =~ ^[Yy]$ ]]; then
    # 在 ~/.bashrc 中添加自动切换
    if ! grep -q "sdkman_auto_env" ~/.bashrc; then
        echo "" >> ~/.bashrc
        echo "# SDKMAN! 自动切换 Java 版本" >> ~/.bashrc
        echo "export sdkman_auto_env=true" >> ~/.bashrc
        echo -e "${GREEN}✓ 已启用自动切换功能${NC}"
    else
        echo -e "${YELLOW}⚠ 自动切换功能已存在${NC}"
    fi
fi

# 显示使用说明
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}常用命令:${NC}"
echo -e "  ${YELLOW}sdk list java${NC}          - 查看可安装的 Java 版本"
echo -e "  ${YELLOW}sdk install java <version>${NC} - 安装指定版本的 Java"
echo -e "  ${YELLOW}sdk use java <version>${NC}   - 临时切换到指定版本"
echo -e "  ${YELLOW}sdk default java <version>${NC} - 设置默认 Java 版本"
echo -e "  ${YELLOW}sdk current java${NC}        - 查看当前使用的 Java 版本"
echo -e "  ${YELLOW}java -version${NC}           - 验证当前 Java 版本"
echo ""
echo -e "${BLUE}项目中使用:${NC}"
echo -e "  cd /home/leo/IdeaProjects/myblog"
echo -e "  sdk env                    # 根据 .sdkmanrc 自动切换 Java 版本"
echo ""
echo -e "${BLUE}重新加载配置:${NC}"
echo -e "  source ~/.bashrc"
echo -e "  或重新打开终端"
echo ""
echo -e "${GREEN}现在可以构建项目了:${NC}"
echo -e "  cd /home/leo/IdeaProjects/myblog"
echo -e "  ./gradlew clean bootJar"
echo ""
