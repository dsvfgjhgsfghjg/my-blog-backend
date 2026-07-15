# 粒子特效系统安全防护说明

## 🛡️ 安全威胁分析

### 潜在攻击向量

1. **XSS（跨站脚本攻击）**
   - 攻击者尝试注入 `<script>` 标签
   - 通过 `onerror`、`onload` 等事件处理器执行恶意代码
   - 示例：`<script>alert('XSS')</script>`

2. **SQL 注入**
   - 尝试通过特效名称注入 SQL 命令
   - 示例：`'; DROP TABLE user_preferences; --`

3. **路径遍历**
   - 尝试访问系统文件
   - 示例：`../../../etc/passwd`

4. **HTML/JavaScript 注入**
   - 注入 `<iframe>`、`<img>`、`<svg>` 等标签
   - 利用编码绕过简单过滤

---

## ✅ 防护机制（多层防御）

### 第一层：DTO 验证（格式校验）

**文件**: `SetParticleModeRequest.java`

```java
@Pattern(
    regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,49}$",
    message = "无效的粒子模式格式"
)
private String particleMode;
```

**作用**: 
- 在 Controller 层拦截明显非法的输入
- 只允许字母、数字、下划线，长度不超过 50
- 拒绝包含特殊字符（`<`, `>`, `'`, `"` 等）的输入

---

### 第二层：数据库白名单验证（核心防护）

**文件**: `t_particle_effect` 表 + `UserPreferenceService.java`

```sql
-- 白名单表结构
CREATE TABLE t_particle_effect (
    effect_code VARCHAR(50) NOT NULL UNIQUE,  -- 特效代码
    effect_name VARCHAR(100) NOT NULL,         -- 特效名称
    is_enabled BOOLEAN DEFAULT TRUE,           -- 是否启用
    ...
);
```

```java
// Service 层验证
LambdaQueryWrapper<ParticleEffect> effectWrapper = new LambdaQueryWrapper<>();
effectWrapper.eq(ParticleEffect::getEffectCode, particleMode)
             .eq(ParticleEffect::getIsEnabled, true);
ParticleEffect effect = particleEffectMapper.selectOne(effectWrapper);

if (effect == null) {
    throw new BusinessException("无效的粒子特效模式");
}
```

**作用**:
- **绝对白名单**：只接受数据库中配置且启用的特效
- **杜绝注入**：即使绕过正则，也无法通过数据库验证
- **动态管理**：添加新特效只需插入数据库，无需修改代码

---

### 第三层：Service 层二次验证

**文件**: `UserPreferenceService.java`

```java
@Transactional(rollbackFor = Exception.class)
public void setParticleMode(Long userId, SetParticleModeRequest request) {
    String particleMode = request.getParticleMode();
    
    // 严格的数据库白名单验证（防止XSS注入）
    LambdaQueryWrapper<ParticleEffect> effectWrapper = new LambdaQueryWrapper<>();
    effectWrapper.eq(ParticleEffect::getEffectCode, particleMode)
                 .eq(ParticleEffect::getIsEnabled, true);
    ParticleEffect effect = particleEffectMapper.selectOne(effectWrapper);
    
    if (effect == null) {
        log.warn("非法的粒子特效模式尝试: userId={}, particleMode={}", 
                 userId, particleMode);
        throw new BusinessException("无效的粒子特效模式");
    }
    
    log.debug("粒子特效白名单验证通过: {} ({})", 
              effect.getEffectCode(), effect.getEffectName());
    
    // ... 保存逻辑
}
```

**作用**:
- 业务逻辑层再次验证
- 记录可疑请求日志
- 统一异常处理

---

### 第四层：读取时验证（防御历史数据）

**文件**: `UserPreferenceService.java`

```java
public ParticleModeResponse getParticleMode(Long userId) {
    // ... 查询逻辑
    
    // 验证数据库中的值是否合法（防止历史恶意数据）
    String particleMode = preference.getParticleMode();
    if (!ParticleMode.isValid(particleMode)) {
        log.warn("发现非法的粒子特效数据: userId={}, particleMode={}，重置为默认值", 
                 userId, particleMode);
        // 自动修复：重置为默认值
        preference.setParticleMode(DEFAULT_PARTICLE_MODE);
        userPreferenceMapper.updateById(preference);
        return new ParticleModeResponse(DEFAULT_PARTICLE_MODE);
    }
    
    return new ParticleModeResponse(particleMode);
}
```

**作用**:
- 检测并修复数据库中可能存在的恶意数据
- 防止旧漏洞遗留的安全隐患
- 自动清理异常数据

---

## 🧪 安全测试

### XSS 注入测试脚本

```bash
./scripts/tests/test_xss_injection_particle.sh
```

**测试用例包括**:
1. `<script>alert("XSS")</script>`
2. `"><img src=x onerror=alert(1)>`
3. `javascript:alert('XSS')`
4. `<svg onload=alert(1)>`
5. `../../../etc/passwd`
6. `"; DROP TABLE user_preferences; --`
7. `<iframe src="javascript:alert(1)"></iframe>`
8. 以及各种编码变体

**预期结果**: 所有攻击均被阻止（返回 400 错误）

---

## 📋 添加新特效的安全流程

当需要添加新的粒子特效时，只需在数据库中插入记录：

### 1. 插入数据库记录

```sql
INSERT INTO t_particle_effect (effect_code, effect_name, description, is_enabled, sort_order) VALUES
('newEffect', '新特效名称', '特效描述', TRUE, 8);
```

### 2. 前端实现

- 在前端代码中添加对应的渲染逻辑
- **不要**直接渲染后端返回的字符串作为 HTML/JavaScript
- 使用映射表或 switch-case 处理不同的特效代码

### 3. 安全审查

- 确保新特效名称只包含字母、数字、下划线
- 不包含任何特殊字符（`<`, `>`, `'`, `"`, `/` 等）
- 长度不超过 50 个字符

---

## 🔒 最佳实践总结

### ✅ 正确做法

1. **数据库白名单**：严格限制可选值，动态管理
2. **多层验证**：DTO + Service + 数据库约束
3. **最小权限原则**：只允许必要的值
4. **日志记录**：记录所有可疑请求
5. **前端映射**：前端使用 ID/Code 映射，不直接使用后端字符串

### ❌ 错误做法

1. **黑名单过滤**：试图过滤危险字符（容易被绕过）
2. **直接拼接**：将用户输入直接拼接到 HTML/JS 中
3. **信任前端验证**：前端验证可以被绕过
4. **无服务端验证**：仅依赖数据库约束
5. **动态执行**：使用 `eval()` 或 `innerHTML` 执行用户输入

---

## 🚨 应急响应

如果发现安全漏洞：

1. **立即禁用相关接口**
2. **检查数据库是否有恶意数据**
   ```sql
   SELECT * FROM user_preferences up
   LEFT JOIN t_particle_effect pe ON up.particle_mode = pe.effect_code
   WHERE pe.effect_code IS NULL OR pe.is_enabled = false;
   ```
3. **清理恶意数据**
   ```sql
   UPDATE user_preferences 
   SET particle_mode = 'stardust'
   WHERE particle_mode IN (
       SELECT up.particle_mode FROM user_preferences up
       LEFT JOIN t_particle_effect pe ON up.particle_mode = pe.effect_code
       WHERE pe.effect_code IS NULL OR pe.is_enabled = false
   );
   ```
4. **修复代码并重新部署**
5. **审计日志，追踪攻击来源**

---

## 📚 参考资料

- [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [Spring Validation Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/validation.html)
- [Java Enum Best Practices](https://www.baeldung.com/a-guide-to-java-enums)

---

**最后更新**: 2026-05-14  
**维护者**: Leo  
**安全等级**: 🔴 高优先级
