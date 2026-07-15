- [x] 1.系统配置里面增加一个项目，可以看被ban的ip,也可以增加ip,该ip直接ban
- [x] 2.登录策略里面增加一个选项，如果登录失败次数过多，则ban ip

## 后端变更说明

### 数据库（已创建 V20 迁移）
- `t_system_config` 新增3个配置项:
  - `security.login.ban_ip_enabled` - 是否启用登录失败IP封禁（默认 false）
  - `security.login.ban_ip_threshold` - 触发IP封禁的登录失败次数（默认 20）
  - `security.login.ban_ip_duration_hours` - IP封禁时长小时（默认 24）

### 已修改的文件
- **SecurityAdminController.java** - IP封禁管理API添加 `@RequireAdmin` 和 `@RequireTotp` 注解
- **SystemConfigService.java** - 新增 `getConfigValue(key, defaultValue)` 方法
- **LoginRateLimitService.java** - 新增IP级失败计数与自动封禁逻辑
- **UserService.java** - 新增 `ipBanService` 依赖，在login()开头添加IP封禁检查
- **OAuth2TokenService.java** - 新增 `ipBanService` 依赖，在handlePasswordGrant()中添加IP封禁检查
