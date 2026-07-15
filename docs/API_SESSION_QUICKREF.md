# 用户会话管理 - 快速参考卡

## 🔑 API速查

### 1. 获取会话列表
```http
GET /api/users/sessions
Authorization: Bearer {token}
```

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "sessionId": "-1234567890",
      "deviceInfo": "小米 MI 9 - 微信 (小米手机 微信)",
      "ipAddress": "192.168.1.100",
      "isCurrent": true
    }
  ]
}
```

---

### 2. 下线指定设备
```http
POST /api/users/sessions/{sessionId}/revoke
Authorization: Bearer {token}
Content-Type: application/json

{"totpCode": "123456"}
```

---

### 3. 下线所有其他设备
```http
POST /api/users/sessions/revoke-others
Authorization: Bearer {token}
Content-Type: application/json

{"totpCode": "123456"}
```

---

## 🎨 React组件模板

### 会话列表组件
```jsx
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const SessionList = () => {
  const queryClient = useQueryClient();
  
  const { data: sessions } = useQuery({
    queryKey: ['sessions'],
    queryFn: () => fetch('/api/users/sessions', {
      headers: { 'Authorization': `Bearer ${getToken()}` }
    }).then(res => res.json())
  });
  
  const revokeMutation = useMutation({
    mutationFn: ({ sessionId, totpCode }) => 
      fetch(`/api/users/sessions/${sessionId}/revoke`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ totpCode })
      }),
    onSuccess: () => queryClient.invalidateQueries(['sessions'])
  });
  
  return (
    <div>
      {sessions?.data?.map(session => (
        <div key={session.sessionId}>
          <h3>{session.deviceInfo}</h3>
          <p>IP: {session.ipAddress}</p>
          {!session.isCurrent && (
            <button onClick={() => handleRevoke(session.sessionId)}>
              下线
            </button>
          )}
        </div>
      ))}
    </div>
  );
};
```

---

## ⚠️ 错误码速查

| 状态码 | 含义 | 处理 |
|--------|------|------|
| 200 | 成功 | 刷新列表 |
| 400 | TOTP错误 | 提示重新输入 |
| 401 | Token失效 | 跳转登录 |
| 404 | 会话不存在 | 刷新列表 |

---

## 🔒 安全要点

- ✅ 所有下线操作需要TOTP验证
- ✅ TOTP为6位数字或8位备份码
- ✅ 不能下线当前会话
- ✅ Token有效期24小时

---

## 📱 设备图标映射

```javascript
const icons = {
  'MOBILE': { 'Android': '🤖', 'iOS': '🍎' },
  'DESKTOP': { 'Windows': '🪟', 'macOS': '🖥️', 'Linux': '🐧' },
  'TABLET': '📟'
};
```

---

## 🎯 Android细化备注示例

| UA特征 | 识别结果 |
|--------|---------|
| MI 9 + WeChat | 小米手机 微信 |
| HUAWEI + Chrome | 华为手机 Chrome |
| Honor + 荣耀浏览器 | 荣耀手机 荣耀浏览器 |
| vivo + baidu | vivo手机 百度 |
| OPPO + UC | OPPO手机 UC浏览器 |

---

## 📖 完整文档

详细文档请查看:
- [API_USER_SESSIONS_FRONTEND.md](./API_USER_SESSIONS_FRONTEND.md)
- [ANDROID_UA_AND_SESSION_SUMMARY.md](./ANDROID_UA_AND_SESSION_SUMMARY.md)

---

**最后更新**: 2026-05-18
