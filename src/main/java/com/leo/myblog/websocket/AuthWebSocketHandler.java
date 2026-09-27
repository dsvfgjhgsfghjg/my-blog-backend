package com.leo.myblog.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.response.DashboardResponse;
import com.leo.myblog.service.AdminDashboardService;
import com.leo.myblog.service.AppAuthService;
import com.leo.myblog.service.CryptoService;
import com.leo.myblog.service.LoginService;
import com.leo.myblog.service.UserCacheService;
import com.leo.myblog.util.AbilityValidator;
import com.leo.myblog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App 认证 WebSocket 处理器
 * <p>
 * 处理 App 与后端之间的加密认证通信
 * <p>
 * 协议流程：
 * 1. bind - 客户端发送 JWT Token 绑定身份（明文）
 * 2. dh_init - 服务端发送 DH 公钥（明文）
 * 3. dh_response - 客户端发送 DH 公钥（明文）
 * 4. dh_ready - 加密通道建立完成（明文）
 * 5. challenge - 服务端发送 3 选 1 挑战（加密）
 * 6. challenge_response - 客户端返回选择的数字（加密）
 * 7. auth_result - 服务端返回认证结果（加密）
 * <p>
 * 加密说明：
 * - dh_ready 之后的所有消息（challenge、challenge_response、auth_result）均使用 AES-GCM 加密
 * - 加密消息格式：{"type":"encrypted","iv":"...","ciphertext":"..."}
 * - 心跳 ping/pong 保持明文
 * - 错误消息保持明文（DH 会话可能尚未建立）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final AppAuthService appAuthService;
    private final CryptoService cryptoService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AdminDashboardService adminDashboardService;
    private final UserCacheService userCacheService;
    private final AbilityValidator abilityValidator;
    private final LoginService loginService;

    // 存储每个会话的 DH 会话状态
    private final Map<String, DhSessionState> dhSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket 连接已建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode rootNode;

        try {
            rootNode = objectMapper.readTree(payload);
        } catch (Exception e) {
            sendError(session, "消息格式错误");
            return;
        }

        String type = rootNode.has("type") ? rootNode.get("type").asText() : null;
        if (type == null) {
            sendError(session, "缺少消息类型");
            return;
        }

        try {
            switch (type) {
                case "bind":
                    handleBind(session, rootNode);
                    break;
                case "dh_response":
                    handleDhResponse(session, rootNode);
                    break;
                case "encrypted":
                    // 解密后处理
                    handleEncryptedMessage(session, rootNode);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    sendError(session, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            log.error("处理消息异常: sessionId={}, type={}", session.getId(), type, e);
            sendError(session, "处理消息失败");
            // 不关闭连接，只发送错误消息，防止异常传播到框架层导致连接被关闭
        }
    }

    /**
     * 处理加密消息
     * 解密后根据内部消息类型分发处理
     */
    private void handleEncryptedMessage(WebSocketSession session, JsonNode rootNode) throws IOException {
        DhSessionState dhState = dhSessions.get(session.getId());
        if (dhState == null || dhState.getAesKey() == null) {
            sendError(session, "加密通道未建立");
            return;
        }

        String iv = rootNode.has("iv") ? rootNode.get("iv").asText() : null;
        String ciphertext = rootNode.has("ciphertext") ? rootNode.get("ciphertext").asText() : null;

        if (iv == null || ciphertext == null) {
            sendError(session, "加密消息格式错误");
            return;
        }

        try {
            // 解密
            byte[] plaintextBytes;
            if (dhState.getCipherName() != null && dhState.getCipherName().startsWith("SM4")) {
                plaintextBytes = cryptoService.sm4GcmDecrypt(iv, ciphertext, dhState.getAesKey());
            } else {
                plaintextBytes = cryptoService.aesGcmDecrypt(iv, ciphertext, dhState.getAesKey());
            }

            // 解析内部消息
            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);
            JsonNode innerNode = objectMapper.readTree(plaintext);

            String innerType = innerNode.has("type") ? innerNode.get("type").asText() : null;
            if (innerType == null) {
                sendError(session, "解密后消息缺少类型");
                return;
            }

            switch (innerType) {
                case "challenge_response":
                    handleChallengeResponse(session, innerNode);
                    break;
                case "admin_dashboard_request":
                    handleAdminDashboardRequest(session);
                    break;
                default:
                    sendError(session, "未知的加密消息类型: " + innerType);
            }
        } catch (Exception e) {
            log.error("解密消息失败: sessionId={}", session.getId(), e);
            sendError(session, "消息解密失败");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        // 防御性检查：只移除如果该 sessionId 确实是当前活跃的 session
        // 防止竞态条件下，旧 session 关闭事件误删已被新 session 替换的映射
        String currentDeviceId = sessionManager.getDeviceIdBySessionId(sessionId);
        if (currentDeviceId != null) {
            WebSocketSession currentSession = sessionManager.getSession(currentDeviceId);
            if (currentSession != null && currentSession.getId().equals(sessionId)) {
                sessionManager.removeSession(sessionId);
            } else {
                log.debug("忽略旧 session 关闭事件: sessionId={}（已被新 session 替换）", sessionId);
            }
        } else {
            log.debug("WebSocket 连接关闭（未找到关联设备）: sessionId={}", sessionId);
        }
        dhSessions.remove(sessionId);
        log.info("WebSocket 连接已关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
        sessionManager.removeSession(session.getId());
        dhSessions.remove(session.getId());
    }

    // ==================== 消息处理 ====================

    /**
     * 处理绑定消息
     * 客户端发送 JWT Token 来绑定身份
     * <p>
     * 增强验证：
     * 1. 验证 JWT Token 有效性
     * 2. 验证设备是否属于该用户
     * 3. 如果设备已有 deviceSecret，验证客户端提供的 deviceSecret
     * 4. 关闭该设备的旧 WebSocket 连接（防止多设备重用同一 deviceId）
     */
    private void handleBind(WebSocketSession session, JsonNode rootNode) throws IOException {
        String token = rootNode.has("token") ? rootNode.get("token").asText() : null;
        String deviceId = rootNode.has("deviceId") ? rootNode.get("deviceId").asText() : null;
        String deviceSecret = rootNode.has("deviceSecret") ? rootNode.get("deviceSecret").asText() : null;

        if (token == null || deviceId == null) {
            sendError(session, "缺少 token 或 deviceId");
            return;
        }

        // 验证 JWT Token
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                sendError(session, "Token 无效");
                return;
            }
        } catch (Exception e) {
            sendError(session, "Token 验证失败");
            return;
        }

        // 验证设备是否属于该用户
        if (!appAuthService.isDeviceBelongToUser(deviceId, userId)) {
            sendError(session, "设备未绑定");
            return;
        }

        // 验证设备密钥（如果设备已有 deviceSecret）
        if (!appAuthService.verifyDeviceSecret(deviceId, deviceSecret)) {
            log.warn("WebSocket 设备密钥验证失败: deviceId={}", deviceId);
            sendError(session, "设备密钥验证失败，请重新获取设备码");
            return;
        }

        // 关闭该设备的旧 WebSocket 连接（防止多设备重用同一 deviceId）
        WebSocketSession oldSession = sessionManager.getSession(deviceId);
        if (oldSession != null && !oldSession.getId().equals(session.getId())) {
            try {
                oldSession.close(CloseStatus.POLICY_VIOLATION);
                log.info("关闭旧 WebSocket 连接: deviceId={}, oldSessionId={}", deviceId, oldSession.getId());
            } catch (IOException e) {
                log.warn("关闭旧 WebSocket 连接失败: deviceId={}", deviceId, e);
            }
            sessionManager.removeSession(oldSession.getId());
        }

        // 注册会话
        sessionManager.registerSession(deviceId, userId, session);

        // 发送绑定确认
        String bindAck = String.format(
                "{\"type\":\"bind_ack\",\"status\":\"ok\",\"userId\":%d}",
                userId
        );
        session.sendMessage(new TextMessage(bindAck));

        log.info("WebSocket 绑定成功: deviceId={}, userId={}", deviceId, userId);

        // 自动开始 DH 密钥交换
        startDhExchange(session);
    }


    /**
     * 开始 DH 密钥交换（服务端发送 dh_init）
     */
    private void startDhExchange(WebSocketSession session) throws IOException {
        // 生成服务端 DH 密钥对
        CryptoService.DhKeyPair keyPair = cryptoService.generateDhKeyPair();

        // 保存 DH 会话状态
        DhSessionState dhState = new DhSessionState();
        dhState.setPrivateKeyBase64(keyPair.getPrivateKeyBase64());
        dhSessions.put(session.getId(), dhState);

        // 发送 DH 初始化消息
        // 使用固定参数（2048-bit MODP Group），这里通过标准的 X509 编码传输公钥
        // 简化设计：直接将公钥编码发送，客户端使用标准 DH 参数
        String dhInitMsg = String.format(
                "{\"type\":\"dh_init\",\"serverPublicKey\":\"%s\"}",
                keyPair.getPublicKeyBase64()
        );
        session.sendMessage(new TextMessage(dhInitMsg));

        log.info("DH 密钥交换开始: sessionId={}", session.getId());
    }

    /**
     * 处理 DH 响应
     */
    private void handleDhResponse(WebSocketSession session, JsonNode rootNode) throws IOException {
        String clientPublicKey = rootNode.has("clientPublicKey") ?
                rootNode.get("clientPublicKey").asText() : null;

        if (clientPublicKey == null) {
            sendError(session, "缺少客户端公钥");
            return;
        }

        DhSessionState dhState = dhSessions.get(session.getId());
        if (dhState == null) {
            sendError(session, "DH 会话未初始化");
            return;
        }

        // 计算共享密钥
        byte[] sharedSecret = cryptoService.computeSharedSecret(
                dhState.getPrivateKeyBase64(), clientPublicKey
        );

        // 获取设备首选加密算法
        String deviceId = sessionManager.getDeviceIdBySessionId(session.getId());
        String selectedCipher = "AES-256-GCM";
        int keySize = 256;
        if (deviceId != null) {
            String cipherPref = appAuthService.getDeviceCipherPref(deviceId);
            if (cipherPref != null) {
                selectedCipher = cipherPref;
                // SM4 固定 128 位密钥，AES 根据后缀决定
                if (selectedCipher.startsWith("SM4")) {
                    keySize = 128;
                } else if (selectedCipher.contains("128")) {
                    keySize = 128;
                } else if (selectedCipher.contains("192")) {
                    keySize = 192;
                }
            }
        }

        // 派生加密密钥（根据算法选择密钥长度）
        byte[] aesKey = cryptoService.deriveAesKey(sharedSecret, keySize);
        dhState.setAesKey(aesKey);
        dhState.setCipherName(selectedCipher);

        if (deviceId != null) {
            String keyBase64 = Base64.getEncoder().encodeToString(aesKey);
            dhState.setCipherKeyBase64(keyBase64);
        }

        // 发送 DH 就绪消息，通知客户端服务端选择的加密算法
        String dhReadyMsg = String.format(
                "{\"type\":\"dh_ready\",\"cipher\":\"%s\"}",
                selectedCipher
        );
        session.sendMessage(new TextMessage(dhReadyMsg));

        log.info("DH 密钥交换完成: sessionId={}, cipher={}", session.getId(), selectedCipher);

        // 尝试补推该用户所有待处理的挑战（登录 + 敏感操作）
        try {
            pushPendingChallenges(session, deviceId);
        } catch (Exception e) {
            log.warn("推送待处理挑战失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 补推该用户所有待处理的挑战（登录挑战 + 敏感操作挑战）
     * <p>
     * 设备上线后调用。手机在兜里、App 在后台时 socket 通常已断，所以「发起时设备不在线」
     * 是常态而非边缘情况 —— 两条链路都必须能在设备上线后把挑战找回来：
     * <ul>
     *   <li>登录挑战：来源是 Redis 的待推送集合 {@code app:auth:login_pending:}；</li>
     *   <li>敏感操作挑战：来源是 {@code t_auth_history} 里 status='pending' 且在时效窗口内的记录。</li>
     * </ul>
     * 单条推送失败不影响其余挑战。
     * <p>
     * 包级可见以便单元测试（{@code PendingChallengeRePushTest}）直接断言补推覆盖面，
     * 无需真的走一遍 DH 握手。
     */
    void pushPendingChallenges(WebSocketSession session, String deviceId) throws IOException {
        if (deviceId == null) {
            return;
        }

        Long userId = sessionManager.getUserId(deviceId);
        if (userId == null) {
            return;
        }

        int pushed = 0;

        // 1. 待处理的登录挑战
        java.util.List<String> pendingChallengeIds = loginService.getUserPendingLoginChallengeIds(userId);
        if (pendingChallengeIds != null) {
            for (String challengeId : pendingChallengeIds) {
                LoginService.LoginChallenge challengeDetail = loginService.getLoginChallengeDetail(challengeId);
                if (challengeDetail == null) {
                    continue;
                }
                if (sendChallengeSafely(session, buildLoginChallengeMessage(challengeDetail), challengeId)) {
                    pushed++;
                    log.info("补推待处理登录挑战: challengeId={}, userId={}, deviceId={}", challengeId, userId, deviceId);
                }
            }
        }

        // 2. 待处理的敏感操作挑战（数据库是权威数据源）
        for (com.leo.myblog.entity.AuthHistory history : appAuthService.getPendingChallengeRecords(userId)) {
            if (history.getChallengeId() == null) {
                continue;
            }
            String message = buildChallengeMessage(
                    history.getChallengeId(),
                    history.getNumbers(),
                    AppAuthService.challengeExpiresAtSeconds(history.getRequestedAt()));
            if (sendChallengeSafely(session, message, history.getChallengeId())) {
                pushed++;
                log.info("补推待处理敏感操作挑战: challengeId={}, userId={}, deviceId={}",
                        history.getChallengeId(), userId, deviceId);
            }
        }

        if (pushed == 0) {
            log.debug("无待处理的挑战: userId={}, deviceId={}", userId, deviceId);
        } else {
            log.info("共补推 {} 个待处理挑战: userId={}, deviceId={}", pushed, userId, deviceId);
        }
    }

    /**
     * 发送单条挑战，吞掉异常
     * <p>
     * 补推是一批多条，一条失败（对端已断开等）不应该让后续挑战都发不出去。
     *
     * @return true=发送成功
     */
    private boolean sendChallengeSafely(WebSocketSession session, String message, String challengeId) {
        try {
            sendEncryptedMessage(session, message);
            return true;
        } catch (Exception e) {
            log.warn("补推挑战失败: challengeId={}, sessionId={}", challengeId, session.getId(), e);
            return false;
        }
    }

    /**
     * 推送两步登录挑战
     * <p>
     * 供 LoginController 在用户点击「使用我的认证器登录」后调用。
     * 注意：主动推送必须由 Controller 发起 —— 本类反向依赖了 LoginService，
     * 若让 LoginService 注入本类会形成循环依赖。
     *
     * @param session   App 的 WebSocket 会话
     * @param challenge 登录挑战详情
     */
    public void pushLoginChallenge(WebSocketSession session, LoginService.LoginChallenge challenge) throws IOException {
        sendEncryptedMessage(session, buildLoginChallengeMessage(challenge));
        log.info("推送登录挑战: challengeId={}, userId={}, deviceId={}",
                challenge.getChallengeId(), challenge.getUserId(),
                sessionManager.getDeviceIdBySessionId(session.getId()));
    }

    /**
     * 构建登录挑战消息
     * <p>
     * 消息结构与敏感操作挑战（{@link #pushChallengeToUser}）完全一致，App 端无需改动。
     * <p>
     * <b>严禁把 correctNumber 放进消息</b>：数字匹配的安全性来自「App 只知道 3 个候选，
     * 由用户从网页上读出正确数字」；一旦下发正确答案，这个机制就退化成了 1/3 概率的盲猜。
     * <p>
     * 包级可见以便单元测试（{@code ChallengeRoutingTest}）直接断言消息内容。
     */
    String buildLoginChallengeMessage(LoginService.LoginChallenge challenge) {
        return buildChallengeMessage(challenge.getChallengeId(), challenge.getNumbers(), challenge.getExpiresAt());
    }

    /**
     * 构建挑战消息（登录挑战与敏感操作挑战共用同一种消息结构）
     * <p>
     * <b>严禁把 correctNumber 放进消息</b>：App 只能拿到 3 个候选，正确答案由用户在网页上读。
     * <p>
     * expiresAt 必须是 <b>Unix 秒</b>：App 端会做 {@code expiresAt * 1000}
     * （见 ChallengeDialogFragment），写成毫秒会让 App 一打开就显示「已过期」。
     */
    String buildChallengeMessage(String challengeId, int[] numbers, long expiresAt) {
        int[] safeNumbers = numbers != null ? numbers : new int[0];
        return String.format(
                "{\"type\":\"challenge\",\"challengeId\":\"%s\",\"numbers\":[%d,%d,%d],\"expiresAt\":%d}",
                challengeId,
                safeNumbers.length > 0 ? safeNumbers[0] : 0,
                safeNumbers.length > 1 ? safeNumbers[1] : 0,
                safeNumbers.length > 2 ? safeNumbers[2] : 0,
                expiresAt
        );
    }

    /**
     * 发送加密消息
     * 使用 DH 协商的 AES/SM4 密钥对消息进行加密
     *
     * @param session   WebSocket 会话
     * @param innerJson 内部消息 JSON 字符串（将被加密）
     */
    private void sendEncryptedMessage(WebSocketSession session, String innerJson) throws IOException {
        DhSessionState dhState = dhSessions.get(session.getId());
        if (dhState == null || dhState.getAesKey() == null) {
            log.warn("加密通道未建立，发送明文消息: sessionId={}", session.getId());
            session.sendMessage(new TextMessage(innerJson));
            return;
        }

        try {
            byte[] plaintext = innerJson.getBytes(StandardCharsets.UTF_8);
            CryptoService.AesGcmEncryptResult encrypted;

            if (dhState.getCipherName() != null && dhState.getCipherName().startsWith("SM4")) {
                encrypted = cryptoService.sm4GcmEncrypt(plaintext, dhState.getAesKey());
            } else {
                encrypted = cryptoService.aesGcmEncrypt(plaintext, dhState.getAesKey());
            }

            String encryptedMsg = String.format(
                    "{\"type\":\"encrypted\",\"iv\":\"%s\",\"ciphertext\":\"%s\"}",
                    encrypted.getIv(),
                    encrypted.getCiphertext()
            );
            session.sendMessage(new TextMessage(encryptedMsg));
        } catch (Exception e) {
            log.error("加密消息失败: sessionId={}", session.getId(), e);
            sendError(session, "消息加密失败");
        }
    }

    /**
     * 对外暴露的推送挑战方法（生成 + 落库 + 推送）
     * <p>
     * 供 AppPushTestController 调用。敏感操作验证链路请改用
     * {@link #pushChallenge} —— 那边必须「先生成一个挑战、再推给所有设备」，
     * 用本方法会导致每台设备各生成一个数字不同的挑战。
     *
     * @param session  WebSocket 会话
     * @param userId   用户 ID
     * @param deviceId 设备 ID
     * @return ChallengeInfo 包含 challengeId、numbers、expiresAt
     */
    public AppAuthService.ChallengeInfo pushChallengeToUser(WebSocketSession session, Long userId, String deviceId) throws IOException {
        // 生成挑战
        AppAuthService.ChallengeInfo challenge = appAuthService.generateChallenge(userId);

        // 记录挑战到数据库
        appAuthService.recordChallenge(userId, challenge.getChallengeId(),
                challenge.getNumbers(), challenge.getCorrectNumber(), deviceId,
                resolveDeviceName(userId, deviceId));

        // 加密后发送
        pushChallenge(session, challenge);

        log.info("推送加密挑战: challengeId={}, userId={}, deviceId={}",
                challenge.getChallengeId(), userId, deviceId);

        return challenge;
    }

    /**
     * 把**已生成好**的挑战推给一台设备
     * <p>
     * 与 {@link #pushChallengeToUser} 的区别：本方法不生成、不落库，只负责推送。
     * 这样才能保证多台在线设备收到的是**同一个 challengeId 与同一组候选数字** ——
     * 页面只展示一个数字、轮询也只认一个 challengeId，若各设备各生成一个挑战，
     * 除第一台以外的设备收到的都是页面之外的挑战，用户选中也验不过。
     *
     * @param session   WebSocket 会话
     * @param challenge 已生成（并已落库）的挑战
     */
    public void pushChallenge(WebSocketSession session, AppAuthService.ChallengeInfo challenge) throws IOException {
        sendEncryptedMessage(session, buildChallengeMessage(
                challenge.getChallengeId(), challenge.getNumbers(), challenge.getExpiresAt()));
    }

    /**
     * 解析设备名称，查不到时退回 deviceId
     */
    private String resolveDeviceName(Long userId, String deviceId) {
        if (deviceId == null) {
            return null;
        }
        return appAuthService.getUserDevices(userId).stream()
                .filter(d -> deviceId.equals(d.getDeviceId()))
                .map(com.leo.myblog.entity.AppDevice::getDeviceName)
                .filter(name -> name != null && !name.isEmpty())
                .findFirst()
                .orElse(deviceId);
    }

    /**
     * 发送 3 选 1 挑战给客户端
     * 挑战消息使用 AES-GCM/SM4-GCM 加密后发送
     */
    private void sendChallenge(WebSocketSession session, Long userId, String deviceId) throws IOException {
        // 使该用户之前的所有挑战失效，确保旧挑战不能复用
        appAuthService.invalidateUserChallenges(userId);

        // 生成挑战
        AppAuthService.ChallengeInfo challenge = appAuthService.generateChallenge(userId);

        // 获取设备名称
        String deviceName = null;
        if (deviceId != null) {
            com.leo.myblog.entity.AppDevice device = appAuthService.getUserDevices(userId).stream()
                    .filter(d -> d.getDeviceId().equals(deviceId))
                    .findFirst().orElse(null);
            if (device != null) {
                deviceName = device.getDeviceName();
            }
        }

        // 记录挑战到数据库
        appAuthService.recordChallenge(userId, challenge.getChallengeId(),
                challenge.getNumbers(), challenge.getCorrectNumber(), deviceId, deviceName);

        // 加密后发送（消息结构由 buildChallengeMessage 统一，绝不包含正确答案）
        sendEncryptedMessage(session, buildChallengeMessage(
                challenge.getChallengeId(), challenge.getNumbers(), challenge.getExpiresAt()));

        log.info("发送加密挑战: challengeId={}, userId={}, deviceId={}",
                challenge.getChallengeId(), userId, deviceId);
    }

    /**
     * 处理挑战响应
     * 注意：此方法接收的 rootNode 已经是解密后的内部消息
     */
    private void handleChallengeResponse(WebSocketSession session, JsonNode rootNode) throws IOException {
        String challengeId = rootNode.has("challengeId") ?
                rootNode.get("challengeId").asText() : null;
        int selectedNumber = rootNode.has("selectedNumber") ?
                rootNode.get("selectedNumber").asInt() : -1;

        if (challengeId == null || selectedNumber < 0 || selectedNumber > 99) {
            sendError(session, "挑战参数无效");
            return;
        }

        // 获取设备对应的用户
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            sendError(session, "会话未绑定");
            return;
        }

        Long userId = sessionManager.getUserId(deviceId);
        if (userId == null) {
            sendError(session, "请先绑定设备");
            return;
        }

        // 路由到正确的验证器：登录挑战与敏感操作挑战使用不同的 Redis 命名空间
        ChallengeOutcome outcome = resolveChallenge(userId, challengeId, selectedNumber);

        // 更新认证历史记录（仅敏感操作挑战会落 auth_history；
        // 登录挑战的结果由 LoginService 自己维护在 login_result 键里）
        // 同时补记应答设备：敏感操作挑战是「先生成落库、再尽力推送」，落库时还不知道
        // 哪台设备会应答，不在这里补记的话 /check-push 就报不出设备名。
        if (!outcome.isLoginChallenge()) {
            String deviceName = resolveDeviceName(userId, deviceId);
            if (outcome.isApproved()) {
                appAuthService.updateChallengeResult(challengeId, "approved", deviceId, deviceName);
            } else if (outcome.isExpired()) {
                appAuthService.updateChallengeResult(challengeId, "expired", deviceId, deviceName);
            } else {
                appAuthService.updateChallengeResult(challengeId, "rejected", deviceId, deviceName);
            }
        }

        // 构建结果消息
        String resultMsg;
        if (outcome.isApproved()) {
            resultMsg = "{\"type\":\"auth_result\",\"status\":\"approved\"}";
        } else if (outcome.isExpired()) {
            resultMsg = "{\"type\":\"auth_result\",\"status\":\"expired\",\"reason\":\"" + outcome.getMessage() + "\"}";
        } else {
            resultMsg = "{\"type\":\"auth_result\",\"status\":\"rejected\",\"reason\":\"" + outcome.getMessage() + "\"}";
        }

        // 加密后发送
        sendEncryptedMessage(session, resultMsg);

        log.info("挑战结果: challengeId={}, deviceId={}, loginChallenge={}, approved={}",
                challengeId, deviceId, outcome.isLoginChallenge(), outcome.isApproved());

        // 更新设备活跃时间
        appAuthService.updateDeviceActivity(deviceId);
    }

    /**
     * 按 Redis 命名空间把挑战应答路由到对应的验证器
     * <p>
     * 登录挑战存在 {@code app:auth:login_challenge:}，敏感操作存在 {@code app:auth:challenge:}。
     * 之前这里无条件调用 {@code appAuthService.verifyChallenge}，导致登录挑战
     * （存在另一个命名空间）永远验不过 —— 这是认证器登录链路根本跑不通的原因之一。
     * <p>
     * 抽成独立方法是为了让这段路由逻辑能够脱离 WebSocket 传输被单元测试覆盖。
     * <p>
     * 边界：挑战详情有 60s TTL，过期后此方法会回落到敏感操作分支并得到「挑战已过期」，
     * 语义正确。
     */
    ChallengeOutcome resolveChallenge(Long userId, String challengeId, int selectedNumber) {
        if (loginService.getLoginChallengeDetail(challengeId) != null) {
            LoginService.LoginChallengeResult result =
                    loginService.verifyLoginChallenge(challengeId, userId, selectedNumber);
            return new ChallengeOutcome(true, result.isApproved(), result.isExpired(), result.getMessage());
        }

        AppAuthService.ChallengeResult result =
                appAuthService.verifyChallenge(challengeId, selectedNumber, userId);
        return new ChallengeOutcome(false, result.isApproved(), result.isExpired(), result.getMessage());
    }

    /**
     * 挑战应答的处理结果
     */
    static class ChallengeOutcome {
        private final boolean loginChallenge;
        private final boolean approved;
        private final boolean expired;
        private final String message;

        ChallengeOutcome(boolean loginChallenge, boolean approved, boolean expired, String message) {
            this.loginChallenge = loginChallenge;
            this.approved = approved;
            this.expired = expired;
            this.message = message;
        }

        /** 是否属于两步登录挑战（决定是否写 auth_history） */
        public boolean isLoginChallenge() { return loginChallenge; }
        public boolean isApproved() { return approved; }
        public boolean isExpired() { return expired; }
        public String getMessage() { return message; }
    }

    /**
     * 处理管理后台仪表盘请求
     * <p>
     * 权限控制：
     * 1. 非管理员 → 返回错误 "权限不足，需要管理员权限"
     * 2. 超级管理员 → 返回完整仪表盘数据
     * 3. 管理员但无任何能力 → 返回 "请联系超级管理员分配权限" 提示
     * 4. 管理员且有管理能力 → 返回完整仪表盘数据
     * <p>
     * 注意：此方法在加密通道中调用，响应也通过加密通道返回
     */
    private void handleAdminDashboardRequest(WebSocketSession session) throws IOException {
        // 1. 获取用户 ID
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            sendError(session, "会话未绑定");
            return;
        }

        Long userId = sessionManager.getUserId(deviceId);
        if (userId == null) {
            sendError(session, "请先绑定设备");
            return;
        }

        // 2. 检查是否为管理员
        com.leo.myblog.entity.User user = userCacheService.getUserById(userId);
        if (user == null) {
            sendError(session, "用户不存在");
            return;
        }

        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            log.warn("非管理员尝试获取仪表盘数据: userId={}", userId);
            sendError(session, "权限不足，需要管理员权限");
            return;
        }

        // 3. 超级管理员直接返回完整数据
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            log.info("超级管理员获取仪表盘数据（WS）: userId={}", userId);
            DashboardResponse dashboard = adminDashboardService.getDashboardForWs(userId);
            sendDashboardResponse(session, dashboard);
            return;
        }

        // 4. 普通管理员：检查是否有任何管理能力
        try {
            // 使用 validateAnyAbility 检查是否有管理能力
            // 如果没有任何能力，会抛出 BusinessException
            abilityValidator.validateAnyAbility(userId,
                    "manage_admins", "manage_admin_abilities", "add_admin",
                    "toggle_user_status", "reset_password",
                    "edit_posts", "delete_posts",
                    "edit_comments", "delete_comments",
                    "edit_tags", "manage_system_config",
                    "view_audit_logs", "audit_posts", "manage_sayings");

            // 有能力，返回完整数据
            log.info("管理员获取仪表盘数据（WS）: userId={}", userId);
            DashboardResponse dashboard = adminDashboardService.getDashboardForWs(userId);
            sendDashboardResponse(session, dashboard);
        } catch (com.leo.myblog.exception.BusinessException e) {
            // 没有任何能力，返回提示信息
            log.info("管理员无任何能力: userId={}", userId);
            String noAbilityMsg = "{\"type\":\"admin_dashboard_response\",\"status\":\"no_ability\",\"message\":\"请联系超级管理员分配权限\"}";
            sendEncryptedMessage(session, noAbilityMsg);
        }
    }

    /**
     * 发送仪表盘响应（加密）
     *
     * @param session   WebSocket 会话
     * @param dashboard 仪表盘数据
     */
    private void sendDashboardResponse(WebSocketSession session, DashboardResponse dashboard) throws IOException {
        try {
            String dashboardJson = objectMapper.writeValueAsString(dashboard);
            String responseMsg = String.format(
                    "{\"type\":\"admin_dashboard_response\",\"status\":\"ok\",\"data\":%s}",
                    dashboardJson
            );
            sendEncryptedMessage(session, responseMsg);
        } catch (JsonProcessingException e) {
            log.error("序列化仪表盘数据失败: sessionId={}", session.getId(), e);
            sendError(session, "仪表盘数据序列化失败");
        }
    }

    /**
     * 处理 Ping（心跳）
     */
    private void handlePing(WebSocketSession session) throws IOException {
        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
    }

    // ==================== 辅助方法 ====================

    private void sendError(WebSocketSession session, String message) throws IOException {
        String errorMsg = String.format("{\"type\":\"error\",\"message\":\"%s\"}", message);
        session.sendMessage(new TextMessage(errorMsg));
        log.warn("WebSocket 错误消息: sessionId={}, message={}", session.getId(), message);
    }

    private String getDeviceIdFromSession(WebSocketSession session) {
        String sessionId = session.getId();
        // 从 sessionManager 反向查找
        // 由于没有提供反向查找方法，直接访问 map
        return sessionManager.getDeviceIdBySessionId(sessionId);
    }

    // ==================== DH 会话状态 ====================

    public static class DhSessionState {
        private String privateKeyBase64;
        private byte[] aesKey;
        private String cipherKeyBase64;
        private String cipherName;

        public String getPrivateKeyBase64() {
            return privateKeyBase64;
        }

        public void setPrivateKeyBase64(String privateKeyBase64) {
            this.privateKeyBase64 = privateKeyBase64;
        }

        public byte[] getAesKey() {
            return aesKey;
        }

        public void setAesKey(byte[] aesKey) {
            this.aesKey = aesKey;
        }

        public String getCipherKeyBase64() {
            return cipherKeyBase64;
        }

        public void setCipherKeyBase64(String cipherKeyBase64) {
            this.cipherKeyBase64 = cipherKeyBase64;
        }

        public String getCipherName() {
            return cipherName;
        }

        public void setCipherName(String cipherName) {
            this.cipherName = cipherName;
        }
    }
}
