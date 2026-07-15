package com.leo.myblog.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 注册 App 认证 WebSocket 端点
 * <p>
 * 提供两个端点：
 * 1. /ws/app/auth - 带 SockJS 降级（用于浏览器端）
 * 2. /ws/app/auth/native - 原生 WebSocket（用于 App 端，无 SockJS 干扰）
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuthWebSocketHandler authWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 带 SockJS 降级的端点（用于浏览器端）
        registry.addHandler(authWebSocketHandler, "/ws/app/auth")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 原生 WebSocket 端点（用于 App 端，无 SockJS 干扰）
        registry.addHandler(authWebSocketHandler, "/ws/app/auth/native")
                .setAllowedOriginPatterns("*");
    }
}
