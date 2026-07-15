package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.response.CleanupProgressEvent;
import com.leo.myblog.dto.response.CleanupProgressEvent.CleanupStats;
import com.leo.myblog.entity.AuditLog;
import com.leo.myblog.entity.Post;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.PostMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 孤立文件清理服务
 * <p>
 * 清理 MinIO 对象存储中孤立的、没有被任何文章引用的垃圾图片数据。
 * 仅清理 images/ 目录下的文件（文章内嵌图片），不碰 avatars/、blogger/、
 * covers/、posts/、audio/ 等目录下的文件。
 * <p>
 * 性能特性：
 * - 防并发执行：通过 AtomicBoolean 确保同一时间只有一个清理任务在运行
 * - 分批删除：大量孤立文件时分批执行删除操作（每批1000个）
 * - 内存优化：使用 HashMap 索引快速查找文件是否被引用
 * <p>
 * 进度反馈：
 * - 支持通过 Consumer 回调实时推送清理进度到前端
 * - 每个删除失败都记录具体错误信息并跳过
 * - 最终结果包含完整统计和错误列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanFileCleanupService {

    private final MinioClient minioClient;
    private final PostMapper postMapper;
    private final PostStorageService postStorageService;
    private final AuditLogService auditLogService;
    private final UserCacheService userCacheService;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 防并发执行标志
     */
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    /**
     * 匹配 Markdown 中引用的图片路径模式
     * 例如: /api/images/stream/images/2026/06/uuid.png
     */
    private static final Pattern IMAGE_REFERENCE_PATTERN = Pattern.compile(
            "/api/images/stream/images/\\d{4}/\\d{2}/[a-fA-F0-9-]+\\.[a-zA-Z]+"
    );

    /**
     * 清理目标目录前缀（仅清理此目录下的孤立文件）
     * 受保护的目录（avatars/, blogger/, covers/, posts/, audio/）不会被扫描到
     */
    private static final String CLEANUP_TARGET_PREFIX = "images/";

    /**
     * 分批删除时每批的最大数量
     */
    private static final int BATCH_DELETE_SIZE = 1000;

    /**
     * 执行孤立文件清理（带进度回调，支持 SSE 流式推送）
     * <p>
     * 1. 检查是否有其他清理任务正在执行（防并发）
     * 2. 扫描 MinIO images/ 目录下的所有图片文件 -> 推送扫描进度
     * 3. 获取所有文章的 Markdown 内容 -> 推送分析进度
     * 4. 从文章内容中提取所有引用的图片路径 -> 推送对比进度
     * 5. 对比找出未被引用的孤立图片
     * 6. 分批删除孤立图片 -> 推送每批删除进度
     * 7. 记录审计日志 -> 推送完成结果（含错误统计）
     *
     * @param userId   操作用户 ID（用于审计日志记录）
     * @param callback 进度回调消费者，每次有进展时调用（可为 null）
     */
    public void cleanupOrphanFiles(Long userId, Consumer<CleanupProgressEvent> callback) {
        // 检查是否已有清理任务正在执行（防并发）
        if (!cleanupRunning.compareAndSet(false, true)) {
            log.warn("有清理任务正在执行中，拒绝并发请求: userId={}", userId);
            throw new BusinessException("清理任务正在执行中，请稍后再试");
        }

        Instant start = Instant.now();
        log.info("开始清理孤立垃圾文件: userId={}", userId);

        // 收集所有错误信息
        List<String> allErrors = new CopyOnWriteArrayList<>();

        try {
            // ========== 步骤1: 扫描 MinIO ==========
            log.info("正在扫描 MinIO images/ 目录...");
            fireEvent(callback, CleanupProgressEvent.progress("正在扫描 MinIO 对象存储中的图片文件...", 5));

            List<ImageFileInfo> allImages = listAllImages();
            if (allImages.isEmpty()) {
                log.info("images/ 目录下没有文件，无需清理");
                fireEvent(callback, CleanupProgressEvent.progress("未发现任何图片文件，无需清理", 100));
                recordAuditLog(userId, 0, 0, 0, 0, List.of(), allErrors,
                        Duration.between(start, Instant.now()).toMillis());
                fireComplete(callback, "清理完成，无需清理", allErrors, 0, 0, 0, 0, List.of(),
                        Duration.between(start, Instant.now()).toMillis());
                return;
            }
            fireEvent(callback, CleanupProgressEvent.progress(
                    "扫描完成，共发现 " + allImages.size() + " 个图片文件", 20));

            // ========== 步骤2: 获取文章列表 ==========
            log.info("正在获取所有文章信息...");
            fireEvent(callback, CleanupProgressEvent.progress("正在获取所有文章信息...", 25));

            List<Post> allPosts = postMapper.selectList(
                    new LambdaQueryWrapper<Post>()
                            .select(Post::getId, Post::getContentPath, Post::getCoverImage)
                            .isNotNull(Post::getContentPath)
            );
            fireEvent(callback, CleanupProgressEvent.progress(
                    "已获取 " + allPosts.size() + " 篇有内容的文章", 30));

            // ========== 步骤3: 分析文章内容 ==========
            log.info("正在分析文章中的图片引用...");
            fireEvent(callback, CleanupProgressEvent.progress(
                    "正在从文章 Markdown 内容中提取图片引用信息...", 35));

            Set<String> referencedImagePaths = extractReferencedImagePaths(allPosts, allErrors);
            fireEvent(callback, CleanupProgressEvent.progress(
                    "文章中共引用 " + referencedImagePaths.size() + " 个不同的图片路径", 50));

            // ========== 步骤4: 对比找出孤立文件 ==========
            log.info("正在对比找出孤立文件...");
            fireEvent(callback, CleanupProgressEvent.progress(
                    "正在对比找出未被引用的孤立文件...", 55));

            // 使用 HashMap 索引加速查找
            Map<String, ImageFileInfo> imageIndex = new HashMap<>();
            for (ImageFileInfo image : allImages) {
                String imageObjectPath = CLEANUP_TARGET_PREFIX + image.relativePath();
                imageIndex.put(imageObjectPath, image);
            }

            // 找出未被引用的孤立图片
            List<String> orphanObjectPaths = new ArrayList<>();
            long totalOrphanBytes = 0;
            for (Map.Entry<String, ImageFileInfo> entry : imageIndex.entrySet()) {
                if (!referencedImagePaths.contains(entry.getKey())) {
                    orphanObjectPaths.add(entry.getKey());
                    totalOrphanBytes += entry.getValue().size();
                }
            }
            log.info("发现 {} 个孤立文件，共 {}", orphanObjectPaths.size(),
                    formatBytes(totalOrphanBytes));

            if (orphanObjectPaths.isEmpty()) {
                fireEvent(callback, CleanupProgressEvent.progress("对比完成，未发现孤立文件", 100));
                recordAuditLog(userId, allImages.size(), referencedImagePaths.size(), 0, 0,
                        List.of(), allErrors, Duration.between(start, Instant.now()).toMillis());
                fireComplete(callback, "清理完成，未发现孤立文件", allErrors,
                        allImages.size(), referencedImagePaths.size(), 0, 0, List.of(),
                        Duration.between(start, Instant.now()).toMillis());
                return;
            }

            fireEvent(callback, CleanupProgressEvent.progress(
                    "发现 " + orphanObjectPaths.size() + " 个孤立文件（共 "
                            + formatBytes(totalOrphanBytes) + "），开始删除", 60));

            // ========== 步骤5: 分批删除 ==========
            List<String> deletedFiles = new ArrayList<>();
            int totalDeletedSuccess = 0;
            int totalBatches = (orphanObjectPaths.size() + BATCH_DELETE_SIZE - 1) / BATCH_DELETE_SIZE;

            for (int i = 0; i < orphanObjectPaths.size(); i += BATCH_DELETE_SIZE) {
                int end = Math.min(i + BATCH_DELETE_SIZE, orphanObjectPaths.size());
                List<String> batch = orphanObjectPaths.subList(i, end);
                int batchIndex = (i / BATCH_DELETE_SIZE) + 1;

                String progressMsg = String.format(
                        "正在删除第 %d/%d 批（%d 个文件）...", batchIndex, totalBatches, batch.size());
                log.info(progressMsg);
                fireEvent(callback, CleanupProgressEvent.progress(progressMsg,
                        60 + (batchIndex * 35 / totalBatches)));

                // 逐个删除，确保每个失败都能被捕获、记录并跳过，继续删下一个
                for (String objectPath : batch) {
                    try {
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectPath)
                                        .build()
                        );
                        deletedFiles.add(objectPath);
                        totalDeletedSuccess++;
                        log.debug("已删除孤立文件: {}", objectPath);
                    } catch (Exception e) {
                        String errMsg = String.format("删除失败: %s - %s", objectPath, e.getMessage());
                        log.error(errMsg);
                        allErrors.add(errMsg);
                        // 跳过，继续删除下一个
                    }
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            int failedCount = orphanObjectPaths.size() - totalDeletedSuccess;
            log.info("孤立文件清理完成: 成功删除={}, 失败={}, 释放空间={}, 耗时={}ms",
                    totalDeletedSuccess, failedCount,
                    formatBytes(totalOrphanBytes), elapsed.toMillis());

            if (!allErrors.isEmpty()) {
                log.warn("清理过程中共出现 {} 个错误:", allErrors.size());
                for (String err : allErrors) {
                    log.warn("  - {}", err);
                }
            }

            // ========== 记录审计日志 ==========
            recordAuditLog(userId, allImages.size(), referencedImagePaths.size(),
                    totalDeletedSuccess, totalOrphanBytes, deletedFiles, allErrors, elapsed.toMillis());

            // ========== 推送完成事件 ==========
            fireComplete(callback,
                    failedCount > 0
                            ? "清理完成，成功删除 " + totalDeletedSuccess + " 个文件，失败 " + failedCount + " 个"
                            : "清理完成，成功删除 " + totalDeletedSuccess + " 个文件",
                    allErrors,
                    allImages.size(), referencedImagePaths.size(),
                    totalDeletedSuccess, totalOrphanBytes, deletedFiles,
                    elapsed.toMillis());

        } catch (BusinessException e) {
            log.warn("清理任务被拒绝: {}", e.getMessage());
            fireEvent(callback, CleanupProgressEvent.error(e.getMessage(), 0, allErrors));
            throw e;
        } catch (Exception e) {
            log.error("清理过程中发生未知错误", e);
            allErrors.add("系统错误: " + e.getMessage());
            fireEvent(callback, CleanupProgressEvent.error(
                    "清理过程中发生错误: " + e.getMessage(), 0, allErrors));
            recordAuditLog(userId, 0, 0, 0, 0, List.of(), allErrors,
                    Duration.between(start, Instant.now()).toMillis());
            throw new BusinessException("清理失败: " + e.getMessage());
        } finally {
            // 释放并发锁
            cleanupRunning.set(false);
        }
    }

    /**
     * 推送进度事件（忽略空回调）
     */
    private void fireEvent(Consumer<CleanupProgressEvent> callback, CleanupProgressEvent event) {
        if (callback != null) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                log.warn("推送进度事件失败（前端可能已断开）: {}", e.getMessage());
            }
        }
    }

    /**
     * 推送完成事件（含统计数据）
     */
    private void fireComplete(Consumer<CleanupProgressEvent> callback, String message,
                              List<String> errors, int totalImages, int referencedCount,
                              int deletedCount, long deletedBytes, List<String> deletedFiles,
                              long executionTimeMs) {
        CleanupStats stats = new CleanupStats(
                totalImages, referencedCount, deletedCount,
                errors.size(), deletedBytes,
                formatBytes(deletedBytes), executionTimeMs,
                deletedFiles
        );
        fireEvent(callback, CleanupProgressEvent.complete(message, 100, errors, stats));
    }

    /**
     * 记录清理操作的审计日志
     */
    private void recordAuditLog(Long userId, int totalImages, int referencedCount,
                                int deletedCount, long deletedBytes, List<String> deletedFiles,
                                List<String> errors, long executionTimeMs) {
        try {
            // 获取用户名
            String username = "未知";
            try {
                var user = userCacheService.getUserById(userId);
                if (user != null) {
                    username = user.getUsername();
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败: userId={}", userId, e);
            }

            // 构建 extraData JSON（包括错误列表）
            Map<String, Object> extraData = new LinkedHashMap<>();
            extraData.put("totalImages", totalImages);
            extraData.put("referencedCount", referencedCount);
            extraData.put("deletedCount", deletedCount);
            extraData.put("failedCount", errors.size());
            extraData.put("deletedBytes", deletedBytes);
            extraData.put("deletedBytesFormatted", formatBytes(deletedBytes));
            if (!errors.isEmpty()) {
                extraData.put("errors", errors);
            }

            String extraDataJson;
            try {
                extraDataJson = objectMapper.writeValueAsString(extraData);
            } catch (JsonProcessingException e) {
                log.warn("序列化 extraData 失败", e);
                extraDataJson = "{}";
            }

            String description = String.format(
                    "清理对象存储孤立垃圾文件: 总数=%d, 被引用=%d, 已删除=%d, 失败=%d, 释放空间=%s",
                    totalImages, referencedCount, deletedCount, errors.size(),
                    formatBytes(deletedBytes));

            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setOperationType("CLEANUP");
            auditLog.setModule("STORAGE");
            auditLog.setDescription(description);
            auditLog.setSuccess(errors.isEmpty());
            auditLog.setExecutionTime(executionTimeMs);
            auditLog.setExtraData(extraDataJson);
            auditLog.setCreateTime(LocalDateTime.now());

            if (!errors.isEmpty()) {
                auditLog.setErrorMessage("删除失败 " + errors.size() + " 个文件");
            }

            auditLogService.recordAuditLog(auditLog);
            log.info("孤立文件清理审计日志已记录: userId={}, executionTime={}ms, success={}, errors={}",
                    userId, executionTimeMs, auditLog.getSuccess(), errors.size());
        } catch (Exception e) {
            log.error("记录审计日志失败", e);
            // 审计日志记录失败不影响主流程
        }
    }

    /**
     * 扫描 MinIO images/ 目录下的所有文件
     */
    private List<ImageFileInfo> listAllImages() {
        List<ImageFileInfo> images = new ArrayList<>();

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(CLEANUP_TARGET_PREFIX)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    String objectPath = item.objectName();
                    String relativePath = objectPath.substring(CLEANUP_TARGET_PREFIX.length());
                    images.add(new ImageFileInfo(relativePath, item.size()));
                }
            }
        } catch (Exception e) {
            log.error("扫描 MinIO images/ 目录失败", e);
            throw new BusinessException("扫描 MinIO 存储失败: " + e.getMessage());
        }

        images.sort(Comparator.comparing(ImageFileInfo::relativePath));
        return images;
    }

    /**
     * 从所有文章中提取被引用的图片路径集合
     */
    private Set<String> extractReferencedImagePaths(List<Post> posts, List<String> errors) {
        Set<String> referencedPaths = new HashSet<>();

        for (Post post : posts) {
            if (post.getContentPath() != null && !post.getContentPath().isEmpty()) {
                try {
                    String content = postStorageService.downloadContent(post.getContentPath());
                    Set<String> paths = extractImagePathsFromContent(content);
                    referencedPaths.addAll(paths);
                } catch (Exception e) {
                    String errMsg = String.format("读取文章内容失败: postId=%s, path=%s, error=%s",
                            post.getId(), post.getContentPath(), e.getMessage());
                    log.warn(errMsg);
                    errors.add(errMsg);
                }
            }
        }

        return referencedPaths;
    }

    /**
     * 从 Markdown 内容中提取引用的图片 MinIO 路径
     */
    Set<String> extractImagePathsFromContent(String content) {
        Set<String> paths = new HashSet<>();
        if (content == null || content.isEmpty()) {
            return paths;
        }

        Matcher matcher = IMAGE_REFERENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group();
            String objectPath = url.substring("/api/images/stream/".length());
            paths.add(objectPath);
        }

        return paths;
    }

    /**
     * 格式化字节数
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * MinIO 中图片文件的信息
     */
    private record ImageFileInfo(String relativePath, long size) {
    }
}
