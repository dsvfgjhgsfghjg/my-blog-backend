package com.leo.myblog.aspect.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审计日志的请求参数脱敏器
 * <p>
 * 为什么需要它：原先切面只判断**形参名**是否含 "password"，而登录方法的形参名是
 * {@code request}（{@code login(@RequestBody LoginRequest request)}），判断永远不成立，
 * 于是整个 DTO 被序列化进 {@code request_params} —— 密码、clientSecret、refreshToken
 * 全部明文入库。所以必须改成递归遍历对象树、按**字段名**打码。
 * <p>
 * 本类不依赖 Spring 容器（只接收一个 ObjectMapper），便于直接单测。
 */
@Slf4j
public class AuditParamSanitizer {

    /** 硬掩码后的占位值。刻意不带长度/前后缀 —— OAuth2 token 前缀固定，截断展示会泄露类型 */
    public static final String MASK = "***MASKED***";

    /** 一律打码：值本身即凭据 */
    private static final Set<String> HARD_MASK = Set.of(
            "password", "passwd", "pwd", "oldpassword", "newpassword", "confirmpassword",
            "secret", "clientsecret", "secretkey", "privatekey", "credential", "credentials",
            "token", "accesstoken", "refreshtoken", "idtoken", "authorization", "cookie",
            "totpcode", "backupcode", "captchacode", "verifycode", "smscode", "authcode",
            // 精确名匹配（用 equals 而非 contains），避免误伤 statusCode / detailCode
            "code");

    /** 部分掩码：保留可辨识形态，便于审计核对"是哪个账号" */
    private static final Set<String> PARTIAL_MASK = Set.of(
            "phone", "mobile", "email", "idcard", "idnumber", "realname", "bankcard");

    /** 降采样：这类字段是坐标序列，原样存会灌进几千个点 */
    private static final Set<String> DOWNSAMPLE = Set.of(
            "captchatrajectory", "trajectory", "points", "path", "trace");

    private static final int MAX_DEPTH = 3;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_OBJECT_KEYS = 50;
    private static final int MAX_VALUE_LEN = 256;
    /** 落库总量上限，超出则整体换成一个说明性的 JSON（保持合法 JSON，不截断成半截） */
    private static final int MAX_TOTAL_LEN = 8192;

    private final ObjectMapper objectMapper;

    public AuditParamSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 序列化并脱敏请求参数
     *
     * @param args            方法实参
     * @param paramNames      形参名（可能为 null）
     * @param paramAnnotations 形参注解，用于跳过操作者类参数；可为 null
     * @return 合法 JSON 字符串；序列化失败时返回带 _error 的 JSON 而不是 "{}"
     */
    public String sanitize(Object[] args, String[] paramNames, Annotation[][] paramAnnotations) {
        ObjectNode params = objectMapper.createObjectNode();

        if (args == null) {
            return "{}";
        }

        for (int i = 0; i < args.length; i++) {
            String name = paramNames != null && i < paramNames.length && paramNames[i] != null
                    ? paramNames[i] : ("arg" + i);
            Object value = args[i];

            // 操作者信息已在 userId/username 独立列，重复记进参数只会增加噪声与泄露面
            if (hasAnnotation(paramAnnotations, i, RequestAttribute.class)
                    || hasAnnotation(paramAnnotations, i, RequestHeader.class)) {
                continue;
            }

            // Servlet 对象无法（也不该）序列化
            if (value instanceof HttpServletRequest || value instanceof HttpServletResponse) {
                continue;
            }

            params.set(name, summarizeSpecial(value));
        }

        try {
            String json = objectMapper.writeValueAsString(params);
            if (json.length() > MAX_TOTAL_LEN) {
                // 保持合法 JSON：不用 substring 砍成半截，否则前端 JSON.parse 会失败
                ObjectNode truncated = objectMapper.createObjectNode();
                truncated.put("_truncated", true);
                truncated.put("_originalLength", json.length());
                return objectMapper.writeValueAsString(truncated);
            }
            return json;
        } catch (Exception e) {
            // 原先失败落 "{}"，无法区分"真的没有参数"和"序列化炸了"
            log.warn("审计参数序列化失败", e);
            return "{\"_error\":\"参数序列化失败\"}";
        }
    }

    /**
     * 递归打码。供 request_params 与 extra_data 复用。
     */
    public JsonNode mask(JsonNode node) {
        return mask(node, 0);
    }

    // ==================== 内部实现 ====================

    private JsonNode mask(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (depth >= MAX_DEPTH) {
            return TextNode.valueOf("{…}");
        }

        if (node.isObject()) {
            ObjectNode source = (ObjectNode) node;
            ObjectNode result = objectMapper.createObjectNode();
            int count = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext() && count < MAX_OBJECT_KEYS) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                // 归一化：项目的 Jackson 用 snake_case 输出（client_secret / refresh_token），
                // 而掩码表按 camelCase 书写。去掉下划线与连字符后再比对，两种命名都能命中 ——
                // 漏掉这一步，最该保护的 refresh_token 反而会被放过。
                String lower = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                JsonNode value = field.getValue();

                if (HARD_MASK.contains(lower)) {
                    result.put(key, MASK);
                } else if (PARTIAL_MASK.contains(lower) && value.isTextual()) {
                    result.put(key, partialMask(value.asText()));
                } else if (DOWNSAMPLE.contains(lower) && value.isArray()) {
                    result.put(key, "[" + value.size() + " 点轨迹]");
                } else {
                    result.set(key, mask(value, depth + 1));
                }
                count++;
            }
            if (fields.hasNext()) {
                result.put("_moreKeys", "…");
            }
            return result;
        }

        if (node.isArray()) {
            ArrayNode source = (ArrayNode) node;
            if (source.size() > MAX_LIST_ITEMS) {
                return TextNode.valueOf("[" + source.size() + " 项]");
            }
            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode item : source) {
                result.add(mask(item, depth + 1));
            }
            return result;
        }

        if (node.isTextual() && node.asText().length() > MAX_VALUE_LEN) {
            return TextNode.valueOf(node.asText().substring(0, MAX_VALUE_LEN) + "…");
        }

        return node;
    }

    /** 无法/不宜序列化的实参，替换成可读摘要 */
    private JsonNode summarizeSpecial(Object value) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        if (value instanceof MultipartFile file) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("filename", file.getOriginalFilename());
            node.put("size", file.getSize());
            return node;
        }
        if (value instanceof byte[] bytes) {
            return TextNode.valueOf("<" + bytes.length + " bytes>");
        }
        if (value instanceof InputStream) {
            return TextNode.valueOf("<stream>");
        }
        try {
            return mask(objectMapper.valueToTree(value), 0);
        } catch (IllegalArgumentException e) {
            // valueToTree 对无法序列化的类型抛此异常
            return TextNode.valueOf("<" + value.getClass().getSimpleName() + ">");
        }
    }

    /** 保留首尾的局部掩码：138****1234 / ab***@example.com */
    private String partialMask(String value) {
        if (value.isEmpty()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at > 0) {
            String local = value.substring(0, at);
            String domain = value.substring(at);
            String head = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
            return head + "***" + domain;
        }
        if (value.length() <= 4) {
            return MASK;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 2);
    }

    private boolean hasAnnotation(Annotation[][] annotations, int index,
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
