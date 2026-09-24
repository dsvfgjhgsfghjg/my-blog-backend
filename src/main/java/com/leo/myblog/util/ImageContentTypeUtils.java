package com.leo.myblog.util;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Locale;
import java.util.Map;

/**
 * 图片内容类型推导 —— 按 MinIO 对象路径的扩展名决定 Content-Type 与 Content-Disposition。
 *
 * <p>头像对象路径形如 {@code avatars/{userId}/{uuid}.gif}，扩展名来自上传时经过白名单 +
 * 魔数双重校验的格式，因此它是比「读取 MinIO 对象元数据」或「嗅探字节」都更廉价、
 * 且可信度相当的依据。
 *
 * <p>此前该逻辑在 UserController 中有一份私有实现，而博主头像则直接把 Content-Type
 * 写死成 image/jpeg —— 结果是「字节是 GIF、声明是 JPEG」，浏览器解码失败直接破图。
 */
public final class ImageContentTypeUtils {

    /** Spring 6.1 的 MediaType 只有 JPEG/PNG/GIF 常量，WebP 与 SVG 需自行解析。 */
    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");
    private static final MediaType IMAGE_SVG = MediaType.parseMediaType("image/svg+xml");

    private static final Map<String, MediaType> BY_EXTENSION = Map.of(
            ".jpg", MediaType.IMAGE_JPEG,
            ".jpeg", MediaType.IMAGE_JPEG,
            ".png", MediaType.IMAGE_PNG,
            ".gif", MediaType.IMAGE_GIF,
            ".webp", IMAGE_WEBP,
            ".svg", IMAGE_SVG
    );

    /**
     * 未知格式的兜底值。刻意不沿用原来的 image/jpeg —— 那是在撒谎，
     * 而生产入口设置了 {@code X-Content-Type-Options: nosniff}，浏览器必须按声明解释，
     * 撒谎的后果就是破图（.svg 此前正是这样被误标成 JPEG 的）。
     */
    private static final MediaType FALLBACK = MediaType.APPLICATION_OCTET_STREAM;

    /**
     * Content-Disposition 取值策略。
     *
     * <ul>
     *   <li>{@code AUTO}：位图用 inline（便于右键另存 / 新标签打开），SVG 与未知格式仍用 attachment</li>
     *   <li>{@code ATTACHMENT}：一律 attachment，用于语义上明确是「下载」的接口</li>
     * </ul>
     */
    public enum Disposition { AUTO, ATTACHMENT }

    private ImageContentTypeUtils() {
    }

    /** 按扩展名推导 Content-Type，未知一律 octet-stream。 */
    public static MediaType contentTypeOf(String objectPath) {
        MediaType type = BY_EXTENSION.get(extensionOf(objectPath));
        return type != null ? type : FALLBACK;
    }

    public static boolean isSvg(String objectPath) {
        return ".svg".equals(extensionOf(objectPath));
    }

    /** 仅返回白名单内的扩展名，其余返回空串 —— 供拼接 filename 用，杜绝响应头注入。 */
    public static String safeExtension(String objectPath) {
        String extension = extensionOf(objectPath);
        return BY_EXTENSION.containsKey(extension) ? extension : "";
    }

    /**
     * 一次性写入 Content-Type 与 Content-Disposition。
     *
     * <p>关于 Content-Disposition：按 RFC 6266 它只作用于顶层导航，对 {@code <img>}/fetch
     * 这类子资源请求会被浏览器忽略。因此它对「防止图片被内联渲染」并无实际作用，
     * 真正的安全边界是 Content-Type 白名单 + nosniff。保留 attachment 仅用于堵住
     * 「用户直接导航到该 URL」这一条路径，故只对唯一具备脚本能力的 SVG 生效。
     */
    public static void applyImageHeaders(HttpHeaders headers, String objectPath, Disposition mode) {
        MediaType contentType = contentTypeOf(objectPath);
        headers.setContentType(contentType);

        boolean attach = mode == Disposition.ATTACHMENT
                || isSvg(objectPath)
                || FALLBACK.equals(contentType);

        headers.setContentDisposition(
                (attach ? ContentDisposition.attachment() : ContentDisposition.inline())
                        .filename("avatar" + safeExtension(objectPath))
                        .build()
        );
    }

    /**
     * 取小写扩展名；返回空串表示「无扩展名」。
     *
     * <p>{@code dot <= slash} 同时覆盖了两种边界：没有点，以及「路径里带点的目录」
     * （如 {@code blogger/avatars/v1.2/uuid} —— 那个点属于目录名，不是文件扩展名）。
     */
    private static String extensionOf(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            return "";
        }
        int slash = objectPath.lastIndexOf('/');
        int dot = objectPath.lastIndexOf('.');
        if (dot <= slash || dot == objectPath.length() - 1) {
            return "";
        }
        return objectPath.substring(dot).toLowerCase(Locale.ROOT);
    }
}
