package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.SecurityEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 安全事件 Mapper
 */
@Mapper
public interface SecurityEventMapper extends BaseMapper<SecurityEvent> {
    
    /**
     * 统计各IP的违规次数（最近24小时）
     */
    @Select("""
        SELECT ip_address, COUNT(*) as violation_count, MAX(create_time) as last_violation
        FROM t_security_event
        WHERE create_time > NOW() - INTERVAL '24 hours'
        GROUP BY ip_address
        HAVING COUNT(*) >= #{minCount}
        ORDER BY violation_count DESC
    """)
    List<Map<String, Object>> getFrequentViolators(int minCount);
    
    /**
     * 统计威胁等级分布（最近7天）
     */
    @Select("""
        SELECT threat_level, COUNT(*) as count
        FROM t_security_event
        WHERE create_time > NOW() - INTERVAL '7 days'
        GROUP BY threat_level
        ORDER BY 
            CASE threat_level 
                WHEN 'CRITICAL' THEN 1 
                WHEN 'HIGH' THEN 2 
                WHEN 'MEDIUM' THEN 3 
                WHEN 'LOW' THEN 4 
            END
    """)
    List<Map<String, Object>> getThreatLevelStats();
}
