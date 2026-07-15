# 前端 API 集成文档汇总

**最后更新**: 2026-05-17  
**版本**: v1.0

---

## 📚 文档目录

本文档汇总了前端需要集成的所有重要 API 接口文档。

### 1️⃣ 用户账户注销
- **文档**: [USER_ACCOUNT_DELETION_API.md](./USER_ACCOUNT_DELETION_API.md)
- **功能**: 用户自助注销账户（软删除）
- **权限**: 需要登录 + TOTP 双因素认证

### 2️⃣ 系统配置管理
- **文档**: [SYSTEM_CONFIG_API.md](./SYSTEM_CONFIG_API.md)
- **快速参考**: [SYSTEM_CONFIG_QUICK_REFERENCE.md](./SYSTEM_CONFIG_QUICK_REFERENCE.md)
- **功能**: 管理员管理系统各项配置
- **权限**: 需要 `manage_system_config` 能力或超级管理员

### 3️⃣ TOTP 双因素认证
- **文档**: [TOTP_SENSITIVE_OPERATIONS_API.md](./TOTP_SENSITIVE_OPERATIONS_API.md)
- **快速参考**: [TOTP_QUICK_REFERENCE.md](./TOTP_QUICK_REFERENCE.md)
- **功能**: 为敏感操作提供双因素认证保护
- **适用**: 修改密码、删除文章、系统设置等 21 个敏感接口

---

## 🔑 核心 API 速查

### 用户账户注销

```http
DELETE /api/users/me/account
Authorization: Bearer {token}
X-TOTP-Code: {6位验证码}
```

**响应**:
```json
{
  "code": 200,
  "message": "账户已注销",
  "data": null
}
```

**前端流程**:
1. 显示确认对话框（不可逆操作警告）
2. 提示用户输入 TOTP 验证码
3. 调用接口
4. 成功后清除本地存储并跳转首页

---

### 系统配置管理

#### 获取所有配置
```http
GET /api/system/config
Authorization: Bearer {admin_token}
```

#### 批量更新配置
```http
PUT /api/system/config/batch
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "site.name": "Leo的博客",
  "site.description": "分享技术与生活"
}
```

#### 获取公开配置（无需登录）
```http
GET /api/system/config/public
```

---

### TOTP 验证机制

**5分钟会话机制**:
- 首次验证成功后，5分钟内无需再次输入 TOTP
- Redis 存储会话状态
- 自动刷新会话有效期

**请求头**:
```http
X-TOTP-Code: 123456
```

**错误处理**:
```json
// 未提供 TOTP
{
  "code": 401,
  "message": "此操作需要双因素认证，请提供 TOTP 验证码"
}

// TOTP 错误
{
  "code": 401,
  "message": "TOTP 验证码错误或已过期"
}
```

---

## 🛡️ 受 TOTP 保护的接口列表

以下 21 个接口需要 TOTP 双因素认证：

### 用户管理（6个）
- ✅ `PUT /api/users/password` - 修改密码
- ✅ `PUT /api/users/admin/{userId}/status` - 停用/启用账户
- ✅ `PUT /api/users/admin/{userId}/reset-password` - 重置密码
- ✅ `PUT /api/users/admin/{userId}/role` - 设置管理员
- ✅ `PUT /api/users/admin/{userId}/super-admin` - 设置超级管理员
- ✅ `POST /api/users/admin/{userId}/abilities` - 设置管理员能力
- ✅ `DELETE /api/users/me/account` - **注销账户**（新增）

### 文章管理（1个）
- ✅ `DELETE /api/posts/{postId}` - 删除文章

### 标签管理（3个）
- ✅ `POST /api/tags` - 创建标签
- ✅ `PUT /api/tags/{tagId}` - 更新标签
- ✅ `DELETE /api/tags/{tagId}` - 删除标签

### 文章审核（2个）
- ✅ `GET /api/post-audit/pending` - 查看待审核文章
- ✅ `POST /api/post-audit/{postId}` - 文章审核

### 审计日志（3个）
- ✅ `GET /api/audit-logs` - 查看审计日志
- ✅ `GET /api/audit-logs/{id}` - 查看日志详情
- ✅ `GET /api/audit-logs/stats` - 查看统计信息

### 系统设置（6个）
- ✅ `GET /api/system/config` - 查看所有配置
- ✅ `GET /api/system/config/category/{category}` - 按分类查看
- ✅ `GET /api/system/config/{configKey}` - 查看单个配置
- ✅ `PUT /api/system/config` - 更新配置
- ✅ `PUT /api/system/config/batch` - 批量更新配置
- ✅ `POST /api/system/config/{configKey}/reset` - 重置配置

---

## 💻 前端集成示例

### Axios 拦截器（推荐）

```javascript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000
});

// 请求拦截器
apiClient.interceptors.request.use(async (config) => {
  // 添加 JWT Token
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  
  // 检查是否需要 TOTP
  const requiresTotp = config.headers['X-Require-TOTP'];
  if (requiresTotp) {
    // 从 sessionStorage 获取缓存的 TOTP 会话
    const totpSession = sessionStorage.getItem('totp_session');
    const sessionExpiry = sessionStorage.getItem('totp_session_expiry');
    
    // 如果会话有效且未过期（5分钟）
    if (totpSession && sessionExpiry && Date.now() < parseInt(sessionExpiry)) {
      console.log('使用缓存的 TOTP 会话');
      return config;
    }
    
    // 否则提示用户输入 TOTP
    const totpCode = await promptForTotp();
    if (totpCode) {
      config.headers['X-TOTP-Code'] = totpCode;
    } else {
      throw new Error('用户取消了操作');
    }
  }
  
  return config;
});

// 响应拦截器
apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      const message = error.response.data?.message;
      
      if (message?.includes('双因素认证')) {
        // TOTP 验证失败，重新提示输入
        return retryWithTotp(error.config);
      }
      
      if (message?.includes('Token') || message?.includes('登录')) {
        // Token 失效，跳转登录页
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    
    return Promise.reject(error);
  }
);

// 提示用户输入 TOTP
async function promptForTotp() {
  return new Promise((resolve) => {
    const code = prompt('请输入 TOTP 验证码（6位数字）：\n\n打开 Microsoft Authenticator 应用获取验证码');
    resolve(code);
  });
}

// 重试带 TOTP 的请求
async function retryWithTotp(config) {
  const totpCode = await promptForTotp();
  if (totpCode) {
    config.headers['X-TOTP-Code'] = totpCode;
    
    // 保存会话（5分钟）
    sessionStorage.setItem('totp_session', 'valid');
    sessionStorage.setItem('totp_session_expiry', Date.now() + 5 * 60 * 1000);
    
    return apiClient(config);
  }
  throw new Error('用户取消了操作');
}

export default apiClient;
```

---

### 用户注销组件（Vue 示例）

```vue
<template>
  <el-button 
    type="danger" 
    @click="handleDeleteAccount"
    :loading="loading"
  >
    注销账户
  </el-button>
</template>

<script>
import apiClient from '@/utils/api';

export default {
  data() {
    return {
      loading: false
    };
  },
  methods: {
    async handleDeleteAccount() {
      // 步骤 1: 确认对话框
      const confirmed = await this.$confirm(
        '⚠️ 警告：此操作不可逆！\n\n' +
        '注销后您将无法再登录账户，所有数据将被匿名化。\n\n' +
        '确定要注销账户吗？',
        '警告',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).catch(() => false);
      
      if (!confirmed) return;
      
      // 步骤 2: 获取 TOTP 验证码
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
      
      // 步骤 3: 调用接口
      this.loading = true;
      
      try {
        const response = await apiClient.delete('/users/me/account', {
          headers: {
            'X-TOTP-Code': totpCode
          }
        });
        
        if (response.data.code === 200) {
          this.$message.success('账户已注销');
          
          // 清除本地数据
          localStorage.clear();
          sessionStorage.clear();
          
          // 跳转到首页
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

### 系统设置页面（React 示例）

```jsx
import React, { useState, useEffect } from 'react';
import apiClient from '@/utils/api';

function SystemSettings() {
  const [configs, setConfigs] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  
  // 加载配置
  useEffect(() => {
    loadConfigs();
  }, []);
  
  const loadConfigs = async () => {
    setLoading(true);
    try {
      const response = await apiClient.get('/system/config');
      setConfigs(response.data.data);
    } catch (error) {
      console.error('加载配置失败:', error);
    } finally {
      setLoading(false);
    }
  };
  
  // 保存配置
  const saveConfigs = async () => {
    setSaving(true);
    try {
      // 提取所有配置项
      const configData = {};
      Object.values(configs).forEach(category => {
        category.forEach(config => {
          configData[config.configKey] = config.configValue;
        });
      });
      
      const response = await apiClient.put('/system/config/batch', configData);
      
      if (response.data.code === 200) {
        message.success('配置保存成功');
        loadConfigs(); // 重新加载
      }
    } catch (error) {
      message.error('保存失败: ' + error.response?.data?.message);
    } finally {
      setSaving(false);
    }
  };
  
  // 更新配置值
  const updateConfigValue = (category, index, value) => {
    setConfigs(prev => {
      const newConfigs = { ...prev };
      newConfigs[category][index].configValue = value;
      return newConfigs;
    });
  };
  
  if (loading) return <div>加载中...</div>;
  
  return (
    <div className="system-settings">
      <h2>系统设置</h2>
      
      {Object.entries(configs).map(([category, items]) => (
        <div key={category} className="config-category">
          <h3>{getCategoryName(category)}</h3>
          
          {items.map((config, index) => (
            <div key={config.configKey} className="config-item">
              <label>{config.description}</label>
              
              {config.configType === 'BOOLEAN' ? (
                <select
                  value={config.configValue}
                  onChange={(e) => updateConfigValue(category, index, e.target.value)}
                >
                  <option value="true">是</option>
                  <option value="false">否</option>
                </select>
              ) : config.configType === 'NUMBER' ? (
                <input
                  type="number"
                  value={config.configValue}
                  onChange={(e) => updateConfigValue(category, index, e.target.value)}
                />
              ) : (
                <input
                  type="text"
                  value={config.configValue}
                  onChange={(e) => updateConfigValue(category, index, e.target.value)}
                />
              )}
            </div>
          ))}
        </div>
      ))}
      
      <button onClick={saveConfigs} disabled={saving}>
        {saving ? '保存中...' : '保存配置'}
      </button>
    </div>
  );
}

function getCategoryName(category) {
  const names = {
    BASIC: '基础配置',
    SECURITY: '安全配置',
    EMAIL: '邮件配置',
    STORAGE: '存储配置',
    POST: '文章配置',
    COMMENT: '评论配置'
  };
  return names[category] || category;
}

export default SystemSettings;
```

---

## ⚠️ 注意事项

### 1. TOTP 会话管理

- ✅ 验证成功后，5 分钟内无需再次输入
- ✅ 使用 `sessionStorage` 存储会话状态
- ✅ 页面刷新后会话仍然有效（直到 5 分钟过期）
- ❌ 不要将 TOTP 码存储在 `localStorage`（不安全）

### 2. 错误处理

```javascript
// 统一的错误处理
try {
  await apiClient.delete('/users/me/account', {
    headers: { 'X-TOTP-Code': totpCode }
  });
} catch (error) {
  if (error.response?.status === 401) {
    // TOTP 验证失败
    alert('TOTP 验证码错误，请重试');
  } else if (error.response?.status === 403) {
    // 权限不足
    alert('您没有权限执行此操作');
  } else {
    // 其他错误
    alert('操作失败，请稍后重试');
  }
}
```

### 3. 权限检查

在路由守卫中检查权限：

```javascript
// Vue Router 守卫
router.beforeEach((to, from, next) => {
  if (to.path === '/system/settings') {
    const hasPermission = store.state.user.abilities.some(
      a => a.name === 'manage_system_config'
    );
    
    if (!hasPermission) {
      next('/403');
    } else {
      next();
    }
  } else {
    next();
  }
});
```

### 4. 菜单显示控制

```vue
<!-- 只显示有权限的菜单 -->
<el-menu-item 
  v-if="hasAbility('manage_system_config')"
  index="/system/settings">
  <i class="el-icon-setting"></i>
  <span>系统设置</span>
</el-menu-item>
```

---

## 📖 详细文档链接

### 必读文档
1. [用户账户注销 API](./USER_ACCOUNT_DELETION_API.md) - 完整的注销流程和示例
2. [系统配置管理 API](./SYSTEM_CONFIG_API.md) - 详细的配置管理接口
3. [TOTP 敏感操作保护](./TOTP_SENSITIVE_OPERATIONS_API.md) - 双因素认证集成指南

### 快速参考
1. [系统配置快速参考](./SYSTEM_CONFIG_QUICK_REFERENCE.md) - 配置项清单
2. [TOTP 快速参考](./TOTP_QUICK_REFERENCE.md) - 核心代码片段

---

## 🆘 常见问题

### Q1: 如何判断哪些接口需要 TOTP？

**A**: 查看 `TOTP_SENSITIVE_OPERATIONS_API.md` 中的完整列表，或者看后端是否返回 401 错误提示需要双因素认证。

### Q2: TOTP 会话过期后怎么办？

**A**: 重新提示用户输入 TOTP 验证码，验证成功后会自动创建新的 5 分钟会话。

### Q3: 系统配置的权限如何检查？

**A**: 检查用户的 `abilities` 数组中是否包含 `manage_system_config`，或者用户是否为超级管理员。

### Q4: 注销账户后能否恢复？

**A**: ❌ 不能。这是不可逆操作，务必在 UI 上明确提示用户。

### Q5: 公开配置在哪里获取？

**A**: 调用 `GET /api/system/config/public`，无需登录即可获取。

---

## 📞 技术支持

如有问题，请联系后端开发团队或查看详细 API 文档。

**文档维护者**: Leo  
**最后更新**: 2026-05-17
