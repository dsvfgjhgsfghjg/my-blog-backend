package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.OAuth2Authorization;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 授权记录 Mapper
 */
@Mapper
public interface OAuth2AuthorizationMapper extends BaseMapper<OAuth2Authorization> {
}
