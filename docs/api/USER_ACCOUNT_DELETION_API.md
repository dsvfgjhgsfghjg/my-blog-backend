# 用户账户注销 API 文档

## 📋 概述

本文档说明如何调用账户注销接口，实现用户账户的软删除。注销后，账户将被禁用，所有个人信息将被清理或匿名化。

**实施日期**: 2026-05-17  
**版本**: v1.0

---

## 🔐 API 端点

### 注销当前用户账户

```
DELETE /api/users/me/account
```

**权限要求**: 
- 需要登录（JWT Token）
- **需要 TOTP 双因素认证**（`X-TOTP-Code` 请求头）

---

## 📤 请求示例

### cURL

```bash
curl -X DELETE "http://localhost:8080/api/users/me/account" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-TOTP-Code: 123456"
```

### JavaScript (Fetch)

```javascript
async function deleteAccount(totpCode) {
  const response = await fetch('/api/users/me/account', {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`,
      'X-TOTP-Code': totpCode  // ← 必需：TOTP 验证码
    }
  });
  
  const result = await response.json();
  
  if (result.code === 200) {
    alert('账户已注销');
    // 清除本地存储并跳转到首页
    localStorage.clear();
    window.location.href = '/';
  } else {
    alert('注销失败: ' + result.message);
  }
}

// 使用
deleteAccount('123456');
```

### Axios

```javascript
import axios from 'axios';

async function deleteAccount(totpCode) {
  try {
    const response = await axios.delete('/api/users/me/account', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'X-TOTP-Code': totpCode
      }
    });
    
    if (response.data.code === 200) {
      alert('账户已注销');
      localStorage.clear();
      window.location.href = '/';
    }
  } catch (error) {
    console.error('注销失败:', error.response?.data?.message);
  }
}
```

---

## 📥 响应格式

### 成功响应（200）

```json
{
  "code": 200,
  "message": "账户已注销",
  "data": null
}
```

### 错误响应

#### 1. 未提供 TOTP 验证码（401）

```json
{
  "code": 401,
  "message": "此操作需要双因素认证，请提供 TOTP 验证码",
  "data": null
}
```

#### 2. TOTP 验证码错误（401）

```json
{
  "code": 401,
  "message": "TOTP 验证码错误或已过期",
  "data": null
}
```

#### 3. 用户不存在（404）

```json
{
  "code": 404,
  "message": "用户不存在",
  "data": null
}
```

---

## ⚙️ 注销操作流程

当用户调用此接口时，系统会执行以下操作：

### 1. 用户名修改

```
原用户名: john_doe
新用户名: 账户已注销_john_doe_1234567890
```

**格式**: `账户已注销_{原用户名}_{用户ID}`

**目的**: 
- 保留历史记录的可追溯性
- 防止用户名被重新注册
- 明确标识该账户已注销

---

### 2. 昵称修改

```
原昵称: John Doe
新昵称: 已注销用户
```

**目的**: 在评论、文章等场景中显示为匿名用户

---

### 3. 头像重置

```
原头像: avatars/123/photo.jpg
新头像: "" (空字符串)
```

**目的**: 恢复为默认头像

---

### 4. 账户禁用

```
enabled: true → false
```

**效果**: 
- 用户无法再登录
- 登录接口返回："账号已被禁用，请联系管理员"

---

### 5. 密码清除

```
原密码: bcrypt_hashed_password
新密码: bcrypt_hashed_random_value
```

**目的**: 即使账户被意外启用，也无法使用旧密码登录

---

### 6. 隐私数据清空

```
email: user@example.com → null
phone: 13800138000 → null
```

**目的**: 保护用户隐私，符合 GDPR 等法规要求

---

### 7. 删除用户偏好设置

**删除内容**:
- 粒子特效设置（`t_user_preferences` 表）

**影响**: 用户的所有个性化设置被清除

---

### 8. 删除用户能力（如果是管理员）

**删除内容**:
- 所有管理员能力（`t_ability` 表）

**影响**: 
- 如果用户是管理员，将失去所有管理能力
- 防止注销的管理员账户仍具有管理权限

---

### 9. 清除 TOTP 会话

**删除内容**:
- Redis 中的 TOTP 会话（`totp:session:{userId}`）

**影响**: 立即失效任何活跃的 TOTP 会话

---

### 10. 清除用户缓存

**删除内容**:
- Redis 中的用户缓存（`user:cache:{userId}`）

**影响**: 确保后续查询获取最新数据

---

## 🎯 前端集成指南

### 1. 确认对话框

在执行注销操作前，必须显示确认对话框：

```javascript
function confirmDeleteAccount() {
  return new Promise((resolve) => {
    const confirmed = window.confirm(
      '⚠️ 警告：此操作不可逆！\n\n' +
      '注销后您将无法再登录账户，所有数据将被匿名化。\n\n' +
      '确定要注销账户吗？'
    );
    resolve(confirmed);
  });
}
```

---

### 2. TOTP 输入流程

```javascript
async function handleDeleteAccount() {
  // 步骤 1: 显示确认对话框
  const confirmed = await confirmDeleteAccount();
  if (!confirmed) return;
  
  // 步骤 2: 提示用户输入 TOTP 验证码
  const totpCode = prompt(
    '请输入 TOTP 验证码（6位数字）：\n\n' +
    '打开 Microsoft Authenticator 应用获取验证码'
  );
  
  if (!totpCode || totpCode.length !== 6) {
    alert('无效的验证码');
    return;
  }
  
  // 步骤 3: 调用注销接口
  try {
    const response = await fetch('/api/users/me/account', {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'X-TOTP-Code': totpCode
      }
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      alert('账户已成功注销');
      
      // 清除本地数据
      localStorage.clear();
      sessionStorage.clear();
      
      // 跳转到首页
      window.location.href = '/';
    } else {
      alert('注销失败: ' + result.message);
    }
  } catch (error) {
    console.error('注销失败:', error);
    alert('网络错误，请稍后重试');
  }
}
```

---

### 3. React 组件示例

```jsx
import React, { useState } from 'react';

function DeleteAccountButton() {
  const [loading, setLoading] = useState(false);
  
  const handleDelete = async () => {
    // 确认对话框
    if (!window.confirm('⚠️ 此操作不可逆！确定要注销账户吗？')) {
      return;
    }
    
    // 获取 TOTP 验证码
    const totpCode = prompt('请输入 TOTP 验证码（6位数字）：');
    if (!totpCode) return;
    
    setLoading(true);
    
    try {
      const response = await fetch('/api/users/me/account', {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'X-TOTP-Code': totpCode
        }
      });
      
      const result = await response.json();
      
      if (result.code === 200) {
        alert('账户已注销');
        localStorage.clear();
        window.location.href = '/';
      } else {
        alert('注销失败: ' + result.message);
      }
    } catch (error) {
      alert('网络错误');
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <button 
      onClick={handleDelete}
      disabled={loading}
      className="btn-danger"
    >
      {loading ? '处理中...' : '注销账户'}
    </button>
  );
}

export default DeleteAccountButton;
```

---

### 4. Vue 组件示例

```vue
<template>
  <el-button 
    type="danger" 
    @click="handleDelete"
    :loading="loading"
  >
    注销账户
  </el-button>
</template>

<script>
export default {
  data() {
    return {
      loading: false
    };
  },
  methods: {
    async handleDelete() {
      // 确认对话框
      const confirmed = await this.$confirm(
        '⚠️ 此操作不可逆！注销后您将无法再登录账户，所有数据将被匿名化。确定要继续吗？',
        '警告',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).catch(() => false);
      
      if (!confirmed) return;
      
      // 获取 TOTP 验证码
      const { value: totpCode } = await this.$prompt(
        '请输入 TOTP 验证码（6位数字）',
        '双因素认证',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPattern: /^\d{6}$/,
          inputErrorMessage: '请输入6位数字'
        }
      ).catch(() => ({ value: null }));
      
      if (!totpCode) return;
      
      this.loading = true;
      
      try {
        const response = await this.$axios.delete('/api/users/me/account', {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'X-TOTP-Code': totpCode
          }
        });
        
        if (response.data.code === 200) {
          this.$message.success('账户已注销');
          localStorage.clear();
          window.location.href = '/';
        } else {
          this.$message.error('注销失败: ' + response.data.message);
        }
      } catch (error) {
        this.$message.error('网络错误，请稍后重试');
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
```

---

## ⚠️ 注意事项

### 1. 不可逆操作

**重要**: 账户注销是**不可逆**的操作！

- ❌ 无法恢复原用户名
- ❌ 无法恢复原密码
- ❌ 无法恢复个人数据

**建议**: 在执行注销前，提醒用户备份重要数据。

---

### 2. TOTP 必需

此接口**必须**携带 TOTP 验证码，否则返回 401 错误。

**原因**: 防止误操作和恶意攻击

---

### 3. 会话失效

注销后，用户的 JWT Token 仍然有效（直到自然过期），但：
- 无法再登录（enabled = false）
- 无法访问需要认证的接口（会被拦截）

**建议**: 前端在注销成功后立即清除本地 Token。

---

### 4. 关联数据处理

**当前实现**:
- ✅ 删除用户偏好设置
- ✅ 删除用户能力
- ✅ 清除 TOTP 会话

**未处理**（可能需要后续扩展）:
- ⚠️ 用户发布的文章（保留，作者显示为"已注销用户"）
- ⚠️ 用户的评论（保留，作者显示为"已注销用户"）
- ⚠️ 用户的头像文件（保留在 MinIO，但不影响存储成本）

---

## 🔗 相关文档

- [TOTP 双因素认证快速参考](./TOTP_QUICK_REFERENCE.md)
- [TOTP 敏感操作保护 API](./TOTP_SENSITIVE_OPERATIONS_API.md)
- [用户管理 API](./API_USER.md)

---

**最后更新**: 2026-05-17  
**维护者**: Leo
