package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.ThirdPartyAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方账户关联 Mapper
 */
@Mapper
public interface ThirdPartyAccountMapper extends BaseMapper<ThirdPartyAccount> {
}
