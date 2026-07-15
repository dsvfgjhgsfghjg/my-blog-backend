package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.response.DashboardResponse;
import com.leo.myblog.entity.*;
import com.leo.myblog.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 控制台主页仪表盘服务（管理员）
 * 提供控制台主页所需的各种统计数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final UserService userService;

    /**
     * 获取控制台主页仪表盘数据
     *
     * @param userId 当前管理员用户ID
     * @return 完整的仪表盘数据
     */
    public DashboardResponse getDashboard(Long userId) {
        DashboardResponse response = new DashboardResponse();

        // 1. 登录信息
        response.setLoginInfo(buildLoginInfo(userId));

        // 2. 统计卡片
        response.setStatsCards(buildStatsCards());

        // 3. 最近更新时间
        response.setLastUpdateTime(getLastUpdateTime());

        // 4. 快捷操作
        response.setQuickActions(buildQuickActions());

        // 5. 最近动态
        response.setRecentActivities(buildRecentActivities());

        log.debug("控制台主页仪表盘数据已生成: userId={}", userId);
        return response;
    }

    /**
     * 获取控制台主页仪表盘数据（WebSocket 上下文）
     * <p>
     * 与 getDashboard() 的区别：
     * - 不依赖 HTTP ServletRequest（WebSocket 上下文中无法获取）
     * - currentIp 固定返回 "unknown"
     * - 其余数据与 REST API 版本一致
     *
     * @param userId 当前管理员用户ID
     * @return 完整的仪表盘数据
     */
    public DashboardResponse getDashboardForWs(Long userId) {
        DashboardResponse response = new DashboardResponse();

        // 1. 登录信息（WS 上下文中无法获取客户端 IP）
        response.setLoginInfo(buildLoginInfoForWs(userId));

        // 2. 统计卡片
        response.setStatsCards(buildStatsCards());

        // 3. 最近更新时间
        response.setLastUpdateTime(getLastUpdateTime());

        // 4. 快捷操作
        response.setQuickActions(buildQuickActions());

        // 5. 最近动态
        response.setRecentActivities(buildRecentActivities());

        log.debug("控制台主页仪表盘数据已生成（WS）: userId={}", userId);
        return response;
    }

    // ==================== 登录信息 ====================

    /**
     * 构建当前管理员登录信息
     */
    private DashboardResponse.LoginInfo buildLoginInfo(Long userId) {
        User user = userService.getUserById(userId);

        DashboardResponse.LoginInfo loginInfo = new DashboardResponse.LoginInfo();
        loginInfo.setCurrentIp(getClientIp());
        loginInfo.setLastLoginIp(user != null ? user.getLastLoginIp() : "unknown");
        loginInfo.setLastLoginTime(user != null ? user.getLastLoginTime() : null);
        loginInfo.setLoginCount(user != null ? user.getLoginCount() : 0L);

        return loginInfo;
    }

    /**
     * 构建当前管理员登录信息（WebSocket 上下文）
     * <p>
     * 与 buildLoginInfo() 的区别：
     * - currentIp 固定返回 "unknown"（WS 上下文中无法获取 HTTP 请求的客户端 IP）
     *
     * @param userId 当前管理员用户ID
     * @return 登录信息
     */
    private DashboardResponse.LoginInfo buildLoginInfoForWs(Long userId) {
        User user = userService.getUserById(userId);

        DashboardResponse.LoginInfo loginInfo = new DashboardResponse.LoginInfo();
        loginInfo.setCurrentIp("unknown");
        loginInfo.setLastLoginIp(user != null ? user.getLastLoginIp() : "unknown");
        loginInfo.setLastLoginTime(user != null ? user.getLastLoginTime() : null);
        loginInfo.setLoginCount(user != null ? user.getLoginCount() : 0L);

        return loginInfo;
    }

    // ==================== 统计卡片 ====================

    /**
     * 构建统计卡片数据（含百分比变化）
     */
    private DashboardResponse.StatsCards buildStatsCards() {
        DashboardResponse.StatsCards stats = new DashboardResponse.StatsCards();

        // 用户统计
        stats.setTotalUsers(buildUserStats());

        // 文章统计
        stats.setTotalPosts(buildPostStats());

        // 今日访问统计（基于登录日志中的成功登录数作为日活指标）
        stats.setTodayVisits(buildTodayVisitsStats());

        return stats;
    }

    /**
     * 用户统计：当前总数、与上周同期对比
     */
    private DashboardResponse.TotalCount buildUserStats() {
        long currentCount = countUsers(null);

        // 计算上周同期的用户数
        LocalDateTime lastWeekStart = LocalDateTime.now().minusWeeks(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime lastWeekEnd = LocalDateTime.now().minusWeeks(1).with(LocalTime.MAX);
        long lastWeekCount = countUsersBefore(lastWeekEnd);

        return computeTotalCount(currentCount, lastWeekCount);
    }

    /**
     * 文章统计：当前总数（已发布）、与上周同期对比
     */
    private DashboardResponse.TotalCount buildPostStats() {
        // 已发布的文章数
        LambdaQueryWrapper<Post> publishedWrapper = new LambdaQueryWrapper<>();
        publishedWrapper.eq(Post::getStatus, 1)
                .eq(Post::getIsDeleted, false);
        long currentCount = postMapper.selectCount(publishedWrapper);

        // 上周同期的文章数
        LocalDateTime lastWeekEnd = LocalDateTime.now().minusWeeks(1).with(LocalTime.MAX);
        LambdaQueryWrapper<Post> lastWeekWrapper = new LambdaQueryWrapper<>();
        lastWeekWrapper.eq(Post::getStatus, 1)
                .eq(Post::getIsDeleted, false)
                .lt(Post::getCreateTime, lastWeekEnd);
        long lastWeekCount = postMapper.selectCount(lastWeekWrapper);

        return computeTotalCount(currentCount, lastWeekCount);
    }

    /**
     * 今日访问统计：今日成功登录数 vs 昨日
     * 作为日活（DAU）的简易指标
     */
    private DashboardResponse.TotalCount buildTodayVisitsStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        // 今日成功登录数
        LambdaQueryWrapper<LoginLog> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(LoginLog::getLoginResult, "SUCCESS")
                .between(LoginLog::getLoginTime, todayStart, todayEnd);
        long todayCount = loginLogMapper.selectCount(todayWrapper);

        // 昨日成功登录数
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime yesterdayStart = yesterday.atStartOfDay();
        LocalDateTime yesterdayEnd = yesterday.atTime(LocalTime.MAX);
        LambdaQueryWrapper<LoginLog> yesterdayWrapper = new LambdaQueryWrapper<>();
        yesterdayWrapper.eq(LoginLog::getLoginResult, "SUCCESS")
                .between(LoginLog::getLoginTime, yesterdayStart, yesterdayEnd);
        long yesterdayCount = loginLogMapper.selectCount(yesterdayWrapper);

        return computeTotalCount(todayCount, yesterdayCount);
    }

    /**
     * 计算总数及百分比变化
     *
     * @param current  当前值
     * @param previous 上一周期的值
     * @return TotalCount 对象
     */
    private DashboardResponse.TotalCount computeTotalCount(long current, long previous) {
        DashboardResponse.TotalCount totalCount = new DashboardResponse.TotalCount();
        totalCount.setCurrent(current);

        if (previous == 0) {
            if (current > 0) {
                totalCount.setPercentageChange(100.0);
                totalCount.setTrend("up");
            } else {
                totalCount.setPercentageChange(0.0);
                totalCount.setTrend("flat");
            }
        } else {
            double change = ((double) (current - previous) / previous) * 100;
            totalCount.setPercentageChange(Math.round(change * 100.0) / 100.0);
            if (change > 0) {
                totalCount.setTrend("up");
            } else if (change < 0) {
                totalCount.setTrend("down");
            } else {
                totalCount.setTrend("flat");
            }
        }

        return totalCount;
    }

    // ==================== 最近更新时间 ====================

    /**
     * 获取最近更新的时间（取文章最近更新时间）
     */
    private LocalDateTime getLastUpdateTime() {
        // 查询最近更新的文章
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getIsDeleted, false)
                .orderByDesc(Post::getUpdateTime)
                .last("LIMIT 1");
        Post latestPost = postMapper.selectOne(wrapper);

        if (latestPost != null && latestPost.getUpdateTime() != null) {
            return latestPost.getUpdateTime();
        }

        // 如果没有任何文章，则取最近一个用户的创建时间
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.orderByDesc(User::getCreateTime)
                .last("LIMIT 1");
        User latestUser = userMapper.selectOne(userWrapper);
        if (latestUser != null) {
            return latestUser.getCreateTime();
        }

        return LocalDateTime.now();
    }

    // ==================== 快捷操作 ====================

    /**
     * 构建快捷操作列表
     */
    private List<DashboardResponse.QuickAction> buildQuickActions() {
        List<DashboardResponse.QuickAction> actions = new ArrayList<>();

        actions.add(new DashboardResponse.QuickAction(
                "new_post", "写文章", "edit", "/admin/posts/create"
        ));
        actions.add(new DashboardResponse.QuickAction(
                "manage_posts", "文章管理", "list", "/admin/posts"
        ));
        actions.add(new DashboardResponse.QuickAction(
                "manage_comments", "评论管理", "message-square", "/admin/comments"
        ));
        actions.add(new DashboardResponse.QuickAction(
                "manage_users", "用户管理", "users", "/admin/users"
        ));
        actions.add(new DashboardResponse.QuickAction(
                "system_config", "系统设置", "settings", "/admin/settings"
        ));
        actions.add(new DashboardResponse.QuickAction(
                "media_library", "媒体库", "image", "/admin/media"
        ));

        return actions;
    }

    // ==================== 最近动态 ====================

    /**
     * 构建最近动态列表（从审计日志中提取）
     * 取最近10条有意义的操作记录
     */
    private List<DashboardResponse.RecentActivity> buildRecentActivities() {
        List<DashboardResponse.RecentActivity> activities = new ArrayList<>();

        // 从审计日志中获取最近的操作记录
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AuditLog::getCreateTime)
                .last("LIMIT 20");
        List<AuditLog> auditLogs = auditLogMapper.selectList(wrapper);

        for (AuditLog log : auditLogs) {
            DashboardResponse.RecentActivity activity = convertAuditLogToActivity(log);
            if (activity != null) {
                activities.add(activity);
            }
            if (activities.size() >= 10) {
                break;
            }
        }

        // 如果审计日志不够，补充其他数据源
        if (activities.size() < 10) {
            fillActivitiesFromOtherSources(activities);
        }

        return activities;
    }

    /**
     * 将审计日志转换为最近动态
     */
    private DashboardResponse.RecentActivity convertAuditLogToActivity(AuditLog auditLog) {
        String type = mapOperationToActivityType(auditLog.getOperationType(), auditLog.getModule());
        if (type == null) {
            return null; // 跳过不关心的日志类型
        }

        String content = buildActivityContent(auditLog);

        return new DashboardResponse.RecentActivity(
                auditLog.getId(),
                type,
                content,
                auditLog.getCreateTime(),
                auditLog.getUsername()
        );
    }

    /**
     * 将操作类型和模块映射为动态类型
     */
    private String mapOperationToActivityType(String operationType, String module) {
        if (operationType == null || module == null) {
            return null;
        }

        String upperOp = operationType.toUpperCase();
        String upperMod = module.toUpperCase();

        // 文章相关
        if ("POST".equals(upperMod)) {
            if ("CREATE".equals(upperOp) || "PUBLISH".equals(upperOp)) {
                return "post_publish";
            }
            if ("UPDATE".equals(upperOp) && "置顶".equals(upperOp)) {
                return "post_top";
            }
            return "post_operation";
        }

        // 评论相关
        if ("COMMENT".equals(upperMod)) {
            return "comment";
        }

        // 用户相关
        if ("USER".equals(upperMod)) {
            if ("REGISTER".equals(upperOp)) {
                return "user_register";
            }
            return "user_operation";
        }

        // 系统相关
        if ("SYSTEM".equals(upperMod)) {
            if ("BACKUP".equals(upperOp)) {
                return "system_backup";
            }
            return "system_operation";
        }

        // 登录/登出
        if ("LOGIN".equals(upperOp) || "LOGOUT".equals(upperOp)) {
            return null; // 登录登出不展示在最近动态中
        }

        return "other";
    }

    /**
     * 从审计日志构建动态内容描述
     */
    private String buildActivityContent(AuditLog auditLog) {
        String operationType = auditLog.getOperationType();
        String module = auditLog.getModule();
        String description = auditLog.getDescription();

        // 如果有自定义描述，优先使用
        if (description != null && !description.isEmpty() && !"N/A".equals(description)) {
            return description;
        }

        // 根据操作类型和模块生成默认描述
        if ("POST".equalsIgnoreCase(module)) {
            String op = operationType != null ? operationType.toUpperCase() : "";
            switch (op) {
                case "CREATE":
                case "PUBLISH":
                    return "发布了新文章";
                case "UPDATE":
                    return "更新了文章";
                case "DELETE":
                    return "删除了文章";
                case "TOP":
                    return "文章被置顶";
                default:
                    return "操作了文章";
            }
        }

        if ("COMMENT".equalsIgnoreCase(module)) {
            return "评论了文章";
        }

        if ("USER".equalsIgnoreCase(module)) {
            if ("REGISTER".equalsIgnoreCase(operationType)) {
                return "注册了新账号";
            }
            return "操作用户";
        }

        if ("SYSTEM".equalsIgnoreCase(module)) {
            if ("BACKUP".equalsIgnoreCase(operationType)) {
                return "系统完成了数据备份";
            }
            return "系统操作";
        }

        return description != null ? description : "执行了操作";
    }

    /**
     * 当审计日志不足时，从其他数据源补充最近动态
     */
    private void fillActivitiesFromOtherSources(List<DashboardResponse.RecentActivity> activities) {
        // 补充用户注册记录
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.orderByDesc(User::getCreateTime)
                .last("LIMIT 5");
        List<User> recentUsers = userMapper.selectList(userWrapper);

        for (User user : recentUsers) {
            if (activities.size() >= 10) break;

            // 检查是否已存在相同内容的动态
            String content = "注册了新账号";
            boolean exists = activities.stream()
                    .anyMatch(a -> content.equals(a.getContent()) && user.getUsername().equals(a.getUsername()));
            if (!exists) {
                activities.add(new DashboardResponse.RecentActivity(
                        user.getId(),
                        "user_register",
                        content,
                        user.getCreateTime(),
                        user.getUsername()
                ));
            }
        }

        // 补充最新的已发布文章记录
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.eq(Post::getStatus, 1)
                .eq(Post::getIsDeleted, false)
                .orderByDesc(Post::getPublishTime)
                .last("LIMIT 5");
        List<Post> recentPosts = postMapper.selectList(postWrapper);

        for (Post post : recentPosts) {
            if (activities.size() >= 10) break;

            String username = "管理员";
            // 尝试获取作者用户名
            User author = userService.getUserById(post.getUserId());
            if (author != null) {
                username = author.getNickname() != null ? author.getNickname() : author.getUsername();
            }

            String content = "发布了新文章《" + post.getTitle() + "》";
            boolean exists = activities.stream()
                    .anyMatch(a -> content.equals(a.getContent()));
            if (!exists) {
                activities.add(new DashboardResponse.RecentActivity(
                        post.getId(),
                        "post_publish",
                        content,
                        post.getPublishTime() != null ? post.getPublishTime() : post.getCreateTime(),
                        username
                ));
            }
        }

        // 按时间排序
        activities.sort((a, b) -> b.getTime().compareTo(a.getTime()));
    }

    // ==================== 辅助方法 ====================

    /**
     * 统计用户总数（在指定时间之前注册的用户数）
     */
    private long countUsersBefore(LocalDateTime beforeTime) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(User::getCreateTime, beforeTime);
        return userMapper.selectCount(wrapper);
    }

    /**
     * 统计用户总数（可选时间范围）
     */
    private long countUsers(LocalDateTime afterTime) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (afterTime != null) {
            wrapper.ge(User::getCreateTime, afterTime);
        }
        return userMapper.selectCount(wrapper);
    }

    /**
     * 获取当前请求的客户端IP地址
     */
    private String getClientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();

                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }

                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }

                return ip != null ? ip : "unknown";
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
        }
        return "unknown";
    }
}
