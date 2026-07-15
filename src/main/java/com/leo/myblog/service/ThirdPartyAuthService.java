package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.response.OAuth2TokenResponse;
import com.leo.myblog.entity.ThirdPartyAccount;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.ThirdPartyAccountMapper;
import com.leo.myblog.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第三方账户关联服务
 * 管理用户与第三方平台（GitHub/Google等）的账户关联
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyAuthService {

    private final ThirdPartyAccountMapper thirdPartyAccountMapper;
    private final UserCacheService userCacheService;

    /**
     * 查找第三方账户关联
     */
    public ThirdPartyAccount findByProviderAndUserId(String provider, String providerUserId) {
        LambdaQueryWrapper<ThirdPartyAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThirdPartyAccount::getProvider, provider);
        wrapper.eq(ThirdPartyAccount::getProviderUserId, providerUserId);
        return thirdPartyAccountMapper.selectOne(wrapper);
    }

    /**
     * 查找用户已关联的所有第三方账户
     */
    public List<ThirdPartyAccount> findByUserId(Long userId) {
        LambdaQueryWrapper<ThirdPartyAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThirdPartyAccount::getUserId, userId);
        return thirdPartyAccountMapper.selectList(wrapper);
    }

    /**
     * 查找用户在指定平台的关联
     */
    public ThirdPartyAccount findByUserIdAndProvider(Long userId, String provider) {
        LambdaQueryWrapper<ThirdPartyAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThirdPartyAccount::getUserId, userId);
        wrapper.eq(ThirdPartyAccount::getProvider, provider);
        return thirdPartyAccountMapper.selectOne(wrapper);
    }

    /**
     * 创建或更新第三方账户关联
     */
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyAccount createOrUpdateLink(Long userId, String provider, String providerUserId,
                                                 String providerUsername, String providerAvatar,
                                                 String providerEmail, String accessToken,
                                                 String refreshToken, Long tokenExpiresIn) {
        ThirdPartyAccount existing = findByUserIdAndProvider(userId, provider);
        
        if (existing != null) {
            existing.setProviderUserId(providerUserId);
            existing.setProviderUsername(providerUsername);
            existing.setProviderAvatar(providerAvatar);
            existing.setProviderEmail(providerEmail);
            existing.setAccessToken(accessToken);
            existing.setRefreshToken(refreshToken);
            existing.setUpdatedAt(LocalDateTime.now());
            if (tokenExpiresIn != null) {
                existing.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenExpiresIn));
            }
            thirdPartyAccountMapper.updateById(existing);
            log.info("第三方账户关联已更新: userId={}, provider={}", userId, provider);
            return existing;
        }

        ThirdPartyAccount account = new ThirdPartyAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        account.setProviderUsername(providerUsername);
        account.setProviderAvatar(providerAvatar);
        account.setProviderEmail(providerEmail);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        if (tokenExpiresIn != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenExpiresIn));
        }
        account.setLinkedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        thirdPartyAccountMapper.insert(account);
        log.info("第三方账户关联已创建: userId={}, provider={}, providerUserId={}",
                userId, provider, providerUserId);
        return account;
    }

    /**
     * 解绑第三方账户
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlink(Long userId, String provider) {
        LambdaQueryWrapper<ThirdPartyAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThirdPartyAccount::getUserId, userId);
        wrapper.eq(ThirdPartyAccount::getProvider, provider);
        thirdPartyAccountMapper.delete(wrapper);
        log.info("第三方账户已解绑: userId={}, provider={}", userId, provider);
    }
}
