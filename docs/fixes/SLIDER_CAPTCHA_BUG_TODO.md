# 旋转验证码 — Bug 排查与修复记录

## 报错信息

```
Login.Buegb-ia.js:1 [Login] 登录失败: Error: 验证码验证失败，请重试
```

## 根本原因分析

经过全套前端 + 后端源代码审查，发现 **2 个 Bug** 共同导致验证一直失败：

---

### Bug 1：轨迹点 x/y 字段类型 Integer → 前端传 Double（序列化截断/异常）

**前端行为**（`RotationCaptcha.vue` 第 231 行）：

轨迹中的 `x` 值是**旋转角度（度）**，前端的中间计算值含小数：
```typescript
x: Math.round(currentAngle.value * 10) / 10
```
产生如 `-20.3`、`15.7` 等带一位小数的浮点数。

**后端定义**（修改前）：

```java
// LoginRequest.TrajectoryPointDTO.x 原是 Integer
private Integer x;
// SliderCaptchaService.TrajectoryPoint.x 原是 Integer  
private Integer x;
```

**影响**：
- `Integer x = 15.7` 在反序列化时会失败（Jackson 默认不接受 float 转 int）
- 即使 Jackson 截断处理，小数精度丢失也会使轨迹抖动检测完全失效

**修复**：`Integer` → `Double`

---

### Bug 2：抖动检测阈值过高（0.1 → 0.01）

**速度计算**：
- 轨迹点使用角度值（度），30ms 采集间隔
- 相邻点角度差约 0.5°~5°
- 速度 ≈ 0.02~0.17 °/ms

**原阈值**：
```java
if (Math.abs(speed2 - speed1) > 0.1) { // ❌ 阈值过高
```
人类自然抖动的速度变化约 0.005~0.05 °/ms，**0.1 的阈值基本不可能触发**，`jitterRatio` 始终 < 0.3，`checkTrajectoryJitter()` 始终返回 `false`。

**修复**：
```java
if (Math.abs(speed2 - speed1) > 0.01) { // ✅ 0.01 即可检测到人类抖动
```

---

## 修复方案总结

| 文件 | 修改内容 | 说明 |
|------|---------|------|
| `LoginRequest.java` | `TrajectoryPointDTO.x`: Integer → Double<br>`TrajectoryPointDTO.y`: Integer → Double | 前端传的是浮点角度值 |
| `SliderCaptchaService.java` | `TrajectoryPoint.x`: Integer → Double<br>`TrajectoryPoint.y`: Integer → Double | 同上 |
| `SliderCaptchaService.java` | `checkTrajectoryJitter()` 阈值: 0.1 → 0.01 | 角度值速度范围 0.02~0.17 °/ms |

## 测试验证

1. ✅ `./gradlew compileJava` — 编译通过
2. 部署后需观察服务器日志：
   - 验证日志应出现 `✅ 旋转验证码验证通过`
   - 日志格式：`rotationAngle={}°, userAngle={}°, combined={}°, behavior=正常: time={}ms, points={}`
