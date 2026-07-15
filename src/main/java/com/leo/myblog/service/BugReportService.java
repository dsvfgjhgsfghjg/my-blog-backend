package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.entity.BugReport;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.BugReportMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Bug 日志上报服务
 * <p>
 * 处理 App 端上报的 Bug 日志：
 * - 接收 Gzip + Base64 压缩的日志内容
 * - 解压后存储到 MinIO
 * - 元数据写入数据库
 * - 提供管理后台的查询、状态管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BugReportService {

    private final MinioClient minioClient;
    private final BugReportMapper bugReportMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    // 最大原始日志大小：5MB
    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024;

    // 最大压缩后大小：2MB
    private static final long MAX_COMPRESSED_SIZE = 2 * 1024 * 1024;

    // 摘要截取长度
    private static final int SUMMARY_MAX_LENGTH = 200;

    /**
     * 提交 Bug 日志
     *
     * @param userId        上报用户 ID
     * @param logData       Gzip + Base64 编码的日志内容
     * @param appVersion    App 版本号
     * @param deviceModel   设备型号
     * @param osVersion     操作系统版本
     * @param summary       日志摘要（可选，前端提供）
     * @return 创建的 BugReport 对象
     */
    @Transactional(rollbackFor = Exception.class)
    public BugReport submitReport(Long userId, String logData, String appVersion,
                                   String deviceModel, String osVersion, String summary) {
        // 1. 验证压缩数据大小
        if (logData == null || logData.isEmpty()) {
            throw new BusinessException(400, "日志内容不能为空");
        }

        byte[] compressedBytes;
        try {
            compressedBytes = Base64.getDecoder().decode(logData);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "日志数据格式错误（Base64 解码失败）");
        }

        if (compressedBytes.length > MAX_COMPRESSED_SIZE) {
            throw new BusinessException(400, "压缩后的日志数据超过 2MB 限制");
        }

        // 2. Gzip 解压
        String logText;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
                if (baos.size() > MAX_LOG_SIZE) {
                    throw new BusinessException(400, "原始日志内容超过 5MB 限制");
                }
            }
            logText = baos.toString(StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gzip 解压失败: userId={}", userId, e);
            throw new BusinessException(400, "日志数据解压失败，请检查压缩格式");
        }

        if (logText.isEmpty()) {
            throw new BusinessException(400, "解压后的日志内容为空");
        }

        // 3. 提取摘要
        String logSummary = (summary != null && !summary.isEmpty())
                ? summary
                : logText.substring(0, Math.min(logText.length(), SUMMARY_MAX_LENGTH));

        // 4. 生成 MinIO 路径
        String minioPath = buildMinioPath();

        // 5. 上传到 MinIO（存储原始文本，方便直接查看）
        try {
            byte[] logBytes = logText.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(logBytes);

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(minioPath)
                    .stream(inputStream, logBytes.length, -1)
                    .contentType("text/plain; charset=utf-8")
                    .build();

            minioClient.putObject(args);
            log.info("Bug 日志上传到 MinIO: path={}, size={}", minioPath, logBytes.length);
        } catch (Exception e) {
            log.error("Bug 日志 MinIO 上传失败: userId={}", userId, e);
            throw new BusinessException("日志存储失败，请稍后重试");
        }

        // 6. 创建数据库记录
        BugReport report = new BugReport();
        report.setUserId(userId);
        report.setAppVersion(appVersion);
        report.setDeviceModel(deviceModel);
        report.setOsVersion(osVersion);
        report.setLogSummary(logSummary);
        report.setMinioPath(minioPath);
        report.setLogSize(logText.length());
        report.setCompressedSize(compressedBytes.length);
        report.setStatus("unread");
        report.setCreatedAt(LocalDateTime.now());

        bugReportMapper.insert(report);

        log.info("Bug 日志上报成功: id={}, userId={}, logSize={}, compressedSize={}",
                report.getId(), userId, report.getLogSize(), report.getCompressedSize());

        return report;
    }

    /**
     * 分页查询 Bug 日志列表
     */
    public Page<BugReport> getReportList(int page, int size, String status) {
        Page<BugReport> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<BugReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            wrapper.eq(BugReport::getStatus, status);
        }
        wrapper.orderByDesc(BugReport::getCreatedAt);

        return bugReportMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 获取 Bug 日志详情（含日志内容）
     */
    public BugReportDetail getReportDetail(Long id) {
        BugReport report = bugReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(404, "日志不存在");
        }

        // 从 MinIO 读取日志内容
        String logContent = readLogFromMinio(report.getMinioPath());

        BugReportDetail detail = new BugReportDetail();
        detail.setReport(report);
        detail.setLogContent(logContent);
        return detail;
    }

    /**
     * 更新 Bug 日志状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status, String resolveNote) {
        if (!"unread".equals(status) && !"read".equals(status) && !"resolved".equals(status)) {
            throw new BusinessException(400, "无效的状态值，仅支持: unread/read/resolved");
        }

        BugReport report = bugReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(404, "日志不存在");
        }

        report.setStatus(status);
        if ("resolved".equals(status)) {
            report.setResolvedAt(LocalDateTime.now());
            report.setResolveNote(resolveNote);
        }
        bugReportMapper.updateById(report);

        log.info("Bug 日志状态更新: id={}, status={}", id, status);
    }

    /**
     * 删除 Bug 日志（同时删除 MinIO 文件）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long id) {
        BugReport report = bugReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(404, "日志不存在");
        }

        // 删除 MinIO 文件
        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(report.getMinioPath())
                    .build();
            minioClient.removeObject(args);
        } catch (Exception e) {
            log.error("删除 MinIO 文件失败: path={}", report.getMinioPath(), e);
            // 不阻断删除，继续删除数据库记录
        }

        bugReportMapper.deleteById(id);
        log.info("Bug 日志删除成功: id={}, minioPath={}", id, report.getMinioPath());
    }

    /**
     * 从 MinIO 读取日志内容
     */
    public String readLogFromMinio(String minioPath) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(minioPath)
                    .build();

            try (InputStream inputStream = minioClient.getObject(args);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("从 MinIO 读取日志失败: path={}", minioPath, e);
            throw new BusinessException("读取日志内容失败");
        }
    }

    /**
     * 构建 MinIO 存储路径
     * 格式: bug-reports/{yyyy}/{MM}/{uuid}.log
     */
    private String buildMinioPath() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        String datePath = now.format(formatter);
        return String.format("bug-reports/%s/%s.log", datePath, UUID.randomUUID());
    }

    /**
     * Bug 日志详情（含日志内容）
     */
    @lombok.Data
    public static class BugReportDetail {
        private BugReport report;
        private String logContent;
    }
}
