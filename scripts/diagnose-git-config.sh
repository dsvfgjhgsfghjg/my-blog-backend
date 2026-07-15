#!/bin/bash

# ============================================
# Git 配置诊断脚本
# 检查敏感文件是否被正确忽略
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Git 配置诊断工具${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

ERRORS=0
WARNINGS=0

# 检查 1: 敏感文件是否被跟踪
echo -e "${YELLOW}🔍 检查 1: 敏感文件跟踪状态${NC}"
TRACKED_SENSITIVE=$(git ls-files | grep -E "config/\.env$|secrets_config|\.pem$|\.key$" || true)

if [ -n "$TRACKED_SENSITIVE" ]; then
    echo -e "${RED}❌ 错误: 发现敏感文件被跟踪:${NC}"
    echo "$TRACKED_SENSITIVE"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✅ 通过: 没有敏感文件被跟踪${NC}"
fi
echo ""

# 检查 2: .env.example 是否被跟踪
echo -e "${YELLOW}🔍 检查 2: 配置文件模板${NC}"
TRACKED_EXAMPLE=$(git ls-files | grep "\.env\.example" || true)

if [ -z "$TRACKED_EXAMPLE" ]; then
    echo -e "${YELLOW}⚠️  警告: .env.example 未被跟踪${NC}"
    echo "   建议: git add config/.env.example"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${GREEN}✅ 通过: .env.example 已被跟踪${NC}"
fi
echo ""

# 检查 3: docker-compose 文件是否被跟踪
echo -e "${YELLOW}🔍 检查 3: Docker Compose 文件${NC}"
TRACKED_COMPOSE=$(git ls-files | grep "^docker-compose.*\.yml$" || true)

if [ -n "$TRACKED_COMPOSE" ]; then
    echo -e "${RED}❌ 错误: Docker Compose 文件仍被跟踪:${NC}"
    echo "$TRACKED_COMPOSE"
    echo ""
    echo "   修复方法:"
    echo "   git rm --cached docker-compose*.yml"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✅ 通过: Docker Compose 文件未被跟踪${NC}"
fi
echo ""

# 检查 4: .gitignore 规则
echo -e "${YELLOW}🔍 检查 4: .gitignore 规则${NC}"
if grep -q "^config/$" .gitignore && grep -q "^!config/\.env\.example$" .gitignore; then
    echo -e "${GREEN}✅ 通过: .gitignore 规则正确${NC}"
else
    echo -e "${RED}❌ 错误: .gitignore 规则不完整${NC}"
    echo "   应该包含:"
    echo "   config/"
    echo "   !config/.env.example"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# 检查 5: 软链接状态
echo -e "${YELLOW}🔍 检查 5: 软链接状态${NC}"
if [ -L ".env" ] && [ -L "config.yaml" ]; then
    echo -e "${GREEN}✅ 通过: 软链接已创建${NC}"
    
    # 验证软链接目标
    if [ -f "config/.env" ] && [ -f "config/config.yaml" ]; then
        echo -e "${GREEN}✅ 通过: 软链接目标存在${NC}"
    else
        echo -e "${RED}❌ 错误: 软链接目标不存在${NC}"
        ERRORS=$((ERRORS + 1))
    fi
else
    echo -e "${YELLOW}⚠️  警告: 软链接未创建${NC}"
    echo "   建议: 运行工作区整理脚本"
    WARNINGS=$((WARNINGS + 1))
fi
echo ""

# 检查 6: 打包排除规则
echo -e "${YELLOW}🔍 检查 6: 打包排除规则${NC}"
if [ -f ".gitignore.package" ]; then
    echo -e "${GREEN}✅ 通过: 打包排除规则文件存在${NC}"
    
    if grep -q "config/\.env" .gitignore.package; then
        echo -e "${GREEN}✅ 通过: 敏感配置在排除列表中${NC}"
    else
        echo -e "${YELLOW}⚠️  警告: 敏感配置可能未被排除${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "${YELLOW}⚠️  警告: .gitignore.package 不存在${NC}"
    echo "   建议: 创建打包排除规则文件"
    WARNINGS=$((WARNINGS + 1))
fi
echo ""

# 总结
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  诊断结果${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✅ 所有检查通过！配置正确。${NC}"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠️  发现 $WARNINGS 个警告，建议修复。${NC}"
    exit 0
else
    echo -e "${RED}❌ 发现 $ERRORS 个错误，需要立即修复！${NC}"
    echo ""
    echo -e "${YELLOW}📝 快速修复命令:${NC}"
    
    if [ -n "$TRACKED_SENSITIVE" ]; then
        echo "git rm --cached $TRACKED_SENSITIVE"
    fi
    
    if [ -n "$TRACKED_COMPOSE" ]; then
        echo "git rm --cached $TRACKED_COMPOSE"
    fi
    
    echo ""
    echo "然后提交更改:"
    echo "git commit -m \"Remove sensitive files from tracking\""
    exit 1
fi
