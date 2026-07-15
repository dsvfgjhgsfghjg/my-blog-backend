package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.PostFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章收藏Mapper
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
}
