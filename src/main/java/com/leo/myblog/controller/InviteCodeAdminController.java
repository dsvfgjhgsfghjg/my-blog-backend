package com.leo.myblog.controller;

import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.InviteCodeResponse;
import com.leo.myblog.service.InviteCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class InviteCodeAdminController {

    private final InviteCodeService inviteCodeService;

    @PostMapping
    @RequireAdmin
    @RequireTotp(operation = "生成注册邀请码", module = "security.totp.user_management")
    @AuditLog(action = AuditAction.CREATE, description = "生成注册邀请码")
    public Result<InviteCodeResponse> generate(@RequestAttribute Long userId) {
        return Result.success("邀请码已生成", inviteCodeService.generate(userId));
    }

    @GetMapping
    @RequireAdmin
    public Result<List<InviteCodeResponse>> list(@RequestAttribute Long userId) {
        return Result.success(inviteCodeService.list(userId));
    }

    @DeleteMapping("/{inviteId}")
    @RequireAdmin
    @RequireTotp(operation = "撤销注册邀请码", module = "security.totp.user_management")
    @AuditLog(action = AuditAction.DELETE, object = "inviteId", description = "撤销邀请码 {inviteId}")
    public Result<Void> revoke(@RequestAttribute Long userId, @PathVariable Long inviteId) {
        inviteCodeService.revoke(userId, inviteId);
        return Result.success("邀请码已撤销", null);
    }
}
