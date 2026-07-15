package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 旋转验证码标识（必填）
     */
    private String captchaNonceStr;

    /**
     * 用户旋转图片的角度（必填）
     * 正值为顺时针旋转，负值为逆时针旋转
     * 用户通过滑块将图片旋转回正，此角度应约等于 -rotationAngle（服务端记录）
     * 允许 ±6° 偏差
     */
    private Double captchaAngle;

    /**
     * 滑动轨迹点列表（可选，用于行为分析）
     * 轨迹点中的 x 表示滑块当前位置的角度值（每帧记录）
     */
    private java.util.List<TrajectoryPointDTO> captchaTrajectory;

    /**
     * 总耗时（毫秒，可选，用于行为分析）
     */
    private Long captchaTotalTime;

    /**
     * 轨迹点 DTO
     *
     * 注意：x 值表示当前旋转角度（度），可能为小数，因此使用 Double 类型。
     * 例如：-20.3 表示图片当前被逆时针旋转了约 20.3 度。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TrajectoryPointDTO {
        /**
         * 滑块当前旋转角度值（度），正为顺时针，负为逆时针
         * 使用 Double 类型以支持小数精度（例如：-20.3、15.7）
         */
        private Double x;

        /**
         * Y 坐标（保留字段，前端传0即可）
         */
        private Double y;

        /**
         * 时间戳（相对开始时间的毫秒数）
         */
        private Long t;
    }
}
