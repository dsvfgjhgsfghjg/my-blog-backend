# 用户会话管理 API - 前端开发指南

## 📋 概述

本文档提供用户会话管理功能的完整前端开发指南，包括API接口说明、请求/响应格式、错误处理以及前端集成示例。

**功能特性**:
- ✅ 查看当前用户所有活跃的登录设备
- ✅ 显示每个设备的IP地址、设备类型、操作系统、浏览器等信息
- ✅ Android设备细化识别（如"小米手机 微信"）
- ✅ 下线指定设备（需要TOTP双因素认证）
- ✅ 下线所有其他设备（需要TOTP双因素认证）

---

## 🔑 前置条件

### 1. 用户必须已登录
所有会话管理接口都需要在请求头中携带JWT Token：
```http
Authorization: Bearer {your_jwt_token}
```

### 2. TOTP双因素认证
下线操作需要提供TOTP验证码或备份码。如果用户未启用TOTP，需要先完成TOTP设置流程。

---

## 📡 API接口列表

### 1. 获取活跃会话列表

**接口**: `GET /api/users/sessions`

**描述**: 获取当前用户所有活跃的登录会话

**请求参数**: 无

**请求头**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "sessionId": "-1234567890",
      "ipAddress": "192.168.1.100",
      "deviceType": "MOBILE",
      "osName": "Android",
      "browserName": "WeChat",
      "deviceInfo": "小米 MI 9 - 微信 (小米手机 微信)",
      "loginTime": "2026-05-18T10:30:00",
      "expireTime": "2026-05-19T10:30:00",
      "isCurrent": true
    },
    {
      "sessionId": "987654321",
      "ipAddress": "10.0.0.50",
      "deviceType": "DESKTOP",
      "osName": "Windows",
      "browserName": "Chrome",
      "deviceInfo": "Windows Desktop - Chrome",
      "loginTime": "2026-05-17T08:15:00",
      "expireTime": "2026-05-18T08:15:00",
      "isCurrent": false
    }
  ]
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID（Token Hash），用于下线操作 |
| ipAddress | String | 登录时的IP地址 |
| deviceType | String | 设备类型：DESKTOP/MOBILE/TABLET |
| osName | String | 操作系统名称 |
| browserName | String | 浏览器/客户端名称 |
| deviceInfo | String | 设备详细描述（Android设备包含细化备注） |
| loginTime | DateTime | 登录时间（ISO 8601格式） |
| expireTime | DateTime | Token过期时间（ISO 8601格式） |
| isCurrent | Boolean | 是否为当前使用的会话 |

**前端展示建议**:
```jsx
// React示例
const SessionList = ({ sessions }) => {
  return (
    <div className="session-list">
      {sessions.map(session => (
        <div key={session.sessionId} className={`session-item ${session.isCurrent ? 'current' : ''}`}>
          <div className="session-header">
            <span className="device-icon">
              {session.deviceType === 'MOBILE' ? '📱' : 
               session.deviceType === 'TABLET' ? '📟' : '💻'}
            </span>
            <span className="device-info">{session.deviceInfo}</span>
            {session.isCurrent && <span className="current-badge">当前设备</span>}
          </div>
          
          <div className="session-details">
            <div>IP: {session.ipAddress}</div>
            <div>系统: {session.osName}</div>
            <div>浏览器: {session.browserName}</div>
            <div>登录时间: {formatDateTime(session.loginTime)}</div>
            <div>过期时间: {formatDateTime(session.expireTime)}</div>
          </div>
          
          {!session.isCurrent && (
            <button 
              onClick={() => handleRevokeSession(session.sessionId)}
              className="revoke-btn"
            >
              下线此设备
            </button>
          )}
        </div>
      ))}
    </div>
  );
};
```

---

### 2. 下线指定会话

**接口**: `POST /api/users/sessions/{sessionId}/revoke`

**描述**: 下线指定的登录会话（需要TOTP验证）

**路径参数**:
- `sessionId`: 要下线的会话ID

**请求头**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**请求体**:
```json
{
  "totpCode": "123456"
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| totpCode | String | 是 | 6位TOTP动态验证码 或 8位备份码 |

**成功响应**:
```json
{
  "code": 200,
  "message": "会话已下线",
  "data": null
}
```

**失败响应**:
```json
{
  "code": 400,
  "message": "TOTP验证码错误或已过期",
  "data": null
}
```

**前端交互流程**:
```jsx
// React示例
const RevokeSessionDialog = ({ sessionId, onClose, onSuccess }) => {
  const [totpCode, setTotpCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (!totpCode || totpCode.length !== 6) {
      setError('请输入6位验证码');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`/api/users/sessions/${sessionId}/revoke`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ totpCode })
      });

      const result = await response.json();

      if (result.code === 200) {
        message.success('会话已下线');
        onSuccess();
        onClose();
      } else {
        setError(result.message);
      }
    } catch (err) {
      setError('网络错误，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title="确认下线设备" visible={true} onCancel={onClose}>
      <div className="revoke-dialog">
        <p>⚠️ 此操作需要双因素认证</p>
        <p>请输入您的TOTP验证码或备份码：</p>
        
        <Input
          placeholder="输入6位验证码"
          value={totpCode}
          onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          maxLength={6}
          disabled={loading}
        />
        
        {error && <div className="error-message">{error}</div>}
        
        <Button 
          type="danger" 
          loading={loading}
          onClick={handleSubmit}
          style={{ marginTop: 16 }}
        >
          确认下线
        </Button>
      </div>
    </Modal>
  );
};
```

---

### 3. 下线所有其他会话

**接口**: `POST /api/users/sessions/revoke-others`

**描述**: 下线除当前设备外的所有其他会话（需要TOTP验证）

**请求头**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**请求体**:
```json
{
  "totpCode": "123456"
}
```

**成功响应**:
```json
{
  "code": 200,
  "message": "已下线 3 个其他会话",
  "data": {
    "revokedCount": 3
  }
}
```

**失败响应**:
```json
{
  "code": 400,
  "message": "TOTP验证码错误或已过期",
  "data": null
}
```

**前端交互示例**:
```jsx
const RevokeAllOthersButton = ({ onRefresh }) => {
  const [visible, setVisible] = useState(false);
  const [totpCode, setTotpCode] = useState('');
  const [loading, setLoading] = useState(false);

  const handleConfirm = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/users/sessions/revoke-others', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ totpCode })
      });

      const result = await response.json();

      if (result.code === 200) {
        message.success(result.message);
        setVisible(false);
        onRefresh();
      } else {
        message.error(result.message);
      }
    } catch (err) {
      message.error('网络错误');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button onClick={() => setVisible(true)}>
        下线所有其他设备
      </Button>
      
      <Modal
        title="⚠️ 危险操作"
        visible={visible}
        onCancel={() => setVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setVisible(false)}>取消</Button>,
          <Button 
            key="confirm" 
            type="danger"
            loading={loading}
            onClick={handleConfirm}
          >
            确认下线
          </Button>
        ]}
      >
        <Alert
          message="此操作将下线除当前设备外的所有其他登录设备"
          type="warning"
          showIcon
        />
        <div style={{ marginTop: 16 }}>
          <p>请输入TOTP验证码确认操作：</p>
          <Input
            placeholder="6位验证码"
            value={totpCode}
            onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            maxLength={6}
          />
        </div>
      </Modal>
    </>
  );
};
```

---

## 🎨 UI/UX设计建议

### 1. 会话列表页面布局

```
┌─────────────────────────────────────────────┐
│  登录设备管理                    [刷新]       │
├─────────────────────────────────────────────┤
│                                             │
│  📱 小米 MI 9 - 微信                [当前]   │
│     IP: 192.168.1.100                       │
│     系统: Android                           │
│     浏览器: WeChat                          │
│     登录时间: 2026-05-18 10:30              │
│     过期时间: 2026-05-19 10:30              │
│                                             │
├─────────────────────────────────────────────┤
│                                             │
│  💻 Windows Desktop - Chrome                │
│     IP: 10.0.0.50                           │
│     系统: Windows                           │
│     浏览器: Chrome                          │
│     登录时间: 2026-05-17 08:15              │
│     过期时间: 2026-05-18 08:15              │
│                               [下线此设备]   │
│                                             │
├─────────────────────────────────────────────┤
│                                             │
│  📱 iPhone 12 - Safari                      │
│     IP: 172.16.0.25                         │
│     系统: iOS                               │
│     浏览器: Safari                          │
│     登录时间: 2026-05-16 14:20              │
│     过期时间: 2026-05-17 14:20              │
│                               [下线此设备]   │
│                                             │
└─────────────────────────────────────────────┘
│  [下线所有其他设备]                          │
└─────────────────────────────────────────────┘
```

### 2. 设备图标映射

```javascript
const getDeviceIcon = (deviceType, osName) => {
  if (deviceType === 'MOBILE') {
    if (osName === 'Android') return '🤖';
    if (osName === 'iOS') return '🍎';
    return '📱';
  }
  if (deviceType === 'TABLET') return '📟';
  if (deviceType === 'DESKTOP') {
    if (osName === 'Windows') return '🪟';
    if (osName === 'macOS') return '🖥️';
    if (osName === 'Linux') return '🐧';
    return '💻';
  }
  return '❓';
};
```

### 3. 安全提示

在会话管理页面顶部添加安全提示：

```jsx
<Alert
  message="安全提示"
  description={
    <div>
      <p>• 定期检查登录设备，发现异常立即下线</p>
      <p>• 不要在公共设备上选择"记住我"</p>
      <p>• 启用双因素认证提高账户安全性</p>
      <p>• 下线操作需要TOTP验证码确认</p>
    </div>
  }
  type="info"
  showIcon
/>
```

---

## ⚠️ 错误处理

### 常见错误码

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| 401 | 未授权/Token失效 | 跳转到登录页 |
| 403 | 权限不足 | 提示用户无权限 |
| 400 | TOTP验证码错误 | 提示重新输入 |
| 404 | 会话不存在 | 刷新列表 |
| 500 | 服务器错误 | 提示稍后重试 |

### 错误处理示例

```javascript
const handleApiError = (error, navigate) => {
  if (error.response) {
    const { status, data } = error.response;
    
    switch (status) {
      case 401:
        message.error('登录已过期，请重新登录');
        localStorage.removeItem('token');
        navigate('/login');
        break;
      case 403:
        message.error('您没有权限执行此操作');
        break;
      case 400:
        message.error(data.message || '请求参数错误');
        break;
      case 404:
        message.warning('会话不存在，可能已被下线');
        refreshSessions();
        break;
      default:
        message.error('服务器错误，请稍后重试');
    }
  } else {
    message.error('网络连接失败，请检查网络');
  }
};
```

---

## 🔒 安全注意事项

### 1. Token存储
```javascript
// ✅ 推荐：使用HttpOnly Cookie（后端设置）
// ❌ 避免：localStorage存储敏感Token

// 如果必须使用localStorage，注意XSS防护
const getToken = () => {
  return localStorage.getItem('access_token');
};
```

### 2. TOTP输入框防护
```jsx
// 防止自动填充和密码管理器干扰
<Input
  type="text"
  inputMode="numeric"
  pattern="[0-9]*"
  autoComplete="off"
  autoCorrect="off"
  autoCapitalize="off"
  spellCheck="false"
/>
```

### 3. 防重复提交
```javascript
const [submitting, setSubmitting] = useState(false);

const handleSubmit = async () => {
  if (submitting) return; // 防止重复点击
  
  setSubmitting(true);
  try {
    // ... API调用
  } finally {
    setSubmitting(false);
  }
};
```

---

## 📱 移动端适配

### 响应式布局示例

```css
.session-list {
  display: grid;
  gap: 16px;
}

@media (max-width: 768px) {
  .session-item {
    padding: 12px;
  }
  
  .session-details {
    font-size: 14px;
  }
  
  .revoke-btn {
    width: 100%;
    margin-top: 8px;
  }
}
```

---

## 🧪 测试用例

### 1. 正常流程测试

```javascript
describe('会话管理', () => {
  it('应该能获取会话列表', async () => {
    const sessions = await getSessions();
    expect(sessions).toBeArray();
    expect(sessions[0]).toHaveProperty('sessionId');
    expect(sessions[0]).toHaveProperty('deviceInfo');
  });

  it('应该能下线指定会话', async () => {
    const result = await revokeSession('test-session-id', '123456');
    expect(result.code).toBe(200);
  });

  it('应该能下线所有其他会话', async () => {
    const result = await revokeOtherSessions('123456');
    expect(result.code).toBe(200);
    expect(result.data.revokedCount).toBeGreaterThanOrEqual(0);
  });
});
```

### 2. 错误场景测试

```javascript
it('TOTP错误时应该提示', async () => {
  try {
    await revokeSession('test-session-id', '000000');
  } catch (error) {
    expect(error.response.status).toBe(400);
    expect(error.response.data.message).toContain('TOTP');
  }
});

it('Token失效时应该跳转登录', async () => {
  localStorage.removeItem('token');
  try {
    await getSessions();
  } catch (error) {
    expect(error.response.status).toBe(401);
  }
});
```

---

## 📊 性能优化建议

### 1. 缓存策略

```javascript
// 使用React Query缓存会话列表
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const useSessions = () => {
  const queryClient = useQueryClient();
  
  // 查询会话列表（缓存5分钟）
  const { data: sessions, isLoading } = useQuery({
    queryKey: ['sessions'],
    queryFn: getSessions,
    staleTime: 5 * 60 * 1000, // 5分钟
  });
  
  // 下线会话Mutation
  const revokeMutation = useMutation({
    mutationFn: ({ sessionId, totpCode }) => 
      revokeSession(sessionId, totpCode),
    onSuccess: () => {
      // 成功后刷新列表
      queryClient.invalidateQueries(['sessions']);
    }
  });
  
  return { sessions, isLoading, revokeSession: revokeMutation.mutate };
};
```

### 2. 懒加载

```javascript
// 只在用户访问时才加载会话管理组件
const SessionManagement = lazy(() => import('./SessionManagement'));

// 路由配置
<Route 
  path="/settings/sessions" 
  element={
    <Suspense fallback={<Loading />}>
      <SessionManagement />
    </Suspense>
  } 
/>
```

---

## 🔗 相关API文档

- [TOTP双因素认证API](./API_TOTP.md)
- [用户登录API](./API_USER_LOGIN.md)
- [审计日志API](./AUDIT_LOG_API.md)

---

## 📞 技术支持

如有问题，请联系后端开发团队或查阅项目文档。

**最后更新**: 2026-05-18
