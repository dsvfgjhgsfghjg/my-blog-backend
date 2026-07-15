package com.leo.myblog.dto.response;

import com.leo.myblog.utils.DataMaskingUtil;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应（脱敏版本）
 * 用于向其他用户展示时，对敏感信息进行脱敏处理
 */
@Data
public class UserMaskedResponse {
    
    private Long id;
    private String username;
    private String nickname;
    
    /**
     * 脱敏后的手机号
     * 示例：138****5678
     */
    private String phone;
    
    /**
     * 脱敏后的邮箱
     * 示例：zh***@gmail.com
     */
    private String email;
    
    /**
     * 头像URL（公开可见，不脱敏）
     */
    private String avatar;
    
    private Boolean isAdmin;
    private LocalDateTime createTime;
    
    /**
     * 从 User 实体转换为脱敏响应
     */
    public static UserMaskedResponse fromUser(com.leo.myblog.entity.User user) {
        if (user == null) {
            return null;
        }
        
        UserMaskedResponse response = new UserMaskedResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        
        // 应用脱敏
        response.setPhone(DataMaskingUtil.maskPhone(user.getPhone()));
        response.setEmail(DataMaskingUtil.maskEmail(user.getEmail()));
        
        response.setAvatar(user.getAvatar());
        response.setIsAdmin(user.getIsAdmin());
        response.setCreateTime(user.getCreateTime());
        
        return response;
    }
}
