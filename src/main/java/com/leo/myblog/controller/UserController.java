package com.leo.myblog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.AbilityInfo;
import com.leo.myblog.dto.request.AdminResetPasswordRequest;
import com.leo.myblog.dto.request.ChangePasswordRequest;
import com.leo.myblog.dto.request.GetUserListRequest;
import com.leo.myblog.dto.request.LoginRequest;
import com.leo.myblog.dto.request.RegisterRequest;
import com.leo.myblog.dto.request.UpdateUserRequest;
import com.leo.myblog.dto.response.AbilityResponse;
import com.leo.myblog.dto.response.LoginInfoResponse;
import com.leo.myblog.dto.response.LoginResponse;
import com.leo.myblog.dto.response.UserBasicResponse;
import com.leo.myblog.dto.response.UserResponse;
import com.leo.myblog.config.CookieSecurityConfig;
import com.leo.myblog.service.AbilityService;
import com.leo.myblog.service.AvatarUploadService;
import com.leo.myblog.service.TokenBlacklistService;
import com.leo.myblog.service.UserService;
import com.leo.myblog.util.AbilityValidator;
import com.leo.myblog.util.ImageContentTypeUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final AbilityService abilityService;
    private final AvatarUploadService avatarUploadService;
    private final TokenBlacklistService tokenBlacklistService;
    private final com.leo.myblog.util.JwtUtil jwtUtil;
    private final AbilityValidator abilityValidator;
    private final CookieSecurityConfig cookieSecurityConfig;
    
    @Value("${jwt.cookie.expose-token-in-body:true}")
    private boolean exposeTokenInBody;
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody(required = false) RegisterRequest request) {
        if (request == null) {
            return Result.error(400, "请求体不能为空，请提供用户名和密码");
        }
        return Result.success("注册成功", userService.register(request));
    }
    
    /**
     * 用户登录
     * 安全增强: 将JWT Token存储到HttpOnly Cookie中，防止XSS攻击窃取
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        LoginResponse loginResponse = userService.login(request);
        
        // 将Token设置到HttpOnly Cookie中（核心安全防护）
        Cookie authCookie = cookieSecurityConfig.createSecureCookie(loginResponse.getToken());
        response.addCookie(authCookie);
        
        // 安全增强：根据配置决定是否在JSON响应体中暴露Token（Cookie模式下建议屏蔽）
        if (!exposeTokenInBody) {
            loginResponse.setToken(null);
            loginResponse.setTokenType(null);
        }
        
        log.info("用户登录成功，已设置HttpOnly Cookie: userId={}, exposeTokenInBody={}", 
                loginResponse.getUserId(), exposeTokenInBody);
        
        return Result.success("登录成功", loginResponse);
    }
    
    /**
     * 获取当前用户信息（包含头像等完整信息）
     */
    @GetMapping("/me")
    public Result<UserResponse> getCurrentUser(@RequestAttribute Long userId,
                                               @RequestAttribute String username) {
        log.debug("获取当前用户信息: userId={}, username={}", userId, username);
        return Result.success(userService.getCurrentUser(userId));
    }
    
    /**
     * 获取当前用户的登录信息（用于管理页面欢迎页）
     * 包含：当前IP、上次登录时间、登录次数等
     */
    @GetMapping("/me/login-info")
    public Result<LoginInfoResponse> getLoginInfo(@RequestAttribute Long userId,
                                                   @RequestAttribute String username) {
        log.debug("获取用户登录信息: userId={}, username={}", userId, username);
        
        // 从数据库获取最新的用户信息
        com.leo.myblog.entity.User user = userService.getUserById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        
        // 获取当前请求的IP地址
        String currentIp = getClientIpAddress();
        
        LoginInfoResponse response = new LoginInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setCurrentLoginIp(currentIp);
        response.setLastLoginIp(user.getLastLoginIp());
        response.setLastLoginTime(user.getLastLoginTime());
        response.setLoginCount(user.getLoginCount());
        
        return Result.success(response);
    }
    
    /**
     * 获取指定用户的基本信息（脱敏，不包含头像）
     * 安全修复V2: 只允许查看自己的信息，管理员可查看所有
     */
    @GetMapping("/{userId}")
    public Result<UserBasicResponse> getUserById(@RequestAttribute Long userId,
                                                 @PathVariable Long targetUserId) {
        log.debug("查询用户基本信息: currentUserId={}, targetUserId={}", userId, targetUserId);
        
        // 从数据库获取当前用户信息，判断是否为管理员
        com.leo.myblog.entity.User currentUser = userService.getUserById(userId);
        boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        // 权限校验：只能查看自己的信息，或者管理员可以查看所有
        if (!userId.equals(targetUserId) && !isAdmin) {
            log.warn("无权查看他人信息: currentUserId={}, targetUserId={}", userId, targetUserId);
            return Result.error(403, "无权查看他人信息");
        }
        
        return Result.success(userService.getUserBasicInfo(targetUserId));
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/me")
    public Result<Void> updateUser(@RequestAttribute Long userId,
                                   @RequestAttribute String username,
                                   @Valid @RequestBody UpdateUserRequest request) {
        log.debug("更新用户信息: userId={}, username={}", userId, username);
        userService.updateUser(userId, request);
        return Result.success("更新成功", null);
    }
    
    /**
     * 修改密码（需要 TOTP 双因素认证）
     */
    @RequireTotp(operation = "修改密码", module = "security.totp.account_security")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestAttribute Long userId,
                                       @RequestAttribute String username,
                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        log.debug("修改密码请求: userId={}, username={}", userId, username);
        // 提取 token (优先从 Authorization header，其次从 Cookie)
        String token = extractToken(authorization);
        if (token == null) {
            token = extractTokenFromCookie();
        }
        userService.changePassword(userId, username, token, request);
        return Result.success("密码修改成功", null);
    }
    
    /**
     * 从Authorization头提取Bearer Token
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
    
    /**
     * 从HttpOnly Cookie提取JWT Token
     */
    private String extractTokenFromCookie() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes = 
                (org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                jakarta.servlet.http.Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (jakarta.servlet.http.Cookie cookie : cookies) {
                        if ("AUTH_TOKEN".equals(cookie.getName())) {
                            String token = cookie.getValue();
                            if (token != null && !token.isEmpty()) {
                                log.debug("从HttpOnly Cookie中提取到Token用于修改密码");
                                return token;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从Cookie提取Token失败", e);
        }
        return null;
    }
    
    /**
     * 注销账户（软删除，需要 TOTP 双因素认证）
     * - 用户名改为"账户已注销_{原用户名}_{用户ID}"
     * - 昵称改为"已注销用户"
     * - 头像重置为默认值
     * - 禁用账户
     * - 删除用户偏好设置和能力
     */
    @RequireTotp(operation = "注销账户", module = "security.totp.account_security")
    @DeleteMapping("/me/account")
    public Result<Void> deleteAccount(@RequestAttribute Long userId,
                                      @RequestAttribute String username) {
        log.info("用户请求注销账户: userId={}, username={}", userId, username);
        userService.deleteAccount(userId);
        return Result.success("账户已注销", null);
    }
    
    /**
     * 用户注销（登出）
     * 安全增强: 将Token从HttpOnly Cookie中清除，并加入黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute Long userId,
                               @RequestAttribute String username,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpServletResponse response) {
        log.info("用户注销: userId={}, username={}", userId, username);
        
        // 提取 token (优先从 Authorization header，其次从 Cookie)
        String token = extractToken(authorization);
        if (token == null) {
            token = extractTokenFromCookie();
        }
        
        if (token != null) {
            // 获取 Token 剩余有效期
            long remainingSeconds = jwtUtil.getRemainingExpiration(token);
            
            // 将 Token 加入黑名单
            tokenBlacklistService.addToBlacklist(userId, token, remainingSeconds);
            
            log.info("Token已加入黑名单，用户注销成功: userId={}, 剩余有效期={}秒", userId, remainingSeconds);
        }
        
        // 清除HttpOnly Cookie
        Cookie clearCookie = cookieSecurityConfig.createClearCookie();
        response.addCookie(clearCookie);
        
        log.info("已清除HttpOnly Cookie，用户注销完成: userId={}", userId);
        
        return Result.success("注销成功", null);
    }
    
    /**
     * 上传头像（multipart/form-data）
     * 上传后自动更新用户头像字段
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestAttribute Long userId,
                                       @RequestAttribute String username,
                                       @RequestParam("file") MultipartFile file) {
        log.info("用户上传头像: userId={}, username={}, fileName={}", userId, username, file.getOriginalFilename());
        
        // 上传头像到 MinIO，返回 MinIO 对象路径
        String objectPath = avatarUploadService.uploadAvatar(file, userId);
        
        // 更新用户头像路径到数据库
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setAvatar(objectPath);
        userService.updateUser(userId, updateRequest);
        
        // 返回展示URL（通过 /api/users/me/avatar 内联展示）
        String accessUrl = "/api/users/me/avatar";
        return Result.success("头像上传成功", accessUrl);
    }
    
    /**
     * 获取当前用户头像（用于浏览器内联展示）
     * Content-Disposition: inline，可直接用于 <img> 标签的 src
     * 需要登录认证
     */
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> displayMyAvatar(@RequestAttribute Long userId) {
        try {
            com.leo.myblog.entity.User user = userService.getUserById(userId);
            String avatarPath = user != null ? user.getAvatar() : null;
            
            if (avatarPath == null || avatarPath.isEmpty()) {
                log.debug("用户头像不存在: userId={}", userId);
                return ResponseEntity.notFound().build();
            }
            
            byte[] avatarData = avatarUploadService.getAvatar(avatarPath);
            
            if (avatarData == null || avatarData.length == 0) {
                log.warn("用户头像文件不存在: userId={}, path={}", userId, avatarPath);
                return ResponseEntity.notFound().build();
            }
            
            // 位图用 inline 直接渲染（GIF 可正常播放）；SVG 强制 attachment
            HttpHeaders headers = new HttpHeaders();
            ImageContentTypeUtils.applyImageHeaders(headers, avatarPath,
                    ImageContentTypeUtils.Disposition.AUTO);
            headers.setContentLength(avatarData.length);
            headers.setCacheControl("private, max-age=3600"); // 缓存1小时（私有）
            
            return new ResponseEntity<>(avatarData, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("展示头像失败: userId={}", userId, e);
            return ResponseEntity.status(500).body(null);
        }
    }
    
    /**
     * 获取用户头像（带权限控制，用于下载）
     * Content-Disposition: attachment，强制浏览器下载
     * - 用户可以查看自己的头像
     * - 管理员可以查看所有用户头像
     * - 普通用户不能查看他人头像
     */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<byte[]> getAvatar(@RequestAttribute Long userId,
                                            @PathVariable("userId") Long targetUserId) {
        log.info("获取头像请求: currentUserId={}, targetUserId={}", userId, targetUserId);
        
        // 从数据库获取当前用户信息，判断是否为管理员
        com.leo.myblog.entity.User currentUser = userService.getUserById(userId);
        boolean isAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsAdmin());
        
        // 权限校验：只能查看自己的头像，或者管理员可以查看所有
        if (!userId.equals(targetUserId) && !isAdmin) {
            log.warn("无权查看他人头像: currentUserId={}, targetUserId={}", userId, targetUserId);
            return ResponseEntity.status(403).body(null);
        }
        
        // 从数据库获取目标用户信息，得到头像路径
        try {
            com.leo.myblog.entity.User targetUser = userService.getUserById(targetUserId);
            String avatarPath = targetUser != null ? targetUser.getAvatar() : null;
            
            if (avatarPath == null || avatarPath.isEmpty()) {
                log.warn("用户头像不存在: userId={}", targetUserId);
                return ResponseEntity.notFound().build();
            }
            
            // 从 MinIO 获取头像文件
            byte[] avatarData = avatarUploadService.getAvatar(avatarPath);
            
            if (avatarData == null || avatarData.length == 0) {
                log.warn("用户头像文件不存在: userId={}, path={}", targetUserId, avatarPath);
                return ResponseEntity.notFound().build();
            }
            
            // 该端点语义是「带权限的下载」，保持 attachment 不变
            HttpHeaders headers = new HttpHeaders();
            ImageContentTypeUtils.applyImageHeaders(headers, avatarPath,
                    ImageContentTypeUtils.Disposition.ATTACHMENT);
            headers.setContentLength(avatarData.length);
            headers.setCacheControl("public, max-age=86400"); // 缓存1天
            
            return new ResponseEntity<>(avatarData, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("获取头像失败: userId={}", targetUserId, e);
            return ResponseEntity.status(500).body(null);
        }
    }
    
    /**
     * 公开获取用户头像（无需登录，用于评论区等场景）
     */
    @GetMapping("/avatar/public/{userId}")
    public ResponseEntity<byte[]> getPublicAvatar(@PathVariable("userId") Long userId) {
        log.debug("公开获取头像: userId={}", userId);
        
        try {
            // 从数据库获取用户信息，得到头像路径
            com.leo.myblog.entity.User user = userService.getUserById(userId);
            String avatarPath = user != null ? user.getAvatar() : null;
            
            if (avatarPath == null || avatarPath.isEmpty()) {
                log.debug("用户头像不存在: userId={}", userId);
                return ResponseEntity.notFound().build();
            }
            
            // 从 MinIO 获取头像文件
            byte[] avatarData = avatarUploadService.getAvatar(avatarPath);
            
            if (avatarData == null || avatarData.length == 0) {
                log.debug("用户头像文件不存在: userId={}, path={}", userId, avatarPath);
                return ResponseEntity.notFound().build();
            }
            
            // 该端点被 <el-avatar :src> 直连（评论区等），位图用 inline 便于渲染
            HttpHeaders headers = new HttpHeaders();
            ImageContentTypeUtils.applyImageHeaders(headers, avatarPath,
                    ImageContentTypeUtils.Disposition.AUTO);
            headers.setContentLength(avatarData.length);
            headers.setCacheControl("public, max-age=86400"); // 缓存1天
            
            return new ResponseEntity<>(avatarData, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("公开获取头像失败: userId={}", userId, e);
            return ResponseEntity.status(500).body(null);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes = 
                (org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                
                // 尝试从代理头中获取真实IP
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                
                // 对于通过多个代理的情况，第一个IP为客户端真实IP
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
        }
        return "unknown";
    }
    
    // ==================== 管理员接口 ====================
    
    /**
     * 分页查询所有用户（管理员）
     */
    @RequireAdmin
    @PostMapping("/admin/list")
    public Result<Page<UserResponse>> getUsers(@Valid @RequestBody GetUserListRequest request) {
        log.debug("管理员查询用户列表: pageNum={}, pageSize={}, username={}, enabled={}",
                request.getPageNum(), request.getPageSize(), request.getUsername(), request.getEnabled());
        return Result.success(userService.getUsers(request));
    }
    
    /**
     * 启用/禁用用户（管理员，需要toggle_user_status能力 + TOTP双因素认证）
     */
    @RequireTotp(operation = "停用/启用账户", module = "security.totp.user_management")
    @RequireAdmin
    @PutMapping("/admin/{targetUserId}/status")
    public Result<Void> toggleUserStatus(@RequestAttribute Long userId,
                                         @PathVariable Long targetUserId,
                                         @RequestParam Boolean enabled) {
        log.info("管理员启用/禁用用户: operatorId={}, targetUserId={}, enabled={}", userId, targetUserId, enabled);
        // 验证能力：需要 toggle_user_status 能力
        abilityValidator.validateAbility(userId, "toggle_user_status");
        userService.toggleUserStatus(targetUserId, enabled);
        return Result.success(enabled ? "用户已启用" : "用户已禁用", null);
    }
    
    /**
     * 重置用户密码（管理员指定新密码，需要reset_password能力 + TOTP双因素认证）
     */
    @RequireTotp(operation = "重置用户密码", module = "security.totp.user_management")
    @RequireAdmin
    @PutMapping("/admin/{targetUserId}/reset-password")
    public Result<Void> resetPassword(@RequestAttribute Long userId,
                                      @PathVariable Long targetUserId,
                                      @Valid @RequestBody AdminResetPasswordRequest request) {
        log.info("管理员重置用户密码: operatorId={}, targetUserId={}", userId, targetUserId);
        // 验证能力：需要 reset_password 能力
        abilityValidator.validateAbility(userId, "reset_password");
        userService.resetPassword(targetUserId, request.getNewPassword());
        return Result.success("密码已重置", null);
    }
    
    /**
     * 修改用户权限（仅超级管理员或有manage_admins/add_admin能力的管理员 + TOTP双因素认证）
     * 注意：
     * 1. 只有 is_super_admin = true 的管理员或拥有 manage_admins/add_admin 能力才能调用
     * 2. 不能修改自己的权限（防止自我降级）
     */
    @RequireTotp(operation = "设置管理员", module = "security.totp.user_management")
    @RequireAdmin
    // 等级由 action 推出（CHANGE_ROLE → 关键）；描述里带上改成什么角色，便于事后追溯
    @AuditLog(action = AuditAction.CHANGE_ROLE,
              description = "修改用户 {targetUserId} 的角色（管理员：{isAdmin}）")
    @PutMapping("/admin/{targetUserId}/role")
    public Result<Void> updateUserRole(@RequestAttribute Long userId,
                                       @PathVariable Long targetUserId,
                                       @RequestParam Boolean isAdmin) {
        log.info("管理员修改用户权限: operatorId={}, targetUserId={}, newRole={}", 
                userId, targetUserId, isAdmin ? "ADMIN" : "USER");
        // 验证能力：需要 manage_admins 或 add_admin 能力
        abilityValidator.validateAnyAbility(userId, "manage_admins", "add_admin");
        userService.updateUserRole(userId, targetUserId, isAdmin);
        return Result.success(isAdmin ? "已设置为管理员" : "已取消管理员权限", null);
    }
    
    /**
     * 设置/取消超级管理员（仅超级管理员 + TOTP双因素认证）
     * 超级管理员可以管理其他管理员的能力分配
     */
    @RequireTotp(operation = "设置超级管理员", module = "security.totp.user_management")
    @RequireAdmin
    @PutMapping("/admin/{targetUserId}/super-admin")
    public Result<Void> setSuperAdmin(@RequestAttribute Long userId,
                                      @PathVariable Long targetUserId,
                                      @RequestParam Boolean isSuperAdmin) {
        log.info("管理员设置超级管理员: operatorId={}, targetUserId={}, isSuperAdmin={}", 
                userId, targetUserId, isSuperAdmin);
        // 验证能力：需要 manage_admins 能力
        abilityValidator.validateAbility(userId, "manage_admins");
        userService.setSuperAdmin(userId, targetUserId, isSuperAdmin);
        return Result.success(isSuperAdmin ? "已设置为超级管理员" : "已取消超级管理员权限", null);
    }
    
    // ==================== 管理员能力管理接口 ====================
    
    /**
     * 批量设置管理员能力（需要 TOTP 双因素认证）
     * 操作者需要有 manage_admin_abilities 能力
     */
    @RequireTotp(operation = "设置管理员能力", module = "security.totp.user_management")
    @RequireAdmin
    @PostMapping("/admin/{targetUserId}/abilities")
    public Result<Void> setAbilities(@RequestAttribute Long userId,
                                     @PathVariable Long targetUserId,
                                     @Valid @RequestBody List<AbilityInfo> abilities) {
        log.info("管理员批量设置能力: operatorId={}, targetUserId={}, count={}", 
                userId, targetUserId, abilities.size());
        // 验证能力：需要 manage_admin_abilities 能力
        abilityValidator.validateAbility(userId, "manage_admin_abilities");
        abilityService.setAbilities(userId, targetUserId, abilities);
        return Result.success("能力设置成功", null);
    }
    
    /**
     * 查询指定用户的所有能力
     */
    @RequireAdmin
    @GetMapping("/admin/{userId}/abilities")
    public Result<List<AbilityResponse>> getUserAbilities(@PathVariable Long userId) {
        log.debug("查询用户能力: userId={}", userId);
        return Result.success(abilityService.getUserAbilities(userId));
    }
    
    /**
     * 获取所有可用的能力种类（去重）
     * 用于前端展示可选的能力列表
     * 权限要求：需要 manage_admin_abilities 能力
     */
    @RequireAdmin
    @GetMapping("/admin/abilities/types")
    public Result<List<AbilityResponse>> getAllAbilityTypes(@RequestAttribute Long userId) {
        log.debug("获取所有能力种类: operatorId={}", userId);
        // 验证能力：需要 manage_admin_abilities 能力
        abilityValidator.validateAbility(userId, "manage_admin_abilities");
        return Result.success(abilityService.getAllAbilityTypes());
    }
}
