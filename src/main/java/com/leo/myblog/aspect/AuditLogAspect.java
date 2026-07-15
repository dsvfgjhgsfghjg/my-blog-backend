package com.leo.myblog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.entity.AuditLog;
import com.leo.myblog.service.AuditLogService;
import com.leo.myblog.util.AndroidUaDetector;
import com.leo.myblog.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志切面
 * 自动记录以下操作：
 * 1. 所有管理员操作（标记了@RequireAdmin）
 * 2. 用户登录/登出/注册
 * 3. 能力管理相关操作
 * 4.UA检测
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {
    
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AndroidUaDetector androidUaDetector;
    private final UserAgentParser userAgentParser;
    
    @Around("@annotation(com.leo.myblog.annotation.RequireAdmin) || " +
            "execution(* com.leo.myblog.controller.UserController.login(..)) || " +
            "execution(* com.leo.myblog.controller.UserController.logout(..)) || " +
            "execution(* com.leo.myblog.controller.UserController.register(..)) || " +
            "execution(* com.leo.myblog.controller.OAuth2Controller.token(..)) || " +
            "execution(* com.leo.myblog.controller.OAuth2Controller.refreshToken(..)) || " +
            "execution(* com.leo.myblog.controller.UserSessionController.revokeSession(..)) || " +
            "execution(* com.leo.myblog.controller.UserSessionController.revokeOtherSessions(..))")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取当前用户信息
        Long userId = getCurrentUserId();
        String username = getCurrentUsername();
        
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 构建审计日志
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUsername(username);
        auditLog.setOperationType(determineOperationType(method));
        auditLog.setModule(determineModule(joinPoint));
        auditLog.setDescription(generateDescription(method, joinPoint));
        
        if (request != null) {
            auditLog.setIpAddress(getClientIp(request));
            auditLog.setUserAgent(request.getHeader("User-Agent"));
            auditLog.setRequestUrl(request.getRequestURI());
            auditLog.setRequestMethod(request.getMethod());
            
            // 脱敏处理请求参数
            try {
                String[] paramNames = signature.getParameterNames();
                String params = serializeAndMaskParams(joinPoint.getArgs(), paramNames);
                auditLog.setRequestParams(params);
            } catch (Exception e) {
                log.warn("序列化请求参数失败", e);
                auditLog.setRequestParams("{}");
            }
        }
        
        // 执行目标方法
        Object result = null;
        boolean success = true;
        String errorMessage = null;
        
        try {
            result = joinPoint.proceed();
            auditLog.setResponseStatus(200);
            auditLog.setResponseMessage("操作成功");
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            auditLog.setResponseStatus(500);
            auditLog.setResponseMessage("操作失败: " + e.getMessage());
            auditLog.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            auditLog.setExecutionTime(executionTime);
            auditLog.setSuccess(success);
            auditLog.setCreateTime(LocalDateTime.now());
            
            // 如果是登录操作，添加设备信息到extraData
            if ("LOGIN".equals(auditLog.getOperationType()) && request != null) {
                try {
                    Map<String, String> deviceInfo = new HashMap<>();
                    String userAgent = request.getHeader("User-Agent");
                    if (userAgent != null) {
                        deviceInfo.put("userAgent", userAgent);
                        
                        // 使用UserAgentParser解析基础设备信息
                        UserAgentParser.DeviceInfo parsedInfo = userAgentParser.parseUserAgent(userAgent);
                        deviceInfo.put("deviceType", parsedInfo.getDeviceType());
                        deviceInfo.put("osName", parsedInfo.getOsName());
                        deviceInfo.put("browserName", parsedInfo.getBrowserName());
                        
                        // 如果是Android设备，添加细化备注
                        if ("Android".equals(parsedInfo.getOsName())) {
                            AndroidUaDetector.AndroidDeviceInfo androidInfo = androidUaDetector.detect(userAgent);
                            if (androidInfo != null) {
                                deviceInfo.put("androidRemark", androidInfo.getRemark());
                                deviceInfo.put("androidDescription", androidInfo.getDescription());
                                log.debug("Android设备识别: {} -> {}", userAgent, androidInfo.getRemark());
                            }
                        }
                    }
                    auditLog.setExtraData(objectMapper.writeValueAsString(deviceInfo));
                } catch (Exception e) {
                    log.warn("序列化设备信息失败", e);
                }
            }
            
            // 异步记录日志（避免影响主业务流程）
            try {
                auditLogService.recordAuditLog(auditLog);
            } catch (Exception e) {
                log.error("记录审计日志失败", e);
            }
        }
        
        return result;
    }
    
    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long) {
                return (Long) principal;
            }
            // 兼容 UserDetails 类型
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                // 从用户名查询用户ID（这里简化处理，实际应该从数据库或缓存获取）
                String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                log.debug("从 UserDetails 获取用户名: {}", username);
                // 由于无法直接获取ID，尝试从 request attribute 中获取
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    Long userId = (Long) attributes.getRequest().getAttribute("userId");
                    if (userId != null) {
                        return userId;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取当前用户ID失败", e);
        }
        return null;
    }
    
    /**
     * 获取当前用户名
     */
    private String getCurrentUsername() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            // 优先从 request attribute 获取（JWT过滤器已设置）
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String username = (String) attributes.getRequest().getAttribute("username");
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
            // 兼容 UserDetails 类型
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            }
        } catch (Exception e) {
            log.debug("获取当前用户名失败", e);
        }
        return "anonymous";
    }
    
    /**
     * 确定操作类型
     */
    private String determineOperationType(Method method) {
        String methodName = method.getName().toLowerCase();
        
        if (methodName.contains("login")) return "LOGIN";
        if (methodName.contains("logout")) return "LOGOUT";
        if (methodName.contains("register")) return "REGISTER";
        if (methodName.contains("create") || methodName.contains("add")) return "CREATE";
        if (methodName.contains("update") || methodName.contains("edit")) return "UPDATE";
        if (methodName.contains("delete") || methodName.contains("remove")) return "DELETE";
        if (methodName.contains("reset") && methodName.contains("password")) return "RESET_PASSWORD";
        if (methodName.contains("change") && methodName.contains("role")) return "CHANGE_ROLE";
        if (methodName.contains("toggle") || methodName.contains("enable") || methodName.contains("disable")) 
            return "TOGGLE_STATUS";
        if (methodName.contains("upload")) return "UPLOAD";
        if (methodName.contains("revoke") && methodName.contains("session")) return "REVOKE_SESSION";
        if (methodName.contains("revoke")) return "REVOKE";
        
        return "OTHER";
    }
    
    /**
     * 确定操作模块
     */
    private String determineModule(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        if (className.contains("User")) return "USER";
        if (className.contains("Post")) return "POST";
        if (className.contains("Comment")) return "COMMENT";
        if (className.contains("Category")) return "CATEGORY";
        if (className.contains("Tag")) return "TAG";
        if (className.contains("Blogger")) return "BLOGGER";
        if (className.contains("Audio")) return "AUDIO";
        if (className.contains("AuditLog")) return "SYSTEM";
        
        return "SYSTEM";
    }
    
    /**
     * 生成操作描述
     */
    private String generateDescription(Method method, ProceedingJoinPoint joinPoint) {
        String methodName = method.getName().toLowerCase();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 会话管理相关操作（需要在User之前，因为UserSessionController也包含"User"）
        if (className.contains("UserSession")) {
            if (methodName.contains("revoke") && methodName.contains("session") && methodName.contains("other")) {
                return "下线所有其他设备";
            }
            if (methodName.contains("revoke") && methodName.contains("session")) {
                // 尝试从请求属性中获取被下线设备的详细信息
                String deviceInfo = getRequestAttribute("revokedDeviceInfo");
                String userAgent = getRequestAttribute("revokedUserAgent");
                
                StringBuilder desc = new StringBuilder("下线设备: ");
                if (deviceInfo != null && !deviceInfo.isEmpty()) {
                    desc.append(deviceInfo);
                } else {
                    desc.append("未知设备");
                }
                if (userAgent != null && !userAgent.isEmpty()) {
                    // 解析User-Agent获取浏览器和OS信息
                    String browserInfo = parseBrowserName(userAgent);
                    String osInfo = parseOsName(userAgent);
                    desc.append(", 客户端: [").append(osInfo).append(", ").append(browserInfo).append("]");
                }
                return desc.toString();
            }
        }
        
        // 用户相关操作
        if (className.contains("User")) {
            if (methodName.contains("login")) return "用户登录";
            if (methodName.contains("logout")) return "用户登出";
            if (methodName.contains("register")) return "用户注册";
            if (methodName.contains("reset") && methodName.contains("password")) return "重置用户密码";
            if (methodName.contains("change") && methodName.contains("password")) return "修改密码";
            if (methodName.contains("update") && methodName.contains("role")) return "修改用户角色";
            if (methodName.contains("set") && methodName.contains("super")) return "设置超级管理员";
            if (methodName.contains("toggle") || methodName.contains("status")) return "启用/禁用用户";
            if (methodName.contains("upload") && methodName.contains("avatar")) return "上传头像";
            if (methodName.contains("update") && methodName.contains("user")) return "更新用户信息";
            if (methodName.contains("create") || methodName.contains("add")) return "创建用户";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除用户";
        }
        
        // 能力管理相关操作
        if (methodName.contains("ability") || methodName.contains("abilities")) {
            if (methodName.contains("set")) return "批量设置管理员能力";
            if (methodName.contains("get") || methodName.contains("query")) return "查询用户能力";
            if (methodName.contains("types")) return "获取能力类型列表";
        }
        
        // 文章相关操作
        if (className.contains("Post")) {
            if (methodName.contains("audit")) return "审计文章";
            if (methodName.contains("create") || methodName.contains("add")) return "创建文章";
            if (methodName.contains("update") || methodName.contains("edit")) return "更新文章";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除文章";
            if (methodName.contains("publish")) return "发布文章";
            if (methodName.contains("upload")) return "上传文章附件";
        }
        
        // 评论相关操作
        if (className.contains("Comment")) {
            if (methodName.contains("create") || methodName.contains("add")) return "创建评论";
            if (methodName.contains("update") || methodName.contains("edit")) return "编辑评论";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除评论";
            if (methodName.contains("toggle") || methodName.contains("status")) return "隐藏/显示评论";
            if (methodName.contains("like") || methodName.contains("thumbs")) return "点赞评论";
        }
        
        // 分类相关操作
        if (className.contains("Category")) {
            if (methodName.contains("create") || methodName.contains("add")) return "创建分类";
            if (methodName.contains("update") || methodName.contains("edit")) return "更新分类";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除分类";
        }
        
        // 标签相关操作
        if (className.contains("Tag")) {
            if (methodName.contains("create") || methodName.contains("add")) return "创建标签";
            if (methodName.contains("update") || methodName.contains("edit")) return "更新标签";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除标签";
        }
        
        // 系统配置相关操作
        if (className.contains("SystemConfig") || className.contains("Config")) {
            if (methodName.contains("update") || methodName.contains("batch")) return "更新系统配置";
            if (methodName.contains("reset")) return "重置系统配置";
            if (methodName.contains("get") || methodName.contains("query")) return "查询系统配置";
        }
        
        // 审计日志相关操作
        if (className.contains("AuditLog")) {
            if (methodName.contains("get") || methodName.contains("query")) return "查询审计日志";
            if (methodName.contains("stats")) return "获取审计统计";
            if (methodName.contains("delete") || methodName.contains("expired")) return "清理过期日志";
        }
        
        // 博主信息相关操作
        if (className.contains("Blogger")) {
            if (methodName.contains("update") || methodName.contains("edit")) return "更新博主信息";
            if (methodName.contains("upload") && methodName.contains("avatar")) return "上传博主头像";
        }
        
        // 音频相关操作
        if (className.contains("Audio")) {
            if (methodName.contains("upload")) return "上传音频文件";
            if (methodName.contains("delete") || methodName.contains("remove")) return "删除音频";
        }
        
        // 默认描述（使用模块+操作类型）
        String operationType = determineOperationType(method);
        String module = determineModule(joinPoint);
        return String.format("%s模块的%s操作", module, operationType);
    }
    
    /**
     * 从当前请求中获取属性值（线程安全）
     */
    private String getRequestAttribute(String attributeName) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                Object value = attributes.getRequest().getAttribute(attributeName);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.debug("获取请求属性失败: name={}", attributeName, e);
        }
        return null;
    }
    
    /**
     * 序列化并脱敏参数
     */
    private String serializeAndMaskParams(Object[] args, String[] paramNames) throws Exception {
        Map<String, Object> params = new HashMap<>();
        
        if (args != null && paramNames != null) {
            for (int i = 0; i < args.length; i++) {
                String paramName = paramNames[i];
                Object paramValue = args[i];
                
                // 跳过HttpServletRequest、HttpServletResponse等对象
                if (paramValue instanceof HttpServletRequest || 
                    paramValue instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                }
                
                // 脱敏密码字段
                if (paramName.toLowerCase().contains("password")) {
                    params.put(paramName, "***MASKED***");
                } else {
                    params.put(paramName, paramValue);
                }
            }
        }
        
        return objectMapper.writeValueAsString(params);
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
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
        
        // 多个代理时，第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * 解析设备类型（简化版）
     */
    private String parseDeviceType(String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || (ua.contains("android") && ua.contains("mobile"))) {
            return "MOBILE";
        }
        if (ua.contains("tablet") || ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile"))) {
            return "TABLET";
        }
        return "DESKTOP";
    }
    
    /**
     * 解析操作系统名称（增强版）
     * 提取详细的操作系统信息，包括平台、架构等
     */
    private String parseOsName(String userAgent) {
        // Linux 系统（需要优先检测，因为很多UA包含Linux关键字）
        if (userAgent.contains("X11") && userAgent.contains("Linux")) {
            // 提取 X11; Linux x86_64 或 X11; Linux aarch64 等
            int x11Index = userAgent.indexOf("X11");
            int linuxIndex = userAgent.indexOf("Linux", x11Index);
            if (linuxIndex != -1) {
                // 查找括号内的完整信息
                int startBracket = userAgent.indexOf('(', x11Index);
                int endBracket = userAgent.indexOf(')', startBracket);
                if (startBracket != -1 && endBracket != -1) {
                    String platformInfo = userAgent.substring(startBracket + 1, endBracket);
                    // 返回如 "X11; Linux x86_64"
                    return platformInfo.trim();
                }
            }
            return "Linux";
        }
        
        if (userAgent.contains("Linux")) {
            // Android 也是基于 Linux，但单独处理
            if (userAgent.contains("Android")) {
                // 提取 Android 版本
                int androidIndex = userAgent.indexOf("Android");
                int semicolonIndex = userAgent.indexOf(';', androidIndex);
                if (semicolonIndex != -1) {
                    String androidVersion = userAgent.substring(androidIndex, semicolonIndex).trim();
                    return androidVersion;
                }
                return "Android";
            }
            return "Linux";
        }
        
        // Windows 系统
        if (userAgent.contains("Windows NT")) {
            int winIndex = userAgent.indexOf("Windows NT");
            int semicolonIndex = userAgent.indexOf(';', winIndex);
            if (semicolonIndex != -1) {
                String winVersion = userAgent.substring(winIndex, semicolonIndex).trim();
                // 映射版本号到友好名称
                if (winVersion.contains("10.0")) return "Windows 10/11";
                if (winVersion.contains("6.3")) return "Windows 8.1";
                if (winVersion.contains("6.2")) return "Windows 8";
                if (winVersion.contains("6.1")) return "Windows 7";
                return winVersion;
            }
            return "Windows";
        }
        
        // macOS
        if (userAgent.contains("Mac OS X")) {
            int macIndex = userAgent.indexOf("Mac OS X");
            int closingBracket = userAgent.indexOf(')', macIndex);
            int semicolonIndex = userAgent.indexOf(';', macIndex);
            int endIndex = (closingBracket != -1 && closingBracket < semicolonIndex) ? closingBracket : semicolonIndex;
            if (endIndex != -1) {
                String macVersion = userAgent.substring(macIndex, endIndex).trim();
                return macVersion;
            }
            return "macOS";
        }
        
        // iOS
        if (userAgent.contains("iPhone OS") || userAgent.contains("iPad OS")) {
            int osIndex = userAgent.contains("iPhone OS") ? userAgent.indexOf("iPhone OS") : userAgent.indexOf("iPad OS");
            int semicolonIndex = userAgent.indexOf(';', osIndex);
            if (semicolonIndex != -1) {
                String iosVersion = userAgent.substring(osIndex, semicolonIndex).trim();
                return iosVersion;
            }
            return userAgent.contains("iPhone") ? "iOS" : "iPadOS";
        }
        
        return "Unknown";
    }
    
    /**
     * 解析浏览器名称（简化版）
     */
    private String parseBrowserName(String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/") || ua.contains("edge/")) return "Edge";
        if (ua.contains("chrome/") && !ua.contains("chromium")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/") && !ua.contains("chrome/")) return "Safari";
        if (ua.contains("opr/") || ua.contains("opera/")) return "Opera";
        return "Unknown";
    }
}
