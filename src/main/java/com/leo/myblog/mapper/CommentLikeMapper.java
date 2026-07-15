package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论点赞记录Mapper
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {
}
