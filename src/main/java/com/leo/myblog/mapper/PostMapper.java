package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
