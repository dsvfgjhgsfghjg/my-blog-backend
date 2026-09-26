package com.leo.myblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.myblog.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 审计日志Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 按模块统计日志条数
     * <p>
     * 替代原先写死的 6 个模块循环计数 —— 那种写法让 BLOGGER/AUDIO 永远不出现在统计里，
     * 新增模块也必须记得回来改代码。这里直接按实际数据分组，来什么统计什么。
     *
     * @return 每项含 {@code name}（模块值）与 {@code total}（条数）
     */
    @Select("SELECT module AS name, COUNT(*) AS total FROM t_audit_log GROUP BY module")
    List<Map<String, Object>> countByModule();

    /**
     * 按等级统计日志条数
     *
     * @return 每项含 {@code name}（等级值）与 {@code total}（条数）
     */
    @Select("SELECT level AS name, COUNT(*) AS total FROM t_audit_log GROUP BY level")
    List<Map<String, Object>> countByLevel();
}
