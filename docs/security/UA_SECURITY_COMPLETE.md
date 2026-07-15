# UA安全拦截系统 - 完整实现指南

## 📋 目录

- [功能概述](#功能概述)
- [架构设计](#架构设计)
- [核心组件](#核心组件)
- [数据库设计](#数据库设计)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [管理API](#管理api)
- [监控与维护](#监控与维护)
- [测试验证](#测试验证)

---

## 功能概述

UA安全拦截系统是MyBlog的高级安全防护模块，通过检测和拦截恶意User-Agent来防止自动化攻击和扫描器探测。

### 核心功能

1. **UA黑名单拦截**：基于模式匹配识别恶意UA（Kali、sqlmap、nmap等）
2. **蜜罐机制**：对高危威胁返回假数据，消耗攻击者时间
3. **IP自动封禁**：多次违规后自动封禁IP 24小时
4. **安全事件日志**：记录所有安全事件到数据库，支持审计分析
5. **定时清理**：自动清理过期的安全事件和IP封禁记录

### 防护范围

| 类别 | 工具/特征 | 处理方式 |
|------|----------|---------|
| 渗透系统 | Kali Linux | 蜜罐（返回假数据） |
| SQL注入 | sqlmap | 蜜罐 |
| 网络扫描 | nmap | 蜜罐 |
| Web扫描 | Nikto, DirBuster, w3af | 拦截（403） |
| 漏洞利用 | Metasploit | 拦截 |
| 脚本工具 | python-requests, python-urllib | 拦截 |
| 无头浏览器 | Go-http-client, Java/* | 拦截 |
| 空UA | (empty) | 拦截 |

---

## 架构设计

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP Request
       ▼
┌─────────────────────┐
│  RequestIdFilter    │ ← Order(1): 生成请求ID
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  UaSecurityFilter   │ ← Order(2): UA安全检查
│                     │
│  1. 检查IP封禁      │
│  2. 检查空UA        │
│  3. 匹配UA黑名单    │
│  4. 蜜罐/拦截处理   │
└──────┬──────────────┘
       │
       ├─ 正常 → ┌──────────────┐
       │         │ Spring MVC   │
       │         └──────────────┘
       │
       ├─ 拦截 → 返回403 + 记录事件
       │
       └─ 蜜罐 → 返回200 + 假数据
```

---

## 核心组件

### 1. UaSecurityFilter（核心过滤器）

**位置**: `src/main/java/com/leo/myblog/filter/UaSecurityFilter.java`

**职责**:
- 在请求处理的早期阶段进行UA检查
- 根据匹配结果决定放行、拦截或蜜罐
- 调用Service记录安全事件

**关键代码**:
```java
@Override
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    // 1. 检查IP是否已被封禁
    if (ipBanService.isIpBanned(clientIp)) {
        return403(response);
        return;
    }
    
    // 2. 检查空UA
    if (userAgent == null || userAgent.trim().isEmpty()) {
        handleSecurityViolation(...);
        return;
    }
    
    // 3. 匹配UA黑名单
    UaBlacklist matchedRule = matchUaBlacklist(userAgent);
    if (matchedRule != null) {
        if ("HONEYPOT".equals(matchedRule.getActionType())) {
            handleHoneypot(...); // 返回假数据
        } else {
            handleSecurityViolation(...); // 返回403
        }
        return;
    }
    
    // 4. 正常请求
    chain.doFilter(request, response);
}
```

### 2. SecurityEventService（安全事件服务）

**位置**: `src/main/java/com/leo/myblog/service/SecurityEventService.java`

**功能**:
- 异步记录安全事件到数据库
- 提供统计查询接口
- 清理过期数据（90天）

**关键方法**:
```java
@Async
@Transactional
public void recordEvent(String eventType, String ipAddress, String userAgent, 
                       String requestUrl, String requestMethod, String matchedPattern,
                       String threatLevel, String actionTaken)
```

### 3. IpBanService（IP封禁服务）

**位置**: `src/main/java/com/leo/myblog/service/IpBanService.java`

**功能**:
- 封禁/解封IP（同时写入数据库和Redis）
- 检查IP封禁状态（优先查Redis）
- 清理过期封禁记录

**双层存储**:
- **Redis**: 快速检查，TTL自动过期
- **PostgreSQL**: 持久化存储，支持审计

### 4. SecurityCleanupTask（定时任务）

**位置**: `src/main/java/com/leo/myblog/task/SecurityCleanupTask.java`

**调度计划**:
- 每天凌晨2点：清理90天前的安全事件
- 每小时整点：清理过期的IP封禁记录

### 5. SecurityAdminController（管理API）

**位置**: `src/main/java/com/leo/myblog/controller/SecurityAdminController.java`

**功能**: 提供管理员专用的安全管理接口

---

## 数据库设计

### t_ua_blacklist（UA黑名单表）

```sql
CREATE TABLE t_ua_blacklist (
    id BIGINT PRIMARY KEY,
    pattern VARCHAR(200) NOT NULL UNIQUE,     -- 匹配模式（不区分大小写）
    description VARCHAR(500),                  -- 描述
    threat_level VARCHAR(20) DEFAULT 'MEDIUM', -- LOW/MEDIUM/HIGH/CRITICAL
    action_type VARCHAR(20) DEFAULT 'BLOCK',   -- BLOCK/HONEYPOT
    is_enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**默认规则**（13条）:
```sql
INSERT INTO t_ua_blacklist VALUES
(1, 'kali', 'Kali Linux 渗透测试系统', 'HIGH', 'HONEYPOT'),
(2, 'sqlmap', 'SQL注入扫描工具', 'CRITICAL', 'HONEYPOT'),
(3, 'nmap', '网络扫描工具', 'HIGH', 'HONEYPOT'),
(4, 'nikto', 'Web应用扫描器', 'HIGH', 'BLOCK'),
(5, 'dirbuster', '目录爆破扫描器', 'HIGH', 'BLOCK'),
(6, 'w3af', 'Web攻击框架', 'HIGH', 'BLOCK'),
(7, 'metasploit', '漏洞利用框架', 'CRITICAL', 'BLOCK'),
(8, 'python-requests', 'Python HTTP库', 'MEDIUM', 'BLOCK'),
(9, 'python-urllib', 'Python URL库', 'MEDIUM', 'BLOCK'),
(10, 'go-http-client', 'Go语言HTTP客户端', 'MEDIUM', 'BLOCK'),
(11, 'java/', 'Java HTTP客户端', 'LOW', 'BLOCK'),
(12, 'curl/', '命令行HTTP工具', 'LOW', 'BLOCK'),
(13, 'wget/', '命令行下载工具', 'LOW', 'BLOCK');
```

### t_security_event（安全事件表）

```sql
CREATE TABLE t_security_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,          -- UA_BLOCKED/HONEYPOT_TRIGGERED
    ip_address VARCHAR(100) NOT NULL,
    user_agent TEXT,
    request_url VARCHAR(500),
    request_method VARCHAR(10),
    matched_pattern VARCHAR(200),             -- 匹配的黑名单模式
    threat_level VARCHAR(20),                 -- LOW/MEDIUM/HIGH/CRITICAL
    action_taken VARCHAR(20),                 -- BLOCKED/HONEYPOT
    trigger_count INTEGER DEFAULT 1,          -- 该IP触发次数
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_event_ip ON t_security_event(ip_address);
CREATE INDEX idx_security_event_time ON t_security_event(create_time DESC);
CREATE INDEX idx_security_event_type ON t_security_event(event_type);
```

### t_ip_ban（IP封禁表）

```sql
CREATE TABLE t_ip_ban (
    id BIGINT PRIMARY KEY,
    ip_address VARCHAR(100) NOT NULL UNIQUE,
    reason VARCHAR(500),                      -- 封禁原因
    ban_until TIMESTAMP NOT NULL,             -- 封禁到期时间
    trigger_count INTEGER DEFAULT 0,          -- 触发次数
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ip_ban_until ON t_ip_ban(ban_until);
```

---

## 配置说明

### 1. 启用定时任务

确保Spring Boot启用了定时任务支持：

```java
@SpringBootApplication
@EnableScheduling  // 添加此注解
public class MyblogApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyblogApplication.class, args);
    }
}
```

### 2. Redis配置

IP封禁依赖Redis进行快速检查，确保`application.yml`中配置了Redis：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 3. 调整阈值

可在`UaSecurityFilter`中修改以下参数：

```java
private static final int MAX_VIOLATIONS_BEFORE_BAN = 10; // 触发封禁的违规次数
private static final long BAN_DURATION_HOURS = 24; // 封禁时长（小时）
```

---

## 使用示例

### 1. 查看被封禁的IP

```bash
# 通过管理API
curl -X GET "http://localhost:8080/api/admin/security/banned-ips" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 2. 手动封禁IP

```bash
curl -X POST "http://localhost:8080/api/admin/security/ban-ip?ipAddress=192.168.1.100&reason=恶意攻击&durationHours=48" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 3. 解除IP封禁

```bash
curl -X DELETE "http://localhost:8080/api/admin/security/unban-ip/192.168.1.100" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 4. 查看安全统计

```bash
# 频繁违规IP
curl -X GET "http://localhost:8080/api/admin/security/frequent-violators?minCount=5" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

# 威胁等级分布
curl -X GET "http://localhost:8080/api/admin/security/threat-stats" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 5. 手动清理数据

```bash
curl -X POST "http://localhost:8080/api/admin/security/cleanup" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

---

## 管理API

### API列表

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/admin/security/banned-ips` | GET | 获取被封禁的IP列表 | 管理员 |
| `/api/admin/security/ban-ip` | POST | 手动封禁IP | 管理员 |
| `/api/admin/security/unban-ip/{ip}` | DELETE | 解除IP封禁 | 管理员 |
| `/api/admin/security/frequent-violators` | GET | 获取频繁违规IP | 管理员 |
| `/api/admin/security/threat-stats` | GET | 获取威胁统计 | 管理员 |
| `/api/admin/security/cleanup` | POST | 清理过期数据 | 管理员 |

### 响应示例

**获取被封禁IP列表**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "1234567890",
      "ipAddress": "192.168.1.100",
      "reason": "UA黑名单违规: kali",
      "banUntil": "2026-05-15T14:30:00",
      "triggerCount": 15,
      "createTime": "2026-05-14T14:30:00"
    }
  ]
}
```

**威胁统计**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"threatLevel": "CRITICAL", "count": 5},
    {"threatLevel": "HIGH", "count": 23},
    {"threatLevel": "MEDIUM", "count": 67},
    {"threatLevel": "LOW", "count": 142}
  ]
}
```

---

## 监控与维护

### 1. 日志监控

**高危事件日志**:
```
WARN  [security] 【高危安全事件】type=UA_BLOCKED, ip=192.168.1.100, 
      ua=sqlmap/1.7, url=/api/posts, pattern=sqlmap, threat=CRITICAL
```

**蜜罐触发日志**:
```
INFO  [security] 蜜罐响应已发送: ip=10.0.0.50, url=/api/users
```

**IP封禁日志**:
```
WARN  [security] IP已被自动封禁: ip=192.168.1.100, 
      reason=UA黑名单违规: kali, violations=10, duration=24h
```

### 2. 数据库查询

**查看最近的安全事件**:
```sql
SELECT * FROM t_security_event 
ORDER BY create_time DESC 
LIMIT 50;
```

**查看高频违规IP**:
```sql
SELECT ip_address, COUNT(*) as violation_count
FROM t_security_event
WHERE create_time > NOW() - INTERVAL '24 hours'
GROUP BY ip_address
HAVING COUNT(*) >= 5
ORDER BY violation_count DESC;
```

**查看当前有效的IP封禁**:
```sql
SELECT * FROM t_ip_ban
WHERE ban_until > NOW()
ORDER BY create_time DESC;
```

### 3. Redis检查

```bash
# 查看所有封禁的IP
redis-cli KEYS "security:banned:*"

# 查看某个IP的封禁原因和剩余时间
redis-cli GET "security:banned:192.168.1.100"
redis-cli TTL "security:banned:192.168.1.100"

# 查看IP的违规次数
redis-cli GET "security:violation:192.168.1.100"
```

### 4. 性能优化建议

1. **Redis缓存命中率**: IP封禁检查优先查Redis，命中率应>95%
2. **数据库索引**: 确保`t_security_event`和`t_ip_ban`的索引正确创建
3. **定时清理**: 定期检查清理任务是否正常执行
4. **日志分级**: 高危事件用WARN，普通事件用DEBUG，避免日志膨胀

---

## 测试验证

### 运行完整测试脚本

```bash
chmod +x scripts/tests/test_ua_security_complete.sh
./scripts/tests/test_ua_security_complete.sh
```

### 手动测试

**1. 正常浏览器访问（应该通过）**:
```bash
curl -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36" \
  http://localhost:8080/api/posts
```

**2. Kali Linux UA（蜜罐模式）**:
```bash
curl -H "User-Agent: Mozilla/5.0 (X11; Linux x86_64; Kali GNU/Linux Rolling)" \
  http://localhost:8080/api/posts
# 预期：返回200和假数据
```

**3. Python脚本（拦截模式）**:
```bash
curl -H "User-Agent: python-requests/2.31.0" \
  http://localhost:8080/api/posts
# 预期：返回403
```

**4. 空UA（拦截模式）**:
```bash
curl http://localhost:8080/api/posts
# 预期：返回403
```

### 验证数据记录

**检查安全事件表**:
```sql
SELECT event_type, ip_address, matched_pattern, threat_level, create_time
FROM t_security_event
ORDER BY create_time DESC
LIMIT 10;
```

**检查IP封禁状态**:
```bash
# 连续发送10次恶意请求后检查
redis-cli GET "security:violation:YOUR_IP"
redis-cli GET "security:banned:YOUR_IP"
```

---

## 常见问题

### Q1: 为什么有些请求没有被拦截？

**A**: 检查以下几点：
1. UA是否在黑名单中（大小写不敏感）
2. `t_ua_blacklist`表的`is_enabled`字段是否为`true`
3. 过滤器是否正确加载（查看启动日志）

### Q2: 如何临时禁用某个UA规则？

**A**: 更新数据库：
```sql
UPDATE t_ua_blacklist SET is_enabled = false WHERE pattern = 'curl';
```

### Q3: 误封了正常用户怎么办？

**A**: 立即解封：
```bash
curl -X DELETE "http://localhost:8080/api/admin/security/unban-ip/IP_ADDRESS" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

### Q4: 蜜罐返回的数据能否自定义？

**A**: 可以，修改`UaSecurityFilter.generateFakeData()`方法，根据不同URL返回不同的假数据。

### Q5: 如何调整封禁阈值？

**A**: 修改`UaSecurityFilter`中的常量：
```java
private static final int MAX_VIOLATIONS_BEFORE_BAN = 20; // 改为20次
private static final long BAN_DURATION_HOURS = 48; // 改为48小时
```

---

## 最佳实践

1. **定期审查安全事件**: 每周查看`t_security_event`，分析攻击趋势
2. **动态调整黑名单**: 根据实际攻击情况添加新的UA模式
3. **蜜罐数据多样化**: 定期更新蜜罐返回的假数据，增加迷惑性
4. **IP白名单机制**: 对于可信的合作伙伴IP，可以考虑添加白名单
5. **告警集成**: 将高危安全事件集成到监控告警系统（如钉钉、企业微信）
6. **日志归档**: 定期备份安全事件日志，用于长期分析

---

## 扩展建议

### 1. 集成WAF

可以将UA黑名单同步到Nginx WAF或云WAF，在网络层就拦截恶意请求。

### 2. 机器学习检测

基于历史数据训练模型，自动识别异常UA模式，而不仅依赖静态规则。

### 3. 威胁情报共享

将高频攻击IP上报到威胁情报平台，与其他系统共享防护信息。

### 4. 行为分析

结合请求频率、访问路径等行为特征，更精准地识别恶意请求。

---

## 总结

UA安全拦截系统为MyBlog提供了多层防护：

✅ **第一层**: 实时拦截已知恶意UA  
✅ **第二层**: 蜜罐迷惑高级攻击者  
✅ **第三层**: IP自动封禁防止持续攻击  
✅ **第四层**: 完整的事件日志支持审计分析  
✅ **第五层**: 定时清理保证系统性能  

通过这五层防护，可以有效抵御大部分自动化攻击和扫描器探测，保护系统安全。
