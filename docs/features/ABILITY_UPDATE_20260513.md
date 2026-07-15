# 能力系统更新日志 - 2026-05-13

## 📋 本次更新概述

新增了3个管理员能力，并重构了相关接口以支持细粒度的权限控制。

---

## ✅ 新增能力

### 1. toggle_user_status - 禁用/启用用户

**能力ID**: `2054570236258279434`  
**描述**: 允许管理员禁用或启用用户账号  
**图标**: `/icons/toggle-user.svg`  
**排序**: 12

**适用接口**:
- `PUT /api/users/admin/{targetUserId}/status`

---

### 2. reset_password - 重置用户密码

**能力ID**: `2054570236258279435`  
**描述**: 允许管理员重置指定用户的密码  
**图标**: `/icons/reset-password.svg`  
**排序**: 13

**适用接口**:
- `PUT /api/users/admin/{targetUserId}/reset-password`

---

### 3. add_admin - 添加管理员

**能力ID**: `2054570236258279436`  
**描述**: 允许管理员将普通用户提升为管理员  
**图标**: `/icons/add-admin.svg`  
**排序**: 14

**适用接口**:
- `PUT /api/users/admin/{targetUserId}/role` (与 `manage_admins` 二选一)

---

## 🔧 代码变更

### 1. 数据库变更

**文件**: PostgreSQL数据库  
**操作**: 在 `t_ability` 表中插入3条新记录

```sql
INSERT INTO t_ability (id, user_id, name, description, icon, sort_order, create_time)
VALUES 
    (2054570236258279434, 1, 'toggle_user_status', '禁用/启用用户', '/icons/toggle-user.svg', 12, CURRENT_TIMESTAMP),
    (2054570236258279435, 1, 'reset_password', '重置用户密码', '/icons/reset-password.svg', 13, CURRENT_TIMESTAMP),
    (2054570236258279436, 1, 'add_admin', '添加管理员', '/icons/add-admin.svg', 14, CURRENT_TIMESTAMP);
```

---

### 2. AbilityValidator.java

**文件**: [AbilityValidator.java](file:///home/leo/IdeaProjects/myblog/src/main/java/com/leo/myblog/util/AbilityValidator.java)  
**变更**: 在 `getAbilityDescription()` 方法中添加3个新能力的描述

```java
case "add_admin" -> "添加管理员";
case "toggle_user_status" -> "禁用/启用用户";
case "reset_password" -> "重置用户密码";
```

---

### 3. UserController.java

**文件**: [UserController.java](file:///home/leo/IdeaProjects/myblog/src/main/java/com/leo/myblog/controller/UserController.java)  
**变更**: 重构3个接口，添加能力验证

#### 接口1: 启用/禁用用户

**修改前**:
```java
@RequireAdmin
@PutMapping("/admin/{userId}/status")
public Result<Void> toggleUserStatus(@PathVariable Long userId,
                                     @RequestParam Boolean enabled) {
    userService.toggleUserStatus(userId, enabled);
    return Result.success(enabled ? "用户已启用" : "用户已禁用", null);
}
```

**修改后**:
```java
@RequireAdmin
@PutMapping("/admin/{userId}/status")
public Result<Void> toggleUserStatus(@RequestAttribute Long operatorId,
                                     @PathVariable Long userId,
                                     @RequestParam Boolean enabled) {
    log.info("管理员启用/禁用用户: operatorId={}, targetUserId={}, enabled={}", 
             operatorId, userId, enabled);
    // 验证能力：需要 toggle_user_status 能力
    abilityValidator.validateAbility(operatorId, "toggle_user_status");
    userService.toggleUserStatus(userId, enabled);
    return Result.success(enabled ? "用户已启用" : "用户已禁用", null);
}
```

#### 接口2: 重置用户密码

**修改前**:
```java
@RequireAdmin
@PutMapping("/admin/{targetUserId}/reset-password")
public Result<Void> resetPassword(@RequestAttribute Long userId,
                                  @PathVariable Long targetUserId,
                                  @Valid @RequestBody AdminResetPasswordRequest request) {
    log.info("管理员重置用户密码: operatorId={}, targetUserId={}", userId, targetUserId);
    userService.resetPassword(targetUserId, request.getNewPassword());
    return Result.success("密码已重置", null);
}
```

**修改后**:
```java
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
```

#### 接口3: 修改用户角色（支持两种能力）

**修改前**:
```java
// 验证能力：需要 manage_admins 能力
abilityValidator.validateAbility(userId, "manage_admins");
```

**修改后**:
```java
// 验证能力：需要 manage_admins 或 add_admin 能力
abilityValidator.validateAnyAbility(userId, "manage_admins", "add_admin");
```

**说明**: 这个接口现在接受两种能力中的任意一个，提供了更灵活的权限配置。

---

## 📊 完整能力列表

截至目前，系统共有 **12个能力**：

### 管理员管理 (3个)
1. `manage_admins` - 添加/删除管理员
2. `manage_admin_abilities` - 管理管理员能力分配
3. `add_admin` - 添加管理员 ⭐新增

### 用户管理 (2个) ⭐新增分类
4. `toggle_user_status` - 禁用/启用用户 ⭐新增
5. `reset_password` - 重置用户密码 ⭐新增

### 内容管理 (5个)
6. `edit_posts` - 编辑文章
7. `delete_posts` - 删除文章
8. `edit_comments` - 编辑评论
9. `delete_comments` - 删除评论
10. `edit_tags` - 编辑标签

### 系统管理 (2个)
11. `manage_system_config` - 管理系统配置
12. `view_audit_logs` - 查看审计日志

---

## 🧪 测试结果

### 测试1: 能力数量验证
```bash
✅ admin用户拥有12个能力
```

### 测试2: 新增能力存在性
```bash
✅ toggle_user_status - 存在
✅ reset_password - 存在
✅ add_admin - 存在
```

### 测试3: 功能测试
```bash
✅ 禁用用户接口 - 成功 (需要 toggle_user_status 能力)
```

---

## 📝 使用示例

### 为内容管理员分配新用户管理能力

```bash
curl -X POST http://localhost:8080/api/users/admin/{userId}/abilities \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '[
    {"name": "toggle_user_status", "description": "禁用/启用用户"},
    {"name": "reset_password", "description": "重置用户密码"}
  ]'
```

### 为人事管理员分配添加管理员能力

```bash
curl -X POST http://localhost:8080/api/users/admin/{userId}/abilities \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '[
    {"name": "add_admin", "description": "添加管理员"}
  ]'
```

---

## 🔐 安全特性

### 1. 细粒度权限控制

- ✅ 每个敏感操作都有独立的能力要求
- ✅ 可以精确控制每个管理员的权限范围
- ✅ 支持能力的组合配置

### 2. 最小权限原则

- ✅ 新管理员默认无任何能力
- ✅ 必须显式分配所需能力
- ✅ 超级管理员不受能力限制

### 3. 审计追踪

- ✅ 所有能力分配操作记录到审计日志
- ✅ 所有管理操作记录操作者ID
- ✅ 便于追溯和审计

---

## 📚 相关文档

- [能力系统完整指南](ABILITY_SYSTEM_GUIDE.md)
- [管理员能力批量管理与权限校验](../project_introduction/管理员能力批量管理与权限校验.md)

---

## 🎯 后续计划

### 可能的扩展方向

1. **更多用户管理能力**
   - `view_user_details` - 查看用户详细信息
   - `export_user_data` - 导出用户数据

2. **内容审核能力**
   - `review_posts` - 审核文章
   - `review_comments` - 审核评论

3. **系统维护能力**
   - `clear_cache` - 清除缓存
   - `backup_database` - 备份数据库

---

## 📞 技术支持

如有问题，请查看：
- 应用日志: 搜索 "能力验证" 或 "权限不足"
- 审计日志: `SELECT * FROM t_audit_log ORDER BY create_time DESC LIMIT 10;`
- 能力列表: `SELECT * FROM t_ability WHERE user_id = <userId>;`

---

**版本**: 1.1  
**更新日期**: 2026-05-13  
**更新人**: MyBlog Team  
**状态**: ✅ 已完成并测试通过
