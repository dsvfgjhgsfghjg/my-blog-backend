package com.leo.myblog.dto.response;

import com.leo.myblog.utils.DataMaskingUtil;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户基本信息响应（脱敏版本，不包含敏感信息）
 * 用于普通用户查询其他用户信息
 */
@Data
public class UserBasicResponse {
    
    private Long id;
    private String username;
    private String nickname;
    // 注意：不包含 phone、email、avatar 字段，保护隐私
    private Boolean isAdmin;
    private LocalDateTime createTime;
    
    /**
     * 从 User 实体转换为基本响应
     */
    public static UserBasicResponse fromUser(com.leo.myblog.entity.User user) {
        if (user == null) {
            return null;
        }
        
        UserBasicResponse response = new UserBasicResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        // 不包含敏感信息
        response.setIsAdmin(user.getIsAdmin());
        response.setCreateTime(user.getCreateTime());
        
        return response;
    }
}
