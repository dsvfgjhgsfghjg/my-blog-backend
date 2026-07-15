package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.request.UpdateBloggerInfoRequest;
import com.leo.myblog.dto.response.BloggerInfoResponse;
import com.leo.myblog.entity.BloggerInfo;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.BloggerInfoMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 博主信息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloggerInfoService {
    
    private final BloggerInfoMapper bloggerInfoMapper;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final SvgSanitizerService svgSanitizerService;  // SVG安全清洗服务
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    // 允许的头像格式（新增.svg）
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
        List.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg")
    );
    
    // 允许的MIME类型（新增image/svg+xml）
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(
        List.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml")
    );
    
    // 最大文件大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    /**
     * 获取博主信息（公开接口）
     */
    public BloggerInfoResponse getBloggerInfo() {
        BloggerInfo info = getOrCreateBloggerInfo();
        return convertToResponse(info);
    }
    
    /**
     * 获取博主头像（公开接口）
     * @return 头像二进制数据
     */
    public byte[] getBloggerAvatar() {
        try {
            BloggerInfo info = getOrCreateBloggerInfo();
            
            if (info.getAvatar() == null || info.getAvatar().isEmpty()) {
                log.warn("博主头像未设置");
                return null;
            }
            
            // 安全修复V13: 验证路径合法性，防止目录穿越攻击
            validateObjectPath(info.getAvatar());
            
            // 从 MinIO 获取头像文件
            io.minio.GetObjectArgs args = io.minio.GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(info.getAvatar())
                    .build();
            
            java.io.InputStream inputStream = minioClient.getObject(args);
            byte[] data = inputStream.readAllBytes();
            inputStream.close();
            
            return data;
            
        } catch (Exception e) {
            log.error("获取博主头像失败", e);
            return null;
        }
    }
    
    /**
     * 上传博主头像（仅管理员）
     * @param file 头像文件
     * @return 完整的头像访问URL
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadBloggerAvatar(MultipartFile file) {
        try {
            // 1. 验证文件
            if (file == null || file.isEmpty()) {
                throw new BusinessException("头像文件不能为空");
            }
            
            // 2. 验证文件大小
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException("头像文件大小不能超过 5MB");
            }
            
            // 3. 验证文件扩展名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new BusinessException("文件名无效");
            }
            
            String safeFilename = Paths.get(originalFilename).getFileName().toString();
            String extension = getFileExtension(safeFilename).toLowerCase();
            
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BusinessException("不支持的图片格式，仅支持: jpg, png, webp, gif, svg");
            }
            
            // 4. 验证 MIME 类型
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessException("图片类型不正确");
            }
            
            // 5. 特殊处理SVG文件：安全清洗
            MultipartFile processedFile = file;
            if (".svg".equals(extension)) {
                log.info("检测到博主SVG头像，执行安全清洗");
                processedFile = sanitizeSvgFile(file);
            }
            
            // 6. 获取当前博主信息
            BloggerInfo info = getOrCreateBloggerInfo();
            
            // 7. 删除旧头像（如果有）
            if (info.getAvatar() != null && !info.getAvatar().isEmpty()) {
                try {
                    // 安全修复V13: 验证路径合法性
                    validateObjectPath(info.getAvatar());
                    
                    minioClient.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(info.getAvatar())
                            .build()
                    );
                    log.info("已删除旧头像: {}", info.getAvatar());
                } catch (Exception e) {
                    log.warn("删除旧头像失败: {}", info.getAvatar(), e);
                }
            }
            
            // 8. 生成安全的文件名
            String uuidFilename = UUID.randomUUID().toString() + extension;
            
            // 9. 构建 MinIO 对象路径
            String objectPath = String.format("blogger/avatars/%s", uuidFilename);
            
            // 10. 上传到 MinIO
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(processedFile.getInputStream(), processedFile.getSize(), -1)
                    .contentType(processedFile.getContentType())
                    .build();
            
            minioClient.putObject(args);
            
            // 11. 更新数据库中的 avatar
            info.setAvatar(objectPath);
            info.setUpdateTime(LocalDateTime.now());
            bloggerInfoMapper.updateById(info);
            
            log.info("博主头像上传成功: path={}", objectPath);
            
            // 12. 返回完整访问URL（公开接口）
            return "/api/blogger/avatar";
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传博主头像失败", e);
            throw new BusinessException("头像上传失败");
        }
    }
    
    /**
     * 更新博主信息（仅管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public BloggerInfoResponse updateBloggerInfo(UpdateBloggerInfoRequest request) {
        BloggerInfo info = getOrCreateBloggerInfo();
        
        // 更新字段
        info.setName(request.getName());
        info.setAvatar(request.getAvatar());
        info.setEmail(request.getEmail());
        info.setGithub(request.getGithub());
        info.setIntroduction(request.getIntroduction());
        
        // 将技术栈列表转换为JSON字符串存储
        if (request.getTechStack() != null) {
            try {
                info.setTechStack(objectMapper.writeValueAsString(request.getTechStack()));
            } catch (JsonProcessingException e) {
                log.error("技术栈JSON序列化失败", e);
                throw new BusinessException("技术栈格式错误");
            }
        }
        
        info.setUpdateTime(LocalDateTime.now());
        bloggerInfoMapper.updateById(info);
        
        log.info("博主信息更新成功: name={}", info.getName());
        
        return convertToResponse(info);
    }
    
    /**
     * 获取或创建博主信息（系统只允许一条记录，ID固定为1）
     */
    private BloggerInfo getOrCreateBloggerInfo() {
        BloggerInfo info = bloggerInfoMapper.selectById(1L);
        
        if (info == null) {
            // 首次使用，创建默认记录
            info = new BloggerInfo();
            info.setId(1L);
            info.setName("博主");
            info.setCreateTime(LocalDateTime.now());
            info.setUpdateTime(LocalDateTime.now());
            bloggerInfoMapper.insert(info);
            log.info("创建默认博主信息");
        }
        
        return info;
    }
    
    /**
     * 转换为响应对象
     * 安全修复V12: 对敏感信息（邮箱）进行脱敏
     */
    private BloggerInfoResponse convertToResponse(BloggerInfo info) {
        BloggerInfoResponse response = new BloggerInfoResponse();
        BeanUtils.copyProperties(info, response);
        
        // 如果有头像，生成完整访问URL（公开接口）
        if (info.getAvatar() != null && !info.getAvatar().isEmpty()) {
            response.setAvatar("/api/blogger/avatar");
        }
        
        // 安全修复V12: 对邮箱进行脱敏处理
        if (response.getEmail() != null && !response.getEmail().isEmpty()) {
            response.setEmail(maskEmail(response.getEmail()));
        }
        
        // 解析技术栈JSON
        if (info.getTechStack() != null && !info.getTechStack().isEmpty()) {
            try {
                List<String> techStack = objectMapper.readValue(
                    info.getTechStack(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
                response.setTechStack(techStack);
            } catch (JsonProcessingException e) {
                log.error("技术栈JSON反序列化失败", e);
                response.setTechStack(Collections.emptyList());
            }
        } else {
            response.setTechStack(Collections.emptyList());
        }
        
        return response;
    }
    
    /**
     * 邮箱脱敏处理
     * 例如: travelingbug@163.com -> t*******g@163.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email; // 邮箱格式异常，不脱敏
        }
        
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        
        if (localPart.length() <= 2) {
            // 短用户名：显示首尾字符
            return localPart.charAt(0) + "*" + localPart.charAt(localPart.length() - 1) + domainPart;
        } else {
            // 长用户名：显示首尾字符，中间用*替代
            return localPart.charAt(0) + "*****" + localPart.charAt(localPart.length() - 1) + domainPart;
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
            
            log.info("博主SVG头像清洗完成: 原始大小={} bytes, 清洗后大小={} bytes", 
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
            log.error("博主SVG头像处理失败", e);
            throw new BusinessException("SVG文件处理失败");
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
            // 允许的路径前缀：blogger/avatars/
            boolean isValidPath = normalizedPath.startsWith("blogger/avatars/");
            
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
