package com.leo.myblog.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * 维护设备ID 和 WebSocket 会话的映射关系
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    // deviceId → WebSocketSession 映射
    private final ConcurrentHashMap<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    // sessionId → deviceId 映射（用于反向查找）
    private final ConcurrentHashMap<String, String> sessionDevices = new ConcurrentHashMap<>();

    // deviceId → 用户ID 映射
    private final ConcurrentHashMap<String, Long> deviceUsers = new ConcurrentHashMap<>();

    /**
     * 注册会话
     * <p>
     * 先注册新 session，再关闭旧 session，防止旧 session 关闭事件
     * 触发 afterConnectionClosed 时误删新注册的 session 映射。
     * 同时检查 sessionId 确保不会关闭刚注册的新 session。
     */
    public void registerSession(String deviceId, Long userId, WebSocketSession session) {
        String newSessionId = session.getId();

        // 1. 先记录新 session 的映射关系
        sessionDevices.put(newSessionId, deviceId);
        deviceSessions.put(deviceId, session);
        deviceUsers.put(deviceId, userId);

        // 2. 再关闭旧 session（如果有且不是同一个 session）
        //    注意：旧 session 的关闭会触发 afterConnectionClosed，
        //    但由于新 session 已经注册，removeSession 通过 sessionId 匹配，
        //    不会误删新 session 的映射。
        WebSocketSession oldSession = deviceSessions.get(deviceId);
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(newSessionId)) {
            try {
                oldSession.close();
                log.info("关闭旧会话: deviceId={}, oldSessionId={}", deviceId, oldSession.getId());
            } catch (Exception e) {
                log.warn("关闭旧会话失败: deviceId={}", deviceId, e);
            }
        }

        log.info("WebSocket 会话注册: deviceId={}, userId={}, sessionId={}",
                deviceId, userId, newSessionId);
    }

    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        String deviceId = sessionDevices.remove(sessionId);
        if (deviceId != null) {
            deviceSessions.remove(deviceId);
            deviceUsers.remove(deviceId);
            log.info("WebSocket 会话移除: deviceId={}, sessionId={}", deviceId, sessionId);
        } else {
            log.debug("WebSocket 会话移除（未找到关联设备）: sessionId={}", sessionId);
        }
    }

    /**
     * 根据设备 ID 获取会话
     */
    public WebSocketSession getSession(String deviceId) {
        return deviceSessions.get(deviceId);
    }

    /**
     * 根据设备 ID 获取用户 ID
     */
    public Long getUserId(String deviceId) {
        return deviceUsers.get(deviceId);
    }

    /**
     * 根据会话 ID 获取设备 ID
     */
    public String getDeviceIdBySessionId(String sessionId) {
        return sessionDevices.get(sessionId);
    }

    /**
     * 判断设备是否在线
     */
    public boolean isDeviceOnline(String deviceId) {
        WebSocketSession session = deviceSessions.get(deviceId);
        return session != null && session.isOpen();
    }

    /**
     * 获取所有在线设备数量
     */
    public int getOnlineDeviceCount() {
        return deviceSessions.size();
    }

    /**
     * 获取指定用户的所有在线设备
     */
    public Map<String, WebSocketSession> getUserSessions(Long userId) {
        Map<String, WebSocketSession> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, WebSocketSession> entry : deviceSessions.entrySet()) {
            Long uid = deviceUsers.get(entry.getKey());
            if (uid != null && uid.equals(userId)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
