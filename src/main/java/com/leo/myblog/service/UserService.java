package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.request.ChangePasswordRequest;
import com.leo.myblog.dto.request.GetUserListRequest;
import com.leo.myblog.dto.request.LoginRequest;
import com.leo.myblog.dto.request.RegisterRequest;
import com.leo.myblog.dto.request.UpdateUserRequest;
import com.leo.myblog.dto.response.LoginResponse;
import com.leo.myblog.dto.response.UserBasicResponse;
import com.leo.myblog.dto.response.UserResponse;
import com.leo.myblog.entity.User;
import com.leo.myblog.enums.NotificationType;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.UserMapper;
import com.leo.myblog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 用户服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserCacheService userCacheService;
    private final LoginRateLimitService loginRateLimitService;  // 安全修复V3
    private final LoginLogService loginLogService;
    private final SliderCaptchaService sliderCaptchaService;  // 登录日志服务
    private final UserPreferenceService userPreferenceService;  // 用户偏好服务
    private final AbilityService abilityService;  // 能力管理服务
    private final com.leo.myblog.service.TotpSessionService totpSessionService;  // TOTP 会话服务
    private final TokenSessionService tokenSessionService;  // Token会话服务
    private final com.leo.myblog.util.UserAgentParser userAgentParser;  // UA解析器
    private final IpBanService ipBanService;  // IP封禁服务
    private final NotificationService notificationService;  // 站内通知服务
    private final SessionRevocationService sessionRevocationService;  // 登录态吊销

    /**
     * 用户注册
     * 安全修复V8: 用户名规范化处理（去除首尾空白、转小写）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // 安全修复V8: 用户名规范化 - 去除首尾空白并转小写
        String normalizedUsername = request.getUsername().trim().toLowerCase();
        
        // 验证规范化后的用户名不为空
        if (normalizedUsername.isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        
        // 检查用户名是否已存在（使用规范化后的用户名）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }
        
        // 创建用户（使用规范化后的用户名）
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : normalizedUsername);
        user.setIsAdmin(false);
        user.setEnabled(true);
        user.setCreateTime(LocalDateTime.now());
        
        userMapper.insert(user);
        
        log.info("用户注册成功：{}", user.getUsername());
        
        // 生成Token并返回
        return generateLoginResponse(user);
    }
    
    /**
     * 用户登录
     * 安全修复V3: 添加登录频率限制，防止暴力破解
     * 安全修复V8: 用户名规范化处理（去除首尾空白、转小写）
     * 安全修复V14: 防止空密码绕过
     * 
     * 旋转验证码说明（2026-06-07 重构）：
     * - 图片旋转验证：服务端将图片随机旋转一定角度，前端将图片修剪为圆形
     * - 用户通过滑块将图片旋转回正，提交用户旋转的角度
     * - 服务端验证用户角度是否与原始旋转角度相抵消（允许 ±6° 偏差）
     * - 行为分析：轨迹抖动 + 时间阈值（可选）
     */
    public LoginResponse login(LoginRequest request) {
        // ⚠️ 验证码校验
        if (request.getCaptchaNonceStr() == null || request.getCaptchaNonceStr().trim().isEmpty()) {
            throw new BusinessException("请完成旋转验证码");
        }
        if (request.getCaptchaAngle() == null) {
            throw new BusinessException("请完成旋转验证码（旋转角度必须提供）");
        }
        
        // 转换轨迹数据
        java.util.List<SliderCaptchaService.TrajectoryPoint> trajectory = null;
        if (request.getCaptchaTrajectory() != null && !request.getCaptchaTrajectory().isEmpty()) {
            trajectory = new java.util.ArrayList<>();
            for (LoginRequest.TrajectoryPointDTO point : request.getCaptchaTrajectory()) {
                trajectory.add(new SliderCaptchaService.TrajectoryPoint(
                    point.getX(), point.getY(), point.getT()
                ));
            }
        }
        
        // 实际调用验证码验证（角度验证 + 行为分析）
        boolean captchaPassed = sliderCaptchaService.verifyCaptcha(
            request.getCaptchaNonceStr(),
            request.getCaptchaAngle(),
            trajectory,
            request.getCaptchaTotalTime()
        );
        
        if (!captchaPassed) {
            log.warn("❌ 旋转验证码验证失败: nonceStr={}, angle={}",
                    request.getCaptchaNonceStr(), request.getCaptchaAngle());
            throw new BusinessException("验证码验证失败，请重试");
        }

        
        // 安全修复V14: 严格验证密码不能为空
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        
        // 安全修复V8: 用户名规范化 - 去除首尾空白并转小写
        String normalizedUsername = request.getUsername().trim().toLowerCase();
        
        // 获取客户端IP地址和User-Agent
        String clientIp = getClientIpAddress();
        String userAgent = getClientUserAgent();
        
        // IP封禁检查：如果IP被封禁，直接拒绝登录
        if (ipBanService.isIpBanned(clientIp)) {
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "IP已被封禁");
            log.warn("IP被封禁，拒绝登录: ip={}, username={}", clientIp, normalizedUsername);
            throw new BusinessException("IP已被封禁，无法登录");
        }
        
        // 安全修复V3: 检查是否被锁定
        if (!loginRateLimitService.isLoginAllowed(normalizedUsername, clientIp)) {
            long remainingTime = loginRateLimitService.getRemainingLockTime(normalizedUsername, clientIp);
            long failureCount = loginRateLimitService.getFailureCount(normalizedUsername, clientIp);
            
            String lockMessage;
            if (failureCount == 5) {
                lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败，锁定3分钟）", remainingTime, failureCount);
            } else if (failureCount == 8) {
                lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败，锁定10分钟）", remainingTime, failureCount);
            } else if (failureCount > 8) {
                lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败，锁定30分钟）", remainingTime, failureCount);
            } else {
                lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败）", remainingTime, failureCount);
            }
            
            // 记录登录失败日志
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, lockMessage);
            
            throw new BusinessException(lockMessage);
        }
        
        // 查询用户（使用规范化后的用户名）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            // 安全修复V3: 记录失败尝试
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            // 记录登录失败日志
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "用户名不存在");
            throw new BusinessException("用户名或密码错误");
        }
        
        // 检查用户是否被禁用
        if (!user.getEnabled()) {
            // 安全修复V3: 记录失败尝试
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            // 记录登录失败日志
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "账号已被禁用");
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 安全修复V3: 记录失败尝试
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            // 记录登录失败日志
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }
        
        // 安全修复V3: 登录成功，清除失败计数
        loginRateLimitService.recordLoginSuccess(normalizedUsername, clientIp);
        
        // 更新登录信息
        updateLoginInfo(user, clientIp);
        
        // 记录登录成功日志
        loginLogService.recordLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);
        
        log.info("用户登录成功：{}, IP: {}, 设备: {}", user.getUsername(), clientIp, userAgent);
        
        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        // 解析设备信息
        com.leo.myblog.util.UserAgentParser.DeviceInfo deviceInfo = userAgentParser.parseUserAgent(userAgent);
        String deviceDescription = deviceInfo.getDescription();
        
        // 如果是Android设备，使用更详细的描述
        if ("Android".equals(deviceInfo.getOsName()) && deviceInfo.getAndroidRemark() != null) {
            deviceDescription = deviceInfo.getAndroidDescription();
        }
        
        // 记录Token会话
        tokenSessionService.recordSession(user.getId(), token, clientIp, userAgent, deviceDescription);
        
        // 生成响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        // 修复：将 MinIO 对象路径转换为前端可访问的 API 路径
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            response.setAvatar("/api/users/avatar/" + user.getId());
        } else {
            response.setAvatar(null);
        }
        
        return response;
    }
    
    /**
     * 获取当前用户信息
     */
    public UserResponse getCurrentUser(Long userId) {
        // 使用缓存获取用户信息
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        return convertToUserResponse(user);
    }
    
    /**
     * 获取指定用户的基本信息（脱敏，不包含头像）
     * 普通用户可以调用此接口查看其他用户的基本信息
     */
    public UserBasicResponse getUserBasicInfo(Long targetUserId) {
        User user = userCacheService.getUserById(targetUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 转换为脱敏响应对象（不包含头像）
        UserBasicResponse response = new UserBasicResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setIsAdmin(user.getIsAdmin());
        response.setCreateTime(user.getCreateTime());
        
        return response;
    }
    
    /**
     * 获取用户实体（用于内部调用，如获取头像路径）
     */
    public User getUserById(Long userId) {
        return userCacheService.getUserById(userId);
    }
    
    /**
     * 根据用户名获取用户实体
     */
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username.trim().toLowerCase());
        return userMapper.selectOne(wrapper);
    }
    
    /**
     * 更新用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, UpdateUserRequest request) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 更新昵称和头像
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            // 安全修复V2: 验证 Avatar URL 白名单，防止 SSRF 和恶意链接
            validateAvatarUrl(request.getAvatar());
            user.setAvatar(request.getAvatar());
        }
        
        userMapper.updateById(user);
        
        // 更新缓存
        userCacheService.updateUserCache(user);
        
        log.info("用户信息更新成功：{}", userId);
    }
    
    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String username, String token, ChangePasswordRequest request) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 验证token中的用户名与请求中的用户名是否一致
        if (!username.equals(request.getUsername())) {
            log.warn("用户名验证失败：token中用户名={}, 请求中用户名={}", username, request.getUsername());
            throw new BusinessException(403, "登录信息已经失效，请重新登录");
        }
        
        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        
        // 更新新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        
        // 清除用户缓存（密码已变更）
        userCacheService.clearUserCache(userId);
        
        // 将当前Token加入黑名单，使其立即失效
        if (token != null) {
            long remainingSeconds = jwtUtil.getRemainingExpiration(token);
            tokenBlacklistService.addToBlacklist(userId, token, remainingSeconds);
            log.info("密码修改成功，Token已加入黑名单: userId={}", userId);
        }

        // 其余设备的登录态也一并吊销。
        // 原先只拉黑「当前」这一个 token，结果是本机登出了、别的设备还留着 ——
        // 这个组合本身就不自洽：改密码的语义应当是「所有地方都得重新登录」。
        sessionRevocationService.revokeAll(userId, "用户修改密码");

        log.info("用户密码修改成功：userId={}, username={}", userId, username);
    }
    
    /**
     * 分页查询所有用户（管理员）
     */
    public Page<UserResponse> getUsers(GetUserListRequest request) {
        // 参数校验
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        
        // 限制最大页面大小为50
        if (pageSize > 50) {
            throw new BusinessException("每页最多只能获取50个用户");
        }
        
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        // 按用户名模糊查询
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            wrapper.like(User::getUsername, request.getUsername());
        }
        
        // 按状态筛选
        if (request.getEnabled() != null) {
            wrapper.eq(User::getEnabled, request.getEnabled());
        }
        
        wrapper.orderByDesc(User::getCreateTime);
        
        Page<User> userPage = userMapper.selectPage(page, wrapper);
        
        // 转换为响应对象
        Page<UserResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(userPage, responsePage, "records");
        responsePage.setRecords(userPage.getRecords().stream()
                .map(this::convertToUserResponse)
                .toList());
        
        log.info("管理员查询用户列表: pageNum={}, pageSize={}, total={}", 
                pageNum, pageSize, responsePage.getTotal());
        
        return responsePage;
    }
    
    /**
     * 启用/禁用用户（管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleUserStatus(Long userId, Boolean enabled) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        user.setEnabled(enabled);
        userMapper.updateById(user);
        
        // 清除缓存
        userCacheService.clearUserCache(userId);
        
        log.info("用户状态更新：{} -> {}", userId, enabled ? "启用" : "禁用");
    }
    
    /**
     * 重置用户密码（管理员）
     * @param userId 目标用户ID
     * @param newPassword 管理员指定的新密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 验证密码强度
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
        
        // 使用管理员提供的新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        
        // 清除缓存
        userCacheService.clearUserCache(userId);
        
        // 记录审计日志（不记录明文密码）
        log.info("管理员重置用户密码: userId={}, username={}", userId, user.getUsername());

        // 吊销该用户的全部登录态。
        // 重置密码的动机通常就是「账号可能不安全」，只改密码而不清登录态的话，
        // 攻击者手上的 token（以及 refresh token）依然有效，这次重置等于没做。
        sessionRevocationService.revokeAll(userId, "管理员重置密码");

        // 通知本人。此前这里只有一行被注释掉的 TODO，用户在被重置后完全不知情，
        // 直到下次登录失败才发现 —— 而且那时也分不清是自己记错了还是被人改了。
        //
        // ⚠️ 刻意**不把新密码写进通知正文**：那等于把明文凭据落进 t_notification 表，
        // 正是 t_audit_log 正在做安全评估的那类问题（见 TODO.md）。
        // 站内信只负责告知「密码被改了」，新密码由管理员通过其它渠道转达。
        notificationService.notify(
                userId,
                NotificationType.PASSWORD_RESET,
                "密码已被重置",
                "你的账号密码已被管理员重置。如果这不是预期的操作，请立即联系管理员。",
                null);
    }
    
    /**
     * 修改用户权限（需要 manage_admins 或 add_admin 能力）
     * @param operatorId 操作者ID（当前登录的管理员）
     * @param targetUserId 目标用户ID
     * @param isAdmin 是否设置为管理员
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRole(Long operatorId, Long targetUserId, Boolean isAdmin) {
        // 1. 获取操作者信息
        User operator = userCacheService.getUserById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作者不存在");
        }
        
        // 2. 验证操作者是否为管理员（能力验证已在Controller层完成）
        if (!Boolean.TRUE.equals(operator.getIsAdmin())) {
            log.warn("非管理员尝试修改用户权限: operatorId={}", operatorId);
            throw new BusinessException("权限不足，需要管理员权限");
        }
        
        // 3. 防止自我降级：管理员不能取消自己的管理员权限
        if (operatorId.equals(targetUserId) && !isAdmin) {
            log.warn("管理员尝试取消自己的权限: operatorId={}", operatorId);
            throw new BusinessException("不能取消自己的管理员权限");
        }
        
        // 4. 获取目标用户
        User targetUser = userCacheService.getUserById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在");
        }
        
        // 5. 如果目标用户已经是期望的状态，直接返回
        if (targetUser.getIsAdmin().equals(isAdmin)) {
            log.info("目标用户权限未变更: userId={}, currentRole={}", targetUserId, isAdmin ? "ADMIN" : "USER");
            return;
        }
        
        // 6. 执行权限修改
        targetUser.setIsAdmin(isAdmin);
        
        // 如果设置为管理员，is_super_admin 默认为 false（需要单独设置）
        if (isAdmin) {
            targetUser.setIsSuperAdmin(false);
        }
        
        userMapper.updateById(targetUser);
        
        // 7. 清除缓存
        userCacheService.clearUserCache(targetUserId);
        
        // 8. 如果设置为管理员，不授予任何能力（需要单独通过能力管理接口分配）
        if (isAdmin) {
            log.info("用户被设置为管理员，但未授予任何能力: userId={}", targetUserId);
            log.info("请使用能力管理接口为该管理员分配具体能力");
        }
        
        // 9. 记录审计日志
        log.info("用户权限已修改: operatorId={}, targetUserId={}, oldRole={}, newRole={}",
                operatorId, targetUserId, 
                !isAdmin ? "ADMIN" : "USER",  // 旧角色
                isAdmin ? "ADMIN" : "USER");  // 新角色
    }
    
    /**
     * 设置/取消超级管理员
     * @param operatorId 操作者ID
     * @param targetUserId 目标用户ID
     * @param isSuperAdmin 是否设置为超级管理员
     */
    @Transactional(rollbackFor = Exception.class)
    public void setSuperAdmin(Long operatorId, Long targetUserId, Boolean isSuperAdmin) {
        // 1. 获取操作者信息
        User operator = userCacheService.getUserById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作者不存在");
        }
        
        // 2. 验证操作者是否为超级管理员
        if (!Boolean.TRUE.equals(operator.getIsSuperAdmin())) {
            log.warn("非超级管理员尝试设置超级管理员: operatorId={}", operatorId);
            throw new BusinessException("权限不足，需要超级管理员权限");
        }
        
        // 3. 防止自我操作：不能取消自己的超级管理员权限
        if (operatorId.equals(targetUserId) && !isSuperAdmin) {
            log.warn("超级管理员尝试取消自己的权限: operatorId={}", operatorId);
            throw new BusinessException("不能取消自己的超级管理员权限");
        }
        
        // 4. 获取目标用户
        User targetUser = userCacheService.getUserById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在");
        }
        
        // 5. 目标用户必须是管理员
        if (!Boolean.TRUE.equals(targetUser.getIsAdmin())) {
            throw new BusinessException("目标用户不是管理员，无法设置为超级管理员");
        }
        
        // 6. 如果状态未变更，直接返回
        if (Boolean.TRUE.equals(targetUser.getIsSuperAdmin()) == isSuperAdmin) {
            log.info("目标用户超级管理员状态未变更: userId={}", targetUserId);
            return;
        }
        
        // 7. 执行修改
        targetUser.setIsSuperAdmin(isSuperAdmin);
        userMapper.updateById(targetUser);
        
        // 8. 清除缓存
        userCacheService.clearUserCache(targetUserId);
        
        // 9. 记录审计日志
        log.info("超级管理员权限已修改: operatorId={}, targetUserId={}, isSuperAdmin={}",
                operatorId, targetUserId, isSuperAdmin);
    }
    
    /**
     * 生成登录响应
     */
    private LoginResponse generateLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        // 安全修复V10: 减少返回字段，仅返回必要信息
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        // 修复：将 MinIO 对象路径转换为前端可访问的 API 路径
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            response.setAvatar("/api/users/avatar/" + user.getId());
        } else {
            response.setAvatar(null);
        }
        
        return response;
    }
    
    /**
     * 转换为UserResponse（应用脱敏）
     */
    private UserResponse convertToUserResponse(User user) {
        // 使用新的脱敏方法
        return UserResponse.fromUser(user);
    }
    
    /**
     * 生成随机密码
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // 尝试从代理头中获取真实IP
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                
                // 对于通过多个代理的情况，第一个IP为客户端真实IP
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
        }
        return "unknown";
    }
    
    /**
     * 获取客户端User-Agent
     */
    private String getClientUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userAgent = request.getHeader("User-Agent");
                return userAgent != null ? userAgent : "Unknown";
            }
        } catch (Exception e) {
            log.warn("获取User-Agent失败", e);
        }
        return "Unknown";
    }
    
    /**
     * 更新用户登录信息
     */
    public void updateLoginInfo(User user, String clientIp) {
        // 保存上次登录信息（在更新前）
        String lastLoginIp = user.getLastLoginIp();
        LocalDateTime lastLoginTime = user.getLastLoginTime();
        Long loginCount = user.getLoginCount();
        
        // 更新当前登录信息
        user.setLastLoginIp(clientIp);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLoginCount(loginCount == null ? 1L : loginCount + 1);
        
        userMapper.updateById(user);
        
        // 清除缓存以确保下次获取最新数据
        userCacheService.clearUserCache(user.getId());
        
        log.debug("更新用户登录信息: userId={}, ip={}, count={}", 
                user.getId(), clientIp, user.getLoginCount());
    }
    
    /**
     * 验证用户密码
     * @param userId 用户ID
     * @param password 明文密码
     * @return 是否匹配
     */
    public boolean verifyPassword(Long userId, String password) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        return passwordEncoder.matches(password, user.getPassword());
    }
    
    /**
     * 验证 Avatar URL 白名单
     * 安全修复V2: 防止 SSRF 和恶意链接注入
     * 
     * @param avatarUrl 头像 URL
     */
    private void validateAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return;  // 允许空值（由其他逻辑处理）
        }
        
        // 1. 允许 MinIO 路径格式：/api/users/avatar/{userId} 或 avatars/{userId}/{filename}
        if (avatarUrl.startsWith("/api/users/avatar/") || 
            avatarUrl.startsWith("avatars/")) {
            log.debug("Avatar URL 为 MinIO 路径，验证通过: {}", avatarUrl);
            return;
        }
        
        // 2. 允许 HTTPS 协议的绝对 URL
        if (avatarUrl.startsWith("https://")) {
            try {
                java.net.URL url = new java.net.URL(avatarUrl);
                String host = url.getHost();
                
                // 域名白名单（可根据实际情况配置）
                // 目前允许所有 HTTPS 域名，后续可根据需要收紧
                if (host != null && !host.isEmpty()) {
                    log.debug("Avatar URL 为 HTTPS 链接，验证通过: {}", avatarUrl);
                    return;
                }
            } catch (java.net.MalformedURLException e) {
                log.warn("Avatar URL 格式无效: {}", avatarUrl);
                throw new BusinessException("无效的头像 URL 格式");
            }
        }
        
        // 3. 拒绝所有其他协议（file://, http://, javascript:, data: 等）
        log.warn("Avatar URL 被拒绝: {} (不允许的协议或格式)", avatarUrl);
        throw new BusinessException("头像 URL 必须是 MinIO 路径或 HTTPS 链接");
    }
    
    /**
     * 注销账户（软删除）
     * - 用户名改为"账户已注销_{原用户名}_{用户ID}"
     * - 昵称改为"已注销用户"
     * - 头像重置为默认值
     * - 禁用账户（enabled = false）
     * - 清除密码（设置为随机值，防止再次登录）
     * - 删除用户偏好设置
     * - 删除用户能力
     * - 清除 TOTP 会话
     * @param userId 要注销的用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        String originalUsername = user.getUsername();
        log.info("开始注销账户: userId={}, username={}", userId, originalUsername);
        
        // 1. 修改用户名为"账户已注销_{原用户名}_{用户ID}"
        String newUsername = "账户已注销_" + originalUsername + "_" + userId;
        user.setUsername(newUsername);
        
        // 2. 修改昵称为"已注销用户"
        user.setNickname("已注销用户");
        
        // 3. 重置头像为默认值（空字符串）
        user.setAvatar("");
        
        // 4. 禁用账户
        user.setEnabled(false);
        
        // 5. 清除密码（设置为随机值，防止再次登录）
        String randomPassword = generateRandomPassword() + System.currentTimeMillis();
        user.setPassword(passwordEncoder.encode(randomPassword));
        
        // 6. 清空邮箱和手机（隐私保护）
        user.setEmail(null);
        user.setPhone(null);
        
        userMapper.updateById(user);
        
        // 7. 删除用户偏好设置
        try {
            userPreferenceService.deleteParticleMode(userId);
            log.info("已删除用户偏好设置: userId={}", userId);
        } catch (Exception e) {
            log.warn("删除用户偏好设置失败（可能不存在）: userId={}", userId, e);
        }
        
        // 8. 删除用户能力（如果是管理员）
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            try {
                abilityService.removeAllAbilities(userId);
                log.info("已删除用户能力: userId={}", userId);
            } catch (Exception e) {
                log.warn("删除用户能力失败: userId={}", userId, e);
            }
        }
        
        // 9. 清除 TOTP 会话
        try {
            totpSessionService.clearSession(userId);
            log.info("已清除 TOTP 会话: userId={}", userId);
        } catch (Exception e) {
            log.warn("清除 TOTP 会话失败: userId={}", userId, e);
        }
        
        // 10. 清除用户缓存
        userCacheService.clearUserCache(userId);
        
        log.info("账户注销成功: userId={}, originalUsername={}, newUsername={}", 
                userId, originalUsername, newUsername);
    }
}
