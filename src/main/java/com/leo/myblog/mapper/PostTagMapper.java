package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章-标签关联Mapper
 */
@Mapper
public interface PostTagMapper extends BaseMapper<PostTag> {
}
