package com.leo.myblog.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 博主信息响应
 */
@Data
public class BloggerInfoResponse {
    
    /**
     * 博主名字
     */
    private String name;
    
    /**
     * 头像URL（完整访问路径）
     */
    private String avatar;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * GitHub地址
     */
    private String github;
    
    /**
     * 简介
     */
    private String introduction;
    
    /**
     * 技术栈列表
     */
    private List<String> techStack;
}
