package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.IpBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * IP封禁 Mapper
 */
@Mapper
public interface IpBanMapper extends BaseMapper<IpBan> {
    
    /**
     * 查询当前有效的封禁记录
     */
    @Select("SELECT * FROM t_ip_ban WHERE ban_until > NOW() ORDER BY ban_until DESC")
    List<IpBan> selectActiveBans();
}
