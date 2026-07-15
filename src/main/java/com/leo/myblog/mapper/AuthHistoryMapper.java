package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.AuthHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证历史 Mapper
 */
@Mapper
public interface AuthHistoryMapper extends BaseMapper<AuthHistory> {
}
