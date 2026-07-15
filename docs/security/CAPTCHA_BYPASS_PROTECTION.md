# 滑块验证码安全加固方案

## 🔴 已修复的安全漏洞

### V15: 验证码可绕过漏洞（高危）

**问题描述**: 
登录接口中验证码验证是可选的，黑客可以不传 `captchaNonceStr` 和 `captchaX` 参数，直接跳过验证码进行暴力破解。

**修复前代码**:
```java
// ❌ 危险：验证码是可选的
if (request.getCaptchaNonceStr() != null && request.getCaptchaX() != null) {
    boolean captchaPassed = sliderCaptchaService.verifyCaptcha(...);
    if (!captchaPassed) {
        throw new BusinessException("验证码错误，请重试");
    }
}
// 如果没有传验证码参数，直接继续执行登录逻辑
```

**修复后代码**:
```java
// ✅ 安全：强制要求验证码
if (request.getCaptchaNonceStr() == null || request.getCaptchaNonceStr().trim().isEmpty()) {
    throw new BusinessException("请完成滑块验证码");
}
if (request.getCaptchaX() == null) {
    throw new BusinessException("请完成滑块验证码");
}

boolean captchaPassed = sliderCaptchaService.verifyCaptcha(...);
if (!captchaPassed) {
    throw new BusinessException("验证码错误，请重试");
}
```

**影响范围**:
- 文件: `UserService.java`
- 方法: `login()`
- 风险等级: 🔴 高危

---

## 🛡️ 多层防护体系

### 第 1 层：强制验证码验证
✅ **已实施** - 登录时必须提供有效的验证码

### 第 2 层：速率限制
✅ **已实施** - `CaptchaRateLimitService`
- 每个 IP 每分钟最多 30 次请求
- 超限后自动锁定 60 秒
- Redis 存储，自动过期

### 第 3 层：一次性使用
✅ **已实施** - `SliderCaptchaService.verifyCaptcha()`
```java
String storedX = redisTemplate.opsForValue().get(key);
if (storedX == null) {
    return false; // nonceStr 不存在或已使用
}
redisTemplate.delete(key); // 立即删除，防止重放
```

### 第 4 层：渐进式账户锁定
✅ **已实施** - `LoginRateLimitService`
- 1-5 次失败：锁定 3 分钟
- 6-8 次失败：锁定 10 分钟
- 9+ 次失败：锁定 30 分钟

### 第 5 层：User-Agent 安全检查
✅ **已实施** - `UaSecurityFilter`
- 拦截恶意 User-Agent（如 curl、python-requests）
- 记录安全事件到数据库
- 低威胁级别自动封禁

### 第 6 层：IP 黑名单
✅ **已实施** - `IpBanService`
- 支持手动/自动封禁 IP
- 可设置封禁时长
- 数据库持久化

### 第 7 层：详细审计日志
✅ **已实施** - `LoginLogService`
- 记录所有登录尝试
- 包含 IP、User-Agent、时间戳
- 成功/失败原因

---

## 🔍 潜在攻击场景分析

### 场景 1: 直接调用 API（无验证码）

**攻击方式**:
```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

**防护效果**: ✅ **完全阻止**
```json
{
  "code": 500,
  "message": "请完成滑块验证码",
  "data": null
}
```

---

### 场景 2: 暴力破解验证码坐标

**攻击方式**:
```python
for x in range(0, 261):
    requests.post('/api/captcha/slider/verify', json={
        'nonceStr': 'xxx',
        'x': x
    })
```

**防护效果**: ✅ **有效阻止**
- 第 30 次请求后被锁定 60 秒
- Redis 计数: `rate:captcha:{ip}`
- 日志记录: `WARN - 验证码请求频率超限`

---

### 场景 3: OCR 图像识别

**攻击方式**:
使用 AI 模型识别滑块位置，自动计算坐标。

**防护效果**: ⚠️ **部分有效**

**当前措施**:
- Base64 编码增加了解析难度
- 图片尺寸固定（310×155），难以缩放
- 允许误差 ±5 像素，降低精确度要求

**建议增强**:
1. **动态图片尺寸** - 每次随机生成不同尺寸
2. **图片干扰** - 添加噪点、扭曲、旋转
3. **行为分析** - 检测滑动轨迹是否自然
4. **时间检测** - 人类滑动需要 0.5-3 秒

---

### 场景 4: 重放攻击

**攻击方式**:
截获合法的 `{nonceStr, x}` 组合，重复使用。

**防护效果**: ✅ **完全阻止**
```java
// 验证后立即删除
redisTemplate.delete(key);

// 第二次使用会返回 false
String storedX = redisTemplate.opsForValue().get(key);
if (storedX == null) {
    return false;
}
```

---

### 场景 5: 分布式攻击（多 IP）

**攻击方式**:
使用代理池，每个 IP 请求 30 次，然后切换 IP。

**防护效果**: ⚠️ **部分有效**

**当前措施**:
- 单 IP 速率限制
- User-Agent 检查
- 账户级别锁定（用户名 + IP）

**建议增强**:
1. **全局速率限制** - 限制总请求数
2. **设备指纹** - 识别同一设备的多个 IP
3. **行为分析** - 检测异常模式
4. **CAPTCHA 升级** - 引入更复杂的验证方式

---

## 📊 安全监控指标

### 关键指标

| 指标 | 正常值 | 告警阈值 | 说明 |
|------|--------|----------|------|
| 验证码请求频率 | < 30/min/IP | > 25/min/IP | 接近上限 |
| 验证成功率 | > 80% | < 60% | 可能被攻击 |
| 登录失败率 | < 10% | > 30% | 暴力破解 |
| MinIO 加载时间 | < 50ms | > 200ms | 性能问题 |
| UA 拦截次数 | < 5/hour | > 20/hour | 恶意扫描 |

### 监控查询示例

```sql
-- 查询最近 1 小时验证码请求最多的 IP
SELECT ip_address, COUNT(*) as count
FROM t_security_event
WHERE event_type = 'CAPTCHA_REQUEST'
  AND create_time > NOW() - INTERVAL '1 hour'
GROUP BY ip_address
ORDER BY count DESC
LIMIT 10;

-- 查询登录失败率高的用户
SELECT username, COUNT(*) as failure_count
FROM t_login_log
WHERE success = false
  AND create_time > NOW() - INTERVAL '1 day'
GROUP BY username
HAVING COUNT(*) > 10
ORDER BY failure_count DESC;
```

---

## 🔧 建议的安全增强

### 短期（1 周内）

#### 1. 滑动轨迹验证
记录用户滑动的轨迹点，检测是否为人工操作：

```java
public class SliderTrajectory {
    private List<Point> points; // 轨迹点列表
    private long duration;      // 滑动耗时（毫秒）
    private double acceleration; // 加速度变化
    
    public boolean isHumanLike() {
        // 检查轨迹是否平滑
        // 检查速度是否合理（0.5-3 秒）
        // 检查是否有加速减速
        return true;
    }
}
```

#### 2. 动态图片尺寸
每次生成验证码时使用随机尺寸：

```java
int width = 280 + random.nextInt(60);  // 280-340
int height = 140 + random.nextInt(30); // 140-170
```

#### 3. 图片干扰增强
在背景图上添加干扰元素：

```java
// 添加噪点
for (int i = 0; i < 100; i++) {
    int x = random.nextInt(width);
    int y = random.nextInt(height);
    graphics.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
    graphics.fillRect(x, y, 2, 2);
}
```

---

### 中期（1 个月内）

#### 4. 设备指纹
收集浏览器特征，生成唯一设备 ID：

```javascript
const fingerprint = {
  userAgent: navigator.userAgent,
  screenResolution: `${screen.width}x${screen.height}`,
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  language: navigator.language,
  platform: navigator.platform,
  canvas: getCanvasFingerprint()
};
```

#### 5. 行为分析
建立用户行为基线，检测异常：

- 正常用户：滑动时间 0.5-3 秒，轨迹平滑
- 机器人：滑动时间 < 0.1 秒，轨迹直线

#### 6. 全局速率限制
限制整个系统的验证码请求总数：

```java
// 全局计数器
String globalKey = "rate:captcha:global";
Long count = redisTemplate.opsForValue().increment(globalKey, 1);
redisTemplate.expire(globalKey, 60, TimeUnit.SECONDS);

if (count > 1000) { // 每分钟最多 1000 次
    throw new BusinessException("系统繁忙，请稍后重试");
}
```

---

### 长期（3 个月内）

#### 7. 机器学习检测
训练模型识别恶意请求：

- 特征：IP、UA、滑动轨迹、时间间隔
- 算法：随机森林、LSTM
- 输出：恶意概率评分

#### 8. 多因素认证
对高风险操作要求额外验证：

- 短信验证码
- 邮箱验证
- TOTP（Google Authenticator）

#### 9. WebAssembly 挑战
使用 WASM 执行复杂计算，增加自动化难度：

```javascript
// 前端执行 WASM 挑战
const result = await wasmModule.solveChallenge(challenge);
// 后端验证结果
```

---

## 📝 安全检查清单

部署前确认：

- [x] V15: 验证码强制验证已启用
- [x] 速率限制服务正常运行
- [x] Redis 连接正常
- [x] 一次性使用机制生效
- [x] 渐进式账户锁定配置正确
- [x] User-Agent 过滤器启用
- [x] IP 黑名单功能可用
- [x] 审计日志正常记录
- [ ] 滑动轨迹验证（待实现）
- [ ] 动态图片尺寸（待实现）
- [ ] 全局速率限制（待实现）
- [ ] 设备指纹收集（待实现）

---

## 🚨 应急响应流程

### 发现攻击时的处理步骤

1. **立即响应**
   ```bash
   # 查看最近的攻击日志
   tail -f logs/myblog.log | grep -E "验证码|captcha|频率超限"
   
   # 封禁攻击 IP
   mc admin policy attach myminio readonly --user attacker_ip
   ```

2. **临时加固**
   - 降低速率限制阈值（30 → 10 次/分钟）
   - 延长锁定时间（60 → 300 秒）
   - 启用更严格的 UA 过滤

3. **数据分析**
   ```sql
   -- 分析攻击模式
   SELECT 
     ip_address,
     COUNT(*) as attempts,
     MIN(create_time) as first_attempt,
     MAX(create_time) as last_attempt
   FROM t_security_event
   WHERE event_type IN ('CAPTCHA_RATE_LIMIT', 'LOGIN_FAILURE')
     AND create_time > NOW() - INTERVAL '1 hour'
   GROUP BY ip_address
   HAVING COUNT(*) > 50;
   ```

4. **长期防护**
   - 更新 WAF 规则
   - 调整速率限制策略
   - 考虑引入第三方验证码服务

---

## 📚 相关文档

- [CAPTCHA_SECURITY_ENHANCEMENT.md](./CAPTCHA_SECURITY_ENHANCEMENT.md) - 验证码安全增强配置
- [MINIO_CAPTCHA_SECURITY.md](./MINIO_CAPTCHA_SECURITY.md) - MinIO 安全策略
- [SLIDER_CAPTCHA_FRONTEND_GUIDE.md](../api/SLIDER_CAPTCHA_FRONTEND_GUIDE.md) - 前端集成指南

---

**最后更新**: 2026-05-17  
**安全等级**: 🔴 高优先级  
**维护者**: Leo
