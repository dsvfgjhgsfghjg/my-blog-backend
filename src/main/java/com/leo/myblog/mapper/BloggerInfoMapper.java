package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.BloggerInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博主信息Mapper
 */
@Mapper
public interface BloggerInfoMapper extends BaseMapper<BloggerInfo> {
}
