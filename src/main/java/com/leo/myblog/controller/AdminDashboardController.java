package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.DashboardResponse;
import com.leo.myblog.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台主页控制器（管理员）
 * 提供管理员控制台主页所需的所有数据
 *
 * 功能：
 * - 登录信息（当前IP、上次登录IP/时间、登录次数）
 * - 统计卡片（总用户数、文章总数、今日访问 + 百分比变化）
 * - 最近更新时间
 * - 快捷操作
 * - 最近动态
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 获取控制台主页仪表盘数据
     * 仅管理员可访问
     *
     * @param userId  当前用户ID（从JWT中解析）
     * @param username 当前用户名（从JWT中解析）
     * @return 完整的仪表盘数据
     */
    @RequireAdmin
    @GetMapping
    public Result<DashboardResponse> getDashboard(
            @RequestAttribute Long userId,
            @RequestAttribute String username) {
        log.debug("管理员获取控制台主页数据: userId={}, username={}", userId, username);
        DashboardResponse dashboard = adminDashboardService.getDashboard(userId);
        return Result.success(dashboard);
    }
}
