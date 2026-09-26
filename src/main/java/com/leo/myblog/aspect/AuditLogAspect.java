package com.leo.myblog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.aspect.audit.AuditLevelResolver;
import com.leo.myblog.aspect.audit.AuditMetadataResolver;
import com.leo.myblog.aspect.audit.AuditParamSanitizer;
import com.leo.myblog.common.Result;
import com.leo.myblog.entity.AuditLog;
import com.leo.myblog.enums.AuditLevel;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.service.AuditLogService;
import com.leo.myblog.util.AndroidUaDetector;
import com.leo.myblog.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志切面
 * <p>
 * 只负责<b>编排</b>：取请求上下文 → 交给解析器算出元数据 → 脱敏参数 → 执行 → 判定结果 → 落库。
 * 具体的「怎么推导操作类型/模块/描述/等级」都在 {@code aspect.audit} 下的纯函数里，
 * 那样才能脱离 Spring 上下文直接单测（见 {@code AuditActionResolverTest} 等）。
 * <p>
 * 覆盖范围：
 * <ol>
 *   <li>{@code @RequireAdmin} —— 管理员接口</li>
 *   <li>{@code @AuditLog} —— 显式声明审计元数据的接口</li>
 *   <li>{@code @RequireTotp} —— 需要二次验证的敏感操作。它的语义天然就是"敏感"，
 *       正好补上原先的盲区：改系统配置、删文章、改密码、启停 2FA 此前完全无日志</li>
 *   <li>认证与会话的几个关键方法（见切点里的 execution 列表）</li>
 * </ol>
 * <b>刻意不纳入</b>：{@code LoginController.pollResult} —— 前端每 2 秒轮询一次，
 * 纳入后一次登录会灌进几百条日志，把审计表变成噪声。
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final AndroidUaDetector androidUaDetector;
    private final UserAgentParser userAgentParser;

    /** 与切面共用同一个 ObjectMapper 配置（含 snake_case 命名策略），否则字段名对不上掩码表 */
    private final ObjectMapper objectMapper;
    private final AuditParamSanitizer paramSanitizer;

    private static final int MAX_RESPONSE_MESSAGE_LEN = 500;

    public AuditLogAspect(AuditLogService auditLogService,
                          AndroidUaDetector androidUaDetector,
                          UserAgentParser userAgentParser,
                          ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.androidUaDetector = androidUaDetector;
        this.userAgentParser = userAgentParser;
        this.objectMapper = objectMapper;
        this.paramSanitizer = new AuditParamSanitizer(objectMapper);
    }

    @Around("@annotation(com.leo.myblog.annotation.RequireAdmin) || " +
            "@annotation(com.leo.myblog.annotation.AuditLog) || " +
            "@annotation(com.leo.myblog.annotation.RequireTotp) || " +
            // 两步登录：真实的登录入口（原先写死的 UserController.login 前端已不再调用）
            "execution(* com.leo.myblog.controller.LoginController.step2Password(..)) || " +
            "execution(* com.leo.myblog.controller.LoginController.step2Challenge(..)) || " +
            // 以下几条前端已不再调用，但 App/第三方可能仍在用，保留覆盖
            "execution(* com.leo.myblog.controller.UserController.login(..)) || " +
            "execution(* com.leo.myblog.controller.UserController.logout(..)) || " +
            "execution(* com.leo.myblog.controller.UserController.register(..)) || " +
            "execution(* com.leo.myblog.controller.OAuth2Controller.token(..)) || " +
            "execution(* com.leo.myblog.controller.OAuth2Controller.refreshToken(..)) || " +
            "execution(* com.leo.myblog.controller.OAuth2Controller.revokeToken(..)) || " +
            "execution(* com.leo.myblog.controller.UserSessionController.revokeSession(..)) || " +
            "execution(* com.leo.myblog.controller.UserSessionController.revokeOtherSessions(..))")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass() : method.getDeclaringClass();

        // 解析元数据（注解优先，推导兜底）
        AuditMetadataResolver.AuditMetadata metadata = AuditMetadataResolver.resolve(
                method, args, paramNames, paramAnnotations, targetClass,
                request != null ? request.getMethod() : null,
                request != null ? request.getRequestURI() : null);

        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(getCurrentUserId());
        auditLog.setUsername(getCurrentUsername());
        auditLog.setOperationType(metadata.action());
        auditLog.setModule(metadata.module());
        auditLog.setDescription(metadata.description());

        if (request != null) {
            auditLog.setIpAddress(getClientIp(request));
            auditLog.setUserAgent(request.getHeader("User-Agent"));
            auditLog.setRequestUrl(request.getRequestURI());
            auditLog.setRequestMethod(request.getMethod());
            if (metadata.recordParams()) {
                auditLog.setRequestParams(paramSanitizer.sanitize(args, paramNames, paramAnnotations));
            }
        }

        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                auditLog.setExecutionTime(System.currentTimeMillis() - startTime);
                auditLog.setCreateTime(LocalDateTime.now());
                applyOutcome(auditLog, result, error);
                auditLog.setLevel(AuditLevelResolver
                        .effective(metadata.baseLevel(), Boolean.TRUE.equals(auditLog.getSuccess()), error)
                        .getValue());

                if (isDeviceInfoRelevant(auditLog) && request != null) {
                    auditLog.setExtraData(buildDeviceInfo(request));
                }

                // 成功时按需降噪：高频只读接口可在注解上关掉；失败一律记录
                if (Boolean.FALSE.equals(auditLog.getSuccess()) || metadata.logOnSuccess()) {
                    auditLogService.recordAuditLog(auditLog);
                }
            } catch (Exception e) {
                // 审计失败不能影响主业务，但必须留痕 —— 静默吞掉会让"日志缺失"无从发现
                log.error("记录审计日志失败: action={}, module={}", metadata.action(), metadata.module(), e);
            }
        }
    }

    /**
     * 由执行结果判定成功与否、响应码与响应消息
     * <p>
     * 改造前只在 {@code catch} 里置 success=false，而控制器大量用
     * {@code return Result.error(400, ...)} 而不是抛异常，于是「TOTP 校验失败」「参数非法」
     * 这类失败全被记成 success=true —— 等级判定与「最近失败」统计都因此失真。
     * <p>
     * 注意字段语义：responseStatus 现在是<b>业务返回码</b>而非 HTTP 状态码。
     */
    private void applyOutcome(AuditLog auditLog, Object result, Throwable error) {
        if (error != null) {
            auditLog.setSuccess(false);
            int code = (error instanceof BusinessException be && be.getCode() != null)
                    ? be.getCode() : 500;
            auditLog.setResponseStatus(code);
            auditLog.setResponseMessage(abbreviate("操作失败: " + error.getMessage()));
            auditLog.setErrorMessage(error.getClass().getSimpleName() + ": " + error.getMessage());
            return;
        }

        if (result instanceof Result<?> r) {
            Integer code = r.getCode();
            auditLog.setResponseStatus(code == null ? 200 : code);
            auditLog.setResponseMessage(abbreviate(r.getMessage()));
            if (code != null && code != 0 && code != 200) {
                auditLog.setSuccess(false);
                auditLog.setErrorMessage(abbreviate("业务返回码 " + code + "：" + r.getMessage()));
            } else {
                auditLog.setSuccess(true);
            }
            return;
        }

        auditLog.setSuccess(true);
        auditLog.setResponseStatus(200);
        auditLog.setResponseMessage("操作成功");
    }

    /** 只有登录类操作才需要设备信息 */
    private boolean isDeviceInfoRelevant(AuditLog auditLog) {
        return "LOGIN".equals(auditLog.getOperationType());
    }

    /**
     * 构建设备信息
     * <p>
     * 刻意<b>不写 userAgent</b>：它已单独存在 {@code user_agent} 列里，重复写进 extraData
     * 只会让每行膨胀一倍。这里只留解析后的结构化字段。
     */
    private String buildDeviceInfo(HttpServletRequest request) {
        try {
            String userAgent = request.getHeader("User-Agent");
            if (userAgent == null || userAgent.isEmpty()) {
                return null;
            }
            Map<String, String> deviceInfo = new HashMap<>();
            UserAgentParser.DeviceInfo parsedInfo = userAgentParser.parseUserAgent(userAgent);
            deviceInfo.put("deviceType", parsedInfo.getDeviceType());
            deviceInfo.put("osName", parsedInfo.getOsName());
            deviceInfo.put("browserName", parsedInfo.getBrowserName());

            if ("Android".equals(parsedInfo.getOsName())) {
                AndroidUaDetector.AndroidDeviceInfo androidInfo = androidUaDetector.detect(userAgent);
                if (androidInfo != null) {
                    deviceInfo.put("androidRemark", androidInfo.getRemark());
                    deviceInfo.put("androidDescription", androidInfo.getDescription());
                }
            }
            // 统一过一遍脱敏，防止将来有人往这里塞凭据
            return objectMapper.writeValueAsString(paramSanitizer.mask(objectMapper.valueToTree(deviceInfo)));
        } catch (Exception e) {
            log.warn("序列化设备信息失败", e);
            return null;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_RESPONSE_MESSAGE_LEN
                ? text.substring(0, MAX_RESPONSE_MESSAGE_LEN) : text;
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long id) {
                return id;
            }
            // 兼容 UserDetails 类型：回退读 JWT 过滤器塞进 request 的 userId
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String username = (String) attributes.getRequest().getAttribute("username");
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails details) {
                return details.getUsername();
            }
        } catch (Exception e) {
            log.debug("获取当前用户名失败", e);
        }
        return "anonymous";
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
}
