package com.leo.myblog.dto.response;

import com.leo.myblog.utils.DataMaskingUtil;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应（管理员可见完整信息）
 */
@Data
public class UserResponse {
    
    private Long id;
    private String username;
    private String nickname;
    
    /**
     * 脱敏后的手机号（管理员可见）
     */
    private String phone;
    
    /**
     * 脱敏后的邮箱（管理员可见）
     */
    private String email;
    
    private String avatar;
    private Boolean isAdmin;
    private LocalDateTime createTime;
    
    /**
     * 从 User 实体转换为响应（应用脱敏）
     */
    public static UserResponse fromUser(com.leo.myblog.entity.User user) {
        if (user == null) {
            return null;
        }
        
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        
        // 即使是管理员，也应用脱敏（保护隐私）
        response.setPhone(DataMaskingUtil.maskPhone(user.getPhone()));
        response.setEmail(DataMaskingUtil.maskEmail(user.getEmail()));
        
        // 设置头像，如果为空则使用默认头像
        // 修复：将 MinIO 对象路径转换为前端可访问的 API 路径
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            response.setAvatar("/api/users/avatar/" + user.getId());
        } else {
            response.setAvatar("https://avatars.githubusercontent.com/u/67023703"); // 默认头像
        }
        
        response.setIsAdmin(user.getIsAdmin());
        response.setCreateTime(user.getCreateTime());
        
        return response;
    }
}
