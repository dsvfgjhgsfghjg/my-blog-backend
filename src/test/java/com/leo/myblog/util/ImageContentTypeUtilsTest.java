package com.leo.myblog.util;

import com.leo.myblog.util.ImageContentTypeUtils.Disposition;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片内容类型推导测试。
 *
 * <p>纯静态工具，不加载 Spring 上下文。
 */
public class ImageContentTypeUtilsTest {

    @Test
    public void testGifMapsToImageGif() {
        // 本次改动的核心：GIF 必须拿到 image/gif，否则浏览器不播放动画
        assertEquals(MediaType.IMAGE_GIF, ImageContentTypeUtils.contentTypeOf("avatars/1/abc.gif"));
        assertEquals(MediaType.IMAGE_GIF, ImageContentTypeUtils.contentTypeOf("blogger/avatars/xyz.gif"));
    }

    @Test
    public void testExtensionIsCaseInsensitive() {
        assertEquals(MediaType.IMAGE_GIF, ImageContentTypeUtils.contentTypeOf("avatars/1/abc.GIF"));
        assertEquals(MediaType.IMAGE_PNG, ImageContentTypeUtils.contentTypeOf("avatars/1/abc.PNG"));
    }

    @Test
    public void testBitmapTypes() {
        assertEquals(MediaType.IMAGE_JPEG, ImageContentTypeUtils.contentTypeOf("avatars/1/a.jpg"));
        assertEquals(MediaType.IMAGE_JPEG, ImageContentTypeUtils.contentTypeOf("avatars/1/a.jpeg"));
        assertEquals(MediaType.IMAGE_PNG, ImageContentTypeUtils.contentTypeOf("avatars/1/a.png"));
        assertEquals("image/webp", ImageContentTypeUtils.contentTypeOf("avatars/1/a.webp").toString());
    }

    @Test
    public void testSvgIsRecognised() {
        // 修复点：.svg 此前落到默认分支被谎报成 image/jpeg，在 nosniff 下是破图
        assertEquals("image/svg+xml", ImageContentTypeUtils.contentTypeOf("avatars/1/a.svg").toString());
        assertTrue(ImageContentTypeUtils.isSvg("avatars/1/a.svg"));
        assertFalse(ImageContentTypeUtils.isSvg("avatars/1/a.png"));
    }

    @Test
    public void testUnknownExtensionFallsBackToOctetStream() {
        // 不再假装成 JPEG
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ImageContentTypeUtils.contentTypeOf("avatars/1/a.bmp"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ImageContentTypeUtils.contentTypeOf("avatars/1/noext"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ImageContentTypeUtils.contentTypeOf(null));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ImageContentTypeUtils.contentTypeOf(""));
    }

    @Test
    public void testDotInsideDirectoryNameIsNotExtension() {
        // "v1.2" 里的点属于目录名，扩展名不存在
        assertEquals(MediaType.APPLICATION_OCTET_STREAM,
                ImageContentTypeUtils.contentTypeOf("blogger/avatars/v1.2/uuid"));
        // 以点结尾同样视为无扩展名
        assertEquals(MediaType.APPLICATION_OCTET_STREAM,
                ImageContentTypeUtils.contentTypeOf("avatars/1/uuid."));
    }

    @Test
    public void testSafeExtensionOnlyReturnsWhitelisted() {
        assertEquals(".gif", ImageContentTypeUtils.safeExtension("avatars/1/a.gif"));
        assertEquals("", ImageContentTypeUtils.safeExtension("avatars/1/a.exe"));
        assertEquals("", ImageContentTypeUtils.safeExtension(null));
        // 展示用 URL 不含扩展名
        assertEquals("", ImageContentTypeUtils.safeExtension("/api/blogger/avatar"));
    }

    @Test
    public void testGifHeaderIsInlineWithCorrectFilename() {
        HttpHeaders headers = new HttpHeaders();
        ImageContentTypeUtils.applyImageHeaders(headers, "avatars/1/a.gif", Disposition.AUTO);

        assertEquals(MediaType.IMAGE_GIF, headers.getContentType());
        assertEquals("inline", headers.getContentDisposition().getType());
        assertEquals("avatar.gif", headers.getContentDisposition().getFilename());
    }

    @Test
    public void testSvgStaysAttachmentEvenInAutoMode() {
        // SVG 是唯一具备脚本能力的格式，直接导航时仍应下载而非渲染
        HttpHeaders headers = new HttpHeaders();
        ImageContentTypeUtils.applyImageHeaders(headers, "avatars/1/a.svg", Disposition.AUTO);

        assertEquals("image/svg+xml", headers.getContentType().toString());
        assertEquals("attachment", headers.getContentDisposition().getType());
        assertEquals("avatar.svg", headers.getContentDisposition().getFilename());
    }

    @Test
    public void testAttachmentModeIsAlwaysAttachment() {
        HttpHeaders headers = new HttpHeaders();
        ImageContentTypeUtils.applyImageHeaders(headers, "avatars/1/a.png", Disposition.ATTACHMENT);

        assertEquals(MediaType.IMAGE_PNG, headers.getContentType());
        assertEquals("attachment", headers.getContentDisposition().getType());
    }

    @Test
    public void testUnknownExtensionIsForcedToAttachment() {
        HttpHeaders headers = new HttpHeaders();
        ImageContentTypeUtils.applyImageHeaders(headers, "avatars/1/a.bin", Disposition.AUTO);

        assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.getContentType());
        assertEquals("attachment", headers.getContentDisposition().getType());
        assertEquals("avatar", headers.getContentDisposition().getFilename());
    }
}
