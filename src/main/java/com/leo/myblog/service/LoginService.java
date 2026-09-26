package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.LoginStep1Request;
import com.leo.myblog.dto.request.LoginStep2ChallengeRequest;
import com.leo.myblog.dto.request.LoginStep2PasswordRequest;
import com.leo.myblog.dto.response.LoginPollResponse;
import com.leo.myblog.dto.response.LoginStep1Response;
import com.leo.myblog.dto.response.OAuth2TokenResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.entity.User;
import com.leo.myblog.entity.UserPreference;
import com.leo.myblog.enums.LoginMethod;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.UserMapper;
import com.leo.myblog.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 两步登录服务
 * <p>
 * 仿微软登录流程：
 * Step 1: 提交用户名 + 旋转验证码 → 获取可用登录方式
 * Step 2a: 密码登录 → 验证密码 → 返回 Token
 * Step 2b: 挑战推送登录 → 生成三选一挑战 → App 验证 → 轮询取 Token
 * <p>
 * 关于「数字匹配」（number matching）：
 * 浏览器展示正确数字，App 只收到 3 个候选数字，用户挑出与浏览器一致的那个。
 * 因此 {@link #step2Challenge} 会把 correctNumber 回给发起登录的浏览器，但推送给 App 的
 * 消息里<b>绝不能</b>包含 correctNumber —— 这个约束在
 * {@code AuthWebSocketHandler.buildLoginChallengeMessage} 里必须严格遵守。
 * <p>
 * 关于 Token 签发位置：认证器的批准发生在 WebSocket 线程里（拿不到真实 IP/UA，也没有
 * HttpServletResponse），所以挑战批准时<b>只置状态不签发 Token</b>，
 * 由浏览器轮询 {@link #pollLoginResult} 时在 HTTP 线程里签发并写 Cookie。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserService userService;
    private final UserPreferenceService userPreferenceService;
    private final AppAuthService appAuthService;
    private final SliderCaptchaService sliderCaptchaService;
    private final LoginRateLimitService loginRateLimitService;
    private final LoginLogService loginLogService;
    private final IpBanService ipBanService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final WebSocketSessionManager webSocketSessionManager;
    private final OAuth2TokenService oAuth2TokenService;

    // Redis 键前缀
    private static final String LAST_LOGIN_METHOD_PREFIX = "user:login:last_method:";
    /** 登录挑战详情，Redis Hash，字段见下方 FIELD_* */
    private static final String LOGIN_CHALLENGE_PREFIX = "app:auth:login_challenge:";
    /** 登录挑战结果：pending / rejected / approved:<userId> */
    private static final String LOGIN_RESULT_PREFIX = "app:auth:login_result:";
    /** 用户待处理登录挑战集合（成员为 challengeId），供设备上线后补推 */
    private static final String LOGIN_PENDING_PREFIX = "app:auth:login_pending:";
    private static final String LAST_VERIFY_METHOD_PREFIX = "user:verify:last_method:";

    // 挑战 Hash 的字段名
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_NUMBERS = "numbers";
    private static final String FIELD_CORRECT_NUMBER = "correctNumber";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private static final long LOGIN_CHALLENGE_TIMEOUT_SECONDS = 60;
    /** 结果比挑战本身多留一段时间，让前端能稳定读到终态 */
    private static final long LOGIN_RESULT_TTL_SECONDS = 90;
    private static final long LAST_METHOD_TTL_DAYS = 30;

    /** 已通过第一步验证码的标记（key 后缀为 username:ip），第二步据此放行 */
    private static final String STEP1_PASSED_PREFIX = "login:step1_passed:";
    private static final long STEP1_PASSED_TTL_SECONDS = 300;

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_QUEUED = "queued";
    private static final String APPROVED_VALUE_PREFIX = STATUS_APPROVED + ":";

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

        // 验证码已通过，登记标记供第二步放行。用户不存在也登记 —— 若只在用户存在时登记，
        // 第二步的「请先完成验证码校验」就会变成一条判断用户名是否存在的旁路信息。
        markStep1Passed(normalizedUsername, clientIp);

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

    // ==================== 第二步门禁 ====================

    /**
     * 登记「该用户名 + IP 已通过第一步验证码」
     * <p>
     * 见 {@link #requireStep1Passed}：标记用 set 而非一次性消费，因为认证器路径的
     * 「重新发送」需要重复调用 step2-challenge。
     */
    void markStep1Passed(String normalizedUsername, String clientIp) {
        redisTemplate.opsForValue().set(
                STEP1_PASSED_PREFIX + normalizedUsername + ":" + clientIp,
                "1", STEP1_PASSED_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 校验第二步请求确实来自刚通过第一步验证码的同一个客户端
     * <p>
     * 两个 step2 接口自身都不校验验证码，没有这道门的话：{@code step2-password} 会变成
     * 无验证码的密码爆破入口，{@code step2-challenge} 则可以用来探测用户名是否存在、
     * 是否绑定设备（「用户名或密码错误」与「没有绑定的 App 设备」的差异会泄露）。
     * <p>
     * 标记按「用户名 + IP」绑定并带 TTL。TTL 过期或换了 IP 都只是要求重走一次第一步，
     * 用户重输用户名 + 验证码即可恢复，不会锁死账号。
     */
    void requireStep1Passed(String normalizedUsername, String clientIp) {
        String key = STEP1_PASSED_PREFIX + normalizedUsername + ":" + clientIp;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.warn("第二步请求未通过第一步验证: username={}, ip={}", normalizedUsername, clientIp);
            throw new BusinessException("验证已超时，请返回上一步重新验证");
        }
    }

    // ==================== Step 2a: 密码登录 ====================

    /**
     * 第二步：密码方式登录
     * <p>
     * 本方法已完成频率限制、封禁检查、密码校验与登录日志，因此 Token 签发走
     * {@code issueTokenForUser} 而不是 {@code issueToken}，避免重复校验验证码与重复计数。
     * <p>
     * 只签发 Token，不写 Cookie —— Cookie 由 Controller 写入响应。
     */
    public OAuth2TokenResponse step2Password(LoginStep2PasswordRequest request) {
        String clientIp = getClientIpAddress();
        String userAgent = getClientUserAgent();

        // IP 封禁检查
        if (ipBanService.isIpBanned(clientIp)) {
            loginLogService.recordLoginFailure(request.getUsername(), clientIp, userAgent, "IP已被封禁");
            throw new BusinessException("IP已被封禁，无法登录");
        }

        String normalizedUsername = request.getUsername().trim().toLowerCase();

        // 必须先过第一步的验证码，否则本接口就是一个无验证码的密码爆破入口
        requireStep1Passed(normalizedUsername, clientIp);

        // 登录频率限制
        if (!loginRateLimitService.isLoginAllowed(normalizedUsername, clientIp)) {
            long remainingTime = loginRateLimitService.getRemainingLockTime(normalizedUsername, clientIp);
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

        // 记录最后一次登录方式
        recordLastLoginMethod(user.getId(), LoginMethod.PASSWORD.getValue());

        log.info("两步登录（密码）成功: userId={}, username={}, IP={}", user.getId(), user.getUsername(), clientIp);

        return oAuth2TokenService.issueTokenForUser(
                user, LoginMethod.PASSWORD.getValue(), clientIp, userAgent);
    }

    // ==================== Step 2b: 挑战推送登录 ====================

    /**
     * 第二步：挑战推送方式登录
     * <p>
     * 只负责生成并登记挑战，返回的 {@link LoginChallenge} 供 Controller 推送给在线设备。
     * 主动推送放在 Controller 层是因为 {@code AuthWebSocketHandler} 反向依赖了本服务，
     * 若在此处注入它会形成循环依赖。
     *
     * @return 挑战详情（含 userId、3 个候选数字、正确数字、过期时间）
     */
    public LoginChallenge step2Challenge(LoginStep2ChallengeRequest request) {
        String clientIp = getClientIpAddress();
        String userAgent = getClientUserAgent();

        // IP 封禁检查
        if (ipBanService.isIpBanned(clientIp)) {
            loginLogService.recordLoginFailure(request.getUsername(), clientIp, userAgent, "IP已被封禁");
            throw new BusinessException("IP已被封禁，无法登录");
        }

        String normalizedUsername = request.getUsername().trim().toLowerCase();

        // 必须先过第一步的验证码：否则本接口可被用来枚举用户名是否存在、是否绑定设备
        requireStep1Passed(normalizedUsername, clientIp);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, normalizedUsername);
        User user = userMapper.selectOne(wrapper);

        // 与密码路径不同：这里不记失败计数（不能因为有人点了几下推送就把账号锁死），
        // 但按「用户名 + IP」独立限流，防止推送轰炸 / MFA 疲劳攻击
        if (!loginRateLimitService.isChallengeAllowed(normalizedUsername, clientIp)) {
            long remaining = loginRateLimitService.getChallengeRemainingWindow(normalizedUsername, clientIp);
            log.warn("登录挑战请求过于频繁: username={}, ip={}", normalizedUsername, clientIp);
            throw new BusinessException(String.format("操作过于频繁，请 %d 秒后重试", remaining));
        }
        loginRateLimitService.recordChallengeRequest(normalizedUsername, clientIp);

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

        // 单活跃挑战：作废该用户此前所有未完成的登录挑战，避免用户面对多条推送、
        // 也把攻击者「同时挂多个挑战碰运气」的空间压到 1 个
        invalidateUserLoginChallenges(user.getId());

        LoginChallenge challenge = generateLoginChallenge(user.getId());

        log.info("生成登录挑战: userId={}, challengeId={}, numbers={}, correctNumber={}",
                user.getId(), challenge.getChallengeId(),
                Arrays.toString(challenge.getNumbers()), challenge.getCorrectNumber());

        return challenge;
    }

    /**
     * 生成并登记登录挑战（Redis Hash + 结果占位 + 待推送集合）
     * <p>
     * 3 个候选数字必须持久化：设备上线补推时要靠它还原挑战内容。
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

        String challengeId = UUID.randomUUID().toString().replace("-", "");

        // expiresAt 用「Unix 秒」：App 端 ChallengeDialogFragment 会做 expiresAt * 1000，
        // 前端倒计时同理。写成毫秒会导致 App 一打开就显示「已过期」。
        long expiresAt = System.currentTimeMillis() / 1000 + LOGIN_CHALLENGE_TIMEOUT_SECONDS;

        // 挑战详情（含 3 个候选数字，供设备上线补推时还原）
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        Map<String, String> challengeData = new HashMap<>();
        challengeData.put(FIELD_USER_ID, String.valueOf(userId));
        challengeData.put(FIELD_NUMBERS, numbers[0] + "," + numbers[1] + "," + numbers[2]);
        challengeData.put(FIELD_CORRECT_NUMBER, String.valueOf(correctNumber));
        challengeData.put(FIELD_EXPIRES_AT, String.valueOf(expiresAt));
        redisTemplate.opsForHash().putAll(challengeKey, challengeData);
        redisTemplate.expire(challengeKey, LOGIN_CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 结果占位。注意只存 userId，不存 Token ——
        // 明文凭证不该落在 Redis 里，且 Token 统一在轮询（HTTP 线程）时签发
        redisTemplate.opsForValue().set(LOGIN_RESULT_PREFIX + challengeId, STATUS_PENDING,
                LOGIN_RESULT_TTL_SECONDS, TimeUnit.SECONDS);

        // 加入用户待处理集合，供设备稍后上线时补推
        String pendingKey = LOGIN_PENDING_PREFIX + userId;
        redisTemplate.opsForSet().add(pendingKey, challengeId);
        redisTemplate.expire(pendingKey, LOGIN_CHALLENGE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);

        return new LoginChallenge(challengeId, userId, numbers, correctNumber, expiresAt);
    }

    /**
     * 作废某用户所有未完成的登录挑战
     */
    public void invalidateUserLoginChallenges(Long userId) {
        String pendingKey = LOGIN_PENDING_PREFIX + userId;
        Set<String> challengeIds = redisTemplate.opsForSet().members(pendingKey);
        if (challengeIds == null || challengeIds.isEmpty()) {
            return;
        }

        for (String challengeId : challengeIds) {
            redisTemplate.delete(LOGIN_CHALLENGE_PREFIX + challengeId);
            // 置为 expired，让仍在轮询旧挑战的页面能立刻停下
            redisTemplate.opsForValue().set(LOGIN_RESULT_PREFIX + challengeId, STATUS_EXPIRED,
                    LOGIN_RESULT_TTL_SECONDS, TimeUnit.SECONDS);
        }
        redisTemplate.delete(pendingKey);

        log.info("已作废 {} 个旧的登录挑战: userId={}", challengeIds.size(), userId);
    }

    /**
     * 轮询登录挑战结果
     * <p>
     * Token 在此签发（而非 WebSocket 批准时）：这是唯一能拿到真实 IP/UA、
     * 也是唯一能由 Controller 写入 HttpOnly Cookie 的位置。
     *
     * @param clientIp  客户端 IP（用于登录日志与设备会话记录）
     * @param userAgent 客户端 User-Agent
     */
    public LoginPollResponse pollLoginResult(String challengeId, String clientIp, String userAgent) {
        String resultKey = LOGIN_RESULT_PREFIX + challengeId;
        String result = redisTemplate.opsForValue().get(resultKey);

        LoginPollResponse response = new LoginPollResponse();
        response.setChallengeId(challengeId);

        if (result == null) {
            // 挑战不存在或已过期
            response.setStatus(STATUS_EXPIRED);
            return response;
        }

        if (STATUS_PENDING.equals(result)) {
            response.setStatus(STATUS_PENDING);
            return response;
        }

        if (STATUS_REJECTED.equals(result)) {
            // 不删结果：保持幂等，重复轮询仍得到 rejected，
            // 否则第二次轮询会变成 expired 造成前端困惑
            response.setStatus(STATUS_REJECTED);
            return response;
        }

        if (STATUS_EXPIRED.equals(result)) {
            // 被 invalidateUserLoginChallenges 作废的挑战会写这个字面量，必须显式映射：
            // 否则会落到末尾的 unknown，前端把 unknown 当成「继续轮询」，要白等到倒计时结束
            response.setStatus(STATUS_EXPIRED);
            return response;
        }

        if (result.startsWith(APPROVED_VALUE_PREFIX)) {
            // 原子消费：并发或重复轮询时只有一个请求能拿到令牌，
            // 其余请求会看到 null 并落到 expired 分支（前端应在 approved 后立刻停止轮询）
            String consumed = redisTemplate.opsForValue().getAndDelete(resultKey);
            if (consumed == null || !consumed.startsWith(APPROVED_VALUE_PREFIX)) {
                response.setStatus(STATUS_EXPIRED);
                return response;
            }

            Long userId;
            try {
                userId = Long.parseLong(consumed.substring(APPROVED_VALUE_PREFIX.length()));
            } catch (NumberFormatException e) {
                log.error("登录挑战结果格式异常: challengeId={}, result={}", challengeId, consumed);
                response.setStatus(STATUS_EXPIRED);
                return response;
            }

            User user = userMapper.selectById(userId);
            if (user == null || !user.getEnabled()) {
                log.warn("登录挑战批准但用户不可用: challengeId={}, userId={}", challengeId, userId);
                response.setStatus(STATUS_EXPIRED);
                return response;
            }

            // 签发与 /api/auth2/token 完全一致的 Token（含 refresh_token 与 oauth2_authorization 落库）
            OAuth2TokenResponse token = oAuth2TokenService.issueTokenForUser(
                    user, LoginMethod.AUTHENTICATOR_APP.getValue(), clientIp, userAgent);

            userService.updateLoginInfo(user, clientIp);
            loginLogService.recordLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);
            recordLastLoginMethod(userId, LoginMethod.AUTHENTICATOR_APP.getValue());
            cleanupLoginChallenge(challengeId);

            response.setStatus(STATUS_APPROVED);
            response.setAccessToken(token.getAccessToken());
            response.setRefreshToken(token.getRefreshToken());
            response.setTokenType(token.getTokenType());
            response.setExpiresIn(token.getExpiresIn());
            response.setRefreshTokenExpiresIn(token.getRefreshTokenExpiresIn());
            response.setUserId(user.getId());
            response.setNickname(user.getNickname());
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                response.setAvatar("/api/users/avatar/" + user.getId());
            }

            log.info("认证器登录成功: userId={}, challengeId={}, IP={}", userId, challengeId, clientIp);
            return response;
        }

        response.setStatus("unknown");
        return response;
    }

    /**
     * 验证登录挑战（由 WebSocket 处理程序在 App 响应后调用）
     * <p>
     * 只做「校验 + 置状态」，<b>不签发 Token</b>：本方法运行在 WebSocket 线程里，
     * 拿不到真实 IP/UA（RequestContextHolder 为空），也没有 HttpServletResponse。
     * Token 由 {@link #pollLoginResult} 在 HTTP 线程里签发。
     *
     * @param challengeId    挑战 ID
     * @param userId         用户 ID
     * @param selectedNumber 用户选择的数字
     * @return 验证结果
     */
    public LoginChallengeResult verifyLoginChallenge(String challengeId, Long userId, int selectedNumber) {
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(challengeKey);

        if (stored == null || stored.isEmpty()) {
            log.warn("登录挑战已过期: challengeId={}", challengeId);
            return LoginChallengeResult.expired();
        }

        String storedUserId = (String) stored.get(FIELD_USER_ID);
        String storedCorrect = (String) stored.get(FIELD_CORRECT_NUMBER);
        if (storedUserId == null || storedCorrect == null) {
            log.warn("登录挑战数据格式错误: challengeId={}", challengeId);
            return LoginChallengeResult.failed("数据异常");
        }

        if (!String.valueOf(userId).equals(storedUserId)) {
            log.warn("登录挑战用户不匹配: challengeId={}, expectedUserId={}, actualUserId={}",
                    challengeId, storedUserId, userId);
            return LoginChallengeResult.failed("用户身份不匹配");
        }

        // 先删挑战详情：一次性、防重放。放在写结果之前，
        // 避免重复审批覆盖掉已经写入的 approved 状态
        redisTemplate.delete(challengeKey);
        redisTemplate.opsForSet().remove(LOGIN_PENDING_PREFIX + userId, challengeId);

        int correctNumber;
        try {
            correctNumber = Integer.parseInt(storedCorrect);
        } catch (NumberFormatException e) {
            log.error("登录挑战正确答案异常: challengeId={}, value={}", challengeId, storedCorrect);
            return LoginChallengeResult.failed("数据异常");
        }

        String resultKey = LOGIN_RESULT_PREFIX + challengeId;

        if (selectedNumber == correctNumber) {
            redisTemplate.opsForValue().set(resultKey, APPROVED_VALUE_PREFIX + userId,
                    LOGIN_RESULT_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("登录挑战验证通过: challengeId={}, userId={}", challengeId, userId);
            return LoginChallengeResult.approved();
        }

        redisTemplate.opsForValue().set(resultKey, STATUS_REJECTED,
                LOGIN_RESULT_TTL_SECONDS, TimeUnit.SECONDS);
        log.warn("登录挑战验证失败: challengeId={}, userId={}, selected={}, correct={}",
                challengeId, userId, selectedNumber, correctNumber);
        return LoginChallengeResult.failed("数字选择错误");
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
            if (STATUS_PENDING.equals(result)) {
                validIds.add(challengeId);
            }
        }
        return validIds;
    }

    /**
     * 获取登录挑战详情（供 WebSocket 推送用）
     * <p>
     * 从 Hash 还原 3 个候选数字 —— 之前这里构造的是空数组，
     * 导致推给 App 的数字恒为占位符 0。
     *
     * @return 挑战详情；不存在或格式异常时返回 null
     */
    public LoginChallenge getLoginChallengeDetail(String challengeId) {
        String challengeKey = LOGIN_CHALLENGE_PREFIX + challengeId;
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(challengeKey);
        if (stored == null || stored.isEmpty()) {
            return null;
        }

        try {
            Long userId = Long.parseLong((String) stored.get(FIELD_USER_ID));
            int correctNumber = Integer.parseInt((String) stored.get(FIELD_CORRECT_NUMBER));
            long expiresAt = Long.parseLong((String) stored.get(FIELD_EXPIRES_AT));

            String rawNumbers = (String) stored.get(FIELD_NUMBERS);
            int[] numbers = new int[0];
            if (rawNumbers != null && !rawNumbers.isEmpty()) {
                String[] parts = rawNumbers.split(",");
                numbers = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    numbers[i] = Integer.parseInt(parts[i].trim());
                }
            }

            return new LoginChallenge(challengeId, userId, numbers, correctNumber, expiresAt);
        } catch (NumberFormatException | NullPointerException e) {
            log.error("登录挑战数据解析失败: challengeId={}", challengeId, e);
            return null;
        }
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

    /**
     * 清理登录挑战相关的 Redis 键
     * <p>
     * pending 集合的成员清理已在 {@link #verifyLoginChallenge} /
     * {@link #invalidateUserLoginChallenges} 中完成（那里才知道 userId）；
     * 未清理的残留成员会被 {@link #getUserPendingLoginChallengeIds} 按结果状态过滤掉，
     * 且集合本身有 TTL 兜底。
     */
    private void cleanupLoginChallenge(String challengeId) {
        redisTemplate.delete(LOGIN_CHALLENGE_PREFIX + challengeId);
        redisTemplate.delete(LOGIN_RESULT_PREFIX + challengeId);
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

    // ==================== 内部类 ====================

    /**
     * 登录挑战信息
     */
    public static class LoginChallenge {
        private final String challengeId;
        private final Long userId;
        private final int[] numbers;
        private final int correctNumber;
        private final long expiresAt;

        public LoginChallenge(String challengeId, Long userId, int[] numbers, int correctNumber, long expiresAt) {
            this.challengeId = challengeId;
            this.userId = userId;
            this.numbers = numbers;
            this.correctNumber = correctNumber;
            this.expiresAt = expiresAt;
        }

        public String getChallengeId() { return challengeId; }
        public Long getUserId() { return userId; }
        public int[] getNumbers() { return numbers; }
        public int getCorrectNumber() { return correctNumber; }
        /** 过期时间，Unix 秒 */
        public long getExpiresAt() { return expiresAt; }
    }

    /**
     * 登录挑战验证结果
     * <p>
     * 不含 Token —— 批准只置状态，Token 由轮询时签发。
     */
    public static class LoginChallengeResult {
        private final boolean approved;
        private final boolean expired;
        private final String message;

        private LoginChallengeResult(boolean approved, boolean expired, String message) {
            this.approved = approved;
            this.expired = expired;
            this.message = message;
        }

        public static LoginChallengeResult approved() {
            return new LoginChallengeResult(true, false, "验证通过");
        }

        public static LoginChallengeResult failed(String message) {
            return new LoginChallengeResult(false, false, message);
        }

        public static LoginChallengeResult expired() {
            return new LoginChallengeResult(false, true, "挑战已过期");
        }

        public boolean isApproved() { return approved; }
        public boolean isExpired() { return expired; }
        public String getMessage() { return message; }
    }
}
