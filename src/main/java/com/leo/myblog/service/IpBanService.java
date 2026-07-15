package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.entity.IpBan;
import com.leo.myblog.mapper.IpBanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IP封禁服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpBanService {
    
    private final IpBanMapper ipBanMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String IP_BANNED_KEY = "security:banned:";
    
    /**
     * 封禁IP（同时写入数据库和Redis）
     */
    @Transactional(rollbackFor = Exception.class)
    public void banIp(String ipAddress, String reason, int triggerCount, long durationHours) {
        try {
            LocalDateTime banUntil = LocalDateTime.now().plusHours(durationHours);
            
            // 1. 写入数据库
            IpBan ipBan = new IpBan();
            ipBan.setIpAddress(ipAddress);
            ipBan.setReason(reason);
            ipBan.setBanUntil(banUntil);
            ipBan.setTriggerCount(triggerCount);
            ipBan.setCreateTime(LocalDateTime.now());
            ipBanMapper.insert(ipBan);
            
            // 2. 写入Redis（快速检查）
            String redisKey = IP_BANNED_KEY + ipAddress;
            redisTemplate.opsForValue().set(redisKey, reason, durationHours, TimeUnit.HOURS);
            
            log.warn("IP已被封禁: ip={}, reason={}, until={}, duration={}h", 
                    ipAddress, reason, banUntil, durationHours);
        } catch (Exception e) {
            log.error("封禁IP失败: ip={}", ipAddress, e);
        }
    }
    
    /**
     * 解除IP封禁
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbanIp(String ipAddress) {
        try {
            // 1. 从数据库删除
            LambdaQueryWrapper<IpBan> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(IpBan::getIpAddress, ipAddress);
            ipBanMapper.delete(wrapper);
            
            // 2. 从Redis删除
            String redisKey = IP_BANNED_KEY + ipAddress;
            redisTemplate.delete(redisKey);
            
            log.info("IP已解除封禁: ip={}", ipAddress);
        } catch (Exception e) {
            log.error("解除IP封禁失败: ip={}", ipAddress, e);
        }
    }
    
    /**
     * 检查IP是否被封禁（优先检查Redis）
     */
    public boolean isIpBanned(String ipAddress) {
        try {
            // 1. 先检查Redis
            String redisKey = IP_BANNED_KEY + ipAddress;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                return true;
            }
            
            // 2. Redis没有，检查数据库
            LambdaQueryWrapper<IpBan> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(IpBan::getIpAddress, ipAddress)
                   .gt(IpBan::getBanUntil, LocalDateTime.now());
            return ipBanMapper.selectCount(wrapper) > 0;
        } catch (Exception e) {
            log.error("检查IP封禁状态失败: ip={}", ipAddress, e);
            return false;
        }
    }
    
    /**
     * 获取当前被封禁的IP列表
     */
    public List<IpBan> getBannedIps() {
        LambdaQueryWrapper<IpBan> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(IpBan::getBanUntil, LocalDateTime.now())
               .orderByDesc(IpBan::getCreateTime);
        return ipBanMapper.selectList(wrapper);
    }
    
    /**
     * 清理过期的封禁记录
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredBans() {
        try {
            LambdaQueryWrapper<IpBan> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(IpBan::getBanUntil, LocalDateTime.now());
            
            int deleted = ipBanMapper.delete(wrapper);
            log.info("清理过期IP封禁记录: count={}", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("清理过期IP封禁记录失败", e);
            return 0;
        }
    }
}
