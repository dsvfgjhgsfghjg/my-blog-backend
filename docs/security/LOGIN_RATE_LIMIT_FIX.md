# 登录渐进式账户锁定策略修复说明

## 问题描述

原有的登录锁定策略实现存在严重逻辑错误，没有按照设计要求正确执行渐进式锁定。

### 原设计需求

- **前5次密码错误**：不锁定，仅记录失败次数
- **第5次失败后**：账户锁定3分钟
- **再3次输错（6-8次）**：不锁定，仅记录
- **第8次失败后**：账户锁定10分钟
- **以后每次输错（9次及以上）**：每次失败都锁定30分钟

### 原有实现的错误

1. **每次失败都创建锁**：导致锁时间被不断覆盖，无法体现渐进式升级
2. **锁定时机错误**：第1-5次每次失败都会锁定3分钟，而不是在第5次失败后才锁定
3. **判断条件错误**：使用 `attempts <= 5` 而非 `attempts == 5`，导致前5次都会被锁定

## 修复方案

### 核心逻辑变更

#### 修改前（错误）
```java
if (attempts <= FIRST_STAGE_MAX_ATTEMPTS) {
    // 第1-5次每次都会锁定3分钟
    lockMinutes = FIRST_STAGE_LOCK_MINUTES;
} else if (attempts <= SECOND_STAGE_MAX_ATTEMPTS) {
    // 第6-8次每次都会锁定10分钟
    lockMinutes = SECOND_STAGE_LOCK_MINUTES;
} else {
    // 第9次及以后每次锁定30分钟
    lockMinutes = THIRD_STAGE_LOCK_MINUTES;
}
// 每次都执行锁定
redisTemplate.opsForValue().set(lockKey, "locked", lockMinutes, TimeUnit.MINUTES);
```

#### 修改后（正确）
```java
if (attempts == FIRST_STAGE_MAX_ATTEMPTS) {
    // 仅在第5次失败时锁定3分钟
    lockMinutes = FIRST_STAGE_LOCK_MINUTES;
    stage = "第一阶段";
} else if (attempts == SECOND_STAGE_MAX_ATTEMPTS) {
    // 仅在第8次失败时锁定10分钟
    lockMinutes = SECOND_STAGE_LOCK_MINUTES;
    stage = "第二阶段";
} else if (attempts > SECOND_STAGE_MAX_ATTEMPTS) {
    // 第9次及以后每次失败都锁定30分钟
    lockMinutes = THIRD_STAGE_LOCK_MINUTES;
    stage = "第三阶段";
} else {
    // 未达到阈值，仅记录日志，不锁定
    log.info("登录失败: username={}, ip={}, 失败次数={}/{}", 
            username, ip, attempts, FIRST_STAGE_MAX_ATTEMPTS);
    return; // 不执行锁定
}
// 仅在达到阈值时执行锁定
redisTemplate.opsForValue().set(lockKey, "locked", lockMinutes, TimeUnit.MINUTES);
```

### 关键改进点

1. **精确的触发条件**：使用 `==` 而非 `<=`，确保只在特定次数触发锁定
2. **提前返回机制**：未达到阈值时直接返回，不执行锁定操作
3. **清晰的阶段标识**：添加 `stage` 变量，便于日志追踪和调试
4. **准确的提示信息**：UserService 中的错误提示与锁定策略完全对应

## 正确的行为流程

### 场景示例：用户连续输入错误密码

```
尝试1: 密码错误 → 记录失败次数=1，不锁定 ✅
尝试2: 密码错误 → 记录失败次数=2，不锁定 ✅
尝试3: 密码错误 → 记录失败次数=3，不锁定 ✅
尝试4: 密码错误 → 记录失败次数=4，不锁定 ✅
尝试5: 密码错误 → 记录失败次数=5，锁定3分钟 ⚠️
       ↓ 等待3分钟或清除Redis锁
尝试6: 密码错误 → 记录失败次数=6，不锁定 ✅
尝试7: 密码错误 → 记录失败次数=7，不锁定 ✅
尝试8: 密码错误 → 记录失败次数=8，锁定10分钟 ⚠️⚠️
       ↓ 等待10分钟或清除Redis锁
尝试9: 密码错误 → 记录失败次数=9，锁定30分钟 ⚠️⚠️⚠️
       ↓ 等待30分钟或清除Redis锁
尝试10: 密码错误 → 记录失败次数=10，锁定30分钟 ⚠️⚠️⚠️
...以此类推
```

### 成功登录的情况

```
尝试1-4: 密码错误 → 不锁定
尝试5: 输入正确密码 → 登录成功，清除所有失败计数和锁 ✅
```

## Redis Key 说明

### 失败计数器
- **Key**: `login:failures:{username}:{ip}`
- **Value**: 失败次数（数字字符串）
- **TTL**: 1小时（自动过期）
- **用途**: 跟踪累计失败次数

### 锁定标记
- **Key**: `login:lock:{username}:{ip}`
- **Value**: `"locked"`
- **TTL**: 根据阶段不同（3/10/30分钟）
- **用途**: 阻止被锁定的账户继续尝试登录

## 测试验证

### 自动化测试脚本
```bash
./scripts/tests/test_login_rate_limit.sh
```

### 手动测试步骤

1. **清理环境**
```bash
redis-cli DEL login:lock:testuser:127.0.0.1
redis-cli DEL login:failures:testuser:127.0.0.1
```

2. **测试第一阶段（第5次锁定3分钟）**
```bash
# 连续5次错误密码
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/users/login \
    -H "Content-Type: application/json" \
    -d '{"username":"testuser","password":"wrong"}'
done

# 第6次应该被锁定
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"wrong"}'
# 预期响应: {"code":500,"message":"密码错误次数过多，账户已锁定180秒（第5次失败，锁定3分钟）"}
```

3. **查看Redis状态**
```bash
# 查看失败次数
redis-cli GET login:failures:testuser:127.0.0.1
# 预期: "5"

# 查看锁定剩余时间
redis-cli TTL login:lock:testuser:127.0.0.1
# 预期: 170-180秒之间
```

## 代码变更清单

### 修改的文件

1. **LoginRateLimitService.java**
   - 修复 `recordLoginFailure()` 方法的锁定逻辑
   - 使用精确匹配（`==`）替代范围匹配（`<=`）
   - 添加未达阈值时的提前返回

2. **UserService.java**
   - 优化锁定错误提示信息
   - 明确显示各阶段的锁定时间（3/10/30分钟）

3. **test_login_rate_limit.sh**
   - 更新测试脚本注释和预期行为说明
   - 添加更清晰的测试指导

## 安全优势

1. **防止暴力破解**：渐进式锁定大幅增加攻击成本
2. **用户体验友好**：前几次失败不立即锁定，允许正常用户纠错
3. **灵活性强**：三个阶段适应不同程度的攻击
4. **可追溯性**：详细的日志记录便于安全审计

## 注意事项

1. **IP + 用户名组合**：锁定基于 `{username}:{ip}` 组合，避免误伤其他用户
2. **自动清理**：失败计数器1小时后自动过期，防止长期累积
3. **成功登录重置**：登录成功后立即清除所有失败记录和锁定
4. **Redis依赖**：确保Redis服务正常运行，否则锁定功能失效

## 后续优化建议

1. **监控告警**：当用户进入第三阶段锁定时发送告警通知
2. **白名单机制**：为可信IP或管理员账户设置不同的锁定策略
3. **验证码集成**：在第3-4次失败后要求输入验证码，而非直接锁定
4. **统计报表**：定期生成登录失败统计，识别潜在攻击模式

---

**修复日期**: 2026-05-13  
**影响范围**: 登录认证模块  
**向后兼容**: 是（API接口不变，仅内部逻辑优化）
