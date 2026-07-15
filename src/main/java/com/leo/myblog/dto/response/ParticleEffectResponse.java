package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 粒子特效响应
 */
@Data
public class ParticleEffectResponse {
    
    /**
     * 特效代码
     */
    private String effectCode;
    
    /**
     * 特效名称
     */
    private String effectName;
    
    /**
     * 特效描述
     */
    private String description;
    
    /**
     * 排序
     */
    private Integer sortOrder;
}
