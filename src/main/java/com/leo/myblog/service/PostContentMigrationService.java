package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.entity.Post;
import com.leo.myblog.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章内容迁移服务
 * 用于将数据库中的文章内容迁移到 MinIO 对象存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostContentMigrationService {
    
    private final PostMapper postMapper;
    private final PostStorageService postStorageService;

    /**
     * t_post.content 列是否已确认不可读
     * <p>
     * 只在「列被删」这种全局性故障上置位，避免为每一篇文章重复抛同一条 SQL 异常、
     * 把日志刷满从而盖住真正的原因。
     */
    private boolean contentColumnMissing = false;

    /**
     * 迁移所有文章的内容到 MinIO
     * 
     * @return 迁移成功的文章数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int migrateAllPosts() {
        log.info("开始迁移文章内容到 MinIO...");
        
        // 查询所有还没有 content_path 的文章
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Post::getContentPath)
               .or()
               .eq(Post::getContentPath, "");
        
        List<Post> posts = postMapper.selectList(wrapper);
        log.info("找到 {} 篇需要迁移的文章", posts.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Post post : posts) {
            try {
                String content = getPostContent(post.getId());

                if (content == null || content.isEmpty()) {
                    log.warn("文章内容为空，跳过: postId={}", post.getId());
                    failCount++;
                    continue;
                }
                
                // 上传到 MinIO
                String contentPath = postStorageService.uploadContent(post.getId(), content);
                
                // 更新数据库
                post.setContentPath(contentPath);
                postMapper.updateById(post);
                
                successCount++;
                log.info("文章迁移成功: postId={}, path={}", post.getId(), contentPath);
                
            } catch (Exception e) {
                failCount++;
                log.error("文章迁移失败: postId={}", post.getId(), e);
            }
        }
        
        log.info("文章迁移完成: 总数={}, 成功={}, 失败={}", posts.size(), successCount, failCount);
        return successCount;
    }
    
    /**
     * 获取文章正文（用于迁移）
     * <p>
     * Post 实体在正文外置到 MinIO 之后就不再映射 {@code content} 字段了，所以这里走
     * {@link PostMapper#selectRawContentById} 直接读原始列。
     * <p>
     * 该列在 {@code sql/migrate_post_content_to_minio.sql} 的「步骤4」里是可选删除的：
     * 一旦线上真的 DROP 掉，本文服务就再没有数据源可用，此时**不要**假装迁移成功 ——
     * 直接让调用方按失败处理，并把「需要从备份恢复」这件事打到日志里。
     *
     * @param postId 文章ID
     * @return 正文原文；列不存在时返回 null
     */
    private String getPostContent(Long postId) {
        if (contentColumnMissing) {
            // 已经确认列不存在，不必每篇都再撞一次 SQL 异常
            return null;
        }

        try {
            return postMapper.selectRawContentById(postId);
        } catch (Exception e) {
            contentColumnMissing = true;
            log.error("读取 t_post.content 失败，该列可能已被 DROP；" +
                    "后续文章将全部跳过，需要先从备份恢复正文再重跑迁移。首次失败 postId={}",
                    postId, e);
            return null;
        }
    }
}
