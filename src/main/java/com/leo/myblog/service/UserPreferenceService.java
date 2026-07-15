package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.SetParticleModeRequest;
import com.leo.myblog.dto.response.ParticleEffectResponse;
import com.leo.myblog.dto.response.ParticleModeResponse;
import com.leo.myblog.entity.ParticleEffect;
import com.leo.myblog.entity.UserPreference;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.ParticleEffectMapper;
import com.leo.myblog.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户偏好设置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {
    
    private final UserPreferenceMapper userPreferenceMapper;
    private final ParticleEffectMapper particleEffectMapper;
    
    /**
     * 默认粒子特效模式
     */
    private static final String DEFAULT_PARTICLE_MODE = "stardust";
    
    /**
     * 获取用户粒子特效设置
     * 如果用户没有设置，返回默认值
     * 
     * 安全机制：验证数据库中的值是否在白名单中
     */
    public ParticleModeResponse getParticleMode(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        
        UserPreference preference = userPreferenceMapper.selectOne(wrapper);
        
        if (preference == null) {
            // 用户没有设置，返回默认值
            log.debug("用户 {} 没有粒子特效设置，返回默认值: {}", userId, DEFAULT_PARTICLE_MODE);
            return new ParticleModeResponse(DEFAULT_PARTICLE_MODE);
        }
        
        // 验证数据库中的值是否合法（防止历史恶意数据）
        String particleMode = preference.getParticleMode();
        LambdaQueryWrapper<ParticleEffect> effectWrapper = new LambdaQueryWrapper<>();
        effectWrapper.eq(ParticleEffect::getEffectCode, particleMode)
                     .eq(ParticleEffect::getIsEnabled, true);
        ParticleEffect effect = particleEffectMapper.selectOne(effectWrapper);
        
        if (effect == null) {
            log.warn("发现非法的粒子特效数据: userId={}, particleMode={}，重置为默认值", userId, particleMode);
            // 自动修复：重置为默认值
            preference.setParticleMode(DEFAULT_PARTICLE_MODE);
            preference.setUpdatedAt(LocalDateTime.now());
            userPreferenceMapper.updateById(preference);
            return new ParticleModeResponse(DEFAULT_PARTICLE_MODE);
        }
        
        return new ParticleModeResponse(particleMode);
    }
    
    /**
     * 设置用户粒子特效
     * 如果用户已有记录则更新，否则插入新记录
     * 
     * 安全机制：
     * 1. DTO层正则验证（第一道防线）
     * 2. 数据库白名单验证（第二道防线，杜绝注入）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setParticleMode(Long userId, SetParticleModeRequest request) {
        String particleMode = request.getParticleMode();
        
        // 严格的数据库白名单验证（防止XSS注入）
        LambdaQueryWrapper<ParticleEffect> effectWrapper = new LambdaQueryWrapper<>();
        effectWrapper.eq(ParticleEffect::getEffectCode, particleMode)
                     .eq(ParticleEffect::getIsEnabled, true);
        ParticleEffect effect = particleEffectMapper.selectOne(effectWrapper);
        
        if (effect == null) {
            log.warn("非法的粒子特效模式尝试: userId={}, particleMode={}", userId, particleMode);
            throw new BusinessException("无效的粒子特效模式");
        }
        
        log.debug("粒子特效白名单验证通过: {} ({})", effect.getEffectCode(), effect.getEffectName());
        
        // 查询是否已有记录
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);
        
        if (existing != null) {
            // 更新现有记录
            existing.setParticleMode(particleMode);
            existing.setUpdatedAt(LocalDateTime.now());
            userPreferenceMapper.updateById(existing);
            log.info("用户 {} 更新粒子特效为: {}", userId, particleMode);
        } else {
            // 创建新记录
            UserPreference preference = new UserPreference();
            preference.setUserId(userId);
            preference.setParticleMode(particleMode);
            preference.setCreatedAt(LocalDateTime.now());
            preference.setUpdatedAt(LocalDateTime.now());
            userPreferenceMapper.insert(preference);
            log.info("用户 {} 创建粒子特效设置: {}", userId, particleMode);
        }
    }
    
    /**
     * 删除用户粒子特效设置（恢复默认）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteParticleMode(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);
        
        if (existing == null) {
            throw new BusinessException("用户偏好设置不存在");
        }
        
        userPreferenceMapper.deleteById(existing.getId());
        log.info("用户 {} 删除粒子特效设置，将使用默认值", userId);
    }
    
    /**
     * 获取所有可用的粒子特效列表
     * 用于前端动态加载特效选项
     */
    public List<ParticleEffectResponse> getAvailableEffects() {
        LambdaQueryWrapper<ParticleEffect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParticleEffect::getIsEnabled, true)
               .orderByAsc(ParticleEffect::getSortOrder);
        
        List<ParticleEffect> effects = particleEffectMapper.selectList(wrapper);
        
        return effects.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为响应对象
     */
    private ParticleEffectResponse convertToResponse(ParticleEffect effect) {
        ParticleEffectResponse response = new ParticleEffectResponse();
        BeanUtils.copyProperties(effect, response);
        return response;
    }
}
