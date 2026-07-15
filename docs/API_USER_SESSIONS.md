# 用户会话管理 API 文档

## 📋 功能概述

用户可以查看当前所有活跃的登录会话,并通过双因素认证(TOTP)下线指定会话或所有其他会话。

### 核心功能

1. **查看活跃会话**: 显示所有未失效的Token会话,包括IP、设备信息、登录时间等
2. **下线指定会话**: 通过TOTP验证后下线某个特定设备的登录状态
3. **下线所有其他会话**: 保留当前设备,下线其他所有设备的登录状态

---

## 🔐 安全机制

### TOTP双因素认证

所有下线操作都需要提供TOTP验证码,确保是用户本人操作:
- 支持6位动态验证码(Microsoft Authenticator、Google Authenticator等)
- 支持8位备份码(一次性使用)
- 验证码有效期30秒

### Token追踪

- 登录时自动记录Token会话到Redis
- 会话包含:IP地址、User-Agent、设备信息、登录时间、过期时间
- Token失效后(Redis TTL过期),会话自动清除

---

## 📡 API接口

### 1. 获取用户活跃会话列表

**接口**: `GET /api/users/sessions`

**请求头**:
```
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "sessionId": "1234567890",
      "ipAddress": "192.168.1.100",
      "deviceType": "MOBILE",
      "osName": "Android",
      "browserName": "微信",
      "deviceInfo": "小米 MI 9 - 微信 (小米手机 微信)",
      "loginTime": "2026-05-18T10:30:00",
      "expireTime": "2026-05-19T10:30:00",
      "isCurrent": true
    },
    {
      "sessionId": "9876543210",
      "ipAddress": "192.168.1.101",
      "deviceType": "DESKTOP",
      "osName": "Windows",
      "browserName": "Chrome",
      "deviceInfo": "Chrome 120 on Windows 10/11 (DESKTOP)",
      "loginTime": "2026-05-17T08:00:00",
      "expireTime": "2026-05-18T08:00:00",
      "isCurrent": false
    }
  ]
}
```

**字段说明**:
- `sessionId`: 会话ID(Token Hash),用于下线操作
- `ipAddress`: 登录IP地址
- `deviceType`: 设备类型(DESKTOP/MOBILE/TABLET/UNKNOWN)
- `osName`: 操作系统名称
- `browserName`: 浏览器名称
- `deviceInfo`: 设备详细描述(品牌+型号+浏览器)
- `loginTime`: 登录时间
- `expireTime`: Token过期时间
- `isCurrent`: 是否为当前使用的会话

---

### 2. 下线指定会话

**接口**: `POST /api/users/sessions/{sessionId}/revoke`

**路径参数**:
- `sessionId`: 要下线的会话ID

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**:
```json
{
  "totpCode": "123456"
}
```

**响应(成功)**:
```json
{
  "code": 200,
  "message": "会话已下线",
  "data": null
}
```

**响应(失败)**:
```json
{
  "code": 400,
  "message": "TOTP验证码错误或已过期",
  "data": null
}
```

**说明**:
- 需要提供有效的TOTP验证码或备份码
- 下线后,该会话的Token立即失效
- 无法下线当前正在使用的会话(前端应隐藏或禁用)

---

### 3. 下线所有其他会话

**接口**: `POST /api/users/sessions/revoke-others`

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**:
```json
{
  "totpCode": "123456"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "已下线2个其他会话",
  "data": {
    "revokedCount": 2
  }
}
```

**说明**:
- 保留当前会话,下线所有其他设备的登录状态
- 常用于"退出其他所有设备"场景
- 需要TOTP双因素认证

---

## 💡 使用示例

### 场景1: 查看登录设备

```bash
curl -X GET http://localhost:8080/api/users/sessions \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 场景2: 下线可疑设备

假设发现一个陌生的登录会话(sessionId: 1234567890):

```bash
curl -X POST http://localhost:8080/api/users/sessions/1234567890/revoke \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"totpCode": "123456"}'
```

### 场景3: 退出其他所有设备

```bash
curl -X POST http://localhost:8080/api/users/sessions/revoke-others \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"totpCode": "123456"}'
```

---

## 🎨 前端集成建议

### 1. 会话列表页面

```jsx
// React示例
function SessionList() {
  const [sessions, setSessions] = useState([]);
  
  useEffect(() => {
    fetch('/api/users/sessions', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(res => res.json())
    .then(data => setSessions(data.data));
  }, []);
  
  const revokeSession = async (sessionId) => {
    const totpCode = prompt('请输入TOTP验证码:');
    if (!totpCode) return;
    
    const res = await fetch(`/api/users/sessions/${sessionId}/revoke`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ totpCode })
    });
    
    const data = await res.json();
    if (data.code === 200) {
      alert('会话已下线');
      // 刷新列表
      window.location.reload();
    } else {
      alert(data.message);
    }
  };
  
  return (
    <div>
      <h2>登录设备管理</h2>
      {sessions.map(session => (
        <div key={session.sessionId}>
          <p>设备: {session.deviceInfo}</p>
          <p>IP: {session.ipAddress}</p>
          <p>登录时间: {session.loginTime}</p>
          <p>过期时间: {session.expireTime}</p>
          {!session.isCurrent && (
            <button onClick={() => revokeSession(session.sessionId)}>
              下线
            </button>
          )}
          {session.isCurrent && <span>(当前设备)</span>}
        </div>
      ))}
      
      <button onClick={() => revokeOtherSessions()}>
        退出其他所有设备
      </button>
    </div>
  );
}
```

### 2. TOTP输入组件

```jsx
function TotpInput({ onSubmit }) {
  const [code, setCode] = useState('');
  
  const handleSubmit = () => {
    if (code.length !== 6 && code.length !== 8) {
      alert('请输入6位验证码或8位备份码');
      return;
    }
    onSubmit(code);
  };
  
  return (
    <div>
      <input
        type="text"
        maxLength="8"
        placeholder="输入TOTP验证码"
        value={code}
        onChange={e => setCode(e.target.value)}
      />
      <button onClick={handleSubmit}>确认</button>
    </div>
  );
}
```

---

## 🔍 技术实现细节

### Redis数据结构

#### 1. 用户会话列表 (Hash)
```
Key: user:sessions:{userId}
Field: {tokenHash}
Value: {
  "tokenHash": "1234567890",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "deviceInfo": "小米手机 微信",
  "loginTime": "2026-05-18T10:30:00",
  "expireTime": "2026-05-19T10:30:00"
}
TTL: 86400秒 (24小时)
```

#### 2. Token反向索引 (String)
```
Key: token:session:{tokenHash}
Value: {userId}
TTL: 86400秒 (24小时)
```

### 会话清理

- **自动清理**: Redis TTL到期后自动删除
- **手动清理**: 下线会话时立即从Redis删除
- **定时任务**: 可选,定期清理孤立数据

---

## ⚠️ 注意事项

1. **当前会话保护**: 前端不应显示当前会话的下线按钮
2. **TOTP提示**: 如果用户未启用TOTP,应引导用户先启用
3. **备份码提示**: 告知用户可以使用8位备份码
4. **实时刷新**: 下线操作后应刷新会话列表
5. **错误处理**: TOTP错误时应明确提示

---

## 🛡️ 安全建议

1. **强制TOTP**: 敏感操作必须要求TOTP验证
2. **IP异常检测**: 如发现陌生IP登录,主动提醒用户
3. **会话限制**: 可考虑限制同一用户最多同时在线设备数
4. **日志审计**: 记录所有下线操作,便于追溯

---

## 📊 数据库设计(可选)

如果需要持久化会话历史,可以添加表:

```sql
CREATE TABLE t_user_session_history (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(50),
    device_info TEXT,
    login_time TIMESTAMP,
    logout_time TIMESTAMP,
    logout_reason VARCHAR(50), -- EXPIRED/MANUAL_REVOKE/PASSWORD_CHANGE
    create_time TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_session ON t_user_session_history(user_id, login_time DESC);
```

---

**更新日期**: 2026-05-18  
**版本**: 1.0
