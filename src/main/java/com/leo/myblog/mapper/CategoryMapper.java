package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分类Mapper
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 统计仍引用该分类的文章行数（<b>含逻辑删除的文章</b>）
     * <p>
     * 刻意不走 {@code PostMapper}/{@code BaseMapper}：那条路会被 {@code Post.isDeleted} 上的
     * {@code @TableLogic} 自动追加 {@code is_deleted = false}，于是查不到「已删除但行还在」的文章。
     * 而 {@code t_post.category_id} 上的外键 {@code fk_post_category} 并不认逻辑删除 ——
     * 漏掉这些行，删除分类时数据库会直接抛出主键/外键冲突的 SQL 异常，
     * 前端只能看到一句「系统繁忙，请稍后重试」，排查时无从下手。
     * <p>
     * 另注：{@code fk_post_category} 没有 ON DELETE 子句，所以这条查询<b>不是</b>安全性的
     * 唯一依靠，只是把数据库的拒绝提前翻译成人话。
     *
     * @param categoryId 分类ID
     * @return 引用该分类的文章总行数（含软删）
     */
    @Select("SELECT COUNT(*) FROM t_post WHERE category_id = #{categoryId}")
    long countPostsIncludingDeleted(@Param("categoryId") Long categoryId);
}
