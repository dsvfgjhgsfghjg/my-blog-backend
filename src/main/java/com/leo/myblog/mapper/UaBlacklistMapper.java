package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.UaBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UA黑名单 Mapper
 */
@Mapper
public interface UaBlacklistMapper extends BaseMapper<UaBlacklist> {
    
    /**
     * 查询所有启用的规则
     */
    @Select("SELECT * FROM t_ua_blacklist WHERE is_enabled = true ORDER BY threat_level DESC")
    List<UaBlacklist> selectEnabledRules();
}
