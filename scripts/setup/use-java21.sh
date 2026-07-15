#!/bin/bash

###############################################################################
# Java 版本快速切换脚本
# 用途：确保当前终端使用正确的 Java 21 版本
###############################################################################

echo "正在切换到 Java 21..."

# 加载 SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 进入项目目录以触发 .sdkmanrc
cd /home/leo/IdeaProjects/myblog

# 应用项目级别的 Java 版本
sdk env

# 验证
echo ""
echo "当前 Java 版本："
java -version

echo ""
echo "✓ 已切换到 Java 21，可以开始构建项目了！"
echo ""
echo "运行以下命令构建项目："
echo "  ./gradlew clean bootJar"
