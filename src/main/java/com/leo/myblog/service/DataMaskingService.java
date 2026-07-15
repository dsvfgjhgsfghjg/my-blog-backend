package com.leo.myblog.service;

import com.leo.myblog.dto.response.UserBasicResponse;
import com.leo.myblog.dto.response.UserMaskedResponse;
import com.leo.myblog.dto.response.UserResponse;
import com.leo.myblog.entity.User;
import org.springframework.stereotype.Service;

/**
 * 数据脱敏服务
 * 提供统一的数据脱敏功能
 */
@Service
public class DataMaskingService {
    
    /**
     * 将用户实体转换为完整响应（应用脱敏）
     * 适用于管理员查看用户列表
     * 
     * @param user 用户实体
     * @return 脱敏后的用户响应
     */
    public UserResponse maskUserResponse(User user) {
        return UserResponse.fromUser(user);
    }
    
    /**
     * 将用户实体转换为基本响应（完全隐藏敏感信息）
     * 适用于普通用户查看其他用户
     * 
     * @param user 用户实体
     * @return 基本用户响应（不含敏感信息）
     */
    public UserBasicResponse maskUserBasicResponse(User user) {
        return UserBasicResponse.fromUser(user);
    }
    
    /**
     * 将用户实体转换为脱敏响应（部分脱敏）
     * 适用于需要展示部分信息的场景
     * 
     * @param user 用户实体
     * @return 脱敏后的用户响应
     */
    public UserMaskedResponse maskUserMaskedResponse(User user) {
        return UserMaskedResponse.fromUser(user);
    }
}
