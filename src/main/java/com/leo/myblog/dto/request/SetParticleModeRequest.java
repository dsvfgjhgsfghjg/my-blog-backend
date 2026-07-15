package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 设置粒子特效模式请求
 */
@Data
public class SetParticleModeRequest {
    
    /**
     * 粒子特效模式
     * 可选值由数据库 t_particle_effect 表配置（白名单）
     * 当前支持: stardust, ice, sakura, firefly, magicCircle, pixel, frostMaple
     */
    @NotBlank(message = "粒子模式不能为空")
    @Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,49}$",
        message = "无效的粒子模式格式"
    )
    private String particleMode;
}
