package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置用户密码请求
 */
@Data
public class AdminResetPasswordRequest {
    
    @NotBlank(message = "新密码不能为空")
    @Size(min = 10, max = 128, message = "新密码长度必须在10-128之间")
    private String newPassword;
}
