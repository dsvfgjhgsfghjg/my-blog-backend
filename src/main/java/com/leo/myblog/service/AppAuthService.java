package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.response.AuthHistoryResponse;
import com.leo.myblog.dto.response.CipherInfoResponse;
import com.leo.myblog.dto.response.DashboardResponse;
import com.leo.myblog.dto.response.UserProfileResponse;
import com.leo.myblog.entity.AppDevice;
import com.leo.myblog.entity.AuthHistory;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AppDeviceMapper;
import com.leo.myblog.mapper.AuthHistoryMapper;
import com.leo.myblog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * App 双因素认证服务
 * <p>
 * 核心职责：
 * 1. 设备码管理（生成、验证设备密钥）
 * 2. 设备绑定（扫码/账号密码两种方式）
 * 3. 生成 3 选 1 挑战
 * 4. 验证挑战结果
 * 5. 配对码管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthService {

    private final AppDeviceMapper appDeviceMapper;
    private final AuthHistoryMapper authHistoryMapper;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    // Redis 键前缀
    private static final String REDIS_PREFIX = "app:auth:";
    private static final String CHALLENGE_PREFIX = REDIS_PREFIX + "challenge:";
    private static final String PAIR_CODE_PREFIX = REDIS_PREFIX + "pair:";
    private static final String DEVICE_SESSION_PREFIX = REDIS_PREFIX + "device_session:";
    private static final String BIND_FAIL_PREFIX = REDIS_PREFIX + "bind_fail:";
    private static final String CHALLENGE_RATE_PREFIX = REDIS_PREFIX + "challenge_rate:";
    private static final String USER_CHALLENGES_PREFIX = REDIS_PREFIX + "user_challenges:";

    // 挑战超时：60 秒
    // 公开给补推 / App 拉取链路：t_auth_history 没有 expires_at 列，
    // 敏感操作挑战的过期时间只能由 requested_at + 本常量推算。
    public static final long CHALLENGE_TIMEOUT_SECONDS = 60;
    // 配对码超时：5 分钟
    private static final long PAIR_CODE_TIMEOUT_SECONDS = 300;
    // 配对码长度
    private static final int PAIR_CODE_LENGTH = 6;

    // 安全限制
    private static final int MAX_BIND_FAILURES = 5;           // 最大绑定失败次数
    private static final long BIND_LOCKOUT_SECONDS = 300;     // 绑定锁定时间（5分钟）
    private static final int MAX_CHALLENGES_PER_MINUTE = 10;  // 每分钟最大挑战生成次数
    private static final long CHALLENGE_RATE_WINDOW = 60;     // 挑战频率窗口（秒）
    private static final int MAX_DEVICES_PER_USER = 5;          // 每个用户最大绑定设备数
    // 敏感操作挑战的消费时效：只接受该时长内被 App 批准的挑战。
    // 挑战本身 60 秒过期（CHALLENGE_TIMEOUT_SECONDS），批准必然发生在窗口内；
    // 这里给 5 分钟是为了吸收前端轮询与人工操作的延迟，同时保证「历史 approved
    // 记录」不会被翻出来重放。取 5 分钟与 TOTP 会话时长一致。
    private static final long CHALLENGE_CONSUME_WINDOW_MINUTES = 5;

    // 挑战状态（与 t_auth_history.status 的取值一致）
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_EXPIRED = "expired";

    private final BCryptPasswordEncoder secretEncoder = new BCryptPasswordEncoder();

    // ==================== 设备码管理 ====================

    /**
     * 生成设备码（Device Secret）
     * <p>
     * 服务端生成一个唯一的设备标识和密钥，用于设备身份验证。
     * deviceId 格式：svr_<UUID>（服务端生成，不可伪造）
     * deviceSecret 格式：32 字节随机 hex 字符串
     *
     * @return 设备码信息（包含 deviceId 和明文 deviceSecret）
     */
    public DeviceSecretResult generateDeviceSecret() {
        SecureRandom random = new SecureRandom();

        // 1. 生成服务端 deviceId：svr_<UUID>
        String deviceId = "svr_" + UUID.randomUUID().toString().replace("-", "");

        // 2. 生成 32 字节随机设备密钥（64 位 hex 字符串）
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        StringBuilder secretHex = new StringBuilder();
        for (byte b : secretBytes) {
            secretHex.append(String.format("%02x", b));
        }
        String plainSecret = secretHex.toString();

        // 3. 生成密钥提示（前4位 + ... + 后4位）
        String hint = plainSecret.substring(0, 4) + "..." + plainSecret.substring(plainSecret.length() - 4);

        // 注意：不在此处插入数据库记录，因为 user_id 为 NOT NULL
        // 设备记录将在绑定时（bindWithPassword / bindWithPairCode）创建

        log.info("生成设备码: deviceId={}, hint={}", deviceId, hint);

        return new DeviceSecretResult(deviceId, plainSecret, hint);
    }

    /**
     * 验证设备码
     * <p>
     * 检查客户端提供的 deviceSecret 是否与数据库中存储的哈希匹配
     *
     * @param deviceId      设备 ID
     * @param deviceSecret  客户端提供的设备密钥明文
     * @return true=验证通过，false=验证失败
     */
    public boolean verifyDeviceSecret(String deviceId, String deviceSecret) {
        if (deviceId == null || deviceSecret == null) {
            return false;
        }

        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice device = appDeviceMapper.selectOne(wrapper);

        if (device == null || device.getDeviceSecret() == null) {
            return false;
        }

        return secretEncoder.matches(deviceSecret, device.getDeviceSecret());
    }

    /**
     * 检查设备是否已绑定用户
     */
    public boolean isDeviceBound(String deviceId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        wrapper.isNotNull(AppDevice::getUserId);
        return appDeviceMapper.selectCount(wrapper) > 0;
    }

    // ==================== 设备绑定 ====================

    /**
     * 账号密码绑定 App 设备（带设备密钥验证）
     *
     * @param username     用户名
     * @param password     密码
     * @param deviceId     设备唯一标识
     * @param deviceName   设备名称
     * @param deviceType   设备类型
     * @param deviceSecret 设备密钥（可选，用于验证设备身份）
     * @return 绑定结果（JWT Token + deviceId）
     */
    @Transactional(rollbackFor = Exception.class)
    public BindResult bindWithPassword(String username, String password,
                                       String deviceId, String deviceName, String deviceType,
                                       String deviceSecret) {
        // 安全增强：绑定失败频率限制
        String bindFailKey = BIND_FAIL_PREFIX + username.trim().toLowerCase();
        String failCountStr = redisTemplate.opsForValue().get(bindFailKey);
        if (failCountStr != null) {
            int failCount = Integer.parseInt(failCountStr);
            if (failCount >= MAX_BIND_FAILURES) {
                long ttl = redisTemplate.getExpire(bindFailKey, TimeUnit.SECONDS);
                log.warn("App 绑定被锁定: username={}, failCount={}, remainingLockTime={}s",
                        username, failCount, ttl);
                throw new BusinessException("绑定尝试次数过多，请 " + ttl + " 秒后重试");
            }
        }

        // 1. 验证用户名密码
        User user = userService.getUserByUsername(username);
        if (user == null) {
            recordBindFailure(bindFailKey);
            throw new BusinessException("用户名或密码错误");
        }
        if (!user.getEnabled()) {
            recordBindFailure(bindFailKey);
            throw new BusinessException("账号已被禁用");
        }

        // 使用 BCrypt 验证密码
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordBindFailure(bindFailKey);
            throw new BusinessException("用户名或密码错误");
        }

        // 绑定成功，清除失败计数
        redisTemplate.delete(bindFailKey);

        // 2. 检查该 deviceId 是否已存在
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice existingDevice = appDeviceMapper.selectOne(wrapper);

        if (existingDevice != null) {
            // 设备已存在
            if (existingDevice.getUserId() != null && !existingDevice.getUserId().equals(user.getId())) {
                // 设备已被其他用户绑定
                throw new BusinessException("该设备已被其他账号绑定，请先解绑");
            }

            // 如果设备已有 deviceSecret，需要验证
            if (existingDevice.getDeviceSecret() != null) {
                if (deviceSecret == null || !secretEncoder.matches(deviceSecret, existingDevice.getDeviceSecret())) {
                    log.warn("设备密钥验证失败: deviceId={}", deviceId);
                    throw new BusinessException("设备密钥验证失败，请重新获取设备码");
                }
            }

            // 同一用户重新绑定同一设备 → 幂等操作：更新设备信息，返回新 Token
            existingDevice.setUserId(user.getId());
            existingDevice.setDeviceName(deviceName);
            if (deviceType != null) existingDevice.setDeviceType(deviceType);
            existingDevice.setLastActiveAt(LocalDateTime.now());
            appDeviceMapper.updateById(existingDevice);

            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            log.info("App 设备重新绑定: userId={}, deviceId={}", user.getId(), deviceId);
            return new BindResult(token, deviceId);
        }

        // 3. 检查用户已有设备数是否超过上限
        long deviceCount = appDeviceMapper.selectCount(
                new LambdaQueryWrapper<AppDevice>().eq(AppDevice::getUserId, user.getId()));
        if (deviceCount >= MAX_DEVICES_PER_USER) {
            throw new BusinessException("已达到最大绑定设备数（" + MAX_DEVICES_PER_USER + "），请先解绑旧设备");
        }

        // 4. 创建设备绑定记录
        AppDevice device = new AppDevice();
        device.setUserId(user.getId());
        device.setDeviceId(deviceId);
        device.setDeviceName(deviceName);
        device.setDeviceType(deviceType != null ? deviceType : "ANDROID");
        device.setCipherPref("AES-256-GCM");
        device.setLastActiveAt(LocalDateTime.now());

        // 如果有 deviceSecret，存储哈希
        if (deviceSecret != null) {
            String hashedSecret = secretEncoder.encode(deviceSecret);
            device.setDeviceSecret(hashedSecret);
            String hint = deviceSecret.substring(0, 4) + "..." + deviceSecret.substring(deviceSecret.length() - 4);
            device.setDeviceSecretHint(hint);
        }

        appDeviceMapper.insert(device);

        // 5. 生成 JWT Token（用于 WebSocket 认证）
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        log.info("App 设备绑定成功: userId={}, deviceId={}, deviceName={}", user.getId(), deviceId, deviceName);

        return new BindResult(token, deviceId);
    }

    /**
     * 账号密码绑定 App 设备（兼容旧版，无 deviceSecret）
     */
    @Transactional(rollbackFor = Exception.class)
    public BindResult bindWithPassword(String username, String password,
                                       String deviceId, String deviceName, String deviceType) {
        return bindWithPassword(username, password, deviceId, deviceName, deviceType, null);
    }

    /**
     * 使用配对码绑定设备（带设备密钥验证）
     * App 端用户在网页上看到配对码后，在 App 中输入此配对码完成绑定
     *
     * @param userId       用户 ID（从 JWT 认证获取）
     * @param pairCode     用户输入的 6 位配对码
     * @param deviceId     设备唯一标识
     * @param deviceName   设备名称
     * @param deviceSecret 设备密钥（可选，用于验证设备身份）
     * @return 设备 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String bindWithPairCode(Long userId, String pairCode, String deviceId, String deviceName, String deviceSecret) {
        // 1. 验证配对码
        String pairKey = PAIR_CODE_PREFIX + pairCode;
        String storedUserId = redisTemplate.opsForValue().get(pairKey);
        if (storedUserId == null) {
            throw new BusinessException("配对码已过期，请重新生成");
        }
        if (!storedUserId.equals(String.valueOf(userId))) {
            throw new BusinessException("配对码无效");
        }

        // 2. 检查该 deviceId 是否已存在
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice existingDevice = appDeviceMapper.selectOne(wrapper);

        if (existingDevice != null) {
            if (existingDevice.getUserId() != null && !existingDevice.getUserId().equals(userId)) {
                throw new BusinessException("该设备已被其他账号绑定");
            }

            // 如果设备已有 deviceSecret，需要验证
            if (existingDevice.getDeviceSecret() != null) {
                if (deviceSecret == null || !secretEncoder.matches(deviceSecret, existingDevice.getDeviceSecret())) {
                    log.warn("设备密钥验证失败（扫码绑定）: deviceId={}", deviceId);
                    throw new BusinessException("设备密钥验证失败，请重新获取设备码");
                }
            }

            // 同一用户重新绑定同一设备 → 幂等操作
            existingDevice.setUserId(userId);
            existingDevice.setDeviceName(deviceName);
            existingDevice.setLastActiveAt(LocalDateTime.now());
            appDeviceMapper.updateById(existingDevice);

            // 清除配对码
            redisTemplate.delete(pairKey);
            log.info("扫码重新绑定: userId={}, deviceId={}", userId, deviceId);
            return deviceId;
        }

        // 3. 检查用户已有设备数是否超过上限
        long deviceCount = appDeviceMapper.selectCount(
                new LambdaQueryWrapper<AppDevice>().eq(AppDevice::getUserId, userId));
        if (deviceCount >= MAX_DEVICES_PER_USER) {
            throw new BusinessException("已达到最大绑定设备数（" + MAX_DEVICES_PER_USER + "），请先解绑旧设备");
        }

        // 4. 创建设备绑定记录
        AppDevice device = new AppDevice();
        device.setUserId(userId);
        device.setDeviceId(deviceId);
        device.setDeviceName(deviceName);
        device.setDeviceType("ANDROID");
        device.setCipherPref("AES-256-GCM");
        device.setLastActiveAt(LocalDateTime.now());

        // 如果有 deviceSecret，存储哈希
        if (deviceSecret != null) {
            String hashedSecret = secretEncoder.encode(deviceSecret);
            device.setDeviceSecret(hashedSecret);
            String hint = deviceSecret.substring(0, 4) + "..." + deviceSecret.substring(deviceSecret.length() - 4);
            device.setDeviceSecretHint(hint);
        }

        appDeviceMapper.insert(device);

        // 5. 清除配对码（一次性使用）
        redisTemplate.delete(pairKey);

        log.info("扫码绑定成功: userId={}, deviceId={}, pairCode={}", userId, deviceId, pairCode);

        return deviceId;
    }

    /**
     * 使用配对码绑定设备（兼容旧版，无 deviceSecret）
     */
    @Transactional(rollbackFor = Exception.class)
    public String bindWithPairCode(Long userId, String pairCode, String deviceId, String deviceName) {
        return bindWithPairCode(userId, pairCode, deviceId, deviceName, null);
    }

    /**
     * 网页端生成配对码
     *
     * @param userId 用户 ID
     * @return 配对码信息
     */
    public PairCodeInfo generatePairCode(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 生成 6 位随机数字配对码
        SecureRandom random = new SecureRandom();
        StringBuilder codeBuilder = new StringBuilder();
        for (int i = 0; i < PAIR_CODE_LENGTH; i++) {
            codeBuilder.append(random.nextInt(10));
        }
        String pairCode = codeBuilder.toString();

        // 存储到 Redis，键格式：app:auth:pair:{pairCode} → userId
        String pairKey = PAIR_CODE_PREFIX + pairCode;
        redisTemplate.opsForValue().set(pairKey, String.valueOf(userId), PAIR_CODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("生成配对码: userId={}, pairCode={}", userId, pairCode);

        return new PairCodeInfo(pairCode, PAIR_CODE_TIMEOUT_SECONDS);
    }

    // ==================== 挑战管理 ====================

    /**
     * 生成 3 选 1 挑战
     * <p>
     * 安全增强：限制同一用户每分钟最多生成 MAX_CHALLENGES_PER_MINUTE 个挑战
     *
     * @param userId 用户 ID
     * @return 挑战信息（不含正确答案）
     */
    public ChallengeInfo generateChallenge(Long userId) {
        // 安全增强：挑战生成频率限制
        String rateKey = CHALLENGE_RATE_PREFIX + userId;
        Long currentCount = redisTemplate.opsForValue().increment(rateKey);
        if (currentCount != null && currentCount == 1) {
            // 第一次设置过期时间
            redisTemplate.expire(rateKey, CHALLENGE_RATE_WINDOW, TimeUnit.SECONDS);
        }
        if (currentCount != null && currentCount > MAX_CHALLENGES_PER_MINUTE) {
            long ttl = redisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
            log.warn("挑战生成频率过高: userId={}, count={}, remainingWindow={}s",
                    userId, currentCount, ttl);
            throw new BusinessException("操作过于频繁，请 " + ttl + " 秒后重试");
        }

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

        // 保存挑战状态到 Redis
        String challengeKey = CHALLENGE_PREFIX + challengeId;
        String challengeData = userId + ":" + correctNumber;
        redisTemplate.opsForValue().set(challengeKey, challengeData, CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 将挑战 ID 加入用户活跃挑战集合
        String userChallengesKey = USER_CHALLENGES_PREFIX + userId;
        redisTemplate.opsForSet().add(userChallengesKey, challengeId);
        redisTemplate.expire(userChallengesKey, CHALLENGE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);

        long expiresAt = System.currentTimeMillis() / 1000 + CHALLENGE_TIMEOUT_SECONDS;

        log.info("生成挑战: challengeId={}, userId={}, expiresAt={}",
                challengeId, userId, expiresAt);

        return new ChallengeInfo(challengeId, numbers, correctNumber, expiresAt);
    }

    /**
     * 验证挑战结果
     *
     * @param challengeId    挑战 ID
     * @param selectedNumber 用户选择的数字
     * @param userId         用户 ID（用于验证）
     * @return true=验证通过，false=验证失败
     */
    public ChallengeResult verifyChallenge(String challengeId, int selectedNumber, Long userId) {
        String challengeKey = CHALLENGE_PREFIX + challengeId;
        String storedData = redisTemplate.opsForValue().get(challengeKey);

        if (storedData == null) {
            log.warn("挑战已过期: challengeId={}", challengeId);
            return ChallengeResult.expired();
        }

        // 解析存储的数据：userId:correctNumber
        String[] parts = storedData.split(":");
        if (parts.length != 2) {
            log.warn("挑战数据格式错误: challengeId={}", challengeId);
            return ChallengeResult.failed("数据异常");
        }

        long storedUserId = Long.parseLong(parts[0]);
        int correctNumber = Integer.parseInt(parts[1]);

        // 验证用户身份
        if (storedUserId != userId) {
            log.warn("挑战用户不匹配: challengeId={}, expectedUserId={}, actualUserId={}",
                    challengeId, storedUserId, userId);
            return ChallengeResult.failed("用户身份不匹配");
        }

        // 删除挑战（一次性使用）
        redisTemplate.delete(challengeKey);
        // 从用户活跃挑战集合中移除
        redisTemplate.opsForSet().remove(USER_CHALLENGES_PREFIX + userId, challengeId);

        // 验证选择的数字
        boolean approved = selectedNumber == correctNumber;
        if (approved) {
            log.info("挑战验证通过: challengeId={}, userId={}", challengeId, userId);
            return ChallengeResult.approved();
        } else {
            log.warn("挑战验证失败: challengeId={}, userId={}, selected={}, correct={}",
                    challengeId, userId, selectedNumber, correctNumber);
            return ChallengeResult.failed("数字选择错误");
        }
    }

    // ==================== 设备管理 ====================

    /**
     * 获取用户的绑定设备列表
     */
    public List<AppDevice> getUserDevices(Long userId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getUserId, userId);
        wrapper.orderByDesc(AppDevice::getLastActiveAt);
        return appDeviceMapper.selectList(wrapper);
    }

    /**
     * 解绑设备
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevice(Long userId, String deviceId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getUserId, userId);
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice device = appDeviceMapper.selectOne(wrapper);

        if (device == null) {
            throw new BusinessException("设备不存在");
        }

        appDeviceMapper.delete(wrapper);
        log.info("设备解绑成功: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 判断用户是否有绑定的 App 设备
     */
    public boolean hasBoundDevice(Long userId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getUserId, userId);
        return appDeviceMapper.selectCount(wrapper) > 0;
    }

    /**
     * 解绑指定用户的所有其他设备（保留指定设备）
     *
     * @param userId       用户 ID
     * @param keepDeviceId 要保留的设备 ID（null 表示全部解绑）
     * @return 被解绑的设备数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int unbindOtherDevices(Long userId, String keepDeviceId) {
        // 查出所有设备
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getUserId, userId);
        if (keepDeviceId != null) {
            wrapper.ne(AppDevice::getDeviceId, keepDeviceId);
        }
        List<AppDevice> devicesToRemove = appDeviceMapper.selectList(wrapper);

        int count = 0;
        for (AppDevice device : devicesToRemove) {
            appDeviceMapper.deleteById(device.getId());
            count++;
            log.info("批量解绑设备: userId={}, deviceId={}, deviceName={}",
                    userId, device.getDeviceId(), device.getDeviceName());
        }

        return count;
    }

    /**
     * 更新设备活跃时间
     */
    public void updateDeviceActivity(String deviceId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice device = appDeviceMapper.selectOne(wrapper);
        if (device != null) {
            device.setLastActiveAt(LocalDateTime.now());
            appDeviceMapper.updateById(device);
        }
    }

    /**
     * 获取设备首选的加密算法
     *
     * @param deviceId 设备唯一标识
     * @return 加密算法名称（如 AES-256-GCM、SM4-GCM），如果设备不存在返回 null
     */
    public String getDeviceCipherPref(String deviceId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        AppDevice device = appDeviceMapper.selectOne(wrapper);
        return device != null ? device.getCipherPref() : null;
    }

    /**
     * 更新设备首选加密算法
     * <p>
     * App 端切换加密算法时调用，更新设备记录的 cipherPref 字段
     *
     * @param userId   用户 ID
     * @param deviceId 设备唯一标识
     * @param cipher   新的加密算法名称（如 AES-256-GCM、SM4-GCM）
     * @throws BusinessException 如果设备不存在或不属于该用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceCipher(Long userId, String deviceId, String cipher) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        wrapper.eq(AppDevice::getUserId, userId);
        AppDevice device = appDeviceMapper.selectOne(wrapper);

        if (device == null) {
            log.warn("更新加密算法失败，设备不存在: userId={}, deviceId={}", userId, deviceId);
            throw new BusinessException("设备不存在");
        }

        device.setCipherPref(cipher);
        device.setLastActiveAt(LocalDateTime.now());
        appDeviceMapper.updateById(device);

        log.info("设备加密算法已更新: userId={}, deviceId={}, cipher={}", userId, deviceId, cipher);
    }

    /**
     * 检查设备是否属于指定用户
     */
    public boolean isDeviceBelongToUser(String deviceId, Long userId) {
        LambdaQueryWrapper<AppDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDevice::getDeviceId, deviceId);
        wrapper.eq(AppDevice::getUserId, userId);
        return appDeviceMapper.selectCount(wrapper) > 0;
    }

    // ==================== 加密算法信息 ====================

    /**
     * 获取加密算法信息
     * 返回当前系统支持的密钥交换和数据加密算法
     *
     * @param userId   用户 ID
     * @param deviceId 设备 ID（可选，用于获取设备偏好）
     * @return 加密算法信息
     */
    public CipherInfoResponse getCipherInfo(Long userId, String deviceId) {
        CipherInfoResponse response = new CipherInfoResponse();

        // 1. 密钥交换算法信息
        CipherInfoResponse.KeyExchangeInfo keyExchange = new CipherInfoResponse.KeyExchangeInfo();
        keyExchange.setAlgorithm("DH");
        keyExchange.setKeySize(2048);
        keyExchange.setGroup("MODP Group (RFC 3526)");
        keyExchange.setHash("SHA-256");
        response.setKeyExchange(keyExchange);

        // 2. 数据加密算法信息（默认 AES-256-GCM）
        CipherInfoResponse.DataEncryptionInfo dataEncryption = new CipherInfoResponse.DataEncryptionInfo();
        dataEncryption.setAlgorithm("AES-256-GCM");
        dataEncryption.setKeySize(256);
        dataEncryption.setIvLength(12);
        dataEncryption.setTagLength(16);
        dataEncryption.setMode("GCM");
        dataEncryption.setPadding("NoPadding");
        response.setDataEncryption(dataEncryption);

        // 3. 可用算法列表
        List<CipherInfoResponse.AlgorithmInfo> algorithms = new ArrayList<>();

        CipherInfoResponse.AlgorithmInfo dhAlgo = new CipherInfoResponse.AlgorithmInfo();
        dhAlgo.setName("DH-2048");
        dhAlgo.setType("key_exchange");
        dhAlgo.setKeySize(2048);
        dhAlgo.setDescription("Diffie-Hellman 2048-bit MODP Group (RFC 3526)");
        algorithms.add(dhAlgo);

        CipherInfoResponse.AlgorithmInfo aes256 = new CipherInfoResponse.AlgorithmInfo();
        aes256.setName("AES-256-GCM");
        aes256.setType("data_encryption");
        aes256.setKeySize(256);
        aes256.setDescription("AES-256-GCM 认证加密，12字节IV，16字节GCM Tag");
        algorithms.add(aes256);

        CipherInfoResponse.AlgorithmInfo aes128 = new CipherInfoResponse.AlgorithmInfo();
        aes128.setName("AES-128-GCM");
        aes128.setType("data_encryption");
        aes128.setKeySize(128);
        aes128.setDescription("AES-128-GCM 认证加密，12字节IV，16字节GCM Tag");
        algorithms.add(aes128);

        CipherInfoResponse.AlgorithmInfo aes192 = new CipherInfoResponse.AlgorithmInfo();
        aes192.setName("AES-192-GCM");
        aes192.setType("data_encryption");
        aes192.setKeySize(192);
        aes192.setDescription("AES-192-GCM 认证加密，12字节IV，16字节GCM Tag");
        algorithms.add(aes192);

        CipherInfoResponse.AlgorithmInfo sm4 = new CipherInfoResponse.AlgorithmInfo();
        sm4.setName("SM4-GCM");
        sm4.setType("data_encryption");
        sm4.setKeySize(128);
        sm4.setDescription("国密 SM4-GCM 认证加密，12字节IV，16字节GCM Tag");
        algorithms.add(sm4);

        response.setAvailableAlgorithms(algorithms);

        // 4. 设备首选加密算法
        if (deviceId != null) {
            String cipherPref = getDeviceCipherPref(deviceId);
            response.setDeviceCipherPref(cipherPref);
        }

        return response;
    }

    // ==================== 用户个人信息 ====================

    /**
     * 获取用户个人信息（用于 App 端 Profile 页面）
     *
     * @param userId 用户 ID
     * @return 用户个人信息
     */
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setAvatarUrl(user.getAvatar());

        // 查询最早绑定的设备时间作为 boundAt
        LambdaQueryWrapper<AppDevice> deviceWrapper = new LambdaQueryWrapper<>();
        deviceWrapper.eq(AppDevice::getUserId, userId);
        deviceWrapper.orderByAsc(AppDevice::getLastActiveAt);
        deviceWrapper.last("LIMIT 1");
        AppDevice firstDevice = appDeviceMapper.selectOne(deviceWrapper);
        if (firstDevice != null) {
            response.setBoundAt(firstDevice.getLastActiveAt());
        }

        // 最后登录时间
        response.setLastLoginAt(user.getLastLoginTime());

        // 已绑定设备数量
        LambdaQueryWrapper<AppDevice> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(AppDevice::getUserId, userId);
        long deviceCount = appDeviceMapper.selectCount(countWrapper);
        response.setDeviceCount((int) deviceCount);

        return response;
    }

    // ==================== Dashboard 聚合接口 ====================

    /**
     * 获取 Authenticator 首页聚合数据
     * <p>
     * 聚合展示用户信息、当前设备信息、最近认证记录等
     *
     * @param userId   用户 ID
     * @param deviceId 当前设备 ID（用于获取设备信息和设备专属认证记录）
     * @return DashboardResponse 聚合数据
     */
    public DashboardResponse getDashboard(Long userId, String deviceId) {
        DashboardResponse response = new DashboardResponse();

        // 1. 用户信息
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        DashboardResponse.DashboardUserInfo userInfo = new DashboardResponse.DashboardUserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        // 头像使用公开代理 URL
        userInfo.setAvatarUrl("/api/users/avatar/public/" + userId);

        // 已绑定设备数量
        LambdaQueryWrapper<AppDevice> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(AppDevice::getUserId, userId);
        long deviceCount = appDeviceMapper.selectCount(countWrapper);
        userInfo.setDeviceCount((int) deviceCount);
        response.setUser(userInfo);

        // 2. 当前设备信息
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            LambdaQueryWrapper<AppDevice> deviceWrapper = new LambdaQueryWrapper<>();
            deviceWrapper.eq(AppDevice::getDeviceId, deviceId);
            deviceWrapper.eq(AppDevice::getUserId, userId);
            AppDevice device = appDeviceMapper.selectOne(deviceWrapper);

            if (device != null) {
                DashboardResponse.DashboardDeviceInfo deviceInfo = new DashboardResponse.DashboardDeviceInfo();
                deviceInfo.setDeviceId(device.getDeviceId());
                deviceInfo.setDeviceName(device.getDeviceName());
                deviceInfo.setCipherPref(device.getCipherPref());
                deviceInfo.setBoundAt(device.getCreatedAt() != null ? device.getCreatedAt().toString() : null);
                response.setDevice(deviceInfo);
            }
        }

        // 3. 最近认证记录（最近 5 条）
        LambdaQueryWrapper<AuthHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(AuthHistory::getUserId, userId);
        historyWrapper.orderByDesc(AuthHistory::getRequestedAt);
        historyWrapper.last("LIMIT 5");
        List<AuthHistory> recentRecords = authHistoryMapper.selectList(historyWrapper);

        // 总认证记录数
        LambdaQueryWrapper<AuthHistory> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(AuthHistory::getUserId, userId);
        long totalHistory = authHistoryMapper.selectCount(totalWrapper);

        DashboardResponse.DashboardRecentHistory recentHistory = new DashboardResponse.DashboardRecentHistory();
        recentHistory.setTotal(totalHistory);
        recentHistory.setRecords(recentRecords.stream()
                .map(this::convertToDashboardHistoryRecord)
                .collect(Collectors.toList()));
        response.setRecentHistory(recentHistory);

        // 4. 上次认证结果
        LambdaQueryWrapper<AuthHistory> lastWrapper = new LambdaQueryWrapper<>();
        lastWrapper.eq(AuthHistory::getUserId, userId);
        lastWrapper.orderByDesc(AuthHistory::getRequestedAt);
        lastWrapper.last("LIMIT 1");
        AuthHistory lastHistory = authHistoryMapper.selectOne(lastWrapper);

        if (lastHistory != null) {
            DashboardResponse.DashboardLastAuthResult lastResult = new DashboardResponse.DashboardLastAuthResult();
            lastResult.setStatus(lastHistory.getStatus());
            lastResult.setDeviceName(lastHistory.getDeviceName());
            lastResult.setRequestedAt(lastHistory.getRequestedAt() != null ? lastHistory.getRequestedAt().toString() : null);
            response.setLastAuthResult(lastResult);
        }

        // 5. 最后登录时间
        response.setLastLoginAt(user.getLastLoginTime() != null ? user.getLastLoginTime().toString() : null);

        return response;
    }

    /**
     * 将 AuthHistory 实体转换为 DashboardHistoryRecord
     */
    private DashboardResponse.DashboardHistoryRecord convertToDashboardHistoryRecord(AuthHistory history) {
        DashboardResponse.DashboardHistoryRecord record = new DashboardResponse.DashboardHistoryRecord();
        record.setId(history.getId());
        record.setChallengeId(history.getChallengeId());
        record.setDeviceName(history.getDeviceName());
        record.setStatus(history.getStatus());
        record.setRequestedAt(history.getRequestedAt() != null ? history.getRequestedAt().toString() : null);
        record.setRespondedAt(history.getRespondedAt() != null ? history.getRespondedAt().toString() : null);
        if (history.getRequestedAt() != null && history.getRespondedAt() != null) {
            record.setResponseTimeMs(
                    java.time.Duration.between(history.getRequestedAt(), history.getRespondedAt()).toMillis()
            );
        }
        return record;
    }

    // ==================== 认证历史 ====================

    /**
     * 分页查询认证历史
     *
     * @param userId 用户 ID
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param status 状态筛选（可选：approved/rejected/expired）
     * @return 分页的认证历史记录
     */
    public Page<AuthHistoryResponse> getAuthHistory(Long userId, int page, int size, String status) {
        // 参数校验
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 20;

        Page<AuthHistory> historyPage = new Page<>(page, size);
        LambdaQueryWrapper<AuthHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthHistory::getUserId, userId);

        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(AuthHistory::getStatus, status.trim().toLowerCase());
        }

        wrapper.orderByDesc(AuthHistory::getRequestedAt);

        Page<AuthHistory> result = authHistoryMapper.selectPage(historyPage, wrapper);

        // 转换为响应对象
        Page<AuthHistoryResponse> responsePage = new Page<>();
        responsePage.setCurrent(result.getCurrent());
        responsePage.setSize(result.getSize());
        responsePage.setTotal(result.getTotal());
        responsePage.setPages(result.getPages());

        List<AuthHistoryResponse> records = result.getRecords().stream()
                .map(this::convertToAuthHistoryResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(records);

        return responsePage;
    }

    /**
     * 记录挑战到数据库
     *
     * @param userId        用户 ID
     * @param challengeId   挑战 ID
     * @param numbers       3 个候选数字
     * @param correctNumber 正确答案
     * @param deviceId      设备 ID
     * @param deviceName    设备名称
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordChallenge(Long userId, String challengeId, int[] numbers,
                                int correctNumber, String deviceId, String deviceName) {
        AuthHistory history = new AuthHistory();
        history.setUserId(userId);
        history.setChallengeId(challengeId);
        history.setDeviceId(deviceId);
        history.setDeviceName(deviceName);
        history.setStatus(STATUS_PENDING);
        history.setNumbers(numbers);
        history.setCorrectNumber(correctNumber);
        history.setRequestedAt(LocalDateTime.now());

        authHistoryMapper.insert(history);

        log.debug("记录挑战历史: challengeId={}, userId={}", challengeId, userId);
    }

    /**
     * 查询用户「仍在时效窗口内」的敏感操作待处理挑战
     * <p>
     * 这是「设备此刻不在线」场景下的权威数据源：挑战在生成时就无条件落库，
     * 于是断线补推（WebSocket）与 App 主动拉取（REST）都能靠它把挑战找回来。
     * <p>
     * 时效窗口取 {@link #CHALLENGE_TIMEOUT_SECONDS}，与 Redis 里挑战本身的 TTL 一致 ——
     * 比它更早的记录即便查出来也验不过（Redis 键已过期），推给 App 只会得到一个
     * 「注定失败」的挑战。
     *
     * @param userId 用户 ID
     * @return 待处理挑战记录，按发起时间倒序；无则返回空列表
     */
    public List<AuthHistory> getPendingChallengeRecords(Long userId) {
        if (userId == null) {
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now().minusSeconds(CHALLENGE_TIMEOUT_SECONDS);
        LambdaQueryWrapper<AuthHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthHistory::getUserId, userId)
                .eq(AuthHistory::getStatus, STATUS_PENDING)
                .ge(AuthHistory::getRequestedAt, since)
                .orderByDesc(AuthHistory::getRequestedAt);

        List<AuthHistory> records = authHistoryMapper.selectList(wrapper);
        return records != null ? records : List.of();
    }

    /**
     * 由挑战发起时间推算过期时间（Unix 秒）
     * <p>
     * {@code t_auth_history} 没有 expires_at 列，而推给 App 的挑战消息与
     * App 拉取接口都要求 expiresAt。库里的 {@code requested_at} 是
     * {@code timestamp without time zone}、按 UTC 语义存（见
     * {@code UtcLocalDateTimeSerializer}），所以这里同样按 UTC 解释。
     *
     * @param requestedAt 挑战发起时间；为 null 时返回 0
     * @return Unix 秒；入参为 null 时返回 0
     */
    public static long challengeExpiresAtSeconds(LocalDateTime requestedAt) {
        if (requestedAt == null) {
            return 0L;
        }
        return requestedAt.toEpochSecond(ZoneOffset.UTC) + CHALLENGE_TIMEOUT_SECONDS;
    }

    /**
     * 查询某个挑战的认证结果
     * <p>
     * 敏感操作验证的权威数据源：结果由 {@link #updateChallengeResult} 落在 auth_history 表里。
     * 注意不要拿登录挑战的 challengeId 来查 —— 登录挑战的结果在 Redis 的
     * {@code app:auth:login_result:} 命名空间，两者是不同的信任域。
     *
     * @param challengeId 挑战 ID
     * @param userId      用户 ID（必须归属校验，防止用别人的挑战 ID 蒙混过关）
     * @return 状态 pending / approved / rejected / expired；查不到返回 null
     */
    public String getChallengeStatus(String challengeId, Long userId) {
        if (challengeId == null || userId == null) {
            return null;
        }
        LambdaQueryWrapper<AuthHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthHistory::getChallengeId, challengeId)
                .eq(AuthHistory::getUserId, userId);
        AuthHistory history = authHistoryMapper.selectOne(wrapper);
        return history != null ? history.getStatus() : null;
    }

    /**
     * 消费一条已被 App 批准的敏感操作挑战（一次性）
     * <p>
     * 条件更新即并发门闩：{@code used_at IS NULL} 保证同一行只会被一个请求消费成功，
     * 其余并发请求更新行数为 0。同时要求挑战在时效窗口内被响应，避免把历史的
     * approved 记录翻出来重放。
     *
     * @param userId      用户 ID
     * @param challengeId 挑战 ID
     * @return true=本次调用消费成功（可用于验证）；false=挑战不存在、已拒绝、已过期、
     * 已被消费过或超出时效窗口
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean consumeApprovedChallenge(Long userId, String challengeId) {
        if (userId == null || challengeId == null || challengeId.trim().isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AuthHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AuthHistory::getChallengeId, challengeId)
                .eq(AuthHistory::getUserId, userId)
                .eq(AuthHistory::getStatus, STATUS_APPROVED)
                .isNull(AuthHistory::getUsedAt)
                .ge(AuthHistory::getRespondedAt, now.minusMinutes(CHALLENGE_CONSUME_WINDOW_MINUTES))
                .set(AuthHistory::getUsedAt, now);

        int updated = authHistoryMapper.update(null, wrapper);
        if (updated == 1) {
            log.info("敏感操作挑战已消费: userId={}, challengeId={}", userId, challengeId);
            return true;
        }

        log.warn("敏感操作挑战消费失败（不存在/已拒绝/已过期/已使用/超出 {} 分钟窗口）: userId={}, challengeId={}",
                CHALLENGE_CONSUME_WINDOW_MINUTES, userId, challengeId);
        return false;
    }

    /**
     * 更新挑战结果
     *
     * @param challengeId 挑战 ID
     * @param status      状态: approved / rejected / expired
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChallengeResult(String challengeId, String status) {
        updateChallengeResult(challengeId, status, null, null);
    }

    /**
     * 更新挑战结果，并记录是哪台设备做出的应答
     * <p>
     * 敏感操作挑战现在「先生成落库、再尽力推送」，落库时还不知道哪台设备会应答
     * （手机可能压根不在线），因此应答设备只能在 App 回消息时补记 ——
     * 否则 {@code /check-push} 返回的 deviceName 恒为 null，前端就显示不出
     * 「是哪台设备批准的」。
     *
     * @param challengeId 挑战 ID
     * @param status      状态: approved / rejected / expired
     * @param deviceId    应答设备 ID；null 表示不修改
     * @param deviceName  应答设备名称；null 表示不修改
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChallengeResult(String challengeId, String status, String deviceId, String deviceName) {
        LambdaQueryWrapper<AuthHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthHistory::getChallengeId, challengeId);
        AuthHistory history = authHistoryMapper.selectOne(wrapper);

        if (history != null) {
            history.setStatus(status);
            history.setRespondedAt(LocalDateTime.now());
            if (deviceId != null) {
                history.setDeviceId(deviceId);
            }
            if (deviceName != null) {
                history.setDeviceName(deviceName);
            }
            authHistoryMapper.updateById(history);
            log.debug("更新挑战结果: challengeId={}, status={}, deviceId={}", challengeId, status, deviceId);
        }
    }

    /**
     * 将 AuthHistory 实体转换为 AuthHistoryResponse
     */
    private AuthHistoryResponse convertToAuthHistoryResponse(AuthHistory history) {
        AuthHistoryResponse response = new AuthHistoryResponse();
        response.setId(history.getId());
        response.setChallengeId(history.getChallengeId());
        response.setDeviceId(history.getDeviceId());
        response.setDeviceName(history.getDeviceName());
        response.setNumbers(history.getNumbers());
        // 刻意不下发 correctNumber —— 见 AuthHistoryResponse 类注释。
        // 这个接口是给 App 用的，而 App **永远只能拿到 3 个候选数字**。
        response.setStatus(history.getStatus());
        response.setRequestedAt(history.getRequestedAt());
        response.setRespondedAt(history.getRespondedAt());
        return response;
    }

    /**
     * 使指定用户的所有活跃挑战失效
     * <p>
     * 在推送新挑战前调用，确保旧挑战不能复用：
     * 1. 从 Redis 删除所有旧挑战数据
     * 2. 将数据库中 pending 状态的旧挑战标记为 expired
     *
     * @param userId 用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void invalidateUserChallenges(Long userId) {
        String userChallengesKey = USER_CHALLENGES_PREFIX + userId;
        Set<String> activeChallengeIds = redisTemplate.opsForSet().members(userChallengesKey);

        if (activeChallengeIds != null && !activeChallengeIds.isEmpty()) {
            // 1. 从 Redis 删除所有旧挑战数据
            for (String challengeId : activeChallengeIds) {
                redisTemplate.delete(CHALLENGE_PREFIX + challengeId);
            }
            // 删除用户活跃挑战集合
            redisTemplate.delete(userChallengesKey);

            // 2. 数据库同步置为 expired。
            //    没有这一步，被作废的挑战仍是 pending 行，会继续出现在「待处理挑战」查询里
            //    （App 直接拉取、以及设备上线时的补推），App 上就冒出一个注定验不过的挑战。
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<AuthHistory> expireWrapper = new LambdaUpdateWrapper<>();
            expireWrapper.in(AuthHistory::getChallengeId, activeChallengeIds)
                    .eq(AuthHistory::getUserId, userId)
                    .eq(AuthHistory::getStatus, STATUS_PENDING)
                    .set(AuthHistory::getStatus, STATUS_EXPIRED)
                    .set(AuthHistory::getRespondedAt, now);
            int expiredRows = authHistoryMapper.update(null, expireWrapper);

            log.info("已失效 {} 个旧挑战: userId={}, challengeIds={}, 同步置过期行数={}",
                    activeChallengeIds.size(), userId, activeChallengeIds, expiredRows);
        }
    }

    /**
     * 记录绑定失败次数
     */
    private void recordBindFailure(String bindFailKey) {
        Long count = redisTemplate.opsForValue().increment(bindFailKey);
        if (count != null && count == 1) {
            redisTemplate.expire(bindFailKey, BIND_LOCKOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 设备码生成结果
     */
    public static class DeviceSecretResult {
        private final String deviceId;
        private final String deviceSecret;
        private final String hint;

        public DeviceSecretResult(String deviceId, String deviceSecret, String hint) {
            this.deviceId = deviceId;
            this.deviceSecret = deviceSecret;
            this.hint = hint;
        }

        public String getDeviceId() { return deviceId; }
        public String getDeviceSecret() { return deviceSecret; }
        public String getHint() { return hint; }
    }

    /**
     * 绑定结果
     */
    public static class BindResult {
        private final String token;
        private final String deviceId;

        public BindResult(String token, String deviceId) {
            this.token = token;
            this.deviceId = deviceId;
        }

        public String getToken() { return token; }
        public String getDeviceId() { return deviceId; }
    }

    /**
     * 挑战信息
     */
    public static class ChallengeInfo {
        private final String challengeId;
        private final int[] numbers;
        private final int correctNumber;
        private final long expiresAt;

        public ChallengeInfo(String challengeId, int[] numbers, int correctNumber, long expiresAt) {
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
     * 挑战验证结果
     */
    public static class ChallengeResult {
        private final boolean approved;
        private final boolean expired;
        private final String message;

        private ChallengeResult(boolean approved, boolean expired, String message) {
            this.approved = approved;
            this.expired = expired;
            this.message = message;
        }

        public static ChallengeResult approved() {
            return new ChallengeResult(true, false, "验证通过");
        }

        public static ChallengeResult failed(String message) {
            return new ChallengeResult(false, false, message);
        }

        public static ChallengeResult expired() {
            return new ChallengeResult(false, true, "挑战已过期");
        }

        public boolean isApproved() { return approved; }
        public boolean isExpired() { return expired; }
        public String getMessage() { return message; }
    }

    /**
     * 配对码信息
     */
    public static class PairCodeInfo {
        private final String pairCode;
        private final long expiresIn;

        public PairCodeInfo(String pairCode, long expiresIn) {
            this.pairCode = pairCode;
            this.expiresIn = expiresIn;
        }

        public String getPairCode() { return pairCode; }
        public long getExpiresIn() { return expiresIn; }
    }
}
