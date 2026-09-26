package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.response.SystemConfigResponse;
import com.leo.myblog.entity.Ability;
import com.leo.myblog.entity.SystemConfig;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.AbilityMapper;
import com.leo.myblog.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {
    
    private final SystemConfigMapper systemConfigMapper;
    private final UserCacheService userCacheService;
    private final AbilityMapper abilityMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取所有系统配置（按分类分组）
     * 只有拥有 manage_system_config 能力的管理员才能调用
     */
    public Map<String, List<SystemConfigResponse>> getAllConfigs(Long userId) {
        // 验证权限
        validateSystemConfigPermission(userId);
        
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SystemConfig::getCategory, SystemConfig::getSortOrder);
        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
        
        // 转换为DTO并按分类分组
        return configs.stream()
                .map(this::convertToResponse)
                .collect(Collectors.groupingBy(SystemConfigResponse::getCategory));
    }
    
    /**
     * 获取指定分类的配置
     */
    public List<SystemConfigResponse> getConfigsByCategory(Long userId, String category) {
        // 验证权限
        validateSystemConfigPermission(userId);
        
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getCategory, category)
                .orderByAsc(SystemConfig::getSortOrder);
        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
        
        return configs.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取单个配置项
     */
    public SystemConfigResponse getConfigByKey(Long userId, String configKey) {
        // 验证权限
        validateSystemConfigPermission(userId);
        
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, configKey);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        
        if (config == null) {
            throw new BusinessException("配置项不存在: " + configKey);
        }
        
        return convertToResponse(config);
    }
    
    /**
     * 更新配置项
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(Long userId, String configKey, String configValue) {
        // 验证权限
        validateSystemConfigPermission(userId);
        
        // 查询配置项
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, configKey);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        
        if (config == null) {
            throw new BusinessException("配置项不存在: " + configKey);
        }
        
        // 检查是否可编辑
        if (!Boolean.TRUE.equals(config.getIsEditable())) {
            throw new BusinessException("该配置项不可编辑: " + configKey);
        }
        
        // 验证配置值类型
        validateConfigValue(configValue, config.getConfigType());
        
        // 更新配置
        String oldValue = config.getConfigValue();
        config.setConfigValue(configValue);
        config.setUpdateTime(LocalDateTime.now());
        systemConfigMapper.updateById(config);

        log.info("系统配置已更新: userId={}, configKey={}, oldValue={}, newValue={}",
                userId, configKey, oldValue, configValue);
    }
    
    /**
     * 批量更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateConfigs(Long userId, Map<String, String> configs) {
        // 验证权限
        validateSystemConfigPermission(userId);
        
        int successCount = 0;
        int skippedCount = 0;
        
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String configKey = entry.getKey();
            String configValue = entry.getValue();
            
            try {
                updateConfig(userId, configKey, configValue);
                successCount++;
            } catch (BusinessException e) {
                // 如果是不可编辑的配置，跳过并记录日志
                if (e.getMessage() != null && e.getMessage().contains("不可编辑")) {
                    log.debug("跳过不可编辑的配置: {}", configKey);
                    skippedCount++;
                } else {
                    // 其他错误，抛出异常
                    throw e;
                }
            }
        }
        
        log.info("批量更新系统配置完成: userId={}, 成功={}, 跳过={}", userId, successCount, skippedCount);
    }
    
    /**
     * 获取公开配置（无需权限，前端可访问）
     */
    public Map<String, String> getPublicConfigs() {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getIsPublic, true);
        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
        
        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        SystemConfig::getConfigValue
                ));
    }
    
    /**
     * 检查指定模块的 TOTP 是否启用
     * @param moduleKey 模块配置键，如 security.totp.user_management
     * @return true=需要TOTP，false=不需要
     */
    public boolean isTotpEnabledForModule(String moduleKey) {
        try {
            LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getConfigKey, moduleKey);
            SystemConfig config = systemConfigMapper.selectOne(wrapper);
            
            if (config == null) {
                // 默认启用 TOTP（安全优先）
                log.debug("TOTP 配置项不存在，默认启用: {}", moduleKey);
                return true;
            }
            
            boolean enabled = "true".equalsIgnoreCase(config.getConfigValue());
            log.debug("TOTP 模块配置: {}={}", moduleKey, enabled);
            return enabled;
        } catch (Exception e) {
            log.error("检查 TOTP 配置失败: {}", moduleKey, e);
            // 出错时默认启用（安全优先）
            return true;
        }
    }
    
    /**
     * 获取配置项的值，如果不存在则返回默认值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getConfigValue(String configKey, String defaultValue) {
        try {
            LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getConfigKey, configKey);
            SystemConfig config = systemConfigMapper.selectOne(wrapper);
            
            if (config == null || config.getConfigValue() == null) {
                return defaultValue;
            }
            
            return config.getConfigValue();
        } catch (Exception e) {
            log.error("获取配置项失败: {}", configKey, e);
            return defaultValue;
        }
    }
    
    /**
     * 重置配置到默认值
     * <p>
     * 默认值存放在 t_system_config.default_value 中，由各版本的种子迁移写入（见 V34）。
     * 未定义默认值（default_value 为 NULL）的配置项不支持重置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetConfig(Long userId, String configKey) {
        // 验证权限
        validateSystemConfigPermission(userId);

        // 查询配置项
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, configKey);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);

        if (config == null) {
            throw new BusinessException("配置项不存在: " + configKey);
        }

        // 重置也是一种修改，与 updateConfig 保持一致：不可编辑的配置项不允许重置
        if (!Boolean.TRUE.equals(config.getIsEditable())) {
            throw new BusinessException("该配置项不可编辑: " + configKey);
        }

        String defaultValue = config.getDefaultValue();
        if (defaultValue == null) {
            throw new BusinessException("该配置项未定义默认值，无法重置: " + configKey);
        }

        String oldValue = config.getConfigValue();
        if (defaultValue.equals(oldValue)) {
            log.info("配置项已是默认值，无需重置: userId={}, configKey={}", userId, configKey);
            return;
        }

        // 重置配置
        config.setConfigValue(defaultValue);
        config.setUpdateTime(LocalDateTime.now());
        systemConfigMapper.updateById(config);

        log.info("系统配置已重置: userId={}, configKey={}, oldValue={}, defaultValue={}",
                userId, configKey, oldValue, defaultValue);
    }

    /**
     * 验证用户是否有管理系统配置的权限
     * 必须是管理员且拥有 manage_system_config 能力或超级管理员
     */
    private void validateSystemConfigPermission(Long userId) {
        User user = userCacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 1. 必须是管理员
        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            log.warn("非管理员尝试访问系统配置: userId={}", userId);
            throw new BusinessException("权限不足，需要管理员权限");
        }
        
        // 2. 检查是否为超级管理员
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            log.debug("超级管理员访问系统配置: userId={}", userId);
            return;
        }
        
        // 3. 检查是否有 manage_system_config 能力
        LambdaQueryWrapper<Ability> abilityWrapper = new LambdaQueryWrapper<>();
        abilityWrapper.eq(Ability::getUserId, userId);
        List<Ability> abilities = abilityMapper.selectList(abilityWrapper);
        
        boolean hasPermission = abilities.stream()
                .anyMatch(a -> "manage_system_config".equals(a.getName()));
        
        if (!hasPermission) {
            log.warn("管理员无系统配置管理权限: userId={}", userId);
            throw new BusinessException("权限不足，需要管理系统配置的权限");
        }
        
        log.debug("管理员权限验证通过: userId={}", userId);
    }
    
    /**
     * 验证配置值的类型
     */
    private void validateConfigValue(String value, String type) {
        if (value == null) {
            return;
        }
        
        switch (type.toUpperCase()) {
            case "NUMBER":
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException("配置值必须是数字: " + value);
                }
                break;
            case "BOOLEAN":
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BusinessException("配置值必须是布尔值（true/false）: " + value);
                }
                break;
            case "JSON":
                validateJsonFormat(value);
                break;
            case "STRING":
            default:
                // 字符串类型，无需验证
                break;
        }
    }
    
    /**
     * 验证JSON格式
     */
    private void validateJsonFormat(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new BusinessException("JSON配置值不能为空");
        }
        
        try {
            // 尝试解析JSON，如果失败会抛出异常
            objectMapper.readTree(json);
            log.debug("JSON格式验证通过");
        } catch (JsonProcessingException e) {
            log.warn("JSON格式验证失败: {}", e.getMessage());
            throw new BusinessException(
                String.format("JSON格式错误: %s。请检查JSON语法是否正确。", e.getOriginalMessage())
            );
        }
    }
    
    /**
     * 转换为响应DTO
     */
    private SystemConfigResponse convertToResponse(SystemConfig config) {
        SystemConfigResponse response = new SystemConfigResponse();
        response.setId(config.getId());
        response.setConfigKey(config.getConfigKey());
        response.setConfigValue(config.getConfigValue());
        response.setDefaultValue(config.getDefaultValue());
        response.setConfigType(config.getConfigType());
        response.setDescription(config.getDescription());
        response.setCategory(config.getCategory());
        response.setIsPublic(config.getIsPublic());
        response.setIsEditable(config.getIsEditable());
        response.setSortOrder(config.getSortOrder());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }
}
