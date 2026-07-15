package com.leo.myblog.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 干净的分页结果类（不包含MyBatis-Plus的额外字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    
    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 总记录数（String类型避免JavaScript精度丢失）
     */
    private String total;
    
    /**
     * 每页大小
     */
    private String size;
    
    /**
     * 当前页码
     */
    private String current;
    
    /**
     * 从 MyBatis-Plus 的 IPage 转换为 PageResult
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(String.valueOf(page.getTotal()));
        result.setSize(String.valueOf(page.getSize()));
        result.setCurrent(String.valueOf(page.getCurrent()));
        return result;
    }
}
