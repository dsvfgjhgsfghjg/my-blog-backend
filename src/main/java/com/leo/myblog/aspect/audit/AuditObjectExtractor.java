package com.leo.myblog.aspect.audit;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 从方法实参里提取"操作对象"，让描述从「更新系统配置」变成「更新系统配置「site.name」」
 * <p>
 * 核心技巧：<b>靠参数注解区分操作者与目标</b>，而不是靠参数名。
 * {@code resetPassword(@RequestAttribute Long userId, @PathVariable Long targetUserId)} 里的
 * {@code userId} 是操作者，{@code targetUserId} 才是目标；而
 * {@code getUserAbilities(@PathVariable Long userId)} 里的 {@code userId} 却是目标。
 * 只看名字无法区分，看注解就一目了然 —— 带 {@code @RequestAttribute}/{@code @RequestHeader}
 * 的形参一律排除。
 */
public final class AuditObjectExtractor {

    private AuditObjectExtractor() {
    }

    /** 高置信度的对象名（小写比较），优先于"第一个路径变量"的兜底策略 */
    private static final Set<String> OBJECT_NAMES = Set.of(
            "targetuserid", "userid", "postid", "commentid", "tagid", "categoryid",
            "audioid", "reportid", "sessionid", "deviceid", "id", "key",
            "configkey", "ipaddress", "username", "tag", "name", "title");

    /** 这些形参是分页/开关/说明之类，不该被当成操作对象 */
    private static final Set<String> REJECT_NAMES = Set.of(
            "pagenum", "pagesize", "keyword", "days", "durationhours", "reason",
            "visible", "status", "enabled", "totpcode", "method", "type", "sort");

    private static final int MAX_LEN = 64;

    /**
     * @param explicitParam 注解 {@code object()} 指定的形参名；为空则自动挑选
     * @return 操作对象的可读值；无法确定时返回 null（调用方据此省略对象后缀，不打印"未知对象"）
     */
    public static String extract(Object[] args, String[] paramNames, Annotation[][] paramAnnotations,
                                 String explicitParam) {
        if (args == null || args.length == 0) {
            return null;
        }

        // 1. 注解显式指定
        if (explicitParam != null && !explicitParam.isBlank() && paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                if (explicitParam.equals(paramNames[i]) && isUsable(args[i])) {
                    return format(args[i]);
                }
            }
        }

        // 2. 名字白名单（跳过操作者类形参）
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                String name = paramNames[i];
                if (name == null || isOperatorParam(paramAnnotations, i) || !isUsable(args[i])) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (OBJECT_NAMES.contains(lower) && !REJECT_NAMES.contains(lower)) {
                    return format(args[i]);
                }
            }
        }

        // 3. 第一个路径变量 / 请求参数（标量）
        for (int i = 0; i < args.length; i++) {
            if (isOperatorParam(paramAnnotations, i) || !isUsable(args[i])) {
                continue;
            }
            if (hasAnnotation(paramAnnotations, i, PathVariable.class)
                    || hasAnnotation(paramAnnotations, i, RequestParam.class)) {
                String name = paramNames != null && i < paramNames.length ? paramNames[i] : null;
                if (name != null && REJECT_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (isScalar(args[i])) {
                    return format(args[i]);
                }
            }
        }

        // 4. 请求体 DTO 里的标识字段
        for (int i = 0; i < args.length; i++) {
            if (isOperatorParam(paramAnnotations, i) || !isUsable(args[i])) {
                continue;
            }
            if (hasAnnotation(paramAnnotations, i, RequestBody.class)) {
                return format(readIdentifier(args[i]));
            }
        }

        return null;
    }

    /** 按 {} 模板里的名字取实参，供描述模板使用 */
    public static Object valueOf(String paramName, Object[] args, String[] paramNames) {
        if (paramName == null || args == null || paramNames == null) {
            return null;
        }
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            if (paramName.equals(paramNames[i])) {
                return args[i];
            }
        }
        return null;
    }

    private static boolean isOperatorParam(Annotation[][] annotations, int index) {
        return hasAnnotation(annotations, index, RequestAttribute.class)
                || hasAnnotation(annotations, index, RequestHeader.class);
    }

    private static boolean isUsable(Object value) {
        if (value == null) {
            return false;
        }
        // Servlet/Multipart 之类不宜作为对象展示
        String type = value.getClass().getSimpleName();
        return !type.startsWith("Http") && !type.contains("MultipartFile")
                && !(value instanceof java.io.InputStream) && !(value instanceof byte[]);
    }

    private static boolean isScalar(Object value) {
        return value instanceof Number || value instanceof String || value instanceof UUID
                || value instanceof Boolean || value instanceof Character;
    }

    /** 从 DTO 里读标识字段，优先级：id → configKey/key → title/name → 任何 *Id */
    private static Object readIdentifier(Object dto) {
        if (dto == null || isScalar(dto)) {
            return dto;
        }
        List<String> preferred = List.of("getId", "getConfigKey", "getKey", "getTitle",
                "getName", "getUsername", "getIpAddress");
        for (String getter : preferred) {
            Object value = invoke(dto, getter);
            if (value != null) {
                return value;
            }
        }
        // 退而求其次：任何以 Id 结尾的 getter
        for (Method method : dto.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().endsWith("Id")
                    && method.getName().startsWith("get")) {
                Object value = invoke(dto, method.getName());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object invoke(Object target, String getter) {
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String format(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_LEN ? text.substring(0, MAX_LEN) + "…" : text;
    }

    private static boolean hasAnnotation(Annotation[][] annotations, int index,
                                         Class<? extends Annotation> type) {
        if (annotations == null || index >= annotations.length || annotations[index] == null) {
            return false;
        }
        for (Annotation annotation : annotations[index]) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }
}
