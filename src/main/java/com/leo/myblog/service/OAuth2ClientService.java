package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.request.OAuth2ClientRequest;
import com.leo.myblog.dto.response.OAuth2ClientResponse;
import com.leo.myblog.entity.OAuth2Client;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.OAuth2ClientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OAuth2 客户端管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientService {

    private final OAuth2ClientMapper clientMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /**
     * 注册 OAuth2 客户端
     */
    @Transactional(rollbackFor = Exception.class)
    public OAuth2ClientResponse registerClient(OAuth2ClientRequest request, Long createdBy) {
        OAuth2Client client = new OAuth2Client();

        // 生成客户端ID和密钥
        String clientId = generateClientId();
        String clientSecret = generateClientSecret();

        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setClientName(request.getClientName());
        client.setDescription(request.getDescription());

        // JSON 序列化列表字段
        client.setRedirectUris(toJson(request.getRedirectUris()));
        client.setGrantTypes(toJson(request.getGrantTypes()));
        client.setScopes(toJson(request.getScopes()));

        client.setLogoUrl(request.getLogoUrl());
        client.setHomepageUrl(request.getHomepageUrl());
        client.setIsConfidential(request.getIsConfidential() != null ? request.getIsConfidential() : true);
        client.setIsEnabled(true);
        client.setCreatedBy(createdBy);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());

        clientMapper.insert(client);

        log.info("OAuth2客户端注册成功: clientId={}, name={}, createdBy={}", clientId, request.getClientName(), createdBy);

        return OAuth2ClientResponse.builder()
                .clientId(clientId)
                .clientSecret(clientSecret) // 仅首次返回明文
                .clientName(request.getClientName())
                .description(request.getDescription())
                .redirectUris(request.getRedirectUris())
                .grantTypes(request.getGrantTypes())
                .scopes(request.getScopes())
                .logoUrl(request.getLogoUrl())
                .homepageUrl(request.getHomepageUrl())
                .isConfidential(client.getIsConfidential())
                .isEnabled(true)
                .createdAt(client.getCreatedAt())
                .build();
    }

    /**
     * 根据 clientId 获取客户端
     */
    public OAuth2Client getByClientId(String clientId) {
        LambdaQueryWrapper<OAuth2Client> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OAuth2Client::getClientId, clientId);
        return clientMapper.selectOne(wrapper);
    }

    /**
     * 验证客户端密钥
     */
    public boolean validateClientSecret(String clientId, String rawSecret) {
        OAuth2Client client = getByClientId(clientId);
        if (client == null) {
            return false;
        }
        return passwordEncoder.matches(rawSecret, client.getClientSecret());
    }

    /**
     * 获取客户端列表（管理员）
     */
    public List<OAuth2ClientResponse> listClients() {
        List<OAuth2Client> clients = clientMapper.selectList(null);
        return clients.stream().map(this::toResponse).toList();
    }

    /**
     * 删除客户端
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(String clientId) {
        LambdaQueryWrapper<OAuth2Client> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OAuth2Client::getClientId, clientId);
        clientMapper.delete(wrapper);
        log.info("OAuth2客户端已删除: clientId={}", clientId);
    }

    /**
     * 解析 JSON 数组为 List
     */
    public List<String> parseJsonArray(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析 JSON 数组失败: {}", json, e);
            return List.of();
        }
    }

    /**
     * 将 List 序列化为 JSON 数组
     */
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("序列化 JSON 数组失败", e);
            return "[]";
        }
    }

    private OAuth2ClientResponse toResponse(OAuth2Client client) {
        return OAuth2ClientResponse.builder()
                .clientId(client.getClientId())
                .clientSecret(null) // 不返回密钥
                .clientName(client.getClientName())
                .description(client.getDescription())
                .redirectUris(parseJsonArray(client.getRedirectUris()))
                .grantTypes(parseJsonArray(client.getGrantTypes()))
                .scopes(parseJsonArray(client.getScopes()))
                .logoUrl(client.getLogoUrl())
                .homepageUrl(client.getHomepageUrl())
                .isConfidential(client.getIsConfidential())
                .isEnabled(client.getIsEnabled())
                .createdAt(client.getCreatedAt())
                .build();
    }

    private String generateClientId() {
        return "client_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateClientSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
