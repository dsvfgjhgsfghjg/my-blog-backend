package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户偏好设置Mapper
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}
