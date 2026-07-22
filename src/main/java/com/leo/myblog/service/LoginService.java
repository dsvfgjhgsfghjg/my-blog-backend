package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.LoginStep1Request;
import com.leo.myblog.dto.request.LoginStep2ChallengeRequest;
import com.leo.myblog.dto.request.LoginStep2PasswordRequest;
import com.leo.myblog.dto.response.LoginPollResponse;
import com.leo.myblog.dto.response.LoginResponse;
import com.leo.myblog.dto.response.LoginStep1Response;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.entity.User;
import com.leo.myblog.entity.UserPreference;
import com.leo.myblog.enums.LoginMethod;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AppDeviceMapper;
import com.leo.myblog.mapper.UserMapper;
import com.leo.myblog.util.JwtUtil;
import com.leo.myblog.util.UserAgentParser;
import com.leo.myblog.config.CookieSecurityConfig;
import com.leo.myblog.websocket.WebSocketSessionManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 两步登录服务
 * <p>
 * 仿微软登录流程：
 * Step 1: 提交用户名 + 旋转验证码 → 获取可用登录方式
 * Step 2a: 密码登录 → 验证密码 → 返回 Token
 * Step 2b: 挑战推送登录 → 生成三选一挑战 → App 验证 → 返回 Token
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserService userService;
    private final UserPreferenceService userPreferenceService;
    private final AppAuthService appAuthService;
    private final SliderCaptchaService sliderCaptchaService;
    private final JwtUtil jwtUtil;
    private final TokenSessionService tokenSessionService;
    private final LoginRateLimitService loginRateLimitService;
    private final LoginLogService loginLogService;
    private final IpBanService ipBanService;
    private final UserAgentParser userAgentParser;
    private final StringRedisTemplate redisTemplate;
    private final AppDeviceMapper appDeviceMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CookieSecurityConfig cookieSecurityConfig;
    private final WebSocketSessionManager webSocketSessionManager;

    // Redis 键前缀
    private static final String LAST_LOGIN_METHOD_PREFIX = "user:login:last_method:";
    private static final String LOGIN_CHALLENGE_PREFIX = "app:auth:login_challenge:";
    private static final String LOGIN_RESULT_PREFIX = "app:auth:login_result:";
    private static final String LOGIN_PENDING_PREFIX = "app:auth:login_pending:";
    private static final String LAST_VERIFY_METHOD_PREFIX = "user:verify:last_method:";

    private static final long LOGIN_CHALLENGE_TIMEOUT_SECONDS = 60;
    private static final long LAST_METHOD_TTL_DAYS = 30;

    // ==================== Step 1: 验证用户名 + 验证码 ====================

    /**
     * 第一步：提交用户名和旋转验证码
     * 返回用户可用的登录方式
     */
    public LoginStep1Response step1(LoginStep1Request request) {
        // 1. 验证码验证
        validateCaptcha(request);

        // 2. 安全检查：IP 封禁
        String clientIp = getClientIpAddress();
        if (ipBanService.isIpBanned(clientIp)) {
            log.warn("IP被封禁，拒绝登录: ip={}", clientIp);
            throw new BusinessException("IP已被封禁，无法登录");
        }

        // 3. 查询用户
        String normalizedUsername = request.getUsername().trim().toLowerCase();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        User user = userMapper.selectOne(wrapper);

        LoginStep1Response response = new LoginStep1Response();

        if (user == null) {
            // 用户不存在，不暴露信息
            response.setUserExists(false);
            response.setDefaultMethod(LoginMethod.PASSWORD.getValue());
            response.setAvailableMethods(List.of(LoginMethod.PASSWORD.getValue()));
            response.setHasBoundDevices(false);
            response.setDevicesOnline(false);
            response.setOnlineDeviceNames(List.of());
            return response;
        }

        // 4. 用户存在，获取偏好设置
        response.setUserExists(true);

        // 获取默认登录方式
        UserPreference preference = userPreferenceService.getUserPreference(user.getId());
        String defaultMethod = LoginMethod.PASSWORD.getValue();
        if (preference != null && preference.getDefaultLoginMethod() != null) {
            defaultMethod = preference.getDefaultLoginMethod();
        }

        // 检查设备绑定情况
        boolean hasBoundDevices = false;
        boolean devicesOnline = false;
        List<String> onlineDeviceNames = new ArrayList<>();

        List<AppDevice> devices = appAuthService.getUserDevices(user.getId());
        if (devices != null && !devices.isEmpty()) {
            hasBoundDevices = true;
            for (AppDevice device : devices) {
                if (webSocketSessionManager.isDeviceOnline(device.getDeviceId())) {
                    devicesOnline = true;
                    onlineDeviceNames.add(device.getDeviceName());
                }
            }
        }

        // 如果默认方式是 authenticator_app 但没有设备，回退到 password
        List<String> availableMethods = new ArrayList<>();
        availableMethods.add(LoginMethod.PASSWORD.getValue());
        if (hasBoundDevices) {
            availableMethods.add(LoginMethod.AUTHENTICATOR_APP.getValue());
        }

        if (LoginMethod.AUTHENTICATOR_APP.getValue().equals(defaultMethod) && !hasBoundDevices) {
            defaultMethod = LoginMethod.PASSWORD.getValue();
        }

        response.setDefaultMethod(defaultMethod);
        response.setAvailableMethods(availableMethods);
        response.setHasBoundDevices(hasBoundDevices);
        response.setDevicesOnline(devicesOnline);
        response.setOnlineDeviceNames(onlineDeviceNames);

        return response;
    }

    // ==================== Step 2a: 密码登录 ====================

    /**
     * 第二步：密码方式登录
     */
    public LoginResponse step2Password(LoginStep2PasswordRequest request, HttpServletResponse httpResponse) {
        String clientIp = getClientIpAddress();
        String userAgent = getClientUserAgent();

        // IP 封禁检查
        if (ipBanService.isIpBanned(clientIp)) {
            loginLogService.recordLoginFailure(request.getUsername(), clientIp, userAgent, "IP已被封禁");
            throw new BusinessException("IP已被封禁，无法登录");
        }

        // 登录频率限制
        String normalizedUsername = request.getUsername().trim().toLowerCase();
        if (!loginRateLimitService.isLoginAllowed(normalizedUsername, clientIp)) {
            long remainingTime = loginRateLimitService.getRemainingLockTime(normalizedUsername, clientIp);
            long failureCount = loginRateLimitService.getFailureCount(normalizedUsername, clientIp);
            String lockMessage = String.format("密码错误次数过多，账户已锁定%d秒", remainingTime);
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, lockMessage);
            throw new BusinessException(lockMessage);
        }

        // 查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "用户名不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "账号已被禁用");
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 验证密码
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginRateLimitService.recordLoginFailure(normalizedUsername, clientIp);
            loginLogService.recordLoginFailure(normalizedUsername, clientIp, userAgent, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginRateLimitService.recordLoginSuccess(normalizedUsername, clientIp);

        // 更新登录信息
        userService.updateLoginInfo(user, clientIp);

        // 记录登录成功日志
        loginLogService.recordLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 解析设备信息
        UserAgentParser.DeviceInfo deviceInfo = userAgentParser.parseUserAgent(userAgent);
        String deviceDescription = deviceInfo.getDescription();
        if ("Android".equals(deviceInfo.getOsName()) && deviceInfo.getAndroidRemark() != null) {
            deviceDescription = deviceInfo.getAndroidDescription();
        }

        // 记录 Token 会话
        tokenSessionService.recordSession(user.getId(), token, clientIp, userAgent, deviceDescription);

        // 设置 HttpOnly Cookie
        Cookie authCookie = cookieSecurityConfig.createSecureCookie(token);
        httpResponse.addCookie(authCookie);

        // 记录最后一次登录方式
        recordLastLoginMethod(user.getId(), LoginMethod.PASSWORD.getValue());

        log.info("两步登录（密码）成功: userId={}, username={}, IP={}", user.getId(), user.getUsername(), clientIp);

        // 构建响应
        return buildLoginResponse(user, token);
    }

    // ==================== Step 2b: 挑战推送登录 ====================

    /**
     * 第二步：挑战推送方式登录
     * 生成登录挑战并排队，等待 App 端验证
     */
    public LoginPollResponse step2Challenge(LoginStep2ChallengeRequest request) {
        String clientIp = getClientIpAddress();
        String userAgent = getClientUserAgent();

        String normalizedUsername = request.getUsername().trim().toLowerCase();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 检查是否有绑定设备
        if (!appAuthService.hasBoundDevice(user.getId())) {
            throw new BusinessException("没有绑定的 App 设备，请先绑定设备或使用密码登录");
        }

        // 生成登录挑战
        LoginChallenge challenge = generateLoginChallenge(user.getId());

        log.info("生成登录挑战: userId={}, challengeId={}", user.getId(), challenge.getChallengeId());

        LoginPollResponse response = new LoginPollResponse();
        response.setStatus("pending");
        // 将 raw challengeId 存储到 Redis 中关联 userId，以便轮询时使用
        String pollKey = LOGIN_CHALLENGE_PREFIX + challenge.getChallengeId();
        redisTemplate.opsForValue().set(pollKey,
                user.getId() + ":" + challenge.getChallengeId(),
                LOGIN_CHALLENGE_TIMEOUT_SECONDS + 30, TimeUnit.SECONDS);

        // 返回 challengeId 给前端，前端用这个 ID 轮询
        response.setStatus("queued");

        return response;
    }

    /**
     * 生成登录挑战
     */
    private LoginChallenge generateLoginChallenge(Long userId) {
        SecureRandom random = new SecureRandom();

        // 生成 3 个 0-99 的随机整数
        int[] numbers = new int[3];
        for (int i = 0; i < 3; i++) {
            numbers[i] = random.nextInt(100);
        }

        // 随机选择其中一个作为正确答案
        int correctIndex = random.nextInt(3);
        int correctNumber = numbers[correctIndex];

        // 生成挑战 ID
        String challengeId = UUID.randomUUID().toString().replace("-", "");

        // 存储登录挑战数据到 Redis
        String resultKey = LOGIN_RESULT_PREFIX + challengeId;
        redisTemplate.opsForValue().set(resultKey, "pending",
                LOGIN_CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 存储挑战详情（供 WebSocket 验证时使用）
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        String challengeData = userId + ":" + correctNumber;
        redisTemplate.opsForValue().set(challengeKey, challengeData,
                LOGIN_CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 加入用户待处理挑战集合
        String pendingKey = LOGIN_PENDING_PREFIX + userId;
        redisTemplate.opsForSet().add(pendingKey, challengeId);
        redisTemplate.expire(pendingKey, LOGIN_CHALLENGE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);

        // 也存储到原有的挑战系统中（用于 WebSocket 推送和验证兼容）
        // 但注意：原有格式是 userId:correctNumber，我们使用 LOGIN_CHALLENGE_PREFIX 命名空间完全隔离
        // 不需要修改原有挑战系统

        long expiresAt = System.currentTimeMillis() / 1000 + LOGIN_CHALLENGE_TIMEOUT_SECONDS;

        log.info("生成登录挑战: challengeId={}, userId={}, numbers={}, correctNumber={}",
                challengeId, userId, Arrays.toString(numbers), correctNumber);

        return new LoginChallenge(challengeId, numbers, correctNumber, expiresAt);
    }

    /**
     * 轮询登录挑战结果
     */
    public LoginPollResponse pollLoginResult(String challengeId) {
        String resultKey = LOGIN_RESULT_PREFIX + challengeId;
        String result = redisTemplate.opsForValue().get(resultKey);

        LoginPollResponse response = new LoginPollResponse();

        if (result == null) {
            // 挑战不存在或已过期
            response.setStatus("expired");
            return response;
        }

        if ("pending".equals(result)) {
            response.setStatus("pending");
            return response;
        }

        if ("rejected".equals(result)) {
            // 清理数据
            cleanupLoginChallenge(challengeId);
            response.setStatus("rejected");
            return response;
        }

        // 格式: approved:{token}
        if (result.startsWith("approved:")) {
            String token = result.substring(9);
            // 解析 token 获取用户信息
            try {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                User user = userMapper.selectById(userId);

                if (user != null) {
                    response.setToken(token);
                    response.setUserId(user.getId());
                    response.setNickname(user.getNickname());
                    if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                        response.setAvatar("/api/users/avatar/" + user.getId());
                    }
                }
            } catch (Exception e) {
                log.error("解析登录挑战 Token 失败: challengeId={}", challengeId, e);
            }

            response.setStatus("approved");

            // 记录最后一次登录方式
            try {
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    recordLastLoginMethod(userId, LoginMethod.AUTHENTICATOR_APP.getValue());
                }
            } catch (Exception e) {
                log.warn("记录登录方式失败", e);
            }

            // 清理挑战数据（延迟清理，让前端能读到结果）
            redisTemplate.delete(resultKey);
            cleanupLoginChallenge(challengeId);

            return response;
        }

        response.setStatus("unknown");
        return response;
    }

    /**
     * 验证登录挑战（由 WebSocket 处理程序在 App 响应后调用）
     *
     * @param challengeId    挑战 ID
     * @param userId         用户 ID
     * @param selectedNumber 用户选择的数字
     * @return 验证结果
     */
    public LoginChallengeResult verifyLoginChallenge(String challengeId, Long userId, int selectedNumber) {
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        String storedData = redisTemplate.opsForValue().get(challengeKey);

        if (storedData == null) {
            log.warn("登录挑战已过期: challengeId={}", challengeId);
            return LoginChallengeResult.expired();
        }

        String[] parts = storedData.split(":");
        if (parts.length != 2) {
            log.warn("登录挑战数据格式错误: challengeId={}", challengeId);
            return LoginChallengeResult.failed("数据异常");
        }

        long storedUserId = Long.parseLong(parts[0]);
        int correctNumber = Integer.parseInt(parts[1]);

        if (storedUserId != userId) {
            log.warn("登录挑战用户不匹配: challengeId={}, expectedUserId={}, actualUserId={}",
                    challengeId, storedUserId, userId);
            return LoginChallengeResult.failed("用户身份不匹配");
        }

        if (selectedNumber == correctNumber) {
            // 验证通过，生成 JWT Token
            User user = userMapper.selectById(userId);
            if (user == null) {
                return LoginChallengeResult.failed("用户不存在");
            }

            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            // 记录 Token 会话
            String clientIp = getClientIpAddress();
            String userAgent = getClientUserAgent();
            tokenSessionService.recordSession(user.getId(), token, clientIp, userAgent, "Authenticator App");

            // 更新登录信息
            user.setLastLoginIp(clientIp);
            user.setLastLoginTime(LocalDateTime.now());
            user.setLoginCount(user.getLoginCount() != null ? user.getLoginCount() + 1 : 1);
            userMapper.updateById(user);

            // 存储验证结果
            String resultKey = LOGIN_RESULT_PREFIX + challengeId;
            redisTemplate.opsForValue().set(resultKey, "approved:" + token,
                    LOGIN_CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("登录挑战验证通过: challengeId={}, userId={}", challengeId, userId);
            return LoginChallengeResult.approved(token);
        } else {
            // 验证失败
            String resultKey = LOGIN_RESULT_PREFIX + challengeId;
            redisTemplate.opsForValue().set(resultKey, "rejected",
                    LOGIN_CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.warn("登录挑战验证失败: challengeId={}, userId={}, selected={}, correct={}",
                    challengeId, userId, selectedNumber, correctNumber);
            return LoginChallengeResult.failed("数字选择错误");
        }
    }

    /**
     * 获取用户待处理的登录挑战（供 WebSocket 推送用）
     */
    public List<String> getUserPendingLoginChallengeIds(Long userId) {
        String pendingKey = LOGIN_PENDING_PREFIX + userId;
        Set<String> challengeIds = redisTemplate.opsForSet().members(pendingKey);
        if (challengeIds == null || challengeIds.isEmpty()) {
            return List.of();
        }

        // 过滤掉已过期的
        List<String> validIds = new ArrayList<>();
        for (String challengeId : challengeIds) {
            String resultKey = LOGIN_RESULT_PREFIX + challengeId;
            String result = redisTemplate.opsForValue().get(resultKey);
            if ("pending".equals(result)) {
                validIds.add(challengeId);
            }
        }
        return validIds;
    }

    /**
     * 获取登录挑战详情（供 WebSocket 推送）
     */
    public LoginChallenge getLoginChallengeDetail(String challengeId) {
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        String data = redisTemplate.opsForValue().get(challengeKey);
        if (data == null) {
            return null;
        }

        String[] parts = data.split(":");
        if (parts.length != 2) {
            return null;
        }

        return new LoginChallenge(challengeId, new int[0], Integer.parseInt(parts[1]), 0);
    }

    // ==================== 验证方式记忆 ====================

    /**
     * 记录最后一次登录方式
     */
    public void recordLastLoginMethod(Long userId, String method) {
        String key = LAST_LOGIN_METHOD_PREFIX + userId;
        redisTemplate.opsForValue().set(key, method, LAST_METHOD_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 获取最后一次登录方式
     */
    public String getLastLoginMethod(Long userId) {
        String key = LAST_LOGIN_METHOD_PREFIX + userId;
        String method = redisTemplate.opsForValue().get(key);
        return method != null ? method : LoginMethod.PASSWORD.getValue();
    }

    /**
     * 记录最后验证方式（敏感操作）
     */
    public void recordLastVerifyMethod(Long userId, String method) {
        String key = LAST_VERIFY_METHOD_PREFIX + userId;
        redisTemplate.opsForValue().set(key, method, LAST_METHOD_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 获取最后验证方式
     */
    public String getLastVerifyMethod(Long userId) {
        String key = LAST_VERIFY_METHOD_PREFIX + userId;
        String method = redisTemplate.opsForValue().get(key);
        return method != null ? method : "totp";
    }

    // ==================== 私有方法 ====================

    private void validateCaptcha(LoginStep1Request request) {
        if (request.getCaptchaNonceStr() == null || request.getCaptchaNonceStr().trim().isEmpty()) {
            throw new BusinessException("请完成旋转验证码");
        }
        if (request.getCaptchaAngle() == null) {
            throw new BusinessException("请完成旋转验证码（旋转角度必须提供）");
        }

        java.util.List<SliderCaptchaService.TrajectoryPoint> trajectory = null;
        if (request.getCaptchaTrajectory() != null && !request.getCaptchaTrajectory().isEmpty()) {
            trajectory = new java.util.ArrayList<>();
            for (com.leo.myblog.dto.request.LoginRequest.TrajectoryPointDTO point : request.getCaptchaTrajectory()) {
                trajectory.add(new SliderCaptchaService.TrajectoryPoint(
                        point.getX(), point.getY(), point.getT()
                ));
            }
        }

        boolean captchaPassed = sliderCaptchaService.verifyCaptcha(
                request.getCaptchaNonceStr(),
                request.getCaptchaAngle(),
                trajectory,
                request.getCaptchaTotalTime()
        );

        if (!captchaPassed) {
            throw new BusinessException("验证码验证失败，请重试");
        }
    }

    private void cleanupLoginChallenge(String challengeId) {
        // 删除挑战相关的所有 key
        redisTemplate.delete(LOGIN_CHALLENGE_PREFIX + challengeId);
        redisTemplate.delete(LOGIN_RESULT_PREFIX + challengeId);

        // 挑战 ID 是全局唯一的，且没有 userId 信息，所以无法精确从 user pending set 中删除
        // 让 TTL 自动过期即可
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
        }
        return "unknown";
    }

    private String getClientUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.warn("获取 User-Agent 失败", e);
        }
        return "unknown";
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            response.setAvatar("/api/users/avatar/" + user.getId());
        } else {
            response.setAvatar(null);
        }
        return response;
    }

    // ==================== 内部类 ====================

    /**
     * 登录挑战信息
     */
    public static class LoginChallenge {
        private final String challengeId;
        private final int[] numbers;
        private final int correctNumber;
        private final long expiresAt;

        public LoginChallenge(String challengeId, int[] numbers, int correctNumber, long expiresAt) {
            this.challengeId = challengeId;
            this.numbers = numbers;
            this.correctNumber = correctNumber;
            this.expiresAt = expiresAt;
        }

        public String getChallengeId() { return challengeId; }
        public int[] getNumbers() { return numbers; }
        public int getCorrectNumber() { return correctNumber; }
        public long getExpiresAt() { return expiresAt; }
    }

    /**
     * 登录挑战验证结果
     */
    public static class LoginChallengeResult {
        private final boolean approved;
        private final boolean expired;
        private final String message;
        private final String token;

        private LoginChallengeResult(boolean approved, boolean expired, String message, String token) {
            this.approved = approved;
            this.expired = expired;
            this.message = message;
            this.token = token;
        }

        public static LoginChallengeResult approved(String token) {
            return new LoginChallengeResult(true, false, "验证通过", token);
        }

        public static LoginChallengeResult failed(String message) {
            return new LoginChallengeResult(false, false, message, null);
        }

        public static LoginChallengeResult expired() {
            return new LoginChallengeResult(false, true, "挑战已过期", null);
        }

        public boolean isApproved() { return approved; }
        public boolean isExpired() { return expired; }
        public String getMessage() { return message; }
        public String getToken() { return token; }
    }
}