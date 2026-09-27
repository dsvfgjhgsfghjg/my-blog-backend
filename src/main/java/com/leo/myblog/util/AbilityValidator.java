package com.leo.myblog.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.entity.Ability;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.exception.ForbiddenException;
import com.leo.myblog.mapper.AbilityMapper;
import com.leo.myblog.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 能力验证工具类
 * 用于检查用户是否具有特定能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityValidator {
    
    private final UserCacheService userCacheService;
    private final AbilityMapper abilityMapper;
    
    /**
     * 验证用户是否具有指定能力
     * @param userId 用户ID
     * @param abilityName 能力名称
     * @throws BusinessException 如果没有权限
     */
    public void validateAbility(Long userId, String abilityName) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 1. 必须是管理员
        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            log.warn("非管理员尝试执行需要能力的操作: userId={}, ability={}", userId, abilityName);
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        
        // 2. 超级管理员直接通过
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            log.debug("超级管理员权限验证通过: userId={}, ability={}", userId, abilityName);
            return;
        }
        
        // 3. 检查是否有指定能力
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ability::getUserId, userId)
               .eq(Ability::getName, abilityName);
        Long count = abilityMapper.selectCount(wrapper);
        
        if (count == 0) {
            log.warn("管理员缺少所需能力: userId={}, ability={}", userId, abilityName);
            throw new ForbiddenException(String.format("权限不足，需要 [%s] 能力", getAbilityDescription(abilityName)));
        }
        
        log.debug("能力验证通过: userId={}, ability={}", userId, abilityName);
    }
    
    /**
     * 验证用户是否具有多个能力中的任意一个
     * @param userId 用户ID
     * @param abilityNames 能力名称列表
     * @throws BusinessException 如果没有任何一个能力
     */
    public void validateAnyAbility(Long userId, String... abilityNames) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 1. 必须是管理员
        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        
        // 2. 超级管理员直接通过
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return;
        }
        
        // 3. 检查是否有任意一个能力
        LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ability::getUserId, userId)
               .in(Ability::getName, (Object[]) abilityNames);
        Long count = abilityMapper.selectCount(wrapper);
        
        if (count == 0) {
            log.warn("管理员缺少所需能力: userId={}, abilities={}", userId, String.join(", ", abilityNames));
            throw new ForbiddenException("权限不足，需要相关管理能力");
        }
    }
    
    /**
     * 获取能力描述
     */
    private String getAbilityDescription(String abilityName) {
        return switch (abilityName) {
            case "manage_admins" -> "添加/删除管理员";
            case "manage_admin_abilities" -> "管理管理员能力分配";
            case "add_admin" -> "添加管理员";
            case "toggle_user_status" -> "禁用/启用用户";
            case "reset_password" -> "重置用户密码";
            case "edit_posts" -> "编辑文章";
            case "delete_posts" -> "删除文章";
            case "edit_comments" -> "编辑评论";
            case "delete_comments" -> "删除评论";
            case "edit_tags" -> "编辑标签";
            case "manage_system_config" -> "管理系统配置";
            case "view_audit_logs" -> "查看审计日志";
            case "audit_posts" -> "审计文章";
            case "manage_sayings" -> "管理名言库（添加/删除名言）";
            default -> abilityName;
        };
    }
}
