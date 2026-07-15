package com.leo.myblog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 孤立文件清理进度事件
 * <p>
 * 通过 SSE 流式推送给前端，让用户实时看到清理进度和出现的错误。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupProgressEvent {

    /**
     * 事件类型
     */
    private String type;

    /**
     * 进度描述文本
     */
    private String message;

    /**
     * 当前进度百分比（0~100）
     */
    private int progress;

    /**
     * 当前发现的错误列表（type=error 或 type=complete 时携带）
     */
    private List<String> errors;

    /**
     * 最终统计数据（type=complete 时携带）
     */
    private CleanupStats stats;

    /**
     * 快速创建一个进度提示事件
     */
    public static CleanupProgressEvent progress(String message, int progress) {
        return new CleanupProgressEvent("progress", message, progress, null, null);
    }

    /**
     * 快速创建一个错误事件
     */
    public static CleanupProgressEvent error(String message, int progress, List<String> errors) {
        return new CleanupProgressEvent("error", message, progress, errors, null);
    }

    /**
     * 快速创建一个完成事件
     */
    public static CleanupProgressEvent complete(String message, int progress, List<String> errors, CleanupStats stats) {
        return new CleanupProgressEvent("complete", message, progress, errors, stats);
    }

    /**
     * 清理统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CleanupStats {
        private int totalImages;
        private int referencedCount;
        private int deletedCount;
        private int failedCount;
        private long deletedBytes;
        private String deletedBytesFormatted;
        private long executionTimeMs;
        private List<String> deletedFiles;
    }
}
