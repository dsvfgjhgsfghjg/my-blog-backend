package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.AbilityInfo;
import com.leo.myblog.dto.response.AbilityResponse;
import com.leo.myblog.entity.Ability;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AbilityMapper;
import com.leo.myblog.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 管理员能力服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityService {
    
    private final AbilityMapper abilityMapper;
    private final UserCacheService userCacheService;
    
    /**
     * 批量设置管理员能力（先删除旧能力，再添加新能力）
     * @param operatorId 操作者ID（当前登录的管理员）
     * @param targetUserId 目标用户ID
     * @param abilities 能力列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void setAbilities(Long operatorId, Long targetUserId, List<AbilityInfo> abilities) {
        // 1. 验证操作者权限（需要有管理管理员能力的权限）
        validateOperatorPermission(operatorId);
        
        // 2. 验证目标用户是否存在且是管理员
        User targetUser = userCacheService.getUserById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在");
        }
        if (!targetUser.getIsAdmin()) {
            throw new BusinessException("目标用户不是管理员，无法设置能力");
        }
        
        // 3. 删除目标用户的所有现有能力
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ability::getUserId, targetUserId);
        abilityMapper.delete(wrapper);
        
        log.info("已清除用户 {} 的原有能力", targetUserId);
        
        // 4. 批量插入新能力
        List<Ability> abilityList = abilities.stream().map(info -> {
            Ability ability = new Ability();
            ability.setUserId(targetUserId);
            ability.setName(info.getName());
            ability.setDescription(info.getDescription());
            ability.setIcon(info.getIcon());
            ability.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : 0);
            return ability;
        }).collect(Collectors.toList());
        
        // 5. 批量插入
        abilityList.forEach(abilityMapper::insert);
        
        log.info("为用户 {} 设置了 {} 个新能力", targetUserId, abilities.size());
    }
    
    /**
     * 查询指定用户的所有能力
     */
    public List<AbilityResponse> getUserAbilities(Long userId) {
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ability::getUserId, userId);
        wrapper.orderByAsc(Ability::getSortOrder);
        
        List<Ability> abilities = abilityMapper.selectList(wrapper);
        
        return abilities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有可用的能力种类（去重）
     * 用于前端展示可选的能力列表
     * @return 所有唯一的能力名称和描述
     */
    public List<AbilityResponse> getAllAbilityTypes() {
        // 查询所有能力记录
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Ability::getName, Ability::getDescription, Ability::getIcon, Ability::getSortOrder)
               .orderByAsc(Ability::getSortOrder);
        List<Ability> allAbilities = abilityMapper.selectList(wrapper);
        
        // 按name去重，保留sortOrder最小的记录
        Map<String, Ability> uniqueAbilities = new LinkedHashMap<>();
        for (Ability ability : allAbilities) {
            String name = ability.getName();
            if (!uniqueAbilities.containsKey(name)) {
                uniqueAbilities.put(name, ability);
            }
        }
        
        // 转换为响应对象
        return uniqueAbilities.values().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * 删除指定用户的所有能力
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeAllAbilities(Long userId) {
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ability::getUserId, userId);
        int deletedCount = abilityMapper.delete(wrapper);
        log.info("已删除用户 {} 的 {} 个能力", userId, deletedCount);
    }
    
    /**
     * 验证操作者是否有管理管理员能力的权限
     * 只有超级管理员或拥有 'manage_admin_abilities' 能力的管理员才能操作
     */
    private void validateOperatorPermission(Long operatorId) {
        User operator = userCacheService.getUserById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作者不存在");
        }
            
        // 1. 必须是管理员
        if (!operator.getIsAdmin()) {
            log.warn("非管理员尝试管理能力: operatorId={}", operatorId);
            throw new BusinessException("权限不足，需要管理员权限");
        }
            
        // 2. 检查是否为超级管理员
        Boolean isSuperAdmin = operator.getIsSuperAdmin();
        if (Boolean.TRUE.equals(isSuperAdmin)) {
            log.debug("超级管理员验证通过: operatorId={}", operatorId);
            return;
        }
            
        // 3. 检查是否有 'manage_admin_abilities' 能力
        LambdaQueryWrapper<Ability> abilityWrapper = new LambdaQueryWrapper<>();
        abilityWrapper.eq(Ability::getUserId, operatorId);
        List<Ability> operatorAbilities = abilityMapper.selectList(abilityWrapper);
        
        boolean hasPermission = operatorAbilities.stream()
            .anyMatch(a -> "manage_admin_abilities".equals(a.getName()));
            
        if (!hasPermission) {
            log.warn("管理员无管理能力权限: operatorId={}, abilities={}", 
                    operatorId, operatorAbilities.stream().map(Ability::getName).collect(Collectors.toList()));
            throw new BusinessException("权限不足，需要管理管理员能力的权限");
        }
            
        log.debug("管理员能力权限验证通过: operatorId={}", operatorId);
    }
    
    /**
     * 转换为响应对象
     */
    private AbilityResponse convertToResponse(Ability ability) {
        AbilityResponse response = new AbilityResponse();
        BeanUtils.copyProperties(ability, response);
        return response;
    }
}
