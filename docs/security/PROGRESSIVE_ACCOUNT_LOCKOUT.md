# 渐进式账户锁定策略说明

## 📋 概述

为了防止暴力破解密码，系统实现了**渐进式账户锁定策略**。根据连续登录失败的次数，逐步增加锁定时间，有效阻止恶意攻击。

## 🔒 锁定策略

### 三阶段渐进式锁定

| 阶段 | 失败次数 | 锁定时间 | 说明 |
|------|---------|---------|------|
| **第一阶段** | 1-5次 | 3分钟 | 初始警告期 |
| **第二阶段** | 6-8次 | 10分钟 | 加强警告期 |
| **第三阶段** | 9次及以上 | 30分钟 | 严重警告期 |

### 工作流程

```
用户尝试登录
    ↓
密码错误？
    ↓ 是
记录失败次数
    ↓
判断失败次数
    ├─ 1-5次 → 锁定3分钟
    ├─ 6-8次 → 锁定10分钟
    └─ 9次+ → 锁定30分钟
    ↓
返回错误信息（包含剩余锁定时间和失败次数）
```

## 💡 技术实现

### Redis Key设计

```
login:failures:{username}:{ip}  - 存储失败次数
login:lock:{username}:{ip}      - 存储锁定状态
```

### 核心逻辑

**LoginRateLimitService.java**:

```java
// 检查是否允许登录
public boolean isLoginAllowed(String username, String ip) {
    String lockKey = "login:lock:" + username + ":" + ip;
    Boolean isLocked = redisTemplate.hasKey(lockKey);
    return !Boolean.TRUE.equals(isLocked);
}

// 记录登录失败
public void recordLoginFailure(String username, String ip) {
    String counterKey = "login:failures:" + username + ":" + ip;
    String lockKey = "login:lock:" + username + ":" + ip;
    
    Long attempts = redisTemplate.opsForValue().increment(counterKey);
    
    // 设置过期时间（1小时）
    if (attempts == 1) {
        redisTemplate.expire(counterKey, 1, TimeUnit.HOURS);
    }
    
    // 根据失败次数确定锁定时间
    long lockMinutes;
    if (attempts <= 5) {
        lockMinutes = 3;   // 第一阶段
    } else if (attempts <= 8) {
        lockMinutes = 10;  // 第二阶段
    } else {
        lockMinutes = 30;  // 第三阶段
    }
    
    // 执行锁定
    redisTemplate.opsForValue().set(lockKey, "locked", lockMinutes, TimeUnit.MINUTES);
}

// 记录登录成功（清除计数）
public void recordLoginSuccess(String username, String ip) {
    String counterKey = "login:failures:" + username + ":" + ip;
    String lockKey = "login:lock:" + username + ":" + ip;
    redisTemplate.delete(counterKey);
    redisTemplate.delete(lockKey);
}
```

### 用户提示

**UserService.java**:

```java
if (!loginRateLimitService.isLoginAllowed(normalizedUsername, clientIp)) {
    long remainingTime = loginRateLimitService.getRemainingLockTime(normalizedUsername, clientIp);
    long failureCount = loginRateLimitService.getFailureCount(normalizedUsername, clientIp);
    
    String lockMessage;
    if (failureCount <= 5) {
        lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败）", 
                                   remainingTime, failureCount);
    } else if (failureCount <= 8) {
        lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败，锁定时间延长）", 
                                   remainingTime, failureCount);
    } else {
        lockMessage = String.format("密码错误次数过多，账户已锁定%d秒（第%d次失败，严重警告）", 
                                   remainingTime, failureCount);
    }
    
    throw new BusinessException(lockMessage);
}
```

## 🧪 测试验证

### 自动化测试脚本

```bash
chmod +x scripts/tests/test_login_rate_limit.sh
./scripts/tests/test_login_rate_limit.sh
```

### 手动测试步骤

#### 测试第一阶段（1-5次失败）

```bash
# 连续5次使用错误密码登录
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/users/login \
    -H "Content-Type: application/json" \
    -d '{"username":"testuser","password":"wrong_password"}'
done

# 第6次应该被锁定
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"wrong_password"}'

# 预期响应：
# {"code":500,"message":"密码错误次数过多，账户已锁定180秒（第5次失败）","data":null}
```

#### 测试第二阶段（6-8次失败）

```bash
# 等待3分钟锁定结束后，继续尝试3次
# 第9次应该被锁定10分钟
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"wrong_password"}'

# 预期响应：
# {"code":500,"message":"密码错误次数过多，账户已锁定600秒（第8次失败，锁定时间延长）","data":null}
```

#### 测试第三阶段（9次及以上）

```bash
# 等待10分钟锁定结束后，继续尝试
# 第10次及以后应该被锁定30分钟
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"wrong_password"}'

# 预期响应：
# {"code":500,"message":"密码错误次数过多，账户已锁定1800秒（第9次失败，严重警告）","data":null}
```

#### 测试成功登录解锁

```bash
# 等待锁定结束后，使用正确密码登录
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"correct_password"}'

# 预期：登录成功，失败计数被清除
```

### Redis监控

```bash
# 查看当前失败次数
redis-cli GET "login:failures:testuser:127.0.0.1"

# 查看锁定状态
redis-cli TTL "login:lock:testuser:127.0.0.1"

# 手动清除（用于测试）
redis-cli DEL "login:failures:testuser:127.0.0.1"
redis-cli DEL "login:lock:testuser:127.0.0.1"
```

## 📊 日志示例

### 第一阶段日志

```
WARN  LoginRateLimitService - 登录失败（第一阶段）: username=testuser, ip=127.0.0.1, 失败次数=1/5, 锁定3分钟
WARN  LoginRateLimitService - 账户已锁定: username=testuser, ip=127.0.0.1, 失败次数=1, 锁定时间=3分钟
```

### 第二阶段日志

```
WARN  LoginRateLimitService - 登录失败（第二阶段）: username=testuser, ip=127.0.0.1, 失败次数=6/8, 锁定10分钟
WARN  LoginRateLimitService - 账户已锁定: username=testuser, ip=127.0.0.1, 失败次数=6, 锁定时间=10分钟
```

### 第三阶段日志

```
WARN  LoginRateLimitService - 登录失败（第三阶段）: username=testuser, ip=127.0.0.1, 失败次数=9, 锁定30分钟
WARN  LoginRateLimitService - 账户已锁定: username=testuser, ip=127.0.0.1, 失败次数=9, 锁定时间=30分钟
```

## ⚠️ 注意事项

### 1. IP和用户名的组合

锁定是基于 `username + ip` 的组合，这意味着：
- 同一用户从不同IP登录，失败计数独立
- 同一IP尝试不同用户，失败计数独立

### 2. 成功登录清除计数

用户成功登录后，所有失败计数和锁定状态都会被清除。

### 3. 失败计数过期时间

失败计数的Redis key会在1小时后自动过期，防止长期累积。

### 4. 管理员账号保护

建议对管理员账号实施更严格的锁定策略：
- 可以配置不同的阈值
- 或者添加额外的验证步骤（如验证码、二次认证）

## 🔧 配置调整

如需调整锁定策略参数，修改 `LoginRateLimitService.java`:

```java
// 第一阶段：前5次失败
private static final int FIRST_STAGE_MAX_ATTEMPTS = 5;
private static final long FIRST_STAGE_LOCK_MINUTES = 3;

// 第二阶段：6-8次失败
private static final int SECOND_STAGE_MAX_ATTEMPTS = 8;
private static final long SECOND_STAGE_LOCK_MINUTES = 10;

// 第三阶段：9次及以上
private static final long THIRD_STAGE_LOCK_MINUTES = 30;
```

## 🎯 安全优势

1. **渐进式防御**: 随着攻击持续，防御力度逐步增强
2. **用户体验**: 偶尔输错密码的用户只会被短暂锁定
3. **攻击成本高**: 暴力破解需要极长时间，成本极高
4. **灵活可控**: 可根据实际情况调整各阶段参数
5. **透明友好**: 明确告知用户剩余锁定时间和失败次数

## 📝 相关文件

- `src/main/java/com/leo/myblog/service/LoginRateLimitService.java` - 核心服务
- `src/main/java/com/leo/myblog/service/UserService.java` - 集成到登录流程
- `scripts/tests/test_login_rate_limit.sh` - 自动化测试脚本
- `docs/渗透测试/安全漏洞修复报告.md` - 完整修复报告

---

**最后更新**: 2026-05-13  
**版本**: v1.0  
**作者**: Security Team
