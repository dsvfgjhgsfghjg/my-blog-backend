package com.leo.myblog.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 能力响应
 */
@Data
public class AbilityResponse {
    
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
