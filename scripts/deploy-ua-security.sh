#!/bin/bash
# UA安全拦截系统部署和验证脚本

set -e  # 遇到错误立即退出

echo "=========================================="
echo "UA安全拦截系统部署"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查是否在项目根目录
if [ ! -f "build.gradle" ]; then
    echo -e "${RED}错误: 请在项目根目录运行此脚本${NC}"
    exit 1
fi

echo -e "${BLUE}步骤 1/5: 检查数据库迁移脚本...${NC}"
if [ -f "src/main/resources/db/migration/V18__create_ua_blacklist_tables.sql" ]; then
    echo -e "${GREEN}✓ V18迁移脚本存在${NC}"
else
    echo -e "${RED}✗ V18迁移脚本不存在${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}步骤 2/5: 检查Java源文件...${NC}"

FILES=(
    "src/main/java/com/leo/myblog/entity/UaBlacklist.java"
    "src/main/java/com/leo/myblog/entity/SecurityEvent.java"
    "src/main/java/com/leo/myblog/entity/IpBan.java"
    "src/main/java/com/leo/myblog/mapper/UaBlacklistMapper.java"
    "src/main/java/com/leo/myblog/mapper/SecurityEventMapper.java"
    "src/main/java/com/leo/myblog/mapper/IpBanMapper.java"
    "src/main/java/com/leo/myblog/service/SecurityEventService.java"
    "src/main/java/com/leo/myblog/service/IpBanService.java"
    "src/main/java/com/leo/myblog/filter/UaSecurityFilter.java"
    "src/main/java/com/leo/myblog/controller/SecurityAdminController.java"
    "src/main/java/com/leo/myblog/task/SecurityCleanupTask.java"
)

ALL_EXIST=true
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}  ✓ ${file}${NC}"
    else
        echo -e "${RED}  ✗ ${file} 缺失${NC}"
        ALL_EXIST=false
    fi
done

if [ "$ALL_EXIST" = false ]; then
    echo -e "${RED}✗ 部分文件缺失，请检查代码${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}步骤 3/5: 检查@EnableScheduling注解...${NC}"
if grep -q "@EnableScheduling" "src/main/java/com/leo/myblog/MyblogApplication.java"; then
    echo -e "${GREEN}✓ 定时任务已启用${NC}"
else
    echo -e "${YELLOW}⚠ 未找到@EnableScheduling注解，正在添加...${NC}"
    sed -i 's/@SpringBootApplication/@SpringBootApplication\n@EnableScheduling/' src/main/java/com/leo/myblog/MyblogApplication.java
    sed -i '/import org.springframework.boot.autoconfigure.SpringBootApplication;/a import org.springframework.scheduling.annotation.EnableScheduling;' src/main/java/com/leo/myblog/MyblogApplication.java
    echo -e "${GREEN}✓ 已添加@EnableScheduling注解${NC}"
fi

echo ""
echo -e "${BLUE}步骤 4/5: 编译项目...${NC}"
./gradlew build -x test --quiet
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 编译成功${NC}"
else
    echo -e "${RED}✗ 编译失败${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}步骤 5/5: 准备测试脚本...${NC}"
chmod +x scripts/tests/test_ua_security_complete.sh
echo -e "${GREEN}✓ 测试脚本已准备就绪${NC}"

echo ""
echo "=========================================="
echo -e "${GREEN}部署完成！${NC}"
echo "=========================================="
echo ""
echo "下一步操作："
echo ""
echo "1. 启动应用（如果尚未启动）："
echo "   ./gradlew bootRun"
echo ""
echo "2. 等待应用启动后，运行测试脚本："
echo "   ./scripts/tests/test_ua_security_complete.sh"
echo ""
echo "3. 检查数据库表是否创建成功："
echo "   docker exec -it myblog-postgres psql -U postgres -d myblog -c '\\dt t_ua_*'"
echo "   docker exec -it myblog-postgres psql -U postgres -d myblog -c '\\dt t_security_*'"
echo "   docker exec -it myblog-postgres psql -U postgres -d myblog -c '\\dt t_ip_*'"
echo ""
echo "4. 查看默认UA黑名单规则："
echo "   docker exec -it myblog-postgres psql -U postgres -d myblog -c 'SELECT pattern, threat_level, action_type FROM t_ua_blacklist;'"
echo ""
echo "5. 监控安全事件日志："
echo "   tail -f logs/application.log | grep -E '安全事件|蜜罐|封禁'"
echo ""
echo -e "${YELLOW}提示:${NC}"
echo "- 确保PostgreSQL和Redis服务正常运行"
echo "- 确保Flyway自动执行V18迁移脚本"
echo "- 管理员API需要有效的JWT token"
echo ""
