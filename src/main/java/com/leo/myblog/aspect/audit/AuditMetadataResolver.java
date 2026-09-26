package com.leo.myblog.aspect.audit;

import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.AuditModule;
import com.leo.myblog.enums.AuditLevel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计元数据编排：把「注解声明」与「自动推导」合成最终要落库的字段
 * <p>
 * 优先级一律是<b>注解优先、推导兜底</b>；没有注解的方法也能得到可读结果，
 * 因此可以只给最关键的模块加注解。
 * <p>
 * 无 Spring 依赖，可直接对真实 controller 方法做反射调用测试。
 */
public final class AuditMetadataResolver {

    private AuditMetadataResolver() {
    }

    /** 描述模板里的占位符：{name} / {name.field} / {0} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private static final int MAX_DESCRIPTION_LEN = 200;
    private static final int MAX_VALUE_LEN = 64;

    /**
     * 解析结果
     *
     * @param module       业务模块
     * @param action       操作类型（同时决定基础等级）
     * @param description  可读描述
     * @param baseLevel    成功时的等级
     * @param recordParams 是否记录请求参数
     * @param logOnSuccess 成功时是否落库
     */
    public record AuditMetadata(String module, String action, String description,
                                AuditLevel baseLevel, boolean recordParams, boolean logOnSuccess) {
    }

    /**
     * @param method       目标方法
     * @param args         实参
     * @param paramNames   形参名（可能为 null）
     * @param paramAnns    形参注解（可能为 null）
     * @param targetClass  目标控制器类
     * @param httpMethod   HTTP 方法，用于兜底描述；可为 null
     * @param uri          请求 URI，用于兜底描述；可为 null
     */
    public static AuditMetadata resolve(Method method, Object[] args, String[] paramNames,
                                        Annotation[][] paramAnns, Class<?> targetClass,
                                        String httpMethod, String uri) {
        AuditLog annotation = findAnnotation(method, targetClass);

        // ---- action 与 module：注解优先，其次推导 ----
        String action = annotation != null && !annotation.action().isBlank()
                ? annotation.action()
                : AuditActionResolver.resolve(method != null ? method.getName() : null);

        String module = annotation != null && !annotation.module().isBlank()
                ? annotation.module()
                : AuditModuleResolver.resolve(targetClass);

        // ---- 操作对象 ----
        String object = AuditObjectExtractor.extract(args, paramNames, paramAnns,
                annotation != null ? annotation.object() : null);

        // ---- 描述 ----
        String description;
        if (annotation != null && !annotation.description().isBlank()) {
            description = renderTemplate(annotation.description(), args, paramNames);
        } else {
            description = compose(action, object, httpMethod, uri);
        }

        boolean recordParams = annotation == null || annotation.recordParams();
        boolean logOnSuccess = annotation == null || annotation.logOnSuccess();

        return new AuditMetadata(module, action, trim(description, MAX_DESCRIPTION_LEN),
                AuditLevelResolver.baseOf(action), recordParams, logOnSuccess);
    }

    /** 类级注解作为方法级缺失时的兜底 */
    private static AuditLog findAnnotation(Method method, Class<?> targetClass) {
        if (method != null) {
            AuditLog onMethod = method.getAnnotation(AuditLog.class);
            if (onMethod != null) {
                return onMethod;
            }
        }
        return targetClass != null ? targetClass.getAnnotation(AuditLog.class) : null;
    }

    /**
     * 通用描述：动作中文 + 操作对象。
     * <p>
     * 例如 {@code 审核 45}、{@code 调整权限 123}、{@code 修改配置 site.title}。
     * 动作是 OTHER 或拿不到对象且毫无信息时，退到「动作 +（HTTP 方法 URI）」——
     * 关键是<b>绝不产出「XX模块的OTHER操作」这种读不出所以然的文案</b>。
     */
    private static String compose(String action, String object, String httpMethod, String uri) {
        String label = AuditAction.label(action);
        if (object != null && !object.isBlank()) {
            return label + " " + object;
        }
        if (AuditAction.OTHER.equals(action) || label.equals(action)) {
            if (httpMethod != null && uri != null) {
                return label + "（" + httpMethod + " " + uri + "）";
            }
        }
        return label;
    }

    /** 渲染注解里的描述模板 */
    private static String renderTemplate(String template, Object[] args, String[] paramNames) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            String replacement = resolveToken(token, args, paramNames);
            // 占位符取不到值时替换成空串（不打印 null）
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    private static String resolveToken(String token, Object[] args, String[] paramNames) {
        // 位置占位：{0}
        if (token.chars().allMatch(Character::isDigit)) {
            int index = Integer.parseInt(token);
            if (args != null && index < args.length) {
                return stringify(args[index]);
            }
            return "";
        }

        // 字段占位：{request.configKey}
        int dot = token.indexOf('.');
        String paramName = dot > 0 ? token.substring(0, dot) : token;
        Object value = AuditObjectExtractor.valueOf(paramName, args, paramNames);
        if (value == null) {
            return "";
        }
        if (dot > 0) {
            String field = token.substring(dot + 1);
            value = readField(value, field);
        }
        return stringify(value);
    }

    /** 读取 DTO 的一级字段：先 getXxx，再 isXxx */
    private static Object readField(Object target, String field) {
        if (target == null) {
            return null;
        }
        String capitalized = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (String getter : new String[]{"get" + capitalized, "is" + capitalized}) {
            try {
                return target.getClass().getMethod(getter).invoke(target);
            } catch (Exception ignored) {
                // 换下一个 getter 形式
            }
        }
        return null;
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return trim(text, MAX_VALUE_LEN);
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
