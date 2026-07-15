package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户信息请求
 */
@Data
public class UpdateUserRequest {
    
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;
    
    private String avatar;
}
