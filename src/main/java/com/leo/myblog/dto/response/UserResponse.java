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

    /**
     * 账号是否启用
     * <p>
     * 列表接口的请求侧（GetUserListRequest）一直有 enabled 筛选参数，响应里却从来没下发过它 ——
     * 于是前端表格无从回显，只能拿 isAdmin 当启用状态使（UserManagement.vue 里那句自认的注释），
     * 结果是普通用户永远是「启用」、管理员永远是「禁用」，超管被误点一次就再没有入口启用自己。
     */
    private Boolean enabled;

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
        response.setEnabled(user.getEnabled());
        response.setCreateTime(user.getCreateTime());
        
        return response;
    }
}
