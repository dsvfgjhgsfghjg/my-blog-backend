package com.leo.myblog.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.entity.UaBlacklist;
import com.leo.myblog.mapper.UaBlacklistMapper;
import com.leo.myblog.service.IpBanService;
import com.leo.myblog.service.SecurityEventService;
import com.leo.myblog.security.ClientIpResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User-Agent 安全拦截过滤器
 * 功能：
 * 1. 拦截恶意UA（Kali、sqlmap等）
 * 2. 记录安全事件日志
 * 3. 自动封禁高频攻击IP
 * 4. 对蜜罐请求返回假数据
 */
@Slf4j
@Component
@Order(2) // 在RequestIdFilter之后执行
@RequiredArgsConstructor
public class UaSecurityFilter implements Filter {
    
    private final UaBlacklistMapper uaBlacklistMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityEventService securityEventService;
    private final IpBanService ipBanService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Redis键前缀
    private static final String IP_VIOLATION_COUNT_KEY = "security:violation:";
    private static final String IP_BANNED_KEY = "security:banned:";
    
    // 配置参数
    private static final int MAX_VIOLATIONS_BEFORE_BAN = 10; // 触发封禁的违规次数
    private static final long BAN_DURATION_HOURS = 24; // 封禁时长（小时）
    
    // 公开接口白名单（不触发UA检查）
    //
    // ⚠️ 这份列表**不要**与 config/PublicPaths 合并 —— 两者回答的是不同的问题：
    //   这里：哪些路径**不做 UA 检查**
    //   那里：哪些路径**不需要鉴权**
    // 看起来都是「公开接口」，实际差别很大。反例：
    //   · GET /api/posts/{id} 是公开可读的（在 PublicPaths 里），但**故意不在这里** ——
    //     爬虫抓公开文章正是最该做 UA 检查的场景，合并会让它们直接免检。
    //   · /api/comments/post（发表评论）需要鉴权，却在这里 —— 因为它的 UA 风险模式不同。
    // 合并会让这套风控静默失效，且不会有任何报错。
    private static final String[] PUBLIC_PATHS = {
        "/api/system/config/public",
        "/api/users/register",
        "/api/users/login",
        "/api/captcha/",
        "/actuator/health",
        "/api/audio/stream/",
        "/api/audio/random",
        "/api/audio/cover/",
        "/api/blogger/info",
        "/api/blogger/avatar",
        "/api/posts/public",
        "/api/posts/category/",
        "/api/homepage/",
        "/api/comments/post",
        "/api/users/avatar/public/"
    };
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = clientIpResolver.resolve(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestUrl = httpRequest.getRequestURI();
        String requestMethod = httpRequest.getMethod();
        
        // 0. 检查是否为公开接口（跳过UA检查）
        if (isPublicPath(requestUrl)) {
            chain.doFilter(request, response);
            return;
        }
        
        // 1. 检查IP是否已被封禁
        if (ipBanService.isIpBanned(clientIp)) {
            log.warn("被封禁的IP尝试访问: ip={}, url={}", clientIp, requestUrl);
            httpResponse.setStatus(403);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":403,\"message\":\"IP已被封禁\"}");
            return;
        }
        
        // 2. 检查空UA
        if (userAgent == null || userAgent.trim().isEmpty()) {
            handleSecurityViolation(clientIp, userAgent, requestUrl, requestMethod, 
                    "EMPTY_UA", "空User-Agent", "LOW", "BLOCK", httpResponse);
            return;
        }
        
        // 3. 检查UA黑名单
        UaBlacklist matchedRule = matchUaBlacklist(userAgent);
        if (matchedRule != null) {
            String threatLevel = matchedRule.getThreatLevel();
            String actionType = matchedRule.getActionType();
            
            log.warn("检测到恶意UA: ip={}, ua={}, pattern={}, threat={}, action={}", 
                    clientIp, userAgent, matchedRule.getPattern(), threatLevel, actionType);
            
            if ("HONEYPOT".equals(actionType)) {
                // 蜜罐模式：返回200和假数据
                handleHoneypot(clientIp, userAgent, requestUrl, requestMethod, 
                        matchedRule.getPattern(), threatLevel, httpResponse);
            } else {
                // 拦截模式：返回403
                handleSecurityViolation(clientIp, userAgent, requestUrl, requestMethod,
                        matchedRule.getPattern(), matchedRule.getDescription(), 
                        threatLevel, "BLOCK", httpResponse);
            }
            return;
        }
        
        // 4. 正常请求，继续处理
        chain.doFilter(request, response);
    }
    
    /**
     * 检查是否为公开接口（跳过UA检查）
     */
    private boolean isPublicPath(String requestUrl) {
        for (String publicPath : PUBLIC_PATHS) {
            if (requestUrl.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 匹配UA黑名单
     */
    private UaBlacklist matchUaBlacklist(String userAgent) {
        try {
            List<UaBlacklist> rules = uaBlacklistMapper.selectEnabledRules();
            String uaLower = userAgent.toLowerCase();
            
            for (UaBlacklist rule : rules) {
                if (uaLower.contains(rule.getPattern().toLowerCase())) {
                    return rule;
                }
            }
        } catch (Exception e) {
            log.error("匹配UA黑名单失败", e);
        }
        return null;
    }
    
    /**
     * 处理安全违规（返回403）
     */
    private void handleSecurityViolation(String ip, String ua, String url, String method,
                                         String pattern, String description, 
                                         String threatLevel, String action, 
                                         HttpServletResponse response) throws IOException {
        // 记录安全事件到数据库
        securityEventService.recordEvent("UA_BLOCKED", ip, ua, url, method, 
                pattern, threatLevel, "BLOCKED");
        
        // 检查是否需要封禁IP
        checkAndBanIp(ip, pattern);
        
        // 返回403
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"访问被拒绝\"}");
    }
    
    /**
     * 处理蜜罐请求（返回200和假数据）
     */
    private void handleHoneypot(String ip, String ua, String url, String method,
                                String pattern, String threatLevel, 
                                HttpServletResponse response) throws IOException {
        // 记录蜜罐触发事件到数据库
        securityEventService.recordEvent("HONEYPOT_TRIGGERED", ip, ua, url, method,
                pattern, threatLevel, "HONEYPOT");
        
        // 生成假数据
        String fakeData = generateFakeData(url);
        
        // 返回200和假数据
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(fakeData);
        
        log.info("蜜罐响应已发送: ip={}, url={}", ip, url);
    }
    
    /**
     * 生成假数据（蜜罐）
     */
    private String generateFakeData(String url) {
        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("code", 200);
        fakeResponse.put("message", "success");
        
        // 根据URL类型返回不同的假数据
        if (url.contains("/api/posts")) {
            // 假文章列表
            Map<String, Object> fakePost = new HashMap<>();
            fakePost.put("id", "9999999999999999999");
            fakePost.put("title", "这是一篇假文章 - 你被蜜罐捕获了");
            fakePost.put("content", "如果你看到这条消息，说明你的扫描器正在访问蜜罐系统...");
            fakePost.put("createTime", LocalDateTime.now().toString());
            fakeResponse.put("data", fakePost);
        } else if (url.contains("/api/users")) {
            // 假用户信息
            Map<String, Object> fakeUser = new HashMap<>();
            fakeUser.put("id", "8888888888888888888");
            fakeUser.put("username", "honeypot_user");
            fakeUser.put("nickname", "蜜罐用户");
            fakeUser.put("email", "honeypot@example.com");
            fakeResponse.put("data", fakeUser);
        } else {
            // 通用假数据
            fakeResponse.put("data", "This is a honeypot response. Your activity is being logged.");
        }
        
        try {
            return objectMapper.writeValueAsString(fakeResponse);
        } catch (Exception e) {
            return "{\"code\":200,\"message\":\"success\",\"data\":\"honeypot\"}";
        }
    }
    

    
    /**
     * 检查并封禁IP
     */
    private void checkAndBanIp(String ip, String pattern) {
        try {
            String key = IP_VIOLATION_COUNT_KEY + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            if (count != null && count >= MAX_VIOLATIONS_BEFORE_BAN) {
                // 封禁IP（同时写入数据库和Redis）
                ipBanService.banIp(ip, "UA黑名单违规: " + pattern, count.intValue(), BAN_DURATION_HOURS);
            }
        } catch (Exception e) {
            log.error("检查IP封禁失败", e);
        }
    }
    

}
