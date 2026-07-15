package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.AppDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * App 绑定设备 Mapper
 */
@Mapper
public interface AppDeviceMapper extends BaseMapper<AppDevice> {
}
