package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 读取 t_post.content 原始列（正文迁移场景专用）
     * <p>
     * Post 实体在正文外置到 MinIO 之后就不再映射 content 字段了，所以这条只能走原生 SQL
     * 直接取列。该列在 {@code sql/migrate_post_content_to_minio.sql} 的「步骤4」里是**可选删除**的，
     * 线上可能已经不存在 —— 调用方需要容忍它抛异常，见
     * {@code PostContentMigrationService.getPostContent}。
     *
     * @param postId 文章ID
     * @return 正文原文；列已删除时由调用方兜住异常
     */
    @Select("SELECT content FROM t_post WHERE id = #{postId}")
    String selectRawContentById(@Param("postId") Long postId);
}
