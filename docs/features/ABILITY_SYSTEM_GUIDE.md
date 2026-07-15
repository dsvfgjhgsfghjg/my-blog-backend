# 管理员能力管理系统 - 完整指南

## 📋 概述

本系统采用**基于能力的访问控制（CBAC）**模型，实现了细粒度的权限管理。管理员必须拥有相应的能力才能执行特定的管理操作。

---

## 🎯 设计理念

### 最小权限原则
- ✅ 新管理员默认**没有任何能力**
- ✅ 必须显式分配所需能力
- ✅ 超级管理员拥有所有权限（无需能力）

### 三层权限验证
1. **必须是管理员** (`is_admin = true`)
2. **超级管理员直接通过** (`is_super_admin = true`)
3. **检查具体能力** (查询 `t_ability` 表)

---

## 🔑 能力列表

### 1. 管理员管理能力

| 能力名称 | 描述 | 适用接口 |
|---------|------|---------|
| `manage_admins` | 添加/删除管理员 | - 修改用户角色<br>- 设置超级管理员 |
| `manage_admin_abilities` | 管理管理员能力分配 | - 批量设置管理员能力 |
| `add_admin` | 添加管理员 | - 修改用户角色（与manage_admins二选一） |

### 2. 用户管理能力

| 能力名称 | 描述 | 适用接口 |
|---------|------|---------|
| `toggle_user_status` | 禁用/启用用户 | - 启用/禁用用户 |
| `reset_password` | 重置用户密码 | - 重置用户密码 |

### 3. 内容管理能力

| 能力名称 | 描述 | 适用接口 |
|---------|------|---------|
| `edit_posts` | 编辑文章 | - 更新文章（管理员） |
| `delete_posts` | 删除文章 | - 删除文章（管理员） |
| `edit_comments` | 编辑评论 | - 隐藏/显示评论 |
| `delete_comments` | 删除评论 | - 删除评论（管理员） |
| `edit_tags` | 编辑标签 | - 创建标签<br>- 更新标签<br>- 删除标签 |

### 4. 系统管理能力

| 能力名称 | 描述 | 适用接口 |
|---------|------|---------|
| `manage_system_config` | 管理系统配置 | - 获取系统配置<br>- 更新系统配置 |
| `view_audit_logs` | 查看审计日志 | - 查看审计日志 |

---

## 🛠️ 使用方法

### 1. 为新管理员分配能力

```bash
# 步骤1: 登录获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# 步骤2: 为目标用户分配能力
curl -s -X POST http://localhost:8080/api/users/admin/{targetUserId}/abilities \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "name": "edit_posts",
      "description": "编辑文章",
      "icon": "/icons/edit-posts.svg",
      "sortOrder": 1
    },
    {
      "name": "delete_posts",
      "description": "删除文章",
      "icon": "/icons/delete-posts.svg",
      "sortOrder": 2
    }
  ]'
```

### 2. 查询用户的能力

```bash
curl -s http://localhost:8080/api/users/admin/{userId}/abilities \
  -H "Authorization: Bearer $TOKEN" | jq '.'
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "2054570236258279429",
      "userId": "1",
      "name": "edit_posts",
      "description": "编辑文章",
      "icon": "/icons/edit-posts.svg",
      "sortOrder": 1,
      "createTime": "2026-05-13T22:31:31.048938"
    },
    {
      "id": "2054570236258279430",
      "userId": "1",
      "name": "delete_posts",
      "description": "删除文章",
      "icon": "/icons/delete-posts.svg",
      "sortOrder": 2,
      "createTime": "2026-05-13T22:31:31.048938"
    }
  ]
}
```

---

## 🔍 权限验证流程

### 代码示例

```java
// 在Controller中注入AbilityValidator
private final AbilityValidator abilityValidator;

// 在执行管理操作前验证能力
@RequireAdmin
@PutMapping("/admin/{targetUserId}/role")
public Result<Void> updateUserRole(@RequestAttribute Long userId,
                                   @PathVariable Long targetUserId,
                                   @RequestParam Boolean isAdmin) {
    // 验证能力：需要 manage_admins 能力
    abilityValidator.validateAbility(userId, "manage_admins");
    
    // 执行业务逻辑
    userService.updateUserRole(userId, targetUserId, isAdmin);
    return Result.success("已设置为管理员", null);
}
```

### 验证逻辑

```java
public void validateAbility(Long userId, String abilityName) {
    User user = userCacheService.getUserById(userId);
    
    // 1. 必须是管理员
    if (!Boolean.TRUE.equals(user.getIsAdmin())) {
        throw new BusinessException("权限不足，需要管理员权限");
    }
    
    // 2. 超级管理员直接通过
    if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
        return;
    }
    
    // 3. 检查是否有指定能力
    LambdaQueryWrapper<Ability> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Ability::getUserId, userId)
           .eq(Ability::getName, abilityName);
    Long count = abilityMapper.selectCount(wrapper);
    
    if (count == 0) {
        throw new BusinessException(
            String.format("权限不足，需要 [%s] 能力", getAbilityDescription(abilityName))
        );
    }
}
```

---

## 📊 已重构的接口

### UserController

| 接口 | 所需能力 | 说明 |
|------|---------|------|
| `PUT /api/users/admin/{targetUserId}/role` | `manage_admins` 或 `add_admin` | 修改用户角色（普通管理员权限） |
| `PUT /api/users/admin/{targetUserId}/super-admin` | `manage_admins` | 设置超级管理员（仅超级管理员） |
| `POST /api/users/admin/{targetUserId}/abilities` | `manage_admin_abilities` | 批量设置能力 |
| `PUT /api/users/admin/{targetUserId}/status` | `toggle_user_status` | 启用/禁用用户 |
| `PUT /api/users/admin/{targetUserId}/reset-password` | `reset_password` | 重置用户密码 |

### PostController

| 接口 | 所需能力 | 说明 |
|------|---------|------|
| `PUT /api/posts/{postId}` | `edit_posts` | 更新文章（管理员） |
| `DELETE /api/posts/{postId}` | `delete_posts` | 删除文章（管理员） |

**注意**: 文章作者始终可以编辑/删除自己的文章，无需能力验证。

### CommentController

| 接口 | 所需能力 | 说明 |
|------|---------|------|
| `DELETE /api/comments/{commentId}` | `delete_comments` | 删除评论（管理员） |
| `PUT /api/comments/{commentId}/status` | `edit_comments` | 隐藏/显示评论 |

**注意**: 评论作者始终可以删除自己的评论，无需能力验证。

### TagController

| 接口 | 所需能力 | 说明 |
|------|---------|------|
| `POST /api/tags` | `edit_tags` | 创建标签 |
| `PUT /api/tags/{tagId}` | `edit_tags` | 更新标签 |
| `DELETE /api/tags/{tagId}` | `edit_tags` | 删除标签 |

---

## ⚠️ 重要提示

### 1. 能力与角色的关系

```
用户 → 是否为管理员 (is_admin)
  ↓ 是
是否为超级管理员 (is_super_admin)
  ↓ 否
检查具体能力 (t_ability 表)
```

### 2. 超级管理员特权

- ✅ 拥有所有权限，无需能力验证
- ✅ 可以管理其他管理员的能力分配
- ⚠️ 谨慎授予，建议仅系统创始人拥有

### 3. 能力分配原则

**推荐的能力组合**:

#### 内容管理员
```json
[
  {"name": "edit_posts"},
  {"name": "delete_posts"},
  {"name": "edit_comments"},
  {"name": "delete_comments"},
  {"name": "edit_tags"}
]
```

#### 系统管理员
```json
[
  {"name": "manage_admins"},
  {"name": "manage_admin_abilities"},
  {"name": "manage_system_config"},
  {"name": "view_audit_logs"}
]
```

#### 全能管理员
```json
[
  {"name": "manage_admins"},
  {"name": "manage_admin_abilities"},
  {"name": "edit_posts"},
  {"name": "delete_posts"},
  {"name": "edit_comments"},
  {"name": "delete_comments"},
  {"name": "edit_tags"},
  {"name": "manage_system_config"},
  {"name": "view_audit_logs"}
]
```

---

## 🔧 数据库结构

### t_ability 表

```sql
CREATE TABLE t_ability (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(255),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, name)
);
```

### 索引

```sql
CREATE INDEX idx_ability_user_id ON t_ability(user_id);
CREATE INDEX idx_ability_name ON t_ability(name);
```

---

## 📝 错误处理

### 无能力时的错误响应

```json
{
  "code": 403,
  "message": "权限不足，需要 [编辑文章] 能力",
  "data": null
}
```

### 非管理员尝试访问

```json
{
  "code": 403,
  "message": "权限不足，需要管理员权限",
  "data": null
}
```

---

## 🎓 最佳实践

### 1. 初始化第一个管理员

```sql
-- 设置创始人为超级管理员
UPDATE t_user SET is_super_admin = TRUE WHERE username = 'admin';

-- 为创始人分配所有能力
INSERT INTO t_ability (id, user_id, name, description, icon, sort_order)
VALUES 
    (2054570236258279426, 1, 'manage_system_config', '管理系统配置', '/icons/settings.svg', 1),
    (2054570236258279427, 1, 'manage_admins', '添加/删除管理员', '/icons/manage-admins.svg', 10),
    -- ... 更多能力
ON CONFLICT (user_id, name) DO NOTHING;
```

### 2. 定期审计能力分配

```sql
-- 查看所有管理员的能力分配
SELECT 
    u.username,
    u.is_super_admin,
    a.name AS ability_name,
    a.description
FROM t_user u
LEFT JOIN t_ability a ON u.id = a.user_id
WHERE u.is_admin = TRUE
ORDER BY u.username, a.sort_order;
```

### 3. 记录能力变更日志

所有能力分配操作都会自动记录到 `t_audit_log` 表中，便于追踪和审计。

---

## 🚀 未来扩展

### 添加新能力的步骤

1. **在数据库中添加能力定义**
   ```sql
   INSERT INTO t_ability (id, user_id, name, description, icon, sort_order)
   VALUES (<id>, <user_id>, '<ability_name>', '<description>', '<icon>', <sort_order>);
   ```

2. **在 AbilityValidator 中添加描述**
   ```java
   private String getAbilityDescription(String abilityName) {
       return switch (abilityName) {
           case "new_ability" -> "新能力描述";
           // ...
       };
   }
   ```

3. **在相关接口中添加验证**
   ```java
   abilityValidator.validateAbility(userId, "new_ability");
   ```

---

## 📞 技术支持

如有问题，请查看：
- 审计日志: `t_audit_log` 表
- 应用日志: 搜索 "能力验证" 或 "权限不足"
- 能力列表: `SELECT * FROM t_ability WHERE user_id = <userId>`

---

**版本**: 1.0  
**最后更新**: 2026-05-13  
**维护者**: MyBlog Team
