package com.leo.myblog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应（包含JWT Token）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    
    /**
     * JWT Token
     */
    private String token;
    
    /**
     * Token类型（Bearer）
     */
    private String tokenType = "Bearer";
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像
     */
    private String avatar;
}
