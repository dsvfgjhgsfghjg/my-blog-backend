package com.leo.myblog.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 两步登录：第一步响应
 */
@Data
public class LoginStep1Response {

    /**
     * 用户是否存在（模糊提示，不存在也返回 true 防止枚举）
     */
    private boolean userExists;

    /**
     * 默认登录方式
     */
    private String defaultMethod;

    /**
     * 可用的登录方式列表
     */
    private List<String> availableMethods;

    /**
     * 是否已绑定 App 设备
     */
    private boolean hasBoundDevices;

    /**
     * 是否有设备在线
     */
    private boolean devicesOnline;

    /**
     * 在线设备名称列表
     */
    private List<String> onlineDeviceNames;
}