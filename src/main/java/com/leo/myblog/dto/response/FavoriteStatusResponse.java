package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 收藏状态响应
 */
@Data
public class FavoriteStatusResponse {
    
    /**
     * 是否已收藏
     */
    private Boolean isFavorited;
}
