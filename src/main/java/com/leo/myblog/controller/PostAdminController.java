package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.common.Result;
import com.leo.myblog.service.PostContentMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文章管理控制器（管理员专用）
 */
@Slf4j
@RestController
@RequestMapping("/api/posts/admin")
@RequiredArgsConstructor
public class PostAdminController {
    
    private final PostContentMigrationService migrationService;
    
    /**
     * 迁移所有文章内容到 MinIO
     * 需要管理员权限
     */
    @RequireAdmin
    @PostMapping("/migrate-content")
    public Result<Integer> migrateContent() {
        log.info("管理员触发文章内容迁移");
        
        try {
            int successCount = migrationService.migrateAllPosts();
            return Result.success("文章迁移完成，成功迁移 " + successCount + " 篇", successCount);
        } catch (Exception e) {
            log.error("文章迁移失败", e);
            return Result.error("文章迁移失败: " + e.getMessage());
        }
    }
}
