package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.leo.myblog.dto.response.InviteCodeResponse;
import com.leo.myblog.entity.InviteCode;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.exception.ForbiddenException;
import com.leo.myblog.mapper.InviteCodeMapper;
import com.leo.myblog.security.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private static final long EXPIRE_HOURS = 24;
    private static final int CODE_BYTES = 16;

    private final InviteCodeMapper inviteCodeMapper;
    private final UserCacheService userCacheService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(rollbackFor = Exception.class)
    public InviteCodeResponse generate(Long operatorId) {
        requireSuperAdmin(operatorId);
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        InviteCode invite = new InviteCode();
        invite.setCodeHash(TokenHashUtils.sha256(rawCode));
        invite.setCreatedBy(operatorId);
        invite.setCreatedAt(LocalDateTime.now());
        invite.setExpiresAt(LocalDateTime.now().plusHours(EXPIRE_HOURS));
        inviteCodeMapper.insert(invite);

        return toResponse(invite, rawCode);
    }

    public List<InviteCodeResponse> list(Long operatorId) {
        requireSuperAdmin(operatorId);
        return inviteCodeMapper.selectList(new LambdaQueryWrapper<InviteCode>()
                        .orderByDesc(InviteCode::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(invite -> toResponse(invite, null))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long operatorId, Long inviteId) {
        requireSuperAdmin(operatorId);
        InviteCode invite = inviteCodeMapper.selectById(inviteId);
        if (invite == null) {
            throw new BusinessException(404, "邀请码不存在");
        }
        if (invite.getUsedAt() != null) {
            throw new BusinessException("邀请码已被使用，不能撤销");
        }
        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(LocalDateTime.now());
            inviteCodeMapper.updateById(invite);
        }
    }

    public void validateAvailable(String rawCode) {
        findAvailable(rawCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public void consume(String rawCode, Long userId) {
        InviteCode invite = findAvailable(rawCode);
        LambdaUpdateWrapper<InviteCode> update = new LambdaUpdateWrapper<>();
        update.eq(InviteCode::getId, invite.getId())
                .isNull(InviteCode::getUsedAt)
                .isNull(InviteCode::getRevokedAt)
                .gt(InviteCode::getExpiresAt, LocalDateTime.now())
                .set(InviteCode::getUsedAt, LocalDateTime.now())
                .set(InviteCode::getUsedBy, userId);
        if (inviteCodeMapper.update(null, update) != 1) {
            throw new BusinessException("邀请码已被使用或已失效");
        }
    }

    private InviteCode findAvailable(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException("邀请码不能为空");
        }
        InviteCode invite = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCodeHash, TokenHashUtils.sha256(rawCode.trim())));
        if (invite == null) {
            throw new BusinessException("邀请码无效");
        }
        if (invite.getRevokedAt() != null) {
            throw new BusinessException("邀请码已撤销");
        }
        if (invite.getUsedAt() != null) {
            throw new BusinessException("邀请码已被使用");
        }
        if (!invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("邀请码已过期");
        }
        return invite;
    }

    private void requireSuperAdmin(Long operatorId) {
        User user = userCacheService.getUserById(operatorId);
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin())
                || !Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new ForbiddenException("仅超级管理员可以管理邀请码");
        }
    }

    private InviteCodeResponse toResponse(InviteCode invite, String rawCode) {
        String status;
        if (invite.getRevokedAt() != null) {
            status = "revoked";
        } else if (invite.getUsedAt() != null) {
            status = "used";
        } else if (!invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            status = "expired";
        } else {
            status = "available";
        }
        return InviteCodeResponse.builder()
                .id(invite.getId())
                .code(rawCode)
                .createdBy(invite.getCreatedBy())
                .createdAt(invite.getCreatedAt())
                .expiresAt(invite.getExpiresAt())
                .usedAt(invite.getUsedAt())
                .usedBy(invite.getUsedBy())
                .revokedAt(invite.getRevokedAt())
                .status(status)
                .build();
    }
}
