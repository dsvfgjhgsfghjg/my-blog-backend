package com.leo.myblog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘响应
 * <p>
 * 同时服务于：
 * 1. 控制台主页仪表盘（管理员）- 使用 loginInfo, statsCards, quickActions, recentActivities 等字段
 * 2. Authenticator 首页（App 端）- 使用 user, device, recentHistory, lastAuthResult 等字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    // ==================== 管理员控制台仪表盘字段 ====================

    /** 登录信息区域 */
    private LoginInfo loginInfo;

    /** 统计卡片区域 */
    private StatsCards statsCards;

    /** 最近更新时间 */
    private LocalDateTime lastUpdateTime;

    /** 快捷操作 */
    private List<QuickAction> quickActions;

    /** 最近动态列表 */
    private List<RecentActivity> recentActivities;

    /** 待办计数（用于快捷操作的角标） */
    private TodoCounts todoCounts;

    /** 近 7 日访问趋势（按日期升序，含今日） */
    private List<VisitTrendPoint> visitTrend;

    // ==================== App Authenticator 首页字段 ====================

    /** 用户信息 */
    private DashboardUserInfo user;

    /** 当前设备信息 */
    private DashboardDeviceInfo device;

    /** 最近认证记录 */
    private DashboardRecentHistory recentHistory;

    /** 上次认证结果快捷字段 */
    private DashboardLastAuthResult lastAuthResult;

    /** 最后登录时间（ISO8601） */
    private String lastLoginAt;

    // ==================== 管理员控制台仪表盘内部类 ====================

    /**
     * 登录信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginInfo {
        /** 当前IP地址 */
        private String currentIp;
        /** 上次登录IP地址 */
        private String lastLoginIp;
        /** 上次登录时间 */
        private LocalDateTime lastLoginTime;
        /** 登录总次数 */
        private Long loginCount;
    }

    /**
     * 统计卡片
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsCards {
        /** 总用户数 */
        private TotalCount totalUsers;
        /** 文章总数（已发布） */
        private TotalCount totalPosts;
        /** 评论总数（可见评论） */
        private TotalCount totalComments;
        /** 今日访问（成功登录数） */
        private TotalCount todayVisits;
    }

    /**
     * 待办计数
     * <p>
     * 用于快捷操作的角标，让管理员不必先点进去才知道有事情等着处理。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodoCounts {
        /** 待审核文章数（与待审核列表同源：t_post_audit.audit_status = 0） */
        private Long pendingPosts;
        /** 未读 Bug 反馈数（t_bug_report.status = 'unread'） */
        private Long unreadBugReports;
    }

    /**
     * 访问趋势的一个数据点
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisitTrendPoint {
        /** 日期，yyyy-MM-dd */
        private String date;
        /** 当日成功登录数（与 statsCards.todayVisits 同口径） */
        private Long count;
    }

    /**
     * 总量及百分比变化
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalCount {
        /** 当前值 */
        private Long current;
        /** 百分比变化（比上一周期的增长/下降百分比） */
        private Double percentageChange;
        /** 变化方向: "up", "down", "flat" */
        private String trend;
    }

    /**
     * 快捷操作
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickAction {
        /** 操作标识 */
        private String action;
        /** 显示名称 */
        private String label;
        /** 图标（前端使用） */
        private String icon;
        /** 前端路由路径 */
        private String route;
    }

    /**
     * 最近动态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        /** 动态ID */
        private Long id;
        /** 动态类型：post_publish, comment, system_backup, user_register, post_top 等 */
        private String type;
        /** 动态内容描述 */
        private String content;
        /** 发生时间 */
        private LocalDateTime time;
        /** 关联用户（可选） */
        private String username;
    }

    // ==================== App Authenticator 首页内部类 ====================

    /**
     * 用户信息
     */
    @Data
    public static class DashboardUserInfo {
        /** 用户 ID */
        private Long userId;

        /** 用户名 */
        private String username;

        /** 昵称（可为 null） */
        private String nickname;

        /** 邮箱地址（可为 null） */
        private String email;

        /** 头像可公开访问的 URL（带时间戳防止 CDN 缓存） */
        private String avatarUrl;

        /** 当前用户已绑定的设备总数 */
        private Integer deviceCount;
    }

    /**
     * 当前设备信息
     */
    @Data
    public static class DashboardDeviceInfo {
        /** 当前设备 ID */
        private String deviceId;

        /** 设备显示名称 */
        private String deviceName;

        /** 设备首选加密算法: AES-256-GCM / SM4-GCM */
        private String cipherPref;

        /** 本设备绑定时间（ISO8601） */
        private String boundAt;
    }

    /**
     * 最近认证记录
     */
    @Data
    public static class DashboardRecentHistory {
        /** 该用户的总认证记录数 */
        private Long total;

        /** 最近 5 条记录（按 requestedAt 降序） */
        private List<DashboardHistoryRecord> records;
    }

    /**
     * 单条认证历史记录
     */
    @Data
    public static class DashboardHistoryRecord {
        /** 记录 ID */
        private Long id;

        /** 挑战 ID */
        private String challengeId;

        /** 发起挑战的设备名称 */
        private String deviceName;

        /** 状态: approved / rejected / expired */
        private String status;

        /** 挑战发起时间（ISO8601） */
        private String requestedAt;

        /** 用户响应时间（ISO8601，可为 null） */
        private String respondedAt;

        /** 响应耗时（毫秒） */
        private Long responseTimeMs;
    }

    /**
     * 上次认证结果快捷字段
     */
    @Data
    public static class DashboardLastAuthResult {
        /** 状态: approved / rejected / expired / null（无记录） */
        private String status;

        /** 发起挑战的设备名称 */
        private String deviceName;

        /** 挑战发起时间（ISO8601） */
        private String requestedAt;
    }
}
