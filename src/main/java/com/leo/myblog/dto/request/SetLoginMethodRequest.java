package com.leo.myblog.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class SetLoginMethodRequest {
    @NotBlank(message = "登录方式不能为空")
    private String defaultMethod;
}
