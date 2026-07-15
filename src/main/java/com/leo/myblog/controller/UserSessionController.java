package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.UserSessionResponse;
import com.leo.myblog.service.TokenSessionService;
import com.leo.myblog.service.TotpService;
import com.leo.myblog.util.UserAgentParser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户会话管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users/sessions")
@RequiredArgsConstructor
public class UserSessionController {
    
    private final TokenSessionService tokenSessionService;
    private final TotpService totpService;
    private final UserAgentParser userAgentParser;
    
    /**
     * 获取当前用户的所有活跃会话
     */
    @GetMapping
    public Result<List<UserSessionResponse>> getUserSessions(@RequestAttribute Long userId,
                                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("获取用户会话列表: userId={}", userId);
        
        // 获取当前Token的Hash（优先从Authorization header，兜底从Cookie）
        String currentToken = extractToken(authorization);
        if (currentToken == null) {
            currentToken = extractTokenFromCookie();
        }
        String currentTokenHash = currentToken != null ? String.valueOf(currentToken.hashCode()) : "";
        
        // 获取所有会话
        List<Map<String, Object>> sessions = tokenSessionService.getUserSessions(userId);
        
        // 转换为响应对象
        List<UserSessionResponse> responseList = sessions.stream()
            .map(session -> convertToResponse(session, currentTokenHash))
            .collect(Collectors.toList());
        
        return Result.success(responseList);
    }
    
    /**
     * 下线指定会话（需要TOTP双因素认证）
     */
    @PostMapping("/{sessionId}/revoke")
    public Result<Void> revokeSession(@RequestAttribute Long userId,
                                       @PathVariable String sessionId,
                                       @RequestBody RevokeSessionRequest request,
                                       jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("用户请求下线会话: userId={}, sessionId={}", userId, sessionId);
        
        // 1. 验证TOTP验证码
        if (request.getTotpCode() == null || request.getTotpCode().trim().isEmpty()) {
            return Result.error(400, "请输入TOTP验证码");
        }
        
        boolean isValid = totpService.verifyTotpCode(userId, request.getTotpCode().trim());
        
        // 尝试作为备份码验证
        if (!isValid) {
            isValid = totpService.verifyBackupCode(userId, request.getTotpCode().trim());
        }
        
        if (!isValid) {
            log.warn("TOTP验证码错误: userId={}", userId);
            return Result.error(400, "TOTP验证码错误或已过期");
        }
        
        // 2. 获取被下线会话的设备信息（用于审计日志）
        Map<String, Object> sessionData = tokenSessionService.getSessionData(userId, sessionId);
        if (sessionData != null) {
            String deviceInfo = (String) sessionData.get("deviceInfo");
            String userAgent = (String) sessionData.get("userAgent");
            if (deviceInfo != null) {
                httpRequest.setAttribute("revokedDeviceInfo", deviceInfo);
            }
            if (userAgent != null) {
                httpRequest.setAttribute("revokedUserAgent", userAgent);
            }
        }
        
        // 3. 下线会话
        boolean success = tokenSessionService.revokeSession(userId, sessionId);
        
        if (success) {
            log.info("会话已下线: userId={}, sessionId={}", userId, sessionId);
            return Result.success("会话已下线", null);
        } else {
            return Result.error(500, "下线失败，请重试");
        }
    }
    
    /**
     * 下线所有其他会话（保留当前会话，需要TOTP双因素认证）
     */
    @PostMapping("/revoke-others")
    public Result<Map<String, Object>> revokeOtherSessions(@RequestAttribute Long userId,
                                                            @RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestBody RevokeSessionRequest request) {
        log.info("用户请求下线所有其他会话: userId={}", userId);
        
        // 1. 验证TOTP验证码
        if (request.getTotpCode() == null || request.getTotpCode().trim().isEmpty()) {
            return Result.error(400, "请输入TOTP验证码");
        }
        
        boolean isValid = totpService.verifyTotpCode(userId, request.getTotpCode().trim());
        
        // 尝试作为备份码验证
        if (!isValid) {
            isValid = totpService.verifyBackupCode(userId, request.getTotpCode().trim());
        }
        
        if (!isValid) {
            log.warn("TOTP验证码错误: userId={}", userId);
            return Result.error(400, "TOTP验证码错误或已过期");
        }
        
        // 2. 获取当前Token Hash（优先从Authorization header，兜底从Cookie）
        String currentToken = extractToken(authorization);
        if (currentToken == null) {
            currentToken = extractTokenFromCookie();
        }
        String currentTokenHash = currentToken != null ? String.valueOf(currentToken.hashCode()) : "";
        
        // 3. 下线所有其他会话
        int count = tokenSessionService.revokeOtherSessions(userId, currentTokenHash);
        
        Map<String, Object> result = new HashMap<>();
        result.put("revokedCount", count);
        
        log.info("已下线{}个其他会话: userId={}", count, userId);
        return Result.success(String.format("已下线%d个其他会话", count), result);
    }
    
    /**
     * 从Authorization头提取Bearer Token
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
    
    /**
     * 从HttpOnly Cookie提取JWT Token
     */
    private String extractTokenFromCookie() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes = 
                (org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                jakarta.servlet.http.Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (jakarta.servlet.http.Cookie cookie : cookies) {
                        if ("AUTH_TOKEN".equals(cookie.getName())) {
                            String token = cookie.getValue();
                            if (token != null && !token.isEmpty()) {
                                log.debug("从HttpOnly Cookie中提取到Token");
                                return token;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从Cookie提取Token失败", e);
        }
        return null;
    }
    
    /**
     * 转换会话数据为响应对象
     */
    private UserSessionResponse convertToResponse(Map<String, Object> sessionData, String currentTokenHash) {
        UserSessionResponse response = new UserSessionResponse();
        
        response.setSessionId((String) sessionData.get("sessionId"));
        response.setIpAddress((String) sessionData.get("ipAddress"));
        response.setDeviceInfo((String) sessionData.get("deviceInfo"));
        response.setIsCurrent(currentTokenHash.equals(sessionData.get("sessionId")));
        
        // 解析登录时间
        String loginTimeStr = (String) sessionData.get("loginTime");
        if (loginTimeStr != null) {
            response.setLoginTime(LocalDateTime.parse(loginTimeStr));
        }
        
        // 解析过期时间
        String expireTimeStr = (String) sessionData.get("expireTime");
        if (expireTimeStr != null) {
            response.setExpireTime(LocalDateTime.parse(expireTimeStr));
        }
        
        // 解析User-Agent获取设备和浏览器信息
        String userAgent = (String) sessionData.get("userAgent");
        if (userAgent != null && !userAgent.isEmpty()) {
            UserAgentParser.DeviceInfo deviceInfo = userAgentParser.parseUserAgent(userAgent);
            response.setDeviceType(deviceInfo.getDeviceType());
            response.setOsName(deviceInfo.getOsName());
            response.setBrowserName(deviceInfo.getBrowserName());
        }
        
        return response;
    }
    
    /**
     * 下线会话请求
     */
    @Data
    public static class RevokeSessionRequest {
        private String totpCode;
    }
}
