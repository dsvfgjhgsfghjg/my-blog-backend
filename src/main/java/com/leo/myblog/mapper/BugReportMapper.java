package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.BugReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * Bug 日志上报 Mapper
 */
@Mapper
public interface BugReportMapper extends BaseMapper<BugReport> {
}
