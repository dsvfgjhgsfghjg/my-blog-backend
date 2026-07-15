package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.PostAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章审计Mapper
 */
@Mapper
public interface PostAuditMapper extends BaseMapper<PostAudit> {
}
