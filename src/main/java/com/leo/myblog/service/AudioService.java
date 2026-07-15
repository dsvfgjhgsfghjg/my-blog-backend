package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.dto.response.AudioResponse;
import com.leo.myblog.entity.Audio;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AudioMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 音频服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {
    
    private final AudioMapper audioMapper;
    private final MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    /**
     * 流式播放音频（支持 Range 请求）
     */
    public StreamingResponseBody streamAudio(Long audioId, Long start, Long end) {
        Audio audio = getAudioById(audioId);
        
        // 增加播放次数
        incrementPlayCount(audioId);
        
        return outputStream -> {
            InputStream inputStream = null;
            try {
                // 安全修复V13: 验证文件路径，防止目录穿越攻击
                validateObjectPath(audio.getFilePath());
                
                // 构建 MinIO 请求
                GetObjectArgs.Builder builder = GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(audio.getFilePath());
                
                // 如果有 Range 请求，设置范围
                if (start != null && end != null) {
                    builder.offset(start).length(end - start + 1);
                    log.info("Range 请求: {}-{}", start, end);
                }
                
                inputStream = minioClient.getObject(builder.build());
                
                // 流式传输
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                
            } catch (Exception e) {
                log.error("音频流式传输失败: audioId={}", audioId, e);
                throw new BusinessException("音频播放失败");
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error("关闭输入流失败", e);
                    }
                }
            }
        };
    }
    
    /**
     * 获取音频信息
     */
    public AudioResponse getAudioInfo(Long audioId) {
        Audio audio = getAudioById(audioId);
        return convertToResponse(audio);
    }
    
    /**
     * 分页查询音频列表
     */
    public Page<AudioResponse> getAudioList(Integer pageNum, Integer pageSize, Boolean isPublic) {
        Page<Audio> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Audio> wrapper = new LambdaQueryWrapper<>();
        
        // 只查询公开的音频（或者当前用户自己的）
        if (isPublic != null && isPublic) {
            wrapper.eq(Audio::getIsPublic, true);
        }
        
        wrapper.orderByDesc(Audio::getCreateTime);
        
        Page<Audio> audioPage = audioMapper.selectPage(page, wrapper);
        
        // 转换为响应对象
        Page<AudioResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(audioPage, responsePage, "records");
        responsePage.setRecords(audioPage.getRecords().stream()
                .map(this::convertToResponse)
                .toList());
        
        return responsePage;
    }
    
    /**
     * 获取随机音频ID
     * 从所有公开音频中随机返回一个ID，用于前端初始化播放
     * @return 随机音频ID，如果没有音频则返回 null
     */
    public Long getRandomAudioId() {
        try {
            // 查询所有公开音频的ID
            LambdaQueryWrapper<Audio> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Audio::getIsPublic, true)
                   .select(Audio::getId);
            
            List<Audio> audios = audioMapper.selectList(wrapper);
            
            if (audios == null || audios.isEmpty()) {
                log.warn("没有可用的公开音频");
                return null;
            }
            
            // 随机选择一个
            int randomIndex = (int) (Math.random() * audios.size());
            Long randomAudioId = audios.get(randomIndex).getId();
            
            log.debug("随机选择音频: audioId={}, 总数={}", randomAudioId, audios.size());
            
            return randomAudioId;
            
        } catch (Exception e) {
            log.error("获取随机音频ID失败", e);
            return null;
        }
    }
    
    /**
     * 上传音频文件（简化版，实际应该接收 MultipartFile）
     */
    @Transactional(rollbackFor = Exception.class)
    public AudioResponse uploadAudio(Audio audio) {
        // 保存音频元数据
        audio.setPlayCount(0L);
        audio.setIsPublic(audio.getIsPublic() != null ? audio.getIsPublic() : true);
        audioMapper.insert(audio);
        
        log.info("音频上传成功: id={}, title={}", audio.getId(), audio.getTitle());
        
        return convertToResponse(audio);
    }
    
    /**
     * 删除音频（仅管理员）
     * 会同时删除音频文件和封面图片
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAudio(Long audioId) {
        Audio audio = getAudioById(audioId);
        
        // 删除数据库记录
        audioMapper.deleteById(audioId);
        
        // 删除 MinIO 中的音频文件
        try {
            // 安全修复V13: 验证路径合法性
            validateObjectPath(audio.getFilePath());
            
            minioClient.removeObject(
                io.minio.RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(audio.getFilePath())
                    .build()
            );
            log.info("已删除音频文件: {}", audio.getFilePath());
        } catch (Exception e) {
            log.error("删除音频文件失败: {}", audio.getFilePath(), e);
            // 不抛出异常，继续删除其他资源
        }
        
        // 删除 MinIO 中的封面图片（如果有）
        if (audio.getCoverUrl() != null && !audio.getCoverUrl().isEmpty()) {
            try {
                // 安全修复V13: 验证路径合法性
                validateObjectPath(audio.getCoverUrl());
                
                minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(audio.getCoverUrl())
                        .build()
                );
                log.info("已删除封面图片: {}", audio.getCoverUrl());
            } catch (Exception e) {
                log.error("删除封面图片失败: {}", audio.getCoverUrl(), e);
            }
        }
        
        log.info("音频删除成功: id={}, title={}", audioId, audio.getTitle());
    }
    
    /**
     * 上传专辑封面图片（仅管理员）
     * 上传后自动更新音频的 cover_url 字段
     * @param audioId 音频ID
     * @param file 封面图片文件
     * @return 完整的封面访问URL
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadCoverImage(Long audioId, org.springframework.web.multipart.MultipartFile file) {
        // 1. 验证音频存在
        Audio audio = getAudioById(audioId);
        
        try {
            // 2. 验证文件
            if (file == null || file.isEmpty()) {
                throw new BusinessException("封面图片不能为空");
            }
            
            // 3. 验证文件大小（最大2MB）
            long maxSize = 2 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                throw new BusinessException("封面图片大小不能超过 2MB");
            }
            
            // 4. 验证文件扩展名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new BusinessException("文件名无效");
            }
            
            String safeFilename = java.nio.file.Paths.get(originalFilename).getFileName().toString();
            String extension = getFileExtension(safeFilename).toLowerCase();
            
            java.util.Set<String> allowedExtensions = new java.util.HashSet<>(
                java.util.Arrays.asList(".jpg", ".jpeg", ".png", ".webp")
            );
            
            if (!allowedExtensions.contains(extension)) {
                throw new BusinessException("不支持的封面格式，仅支持: jpg, png, webp");
            }
            
            // 5. 验证 MIME 类型
            String contentType = file.getContentType();
            java.util.Set<String> allowedMimeTypes = new java.util.HashSet<>(
                java.util.Arrays.asList("image/jpeg", "image/png", "image/webp")
            );
            
            if (contentType == null || !allowedMimeTypes.contains(contentType.toLowerCase())) {
                throw new BusinessException("封面图片类型不正确");
            }
            
            // 6. 删除旧封面（如果有）
            if (audio.getCoverUrl() != null && !audio.getCoverUrl().isEmpty()) {
                try {
                    minioClient.removeObject(
                        io.minio.RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(audio.getCoverUrl())
                            .build()
                    );
                    log.info("已删除旧封面: {}", audio.getCoverUrl());
                } catch (Exception e) {
                    log.warn("删除旧封面失败: {}", audio.getCoverUrl(), e);
                }
            }
            
            // 7. 生成安全的文件名
            String uuidFilename = java.util.UUID.randomUUID().toString() + extension;
            
            // 8. 构建 MinIO 对象路径
            String objectPath = String.format("covers/%s", uuidFilename);
            
            // 9. 上传到 MinIO
            io.minio.PutObjectArgs args = io.minio.PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build();
            
            minioClient.putObject(args);
            
            // 10. 更新数据库中的 cover_url（只存储MinIO对象路径）
            audio.setCoverUrl(objectPath);  // 如: covers/uuid.jpg
            audio.setUpdateTime(LocalDateTime.now());
            audioMapper.updateById(audio);
            
            log.info("专辑封面上传并关联成功: audioId={}, path={}", audioId, objectPath);
            
            // 11. 返回完整访问URL（供前端使用）
            return "/api/audio/cover/" + objectPath;
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传专辑封面失败", e);
            throw new BusinessException("封面图片上传失败");
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
     * 更新音频信息（仅管理员）
     * 可更新字段：title, artist, album, isPublic, coverUrl
     */
    @Transactional(rollbackFor = Exception.class)
    public AudioResponse updateAudioInfo(Long audioId, com.leo.myblog.dto.request.UpdateAudioRequest request) {
        Audio audio = getAudioById(audioId);
        
        // 更新允许的字段
        if (request.getTitle() != null) {
            audio.setTitle(sanitizeInput(request.getTitle()));
        }
        
        if (request.getArtist() != null) {
            audio.setArtist(sanitizeInput(request.getArtist()));
        }
        
        if (request.getAlbum() != null) {
            audio.setAlbum(sanitizeInput(request.getAlbum()));
        }
        
        if (request.getIsPublic() != null) {
            audio.setIsPublic(request.getIsPublic());
        }
        
        if (request.getCoverUrl() != null) {
            // coverUrl 可能是完整URL或MinIO对象路径，需要统一处理
            String coverPath = request.getCoverUrl();
            
            // 如果是完整URL，提取对象路径
            if (coverPath.startsWith("/api/audio/cover/")) {
                coverPath = coverPath.replace("/api/audio/cover/", "");
            }
            
            audio.setCoverUrl(coverPath);
        }
        
        audio.setUpdateTime(LocalDateTime.now());
        audioMapper.updateById(audio);
        
        log.info("音频信息更新成功: id={}, title={}", audioId, audio.getTitle());
        
        return convertToResponse(audio);
    }
    
    /**
     * 清理用户输入（防止 XSS）
     */
    private String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // 简单的 HTML 转义
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
    
    /**
     * 根据ID获取音频
     */
    private Audio getAudioById(Long audioId) {
        Audio audio = audioMapper.selectById(audioId);
        if (audio == null) {
            throw new BusinessException("音频不存在");
        }
        return audio;
    }
    
    /**
     * 增加播放次数
     */
    private void incrementPlayCount(Long audioId) {
        Audio audio = audioMapper.selectById(audioId);
        if (audio != null) {
            audio.setPlayCount(audio.getPlayCount() + 1);
            audioMapper.updateById(audio);
        }
    }
    
    /**
     * 转换为响应对象
     */
    private AudioResponse convertToResponse(Audio audio) {
        AudioResponse response = new AudioResponse();
        BeanUtils.copyProperties(audio, response);
        
        // 生成流式播放URL
        response.setStreamUrl("/api/audio/stream/" + audio.getId());
        
        // 如果有封面，生成完整的访问URL
        if (audio.getCoverUrl() != null && !audio.getCoverUrl().isEmpty()) {
            // coverUrl存储的是MinIO对象路径，如: covers/uuid.jpg
            // 前端可以直接拼接: /api/audio/cover/{objectPath}
            response.setCoverUrl("/api/audio/cover/" + audio.getCoverUrl());
        }
        
        return response;
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
            // 允许的路径前缀：audio/, covers/
            boolean isValidPath = normalizedPath.startsWith("audio/") ||
                                  normalizedPath.startsWith("covers/");
            
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
