package com.leo.myblog.service;

import com.leo.myblog.dto.response.ImageUploadResponse;
import com.leo.myblog.exception.BusinessException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 文章图片上传服务
 * <p>
 * 支持格式：jpg/png/gif/webp/svg
 * 单文件最大：10MB
 * 存储路径：images/{yyyy}/{MM}/{uuid}.{ext}
 * 访问方式：通过后端 API 代理，返回可公开访问的 URL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final MinioClient minioClient;
    private final SvgSanitizerService svgSanitizerService;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    // 允许的图片格式白名单
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"
    ));

    // 允许的 MIME 类型
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml"
    ));

    // 最大文件大小：10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 封面图片最大大小：2MB
    private static final long MAX_COVER_SIZE = 2 * 1024 * 1024;

    // 图片魔数签名
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F'};

    /**
     * 上传文章图片
     *
     * @param file   图片文件
     * @param userId 上传用户 ID
     * @return 图片上传响应（含公开访问 URL）
     */
    public ImageUploadResponse uploadImage(MultipartFile file, Long userId) {
        // 1. 验证文件不为空
        validateFileNotEmpty(file);

        // 2. 验证文件大小
        validateFileSize(file);

        // 3. 验证文件扩展名
        String originalFilename = validateFileExtension(file.getOriginalFilename());

        // 4. 验证 MIME 类型
        validateMimeType(file.getContentType());

        // 5. 验证文件内容
        String extension = getFileExtension(originalFilename).toLowerCase();
        MultipartFile processedFile = file;

        if (".svg".equals(extension)) {
            // SVG：安全清洗
            processedFile = sanitizeSvgFile(file);
        } else {
            // 非 SVG：检查魔数签名
            validateFileContent(file, extension);
        }

        // 6. 生成安全文件名
        String safeFileName = generateSafeFileName(originalFilename);

        // 7. 构建 MinIO 对象路径：images/{yyyy}/{MM}/{uuid}.{ext}
        String objectPath = buildObjectPath(safeFileName);

        try {
            // 8. 上传到 MinIO
            uploadToMinio(processedFile.getInputStream(), objectPath,
                    processedFile.getContentType(), processedFile.getSize());

            log.info("文章图片上传成功: userId={}, path={}, size={}",
                    userId, objectPath, file.getSize());
        } catch (IOException e) {
            log.error("文章图片上传失败: userId={}", userId, e);
            throw new BusinessException("图片上传失败");
        }

        // 9. 构建响应
        return buildResponse(objectPath, originalFilename, file, extension);
    }

    /**
     * 上传文章封面图片
     * <p>
     * 封面图片存储在 MinIO 的 covers/ 目录下，与音频封面共用同一目录。
     * 上传后返回可公开访问的 URL，前端需将此 URL 传入创建/更新文章的 coverImage 字段。
     * <p>
     * 格式限制：jpg/png/gif/webp/svg，最大 2MB
     *
     * @param file   封面图片文件
     * @param userId 上传用户 ID
     * @return 图片上传响应（含可公开访问的 URL）
     */
    public ImageUploadResponse uploadCoverImage(MultipartFile file, Long userId) {
        // 1. 验证文件不为空
        validateFileNotEmpty(file);

        // 2. 验证文件大小（封面不超过 2MB）
        validateCoverFileSize(file);

        // 3. 验证文件扩展名
        String originalFilename = validateFileExtension(file.getOriginalFilename());

        // 4. 验证 MIME 类型
        validateMimeType(file.getContentType());

        // 5. 验证文件内容
        String extension = getFileExtension(originalFilename).toLowerCase();
        MultipartFile processedFile = file;

        if (".svg".equals(extension)) {
            processedFile = sanitizeSvgFile(file);
        } else {
            validateFileContent(file, extension);
        }

        // 6. 生成安全文件名
        String safeFileName = generateSafeFileName(originalFilename);

        // 7. 构建 MinIO 对象路径：covers/{uuid}.{ext}
        String objectPath = buildCoverObjectPath(safeFileName);

        try {
            // 8. 上传到 MinIO
            uploadToMinio(processedFile.getInputStream(), objectPath,
                    processedFile.getContentType(), processedFile.getSize());

            log.info("文章封面上传成功: userId={}, path={}, size={}",
                    userId, objectPath, file.getSize());
        } catch (IOException e) {
            log.error("文章封面上传失败: userId={}", userId, e);
            throw new BusinessException("封面上传失败");
        }

        // 9. 构建响应
        return buildResponse(objectPath, originalFilename, file, extension);
    }

    /**
     * 构建图片访问 URL
     * 格式：/api/images/stream/{yyyy}/{MM}/{uuid}.{ext}
     */
    public String buildImageUrl(String objectPath) {
        return "/api/images/stream/" + objectPath;
    }

    /**
     * 验证文件不为空
     */
    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
    }

    /**
     * 验证文件大小
     */
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "图片大小不能超过10MB");
        }
        if (file.getSize() == 0) {
            throw new BusinessException(400, "文件大小不能为0");
        }
    }

    /**
     * 验证封面图片文件大小（不超过 2MB）
     */
    private void validateCoverFileSize(MultipartFile file) {
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new BusinessException(400, "封面图片大小不能超过2MB");
        }
        if (file.getSize() == 0) {
            throw new BusinessException(400, "文件大小不能为0");
        }
    }

    /**
     * 验证文件扩展名（白名单）
     */
    private String validateFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new BusinessException(400, "文件名无效");
        }

        // 防止路径遍历攻击
        String safeFilename = Paths.get(filename).getFileName().toString();

        // 检查是否包含危险字符
        if (safeFilename.contains("..") || safeFilename.contains("/") || safeFilename.contains("\\")) {
            throw new BusinessException(400, "文件名包含非法字符");
        }

        String extension = getFileExtension(safeFilename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("不允许的图片扩展名: {}", extension);
            throw new BusinessException(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/svg");
        }

        return safeFilename;
    }

    /**
     * 验证 MIME 类型
     */
    private void validateMimeType(String contentType) {
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("不允许的 MIME 类型: {}", contentType);
            throw new BusinessException(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/svg");
        }
    }

    /**
     * 验证文件内容（检查魔数签名）
     */
    private void validateFileContent(MultipartFile file, String extension) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);

            if (bytesRead < 4) {
                throw new BusinessException(400, "文件损坏或格式不正确");
            }

            switch (extension) {
                case ".jpg":
                case ".jpeg":
                    if (!(header[0] == JPEG_MAGIC[0] && header[1] == JPEG_MAGIC[1] && header[2] == JPEG_MAGIC[2])) {
                        log.warn("JPEG 魔数验证失败");
                        throw new BusinessException(400, "文件内容与扩展名不匹配");
                    }
                    break;

                case ".png":
                    if (!(header[0] == PNG_MAGIC[0] && header[1] == PNG_MAGIC[1] &&
                            header[2] == PNG_MAGIC[2] && header[3] == PNG_MAGIC[3])) {
                        log.warn("PNG 魔数验证失败");
                        throw new BusinessException(400, "文件内容与扩展名不匹配");
                    }
                    break;

                case ".gif":
                    if (!(header[0] == GIF_MAGIC[0] && header[1] == GIF_MAGIC[1] &&
                            header[2] == GIF_MAGIC[2] && header[3] == GIF_MAGIC[3])) {
                        log.warn("GIF 魔数验证失败");
                        throw new BusinessException(400, "文件内容与扩展名不匹配");
                    }
                    break;

                case ".webp":
                    if (!(header[0] == WEBP_MAGIC[0] && header[1] == WEBP_MAGIC[1] &&
                            header[2] == WEBP_MAGIC[2] && header[3] == WEBP_MAGIC[3])) {
                        log.warn("WebP 魔数验证失败");
                        throw new BusinessException(400, "文件内容与扩展名不匹配");
                    }
                    if (bytesRead >= 12 && !(header[8] == 'W' && header[9] == 'E' &&
                            header[10] == 'B' && header[11] == 'P')) {
                        log.warn("WebP 格式验证失败");
                        throw new BusinessException(400, "文件内容与扩展名不匹配");
                    }
                    break;

                default:
                    log.warn("未知的文件扩展名: {}", extension);
                    throw new BusinessException(400, "不支持的图片格式");
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取文件头部失败", e);
            throw new BusinessException("文件读取失败");
        }
    }

    /**
     * 清洗 SVG 文件
     */
    private MultipartFile sanitizeSvgFile(MultipartFile originalFile) {
        try {
            byte[] originalContent = originalFile.getBytes();
            String svgContent = new String(originalContent, StandardCharsets.UTF_8);

            // 验证是否为有效的 SVG
            if (!svgSanitizerService.isValidSvg(originalContent)) {
                throw new BusinessException(400, "文件格式不是有效的 SVG");
            }

            // 执行 SVG 安全清洗
            String sanitizedContent = svgSanitizerService.sanitizeSvg(svgContent);
            byte[] sanitizedBytes = sanitizedContent.getBytes(StandardCharsets.UTF_8);

            log.info("SVG 清洗完成: 原始={} bytes, 清洗后={} bytes",
                    originalContent.length, sanitizedBytes.length);

            // 创建新的 MultipartFile
            return new MultipartFile() {
                @Override
                public String getName() {
                    return originalFile.getName();
                }

                @Override
                public String getOriginalFilename() {
                    return originalFile.getOriginalFilename();
                }

                @Override
                public String getContentType() {
                    return "image/svg+xml";
                }

                @Override
                public boolean isEmpty() {
                    return sanitizedBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return sanitizedBytes.length;
                }

                @Override
                public byte[] getBytes() {
                    return sanitizedBytes;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(sanitizedBytes);
                }

                @Override
                public void transferTo(java.io.File dest) throws IOException {
                    java.nio.file.Files.write(dest.toPath(), sanitizedBytes);
                }
            };

        } catch (IOException e) {
            log.error("SVG 文件处理失败", e);
            throw new BusinessException("SVG 文件处理失败");
        }
    }

    /**
     * 生成安全文件名（UUID）
     */
    private String generateSafeFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * 构建 MinIO 对象路径
     * 格式：images/{yyyy}/{MM}/{uuid}.{ext}
     */
    private String buildObjectPath(String fileName) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        String datePath = now.format(formatter);

        return String.format("images/%s/%s", datePath, fileName);
    }

    /**
     * 构建封面图片的 MinIO 对象路径
     * 格式：covers/{uuid}.{ext}
     */
    private String buildCoverObjectPath(String fileName) {
        return String.format("covers/%s", fileName);
    }

    /**
     * 上传到 MinIO
     */
    private void uploadToMinio(InputStream inputStream, String objectPath,
                               String contentType, long size) throws IOException {
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build();

            minioClient.putObject(args);

        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", objectPath, e);
            throw new IOException("文件存储失败", e);
        }
    }

    /**
     * 构建响应对象
     */
    private ImageUploadResponse buildResponse(String objectPath, String originalFilename,
                                              MultipartFile file, String extension) {
        ImageUploadResponse response = new ImageUploadResponse();
        response.setUrl(buildImageUrl(objectPath));
        response.setFilename(getFileName(objectPath));
        response.setOriginalName(originalFilename);
        response.setSize(file.getSize());
        response.setMimeType(file.getContentType());

        // SVG 不尝试读取尺寸
        if (!".svg".equals(extension)) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
                if (image != null) {
                    response.setWidth(image.getWidth());
                    response.setHeight(image.getHeight());
                }
            } catch (Exception e) {
                log.warn("读取图片尺寸失败: {}", originalFilename, e);
            }
        }

        return response;
    }

    /**
     * 从路径中提取文件名（不含目录）
     */
    private String getFileName(String objectPath) {
        int lastSlash = objectPath.lastIndexOf('/');
        return lastSlash >= 0 ? objectPath.substring(lastSlash + 1) : objectPath;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex);
    }
}
