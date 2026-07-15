package com.leo.myblog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 粒子特效模式响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticleModeResponse {
    
    /**
     * 粒子特效模式
     */
    private String particleMode;
}
