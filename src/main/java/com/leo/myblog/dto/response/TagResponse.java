package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签响应
 */
@Data
public class TagResponse {
    
    /**
     * 标签ID
     */
    private Long id;
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 标签颜色
     */
    private String color;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
