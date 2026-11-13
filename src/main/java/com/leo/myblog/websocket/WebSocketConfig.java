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
 * 只提供 App 使用的原生 WebSocket 端点。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuthWebSocketHandler authWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 原生 WebSocket 端点（用于 App 端）。原生客户端不发送 Origin，
        // 真正的访问控制由 JWT + deviceId + deviceSecret 完成。
        registry.addHandler(authWebSocketHandler, "/ws/app/auth/native")
                .setAllowedOriginPatterns("https://leo-blog.top", "https://www.leo-blog.top");
    }
}
