# TOTP 前端集成指南

## 📱 Vue 3 示例

### 1. TOTP 设置页面组件

```vue
<template>
  <div class="totp-setup">
    <h2>启用双因素认证</h2>
    
    <!-- Step 1: 显示二维码 -->
    <div v-if="step === 1" class="qr-section">
      <p>使用 Microsoft Authenticator 扫描下方二维码：</p>
      <img :src="qrCodeImage" alt="TOTP QR Code" />
      
      <div class="manual-input">
        <p>或手动输入密钥：</p>
        <code>{{ totpSecret }}</code>
        <button @click="copySecret">复制</button>
      </div>
      
      <div class="backup-codes">
        <h3>⚠️ 重要：保存备份码</h3>
        <p>这些代码只能查看一次，请妥善保存！</p>
        <div class="codes-grid">
          <div v-for="code in backupCodes" :key="code" class="code-item">
            {{ code }}
          </div>
        </div>
        <button @click="downloadBackupCodes">下载备份码</button>
      </div>
      
      <button @click="goToStep2" class="btn-primary">下一步</button>
    </div>
    
    <!-- Step 2: 验证验证码 -->
    <div v-if="step === 2" class="verify-section">
      <p>请输入 Microsoft Authenticator 中显示的 6 位验证码：</p>
      <input 
        v-model="verificationCode" 
        type="text" 
        maxlength="6"
        placeholder="000000"
        class="totp-input"
      />
      <button @click="enableTotp" :disabled="loading" class="btn-primary">
        {{ loading ? '验证中...' : '启用' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import axios from 'axios';

const step = ref(1);
const totpSecret = ref('');
const qrCodeUrl = ref('');
const backupCodes = ref<string[]>([]);
const verificationCode = ref('');
const loading = ref(false);

// 生成二维码图片（使用 qrcode 库）
const qrCodeImage = computed(() => {
  return `https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(qrCodeUrl.value)}&size=200x200`;
});

// Step 1: 生成 TOTP 密钥
const generateTotp = async () => {
  try {
    const response = await axios.post('/api/auth/totp/generate', {}, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    
    if (response.data.code === 200) {
      totpSecret.value = response.data.data.secret;
      qrCodeUrl.value = response.data.data.qrCodeUrl;
      backupCodes.value = response.data.data.backupCodes;
    }
  } catch (error) {
    alert('生成失败，请重试');
  }
};

// 复制密钥
const copySecret = () => {
  navigator.clipboard.writeText(totpSecret.value);
  alert('密钥已复制');
};

// 下载备份码
const downloadBackupCodes = () => {
  const content = `MyBlog TOTP 备份码\n生成时间: ${new Date().toLocaleString()}\n\n${backupCodes.value.join('\n')}`;
  const blob = new Blob([content], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'totp-backup-codes.txt';
  a.click();
  URL.revokeObjectURL(url);
};

// 进入 Step 2
const goToStep2 = () => {
  step.value = 2;
};

// Step 2: 启用 TOTP
const enableTotp = async () => {
  if (verificationCode.value.length !== 6) {
    alert('请输入 6 位验证码');
    return;
  }
  
  loading.value = true;
  try {
    const response = await axios.post('/api/auth/totp/enable', {
      code: verificationCode.value
    }, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    
    if (response.data.code === 200) {
      alert('TOTP 启用成功！');
      // 跳转到安全设置页面
      window.location.href = '/settings/security';
    } else {
      alert('验证码错误，请重试');
    }
  } catch (error) {
    alert('验证失败，请重试');
  } finally {
    loading.value = false;
  }
};

// 组件加载时生成密钥
generateTotp();
</script>

<style scoped>
.totp-setup {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

.qr-section, .verify-section {
  text-align: center;
}

.qr-section img {
  margin: 20px 0;
  border: 1px solid #ddd;
  padding: 10px;
}

.manual-input {
  margin: 20px 0;
}

.manual-input code {
  display: block;
  background: #f5f5f5;
  padding: 10px;
  margin: 10px 0;
  font-size: 18px;
  letter-spacing: 2px;
}

.backup-codes {
  background: #fff3cd;
  border: 1px solid #ffc107;
  padding: 15px;
  margin: 20px 0;
  border-radius: 5px;
}

.codes-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin: 15px 0;
}

.code-item {
  background: white;
  padding: 8px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 16px;
}

.totp-input {
  font-size: 24px;
  letter-spacing: 8px;
  text-align: center;
  width: 200px;
  padding: 10px;
  margin: 20px 0;
}

.btn-primary {
  background: #007bff;
  color: white;
  border: none;
  padding: 10px 30px;
  font-size: 16px;
  border-radius: 5px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
```

---

### 2. 敏感操作前的 TOTP 验证

```vue
<template>
  <div class="sensitive-operation">
    <button @click="showTotpDialog = true" class="btn-danger">
      重置用户密码
    </button>
    
    <!-- TOTP 验证对话框 -->
    <div v-if="showTotpDialog" class="modal-overlay">
      <div class="modal">
        <h3>二次验证</h3>
        <p>请输入 Microsoft Authenticator 中的验证码：</p>
        
        <input 
          v-model="totpCode" 
          type="text" 
          maxlength="6"
          placeholder="000000"
          class="totp-input"
        />
        
        <div class="modal-actions">
          <button @click="showTotpDialog = false">取消</button>
          <button @click="verifyAndExecute" :disabled="verifying">
            {{ verifying ? '验证中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import axios from 'axios';

const showTotpDialog = ref(false);
const totpCode = ref('');
const verifying = ref(false);

// 验证 TOTP 并执行敏感操作
const verifyAndExecute = async () => {
  if (totpCode.value.length !== 6) {
    alert('请输入 6 位验证码');
    return;
  }
  
  verifying.value = true;
  
  try {
    // Step 1: 验证 TOTP
    const verifyResponse = await axios.post('/api/auth/totp/verify', {
      code: totpCode.value
    }, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    
    if (!verifyResponse.data.data) {
      alert('验证码错误');
      return;
    }
    
    // Step 2: 执行敏感操作（例如重置密码）
    const resetResponse = await axios.put('/api/users/admin/123/reset-password', {
      newPassword: 'NewPass@123'
    }, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    
    if (resetResponse.data.code === 200) {
      alert('密码重置成功');
      showTotpDialog.value = false;
    }
  } catch (error) {
    alert('操作失败');
  } finally {
    verifying.value = false;
  }
};
</script>
```

---

## ⚛️ React 示例

### TOTP 设置组件

```tsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

interface TotpData {
  qrCodeUrl: string;
  secret: string;
  backupCodes: string[];
}

const TotpSetup: React.FC = () => {
  const [step, setStep] = useState(1);
  const [totpData, setTotpData] = useState<TotpData | null>(null);
  const [verificationCode, setVerificationCode] = useState('');
  const [loading, setLoading] = useState(false);

  // 生成 TOTP 密钥
  useEffect(() => {
    const generateTotp = async () => {
      try {
        const response = await axios.post('/api/auth/totp/generate', {}, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        });
        
        if (response.data.code === 200) {
          setTotpData(response.data.data);
        }
      } catch (error) {
        console.error('生成 TOTP 失败', error);
      }
    };
    
    generateTotp();
  }, []);

  // 启用 TOTP
  const enableTotp = async () => {
    if (verificationCode.length !== 6) {
      alert('请输入 6 位验证码');
      return;
    }
    
    setLoading(true);
    try {
      const response = await axios.post('/api/auth/totp/enable', {
        code: verificationCode
      }, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      
      if (response.data.code === 200) {
        alert('TOTP 启用成功！');
        window.location.href = '/settings/security';
      } else {
        alert('验证码错误，请重试');
      }
    } catch (error) {
      alert('验证失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // 下载备份码
  const downloadBackupCodes = () => {
    if (!totpData) return;
    
    const content = `MyBlog TOTP 备份码\n生成时间: ${new Date().toLocaleString()}\n\n${totpData.backupCodes.join('\n')}`;
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'totp-backup-codes.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  if (!totpData) {
    return <div>加载中...</div>;
  }

  return (
    <div className="totp-setup">
      <h2>启用双因素认证</h2>
      
      {step === 1 && (
        <div className="qr-section">
          <p>使用 Microsoft Authenticator 扫描下方二维码：</p>
          <img 
            src={`https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(totpData.qrCodeUrl)}&size=200x200`}
            alt="TOTP QR Code" 
          />
          
          <div className="manual-input">
            <p>或手动输入密钥：</p>
            <code>{totpData.secret}</code>
            <button onClick={() => navigator.clipboard.writeText(totpData.secret)}>
              复制
            </button>
          </div>
          
          <div className="backup-codes">
            <h3>⚠️ 重要：保存备份码</h3>
            <div className="codes-grid">
              {totpData.backupCodes.map((code, index) => (
                <div key={index} className="code-item">{code}</div>
              ))}
            </div>
            <button onClick={downloadBackupCodes}>下载备份码</button>
          </div>
          
          <button onClick={() => setStep(2)} className="btn-primary">
            下一步
          </button>
        </div>
      )}
      
      {step === 2 && (
        <div className="verify-section">
          <p>请输入 Microsoft Authenticator 中显示的 6 位验证码：</p>
          <input 
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            type="text" 
            maxLength={6}
            placeholder="000000"
            className="totp-input"
          />
          <button onClick={enableTotp} disabled={loading} className="btn-primary">
            {loading ? '验证中...' : '启用'}
          </button>
        </div>
      )}
    </div>
  );
};

export default TotpSetup;
```

---

## 🔧 API 封装工具类

```typescript
// totp.ts
import axios from 'axios';

const API_BASE = '/api/auth/totp';

export interface TotpConfig {
  qrCodeUrl: string;
  secret: string;
  backupCodes: string[];
}

export interface TotpStatus {
  enabled: boolean;
  remainingBackupCodes: number;
}

/**
 * 生成 TOTP 密钥
 */
export const generateTotp = async (): Promise<TotpConfig> => {
  const response = await axios.post(`${API_BASE}/generate`, {}, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
  
  return response.data.data;
};

/**
 * 启用 TOTP
 */
export const enableTotp = async (code: string): Promise<void> => {
  const response = await axios.post(`${API_BASE}/enable`, { code }, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
};

/**
 * 禁用 TOTP
 */
export const disableTotp = async (password: string): Promise<void> => {
  const response = await axios.post(`${API_BASE}/disable`, { password }, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
};

/**
 * 查询 TOTP 状态
 */
export const getTotpStatus = async (): Promise<TotpStatus> => {
  const response = await axios.get(`${API_BASE}/status`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
  
  return response.data.data;
};

/**
 * 验证 TOTP 码
 */
export const verifyTotp = async (code: string): Promise<boolean> => {
  const response = await axios.post(`${API_BASE}/verify`, { code }, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
  
  return response.data.data;
};

export interface TotpRequiredInfo {
  needTotp: boolean;
  userHasTotp: boolean;
  sessionValid: boolean;
}

/**
 * 预检查是否需要 TOTP 验证（避免不必要的弹窗）
 * @param moduleKey 可选，模块配置键
 */
export const checkTotpRequired = async (moduleKey?: string): Promise<TotpRequiredInfo> => {
  const params = moduleKey ? { moduleKey } : {};
  const response = await axios.get(`${API_BASE}/check-required`, {
    params,
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message);
  }
  
  return response.data.data;
};
```

---

## 🎨 UI 设计建议

### 1. 二维码展示

- 使用清晰的二维码图片（至少 200x200px）
- 提供手动输入密钥的选项
- 添加"复制密钥"按钮

### 2. 备份码展示

- 使用醒目的警告色（黄色/橙色）
- 网格布局展示（2 列或 5 列）
- 提供下载为文本文件的功能
- 强调"只能查看一次"

### 3. 验证码输入

- 大字体、等宽字体（monospace）
- 自动聚焦输入框
- 支持自动提交（输入 6 位后自动触发）
- 显示倒计时（30 秒刷新）

### 4. 错误提示

- 验证码错误：红色提示 + 重新输入
- 网络错误：友好提示 + 重试按钮
- 超时：提示用户等待下一个验证码

---

## ✅ 测试清单

- [ ] 扫描二维码成功绑定
- [ ] 手动输入密钥成功绑定
- [ ] 验证码验证成功
- [ ] 验证码错误提示
- [ ] 备份码下载功能
- [ ] 备份码验证功能
- [ ] 禁用 TOTP（需要密码）
- [ ] 敏感操作前 TOTP 验证
- [ ] 响应式设计（移动端适配）

---

**最后更新**: 2026-05-17
