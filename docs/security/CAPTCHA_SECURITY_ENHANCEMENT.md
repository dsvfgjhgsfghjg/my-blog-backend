# 验证码安全增强配置

## 🛡️ 已实施的安全措施

### ✅ 1. 速率限制（Rate Limiting）

**实现**: `CaptchaRateLimitService.java`

**策略**:
- 每个 IP 每分钟最多请求 **30 次**验证码
- 超过限制后，需要等待 **60 秒**才能再次请求
- 使用 Redis 存储计数，自动过期

**Redis Key 格式**:
```
rate:captcha:{client_ip}
```

**配置参数**:
```java
MAX_REQUESTS_PER_MINUTE = 30  // 每分钟最大请求次数
TIME_WINDOW = 60              // 时间窗口（秒）
```

**响应示例**:
```json
{
  "code": 500,
  "message": "请求过于频繁，请45秒后再试",
  "data": null
}
```

---

### ✅ 2. 详细访问日志（Access Logging）

**实现位置**: `SliderCaptchaController.java`

**记录内容**:

#### 获取验证码时
```
INFO - 验证码请求: IP=192.168.1.100, User-Agent=Mozilla/5.0..., 剩余次数=29/30
DEBUG - 验证码生成成功: IP=192.168.1.100, nonceStr=a1b2c3d4e5f6
```

#### 验证失败时
```
WARN - 验证码验证失败: IP=192.168.1.100, nonceStr=a1b2c3d4e5f6, x=150
```

#### 验证成功时
```
INFO - 验证码验证通过: IP=192.168.1.100, nonceStr=a1b2c3d4e5f6
```

#### MinIO 加载图片
```
INFO - 开始从 MinIO 加载背景图片列表: bucket=captcha-backgrounds
INFO - 从 MinIO 加载背景图片完成: 共200张
DEBUG - 成功加载背景图: bg_042.jpg (耗时=15ms, 尺寸=310x155)
```

**日志级别建议**:
- `INFO`: 正常访问、验证结果
- `WARN`: 频率超限、验证失败
- `ERROR`: 系统异常、MinIO 连接失败
- `DEBUG`: 详细调试信息（生产环境可关闭）

---

### ✅ 3. MinIO Bucket 私有访问

**配置**:
```bash
# 确保 Bucket 是私有的
mc anonymous set private myminio/captcha-backgrounds
```

**访问方式**:
- ❌ 不允许直接 HTTP 访问
- ✅ 只能通过后端 API（使用 Access Key）
- ✅ 图片通过 Base64 返回给前端

**安全性**:
- 外部无法直接访问 MinIO
- 所有请求都经过后端鉴权
- 可以利用现有的限流机制

---

### ✅ 4. MinIO 操作审计日志

**实现**: `MinioBackgroundService.java`

**记录内容**:

#### 初始化时
```
INFO - 开始从 MinIO 加载背景图片列表: bucket=captcha-backgrounds
DEBUG - 发现图片: bg_001.jpg (size=5908 bytes)
...
INFO - 从 MinIO 加载背景图片完成: 共200张
```

#### 读取图片时
```
DEBUG - 从 MinIO 读取图片: bucket=captcha-backgrounds, object=bg_042.jpg
DEBUG - 成功加载背景图: bg_042.jpg (耗时=15ms, 尺寸=310x155)
```

#### 异常时
```
ERROR - 从 MinIO 加载图片失败: bucket=captcha-backgrounds, object=bg_042.jpg, 
        耗时=120ms, error=Connection timeout
```

**监控指标**:
- 图片加载成功率
- 平均加载时间
- 异常频率

---

## 🔧 配置调整

### 修改速率限制

编辑 `CaptchaRateLimitService.java`:

```java
// 更严格的限制（推荐用于高安全场景）
private static final int MAX_REQUESTS_PER_MINUTE = 20;

// 更宽松的限制（推荐用于用户体验优先）
private static final int MAX_REQUESTS_PER_MINUTE = 50;

// 更长的锁定时间
private static final long TIME_WINDOW = 120;  // 2分钟
```

### 调整日志级别

在 `application.yml` 中配置：

```yaml
logging:
  level:
    # 验证码相关日志
    com.leo.myblog.controller.SliderCaptchaController: INFO
    com.leo.myblog.service.CaptchaRateLimitService: WARN
    com.leo.myblog.service.MinioBackgroundService: INFO
    
    # 生产环境关闭 DEBUG
    root: WARN
```

---

## 📊 监控和告警

### 关键指标

1. **验证码请求频率**
   - 正常：每 IP < 30 次/分钟
   - 异常：某 IP 持续达到上限

2. **验证成功率**
   - 正常：> 80%
   - 异常：< 50%（可能被攻击）

3. **MinIO 加载时间**
   - 正常：< 50ms
   - 异常：> 500ms（网络问题）

4. **错误率**
   - 正常：< 1%
   - 异常：> 5%（系统故障）

### 告警规则（可选）

如果使用监控系统（如 Prometheus + Grafana），可以设置：

```yaml
# 示例告警规则
alerts:
  - name: CaptchaRateLimitExceeded
    condition: rate(captcha_requests_rejected[5m]) > 10
    message: "验证码频率限制触发过多，可能存在攻击"
    
  - name: MinIOLoadTimeHigh
    condition: histogram_quantile(0.95, minio_load_time_seconds) > 0.5
    message: "MinIO 图片加载时间过长"
```

---

## 🧪 测试

### 测试速率限制

```bash
# 快速请求 35 次（应该被拒绝）
for i in {1..35}; do
  curl -s http://localhost:18080/api/captcha/slider | jq '.message'
done

# 预期输出：
# 前 30 次: "操作成功"
# 后 5 次: "请求过于频繁，请XX秒后再试"
```

### 查看日志

```bash
# 实时查看验证码相关日志
tail -f logs/myblog.log | grep -E "验证码|captcha"

# 查看频率超限警告
grep "频率超限" logs/myblog.log

# 查看 MinIO 加载情况
grep "MinIO" logs/myblog.log
```

---

## 🚀 部署检查清单

上线前确认：

- [x] 速率限制服务已启用
- [x] 访问日志正常记录
- [x] MinIO Bucket 为私有
- [x] MinIO 操作有审计日志
- [ ] Redis 正常运行
- [ ] MinIO 连接正常
- [ ] 日志级别配置正确
- [ ] 监控系统已配置（可选）

---

## 📝 故障排查

### 问题 1：所有请求都被拒绝

**症状**: 每次请求都返回"请求过于频繁"

**原因**: Redis 中的计数未正确清理

**解决**:
```bash
# 清除所有限制
redis-cli KEYS "rate:captcha:*" | xargs redis-cli DEL
```

### 问题 2：MinIO 加载失败

**症状**: 日志显示"从 MinIO 加载图片失败"

**检查**:
```bash
# 1. 检查 MinIO 是否运行
docker compose ps minio

# 2. 检查 Bucket 是否存在
mc ls myminio/captcha-backgrounds

# 3. 检查网络连接
curl http://localhost:9000/minio/health/live
```

### 问题 3：日志过多

**解决**: 调整日志级别为 WARN 或 ERROR

```yaml
logging:
  level:
    com.leo.myblog.controller.SliderCaptchaController: WARN
    com.leo.myblog.service.MinioBackgroundService: WARN
```

---

## 🔐 安全最佳实践

1. **定期轮换 Access Key**
   ```bash
   # 每季度更换一次 MinIO Access Key
   mc admin user add myminio newuser newpassword
   ```

2. **监控异常 IP**
   ```bash
   # 找出请求最多的 IP
   grep "验证码请求" logs/myblog.log | \
     awk -F'IP=' '{print $2}' | \
     awk '{print $1}' | \
     sort | uniq -c | sort -rn | head -10
   ```

3. **启用 Fail2Ban（可选）**
   - 自动封禁恶意 IP
   - 结合日志分析

4. **定期审查日志**
   - 每周检查一次异常模式
   - 及时调整安全策略

---

**最后更新**: 2026-05-17  
**相关文件**:
- `CaptchaRateLimitService.java` - 速率限制服务
- `SliderCaptchaController.java` - 控制器（含日志）
- `MinioBackgroundService.java` - MinIO 服务（含审计日志）
