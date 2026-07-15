package com.leo.myblog.service;

import com.leo.myblog.dto.response.AudioResponse;
import com.leo.myblog.entity.Audio;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AudioMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 音频上传服务（带安全防护）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioUploadService {
    
    private final AudioMapper audioMapper;
    private final MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    // 允许的音频格式白名单
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a", ".wma"
    ));
    
    // 允许的文件 MIME 类型
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "audio/mpeg",      // MP3
            "audio/flac",      // FLAC
            "audio/wav",       // WAV
            "audio/x-wav",     // WAV (alternate)
            "audio/aac",       // AAC
            "audio/ogg",       // OGG
            "audio/mp4",       // M4A
            "audio/x-ms-wma"   // WMA
    ));
    
    // 允许的封面图片格式
    private static final Set<String> ALLOWED_COVER_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".webp"
    ));
    
    // 允许的封面图片 MIME 类型
    private static final Set<String> ALLOWED_COVER_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/webp"
    ));
    
    // 封面图片最大大小：2MB
    private static final long MAX_COVER_SIZE = 2 * 1024 * 1024;
    
    // 最大文件大小：100MB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;
    
    // 音频文件魔数签名（用于验证文件真实性）
    private static final byte[] MP3_MAGIC = {(byte) 0xFF, (byte) 0xFB};  // MP3 frame header
    private static final byte[] FLAC_MAGIC = {'f', 'L', 'a', 'C'};        // FLAC signature
    private static final byte[] WAV_MAGIC = {'R', 'I', 'F', 'F'};         // RIFF container
    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};         // Ogg stream
    
    /**
     * 上传音频文件（带安全检查）
     */
    @Transactional(rollbackFor = Exception.class)
    public AudioResponse uploadAudio(MultipartFile file, String title, String artist, 
                                     String album, MultipartFile coverImage, 
                                     Boolean isPublic, Long userId) {
        
        // 1. 验证文件不为空
        validateFileNotEmpty(file);
        
        // 2. 验证文件大小
        validateFileSize(file);
        
        // 3. 验证文件扩展名
        String originalFilename = validateFileExtension(file.getOriginalFilename());
        
        // 4. 验证 MIME 类型
        validateMimeType(file.getContentType());
        
        // 5. 验证文件内容（检查魔数）
        validateFileContent(file);
        
        // 6. 生成安全的文件名
        String safeFileName = generateSafeFileName(originalFilename);
        
        // 7. 构建 MinIO 对象路径
        String objectPath = buildObjectPath(userId, safeFileName);
        
        try {
            // 8. 上传到 MinIO
            uploadToMinio(file.getInputStream(), objectPath, file.getContentType(), file.getSize());
            
            log.info("音频文件上传到 MinIO 成功: {}", objectPath);
            
        } catch (IOException e) {
            log.error("上传音频文件失败", e);
            throw new BusinessException("文件上传失败");
        }
        
        // 9. 上传封面图片（如果有）
        String coverUrl = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            coverUrl = uploadCoverImage(coverImage, userId);
        }
        
        // 10. 保存元数据到数据库
        Audio audio = new Audio();
        audio.setTitle(sanitizeInput(title));
        audio.setArtist(artist != null ? sanitizeInput(artist) : null);
        audio.setAlbum(album != null ? sanitizeInput(album) : null);
        audio.setFilePath(objectPath);
        audio.setFormat(getFileExtension(originalFilename).substring(1)); // 去掉点号
        audio.setFileSize(file.getSize());
        audio.setCoverUrl(coverUrl);
        audio.setUserId(userId);
        audio.setIsPublic(isPublic != null ? isPublic : true);
        audio.setPlayCount(0L);
        audio.setCreateTime(LocalDateTime.now());
        audio.setUpdateTime(LocalDateTime.now());
        
        audioMapper.insert(audio);
        
        log.info("音频上传成功: id={}, title={}, userId={}", audio.getId(), audio.getTitle(), userId);
        
        return convertToResponse(audio);
    }
    
    /**
     * 验证文件不为空
     */
    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
    }
    
    /**
     * 验证文件大小
     */
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 100MB");
        }
        if (file.getSize() == 0) {
            throw new BusinessException("文件大小不能为 0");
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
            throw new BusinessException("不支持的文件格式，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        
        return safeFilename;
    }
    
    /**
     * 验证 MIME 类型
     */
    private void validateMimeType(String contentType) {
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("不允许的 MIME 类型: {}", contentType);
            throw new BusinessException("文件类型不正确");
        }
    }
    
    /**
     * 验证文件内容（检查魔数签名）
     */
    private void validateFileContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                throw new BusinessException("文件损坏或格式不正确");
            }
            
            // 检查 MP3
            if (header[0] == MP3_MAGIC[0] && header[1] == MP3_MAGIC[1]) {
                log.debug("检测到 MP3 文件");
                return;
            }
            
            // 检查 FLAC
            if (header[0] == FLAC_MAGIC[0] && header[1] == FLAC_MAGIC[1] &&
                header[2] == FLAC_MAGIC[2] && header[3] == FLAC_MAGIC[3]) {
                log.debug("检测到 FLAC 文件");
                return;
            }
            
            // 检查 WAV
            if (header[0] == WAV_MAGIC[0] && header[1] == WAV_MAGIC[1] &&
                header[2] == WAV_MAGIC[2] && header[3] == WAV_MAGIC[3]) {
                log.debug("检测到 WAV 文件");
                return;
            }
            
            // 检查 OGG
            if (header[0] == OGG_MAGIC[0] && header[1] == OGG_MAGIC[1] &&
                header[2] == OGG_MAGIC[2] && header[3] == OGG_MAGIC[3]) {
                log.debug("检测到 OGG 文件");
                return;
            }
            
            // 如果没有匹配的魔数，但 MIME 类型是音频，也允许通过
            // （某些格式可能有不同的头部）
            log.warn("未检测到已知音频格式魔数，但 MIME 类型验证通过");
            
        } catch (IOException e) {
            log.error("读取文件头部失败", e);
            throw new BusinessException("文件读取失败");
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
     * 格式: audios/{userId}/{year}/{month}/{filename}
     */
    private String buildObjectPath(Long userId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        String datePath = now.format(formatter);
        
        return String.format("audios/%d/%s/%s", userId, datePath, fileName);
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
     * 上传封面图片
     * 存储在公开目录，返回MinIO对象路径
     */
    private String uploadCoverImage(MultipartFile coverImage, Long userId) {
        try {
            // 1. 验证文件
            if (coverImage == null || coverImage.isEmpty()) {
                return null;
            }
            
            // 2. 验证文件大小
            if (coverImage.getSize() > MAX_COVER_SIZE) {
                log.warn("封面图片过大: {} bytes", coverImage.getSize());
                throw new BusinessException("封面图片大小不能超过 2MB");
            }
            
            // 3. 验证文件扩展名
            String originalFilename = coverImage.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new BusinessException("文件名无效");
            }
            
            String safeFilename = Paths.get(originalFilename).getFileName().toString();
            String extension = getFileExtension(safeFilename).toLowerCase();
            
            if (!ALLOWED_COVER_EXTENSIONS.contains(extension)) {
                log.warn("不支持的封面格式: {}", extension);
                throw new BusinessException("不支持的封面格式，仅支持: jpg, png, webp");
            }
            
            // 4. 验证 MIME 类型
            String contentType = coverImage.getContentType();
            if (contentType == null || !ALLOWED_COVER_MIME_TYPES.contains(contentType.toLowerCase())) {
                log.warn("不支持的封面 MIME 类型: {}", contentType);
                throw new BusinessException("封面图片类型不正确");
            }
            
            // 5. 生成安全的文件名
            String uuidFilename = UUID.randomUUID().toString() + extension;
            
            // 6. 构建 MinIO 对象路径（公开目录）
            // 格式: covers/{uuid}.{ext}
            String objectPath = String.format("covers/%s", uuidFilename);
            
            // 7. 上传到 MinIO
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(coverImage.getInputStream(), coverImage.getSize(), -1)
                    .contentType(contentType)
                    .build();
            
            minioClient.putObject(args);
            
            log.info("封面图片上传成功: {}", objectPath);
            
            // 8. 返回MinIO对象路径（用于数据库存储）
            return objectPath;
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传封面图片失败", e);
            throw new BusinessException("封面图片上传失败");
        }
    }
    
    /**
     * 清理用户输入（防止 XSS）
     */
    private String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // 移除潜在的恶意字符
        return input.replaceAll("[<>\"'&]", "")
                   .trim();
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
     * 转换为响应对象
     */
    private AudioResponse convertToResponse(Audio audio) {
        AudioResponse response = new AudioResponse();
        org.springframework.beans.BeanUtils.copyProperties(audio, response);
        response.setStreamUrl("/api/audio/stream/" + audio.getId());
        return response;
    }
}
