package com.leo.myblog.service;

import com.leo.myblog.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 头像上传服务（带安全防护）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUploadService {
    
    private final MinioClient minioClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SvgSanitizerService svgSanitizerService;  // SVG安全清洗服务
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    // 最大保留头像数量
    private static final int MAX_AVATAR_COUNT = 10;
    
    // Redis Key 前缀：用于存储用户头像历史
    private static final String AVATAR_HISTORY_PREFIX = "avatar:history:";
    
    // 允许的图片格式白名单（新增.svg）
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"
    ));
    
    // 允许的文件 MIME 类型（新增image/svg+xml）
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml"
    ));
    
    // 最大文件大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    // 图片文件魔数签名（用于验证文件真实性）
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};  // JPEG
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};                // PNG
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};                        // GIF
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F'};                       // WebP (RIFF container)
    
    /**
     * 上传头像文件
     * @param file 头像文件
     * @param userId 用户ID
     * @return 头像访问URL
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        // 1. 验证文件不为空
        validateFileNotEmpty(file);
        
        // 2. 验证文件大小
        validateFileSize(file);
        
        // 3. 验证文件扩展名
        String originalFilename = validateFileExtension(file.getOriginalFilename());
        
        // 4. 验证 MIME 类型
        validateMimeType(file.getContentType());
        
        // 5. 特殊处理SVG文件：安全清洗
        MultipartFile processedFile = file;
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (".svg".equals(extension)) {
            log.info("检测到SVG文件，执行安全清洗: userId={}", userId);
            processedFile = sanitizeSvgFile(file);
        } else {
            // 6. 非SVG文件：验证文件内容（检查魔数）
            validateFileContent(file);
        }
        
        // 7. 生成安全的文件名
        String safeFileName = generateSafeFileName(originalFilename);
        
        // 8. 构建 MinIO 对象路径（使用 avatars 目录，按用户隔离）
        String objectPath = buildObjectPath(userId, safeFileName);
        
        try {
            // 9. 上传到 MinIO
            uploadToMinio(processedFile.getInputStream(), objectPath, processedFile.getContentType(), processedFile.getSize());
            
            log.info("头像文件上传到 MinIO 成功: {}", objectPath);
            
        } catch (IOException e) {
            log.error("上传头像文件失败", e);
            throw new BusinessException("头像上传失败");
        }
        
        // 10. 记录头像历史到 Redis
        recordAvatarHistory(userId, objectPath);
        
        // 11. 清理超出限制的旧头像
        cleanupOldAvatars(userId);
        
        // 12. 返回 MinIO 对象路径（完整路径，用于数据库存储）
        log.info("头像上传成功: userId={}, objectPath={}", userId, objectPath);
        
        return objectPath;
    }
    
    /**
     * 验证文件不为空
     */
    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("头像文件不能为空");
        }
    }
    
    /**
     * 验证文件大小
     */
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("头像文件大小不能超过 5MB");
        }
        if (file.getSize() == 0) {
            throw new BusinessException("头像文件大小不能为 0");
        }
    }
    
    /**
     * 验证文件扩展名（白名单）
     */
    private String validateFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new BusinessException("文件名无效");
        }
        
        // 防止路径遍历攻击
        String safeFilename = Paths.get(filename).getFileName().toString();
        
        // 检查是否包含危险字符
        if (safeFilename.contains("..") || safeFilename.contains("/") || safeFilename.contains("\\")) {
            throw new BusinessException("文件名包含非法字符");
        }
        
        String extension = getFileExtension(safeFilename).toLowerCase();
        
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("不允许的文件扩展名: {}", extension);
            throw new BusinessException("不支持的图片格式，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        
        return safeFilename;
    }
    
    /**
     * 验证 MIME 类型
     */
    private void validateMimeType(String contentType) {
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("不允许的 MIME 类型: {}", contentType);
            throw new BusinessException("文件类型不正确，请上传图片文件");
        }
    }
    
    /**
     * 验证文件内容（检查魔数签名）
     * 防止攻击者上传恶意文件（如 JSP Webshell）伪装成图片
     */
    private void validateFileContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                throw new BusinessException("文件损坏或格式不正确");
            }
            
            String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
            
            // 根据扩展名验证对应的魔数签名
            switch (extension) {
                case ".jpg":
                case ".jpeg":
                    // JPEG: FF D8 FF
                    if (!(header[0] == JPEG_MAGIC[0] && header[1] == JPEG_MAGIC[1] && header[2] == JPEG_MAGIC[2])) {
                        log.warn("JPEG 文件魔数验证失败: expected FF D8 FF, got {} {} {}",
                                String.format("%02X", header[0]),
                                String.format("%02X", header[1]),
                                String.format("%02X", header[2]));
                        throw new BusinessException("文件内容与扩展名不匹配，请上传有效的 JPEG 图片");
                    }
                    log.debug("JPEG 文件魔数验证通过");
                    break;
                    
                case ".png":
                    // PNG: 89 50 4E 47
                    if (!(header[0] == PNG_MAGIC[0] && header[1] == PNG_MAGIC[1] && 
                          header[2] == PNG_MAGIC[2] && header[3] == PNG_MAGIC[3])) {
                        log.warn("PNG 文件魔数验证失败: expected 89 50 4E 47, got {} {} {} {}",
                                String.format("%02X", header[0]),
                                String.format("%02X", header[1]),
                                String.format("%02X", header[2]),
                                String.format("%02X", header[3]));
                        throw new BusinessException("文件内容与扩展名不匹配，请上传有效的 PNG 图片");
                    }
                    log.debug("PNG 文件魔数验证通过");
                    break;
                    
                case ".gif":
                    // GIF: 47 49 46 38 ("GIF8")
                    if (!(header[0] == GIF_MAGIC[0] && header[1] == GIF_MAGIC[1] && 
                          header[2] == GIF_MAGIC[2] && header[3] == GIF_MAGIC[3])) {
                        log.warn("GIF 文件魔数验证失败: expected 47 49 46 38, got {} {} {} {}",
                                String.format("%02X", header[0]),
                                String.format("%02X", header[1]),
                                String.format("%02X", header[2]),
                                String.format("%02X", header[3]));
                        throw new BusinessException("文件内容与扩展名不匹配，请上传有效的 GIF 图片");
                    }
                    log.debug("GIF 文件魔数验证通过");
                    break;
                    
                case ".webp":
                    // WebP: RIFF + WEBP (RIFF container at offset 0, WEBP at offset 8)
                    if (!(header[0] == WEBP_MAGIC[0] && header[1] == WEBP_MAGIC[1] && 
                          header[2] == WEBP_MAGIC[2] && header[3] == WEBP_MAGIC[3])) {
                        log.warn("WebP 文件魔数验证失败: expected RIFF, got {} {} {} {}",
                                (char)header[0], (char)header[1], (char)header[2], (char)header[3]);
                        throw new BusinessException("文件内容与扩展名不匹配，请上传有效的 WebP 图片");
                    }
                    // 进一步验证 WEBP 标识（在第8-11字节）
                    if (bytesRead >= 12 && !(header[8] == 'W' && header[9] == 'E' && 
                                             header[10] == 'B' && header[11] == 'P')) {
                        log.warn("WebP 文件格式验证失败: expected WEBP at offset 8");
                        throw new BusinessException("文件内容与扩展名不匹配，请上传有效的 WebP 图片");
                    }
                    log.debug("WebP 文件魔数验证通过");
                    break;
                    
                default:
                    // SVG 已由 sanitizeSvgFile 处理，不会进入此分支
                    log.warn("未知的文件扩展名: {}", extension);
                    throw new BusinessException("不支持的文件类型");
            }
            
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (IOException e) {
            log.error("读取文件头部失败", e);
            throw new BusinessException("文件读取失败");
        }
    }
    
    /**
     * 清洗SVG文件并返回安全的MultipartFile
     */
    private MultipartFile sanitizeSvgFile(MultipartFile originalFile) {
        try {
            // 1. 读取原始文件内容
            byte[] originalContent = originalFile.getBytes();
            String svgContent = new String(originalContent, StandardCharsets.UTF_8);
            
            // 2. 验证是否为有效的SVG格式
            if (!svgSanitizerService.isValidSvg(originalContent)) {
                throw new BusinessException("文件格式不是有效的SVG");
            }
            
            // 3. 执行SVG安全清洗
            String sanitizedContent = svgSanitizerService.sanitizeSvg(svgContent);
            byte[] sanitizedBytes = sanitizedContent.getBytes(StandardCharsets.UTF_8);
            
            log.info("SVG清洗完成: 原始大小={} bytes, 清洗后大小={} bytes", 
                    originalContent.length, sanitizedBytes.length);
            
            // 4. 创建新的MultipartFile
            return new org.springframework.web.multipart.MultipartFile() {
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
                public byte[] getBytes() throws IOException {
                    return sanitizedBytes;
                }
                
                @Override
                public InputStream getInputStream() throws IOException {
                    return new java.io.ByteArrayInputStream(sanitizedBytes);
                }
                
                @Override
                public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                    java.nio.file.Files.write(dest.toPath(), sanitizedBytes);
                }
            };
            
        } catch (IOException e) {
            log.error("SVG文件处理失败", e);
            throw new BusinessException("SVG文件处理失败");
        }
    }
    
    /**
     * 生成安全的文件名
     */
    private String generateSafeFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        // 使用 UUID 生成唯一文件名，防止文件名冲突和注入攻击
        return UUID.randomUUID().toString() + extension;
    }
    
    /**
     * 构建 MinIO 对象路径
     * 格式: avatars/{userId}/{filename}
     */
    private String buildObjectPath(Long userId, String fileName) {
        return String.format("avatars/%d/%s", userId, fileName);
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
                    .stream(inputStream, size, -1)  // -1 表示未知分片大小
                    .contentType(contentType)
                    .build();
            
            minioClient.putObject(args);
            
        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", objectPath, e);
            throw new IOException("文件存储失败", e);
        }
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
    
    /**
     * 从 MinIO 获取用户头像
     * @param objectPath MinIO对象路径（从数据库获取）
     * @return 头像文件的字节数组
     */
    public byte[] getAvatar(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            log.warn("头像路径为空");
            return null;
        }
        
        // 安全修复V13: 防止目录穿越攻击
        validateObjectPath(objectPath);
        
        try {
            // 从 MinIO 下载文件
            InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build()
            );
            
            // 将 InputStream 转换为 byte[]
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            
            inputStream.close();
            
            log.debug("成功获取用户头像: path={}, size={} bytes", objectPath, buffer.size());
            return buffer.toByteArray();
            
        } catch (Exception e) {
            log.error("从 MinIO 获取头像失败: path={}", objectPath, e);
            return null;
        }
    }
    
    /**
     * 记录头像历史到 Redis
     * 使用 Sorted Set，score 为时间戳，member 为对象路径
     */
    private void recordAvatarHistory(Long userId, String objectPath) {
        String key = AVATAR_HISTORY_PREFIX + userId;
        try {
            // 使用当前时间戳作为 score
            double score = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(key, objectPath, score);
            
            // 设置过期时间为 30 天（防止 Redis 数据无限增长）
            redisTemplate.expire(key, 30, TimeUnit.DAYS);
            
            log.debug("头像历史记录成功: userId={}, path={}", userId, objectPath);
        } catch (Exception e) {
            log.error("记录头像历史失败: userId={}", userId, e);
        }
    }
    
    /**
     * 清理超出限制的旧头像
     * 保留最新的 MAX_AVATAR_COUNT 个头像，删除多余的
     */
    private void cleanupOldAvatars(Long userId) {
        String key = AVATAR_HISTORY_PREFIX + userId;
        try {
            // 获取头像总数
            Long totalCount = redisTemplate.opsForZSet().size(key);
            
            if (totalCount == null || totalCount <= MAX_AVATAR_COUNT) {
                log.debug("头像数量未超限: userId={}, count={}", userId, totalCount);
                return;
            }
            
            // 计算需要删除的数量
            long toDeleteCount = totalCount - MAX_AVATAR_COUNT;
            
            // 获取最老的 toDeleteCount 个头像（score 最小的）
            Set<Object> oldAvatars = redisTemplate.opsForZSet().range(key, 0, toDeleteCount - 1);
            
            if (oldAvatars != null && !oldAvatars.isEmpty()) {
                log.info("开始清理旧头像: userId={}, 总数={}, 删除数量={}", userId, totalCount, toDeleteCount);
                
                for (Object avatarPath : oldAvatars) {
                    String path = (String) avatarPath;
                    try {
                        // 安全修复V13: 验证路径合法性
                        validateObjectPath(path);
                        
                        // 从 MinIO 删除文件
                        minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(path)
                                .build()
                        );
                        
                        // 从 Redis 中删除记录
                        redisTemplate.opsForZSet().remove(key, path);
                        
                        log.info("已删除旧头像: {}", path);
                    } catch (Exception e) {
                        log.error("删除旧头像失败: path={}", path, e);
                    }
                }
                
                log.info("旧头像清理完成: userId={}, 剩余数量={}", userId, MAX_AVATAR_COUNT);
            }
        } catch (Exception e) {
            log.error("清理旧头像失败: userId={}", userId, e);
        }
    }
    
    /**
     * 安全修复V13: 验证 MinIO 对象路径，防止目录穿越攻击
     * @param objectPath 对象路径
     * @throws BusinessException 如果路径不合法
     */
    private void validateObjectPath(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            throw new BusinessException("对象路径无效");
        }
        
        // 1. 检查是否包含目录穿越序列
        if (objectPath.contains("..") || objectPath.contains("./") || objectPath.contains("\\")) {
            log.warn("检测到可疑的路径穿越尝试: {}", objectPath);
            throw new BusinessException("非法的文件路径");
        }
        
        // 2. 检查是否以 / 开头（绝对路径）
        if (objectPath.startsWith("/")) {
            log.warn("检测到绝对路径尝试: {}", objectPath);
            throw new BusinessException("非法的文件路径");
        }
        
        // 3. 规范化路径并验证
        try {
            // 使用 Paths.get 规范化路径
            String normalizedPath = Paths.get(objectPath).normalize().toString();
            
            // 替换 Windows 风格的路径分隔符为 Unix 风格
            normalizedPath = normalizedPath.replace("\\", "/");
            
            // 4. 验证路径是否在允许的目录范围内
            // 允许的路径前缀：avatars/, blogger/avatars/, covers/, audio/
            boolean isValidPath = normalizedPath.startsWith("avatars/") ||
                                  normalizedPath.startsWith("blogger/avatars/") ||
                                  normalizedPath.startsWith("covers/") ||
                                  normalizedPath.startsWith("audio/") ||
                                  normalizedPath.startsWith("images/");
            
            if (!isValidPath) {
                log.warn("对象路径不在允许的范围内: {}", objectPath);
                throw new BusinessException("非法的文件路径");
            }
            
            // 5. 验证规范化后的路径与原始路径一致（防止编码绕过）
            if (!normalizedPath.equals(objectPath)) {
                log.warn("路径规范化后不一致，可能存在攻击: original={}, normalized={}", 
                        objectPath, normalizedPath);
                throw new BusinessException("非法的文件路径");
            }
            
            log.debug("对象路径验证通过: {}", objectPath);
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("路径验证失败: {}", objectPath, e);
            throw new BusinessException("非法的文件路径");
        }
    }
}
