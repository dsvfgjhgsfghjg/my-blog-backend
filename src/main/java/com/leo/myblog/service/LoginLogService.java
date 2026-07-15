package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.entity.LoginLog;
import com.leo.myblog.mapper.LoginLogMapper;
import com.leo.myblog.util.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {
    
    private final LoginLogMapper loginLogMapper;
    private final UserAgentParser userAgentParser;
    
    /**
     * 记录登录成功日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginSuccess(Long userId, String username, String ipAddress, String userAgent) {
        try {
            LoginLog loginLog = createLoginLog(userId, username, ipAddress, userAgent, "SUCCESS", null);
            loginLogMapper.insert(loginLog);
            log.debug("记录登录成功日志: userId={}, username={}, ip={}", userId, username, ipAddress);
        } catch (Exception e) {
            log.error("记录登录成功日志失败", e);
        }
    }
    
    /**
     * 记录登录失败日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginFailure(String username, String ipAddress, String userAgent, String failureReason) {
        try {
            LoginLog loginLog = createLoginLog(null, username, ipAddress, userAgent, "FAILED", failureReason);
            loginLogMapper.insert(loginLog);
            log.debug("记录登录失败日志: username={}, ip={}, reason={}", username, ipAddress, failureReason);
        } catch (Exception e) {
            log.error("记录登录失败日志失败", e);
        }
    }
    
    /**
     * 创建登录日志对象
     */
    private LoginLog createLoginLog(Long userId, String username, String ipAddress, 
                                     String userAgent, String loginResult, String failureReason) {
        // 解析User-Agent
        UserAgentParser.DeviceInfo deviceInfo = userAgentParser.parseUserAgent(userAgent);
        
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setIpAddress(ipAddress);
        loginLog.setDeviceType(deviceInfo.getDeviceType());
        loginLog.setOsName(deviceInfo.getOsName());
        loginLog.setOsVersion(deviceInfo.getOsVersion());
        loginLog.setBrowserName(deviceInfo.getBrowserName());
        loginLog.setBrowserVersion(deviceInfo.getBrowserVersion());
        loginLog.setUserAgent(deviceInfo.getUserAgent());
        loginLog.setLoginTime(LocalDateTime.now());
        loginLog.setLoginResult(loginResult);
        loginLog.setFailureReason(failureReason);
        
        return loginLog;
    }
    
    /**
     * 获取用户的最近登录日志
     */
    public LoginLog getLatestLoginLog(Long userId) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoginLog::getUserId, userId)
               .eq(LoginLog::getLoginResult, "SUCCESS")
               .orderByDesc(LoginLog::getLoginTime)
               .last("LIMIT 1");
        return loginLogMapper.selectOne(wrapper);
    }
}
