package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 两步登录：第一步请求
 * 用户提交用户名 + 旋转验证码
 */
@Data
public class LoginStep1Request {

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 旋转验证码标识（必填）
     */
    private String captchaNonceStr;

    /**
     * 用户旋转图片的角度（必填）
     */
    private Double captchaAngle;

    /**
     * 滑动轨迹点列表（可选，用于行为分析）
     */
    private List<LoginRequest.TrajectoryPointDTO> captchaTrajectory;

    /**
     * 总耗时（毫秒，可选，用于行为分析）
     */
    private Long captchaTotalTime;
}