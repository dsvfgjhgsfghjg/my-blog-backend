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
                // 注意：这里假设 Post 实体中仍有 content 字段用于迁移
                // 如果已删除 content 字段，需要从备份或其他地方获取内容
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
     * 获取文章内容（用于迁移）
     * 注意：这个方法需要根据实际情况实现
     * 如果 content 字段已被删除，需要从备份或其他数据源获取
     */
    private String getPostContent(Long postId) {
        // TODO: 根据实际情况实现
        // 选项1: 如果 content 字段还存在，直接查询
        // 选项2: 从备份数据库恢复
        // 选项3: 从其他数据源获取
        
        Post post = postMapper.selectById(postId);
        if (post != null) {
            // 如果 content 字段还存在，返回它
            // return post.getContent();
            
            // 如果已删除，返回 null
            log.warn("需要从备份数据源获取文章内容: postId={}", postId);
        }
        
        return null;
    }
}
