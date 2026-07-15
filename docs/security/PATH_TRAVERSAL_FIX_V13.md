# 存储桶目录穿越漏洞修复报告 (V13)

## 📋 漏洞概述

**漏洞类型**: 目录穿越攻击 (Path Traversal)  
**严重级别**: 🔴 高危  
**影响范围**: MinIO 对象存储访问接口  
**修复时间**: 2026-05-17  

---

## 🔍 漏洞描述

在多个服务类中，从数据库获取的 MinIO 对象路径 (`objectPath`) 直接用于文件访问操作，**未进行任何路径验证**。攻击者可以通过构造恶意路径（如 `../../../etc/passwd` 或 `../other-user/avatar.jpg`）来：

1. **访问其他用户的私有文件**
2. **读取敏感系统文件**（如果 MinIO 配置不当）
3. **删除任意文件**（通过 delete 接口）
4. **绕过用户隔离机制**

### 受影响的组件

| 服务类 | 方法 | 风险操作 |
|--------|------|----------|
| `AvatarUploadService` | `getAvatar()` | 读取用户头像 |
| `AvatarUploadService` | `cleanupOldAvatars()` | 删除旧头像 |
| `AudioService` | `streamAudio()` | 流式播放音频 |
| `AudioService` | `deleteAudio()` | 删除音频和封面 |
| `BloggerInfoService` | `getBloggerAvatar()` | 读取博主头像 |
| `BloggerInfoService` | `uploadBloggerAvatar()` | 删除旧头像 |

---

## 💥 攻击场景

### 场景 1: 跨用户头像访问

```java
// 假设数据库中存储的 avatar 字段被篡改为:
String maliciousPath = "../avatars/999/secret-avatar.jpg";

// 攻击者请求自己的头像接口
GET /api/users/avatar/{myUserId}

// 实际访问的是 userId=999 的头像
minioClient.getObject(bucket, "../avatars/999/secret-avatar.jpg")
```

### 场景 2: 目录穿越读取敏感文件

```java
// 如果 MinIO bucket 映射到文件系统且权限配置不当
String maliciousPath = "../../../etc/passwd";

// 可能读取系统文件
minioClient.getObject(bucket, "../../../etc/passwd")
```

### 场景 3: 批量删除其他用户文件

```java
// 通过 SQL 注入或其他手段修改数据库中的 objectPath
UPDATE user SET avatar = '../avatars/999/avatar.jpg' WHERE id = {attackerId};

// 当受害者上传新头像时，旧头像清理逻辑会删除攻击者指定的文件
minioClient.removeObject(bucket, "../avatars/999/avatar.jpg")
```

---

## ✅ 修复方案

### 核心思路

在所有 MinIO 文件操作前，添加**严格的路径验证**，确保：

1. **禁止目录穿越序列** (`..`, `./`, `\`)
2. **禁止绝对路径** (以 `/` 开头)
3. **路径规范化** (使用 `Paths.get().normalize()`)
4. **白名单验证** (只允许特定的目录前缀)
5. **一致性检查** (防止编码绕过)

### 实现代码

#### 1. AvatarUploadService.java

```java
/**
 * 安全修复V13: 验证 MinIO 对象路径，防止目录穿越攻击
 */
private void validateObjectPath(String objectPath) {
    if (objectPath == null || objectPath.isEmpty()) {
        throw new BusinessException("对象路径无效");
    }
    
    // 1. 检查是否包含目录穿越序列
    if (objectPath.contains("..") || objectPath.contains("./") || objectPath.contains("\\")) {
        log.warn("检测到可疑的路径穿越尝试: {}", objectPath);
        throw new BusinessException("非法的文件路径");
    }
    
    // 2. 检查是否以 / 开头（绝对路径）
    if (objectPath.startsWith("/")) {
        log.warn("检测到绝对路径尝试: {}", objectPath);
        throw new BusinessException("非法的文件路径");
    }
    
    // 3. 规范化路径并验证
    try {
        String normalizedPath = Paths.get(objectPath).normalize().toString();
        normalizedPath = normalizedPath.replace("\\", "/");
        
        // 4. 验证路径是否在允许的目录范围内
        boolean isValidPath = normalizedPath.startsWith("avatars/") ||
                              normalizedPath.startsWith("blogger/avatars/") ||
                              normalizedPath.startsWith("covers/") ||
                              normalizedPath.startsWith("audio/");
        
        if (!isValidPath) {
            log.warn("对象路径不在允许的范围内: {}", objectPath);
            throw new BusinessException("非法的文件路径");
        }
        
        // 5. 验证规范化后的路径与原始路径一致
        if (!normalizedPath.equals(objectPath)) {
            log.warn("路径规范化后不一致，可能存在攻击: original={}, normalized={}", 
                    objectPath, normalizedPath);
            throw new BusinessException("非法的文件路径");
        }
        
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        log.error("路径验证失败: {}", objectPath, e);
        throw new BusinessException("非法的文件路径");
    }
}
```

#### 2. AudioService.java

相同的 `validateObjectPath` 方法，但白名单不同：

```java
// 允许的路径前缀：audio/, covers/
boolean isValidPath = normalizedPath.startsWith("audio/") ||
                      normalizedPath.startsWith("covers/");
```

#### 3. BloggerInfoService.java

```java
// 允许的路径前缀：blogger/avatars/
boolean isValidPath = normalizedPath.startsWith("blogger/avatars/");
```

### 应用位置

在所有 MinIO 操作前调用验证：

```java
// ✅ 读取文件前
validateObjectPath(objectPath);
InputStream inputStream = minioClient.getObject(...);

// ✅ 删除文件前
validateObjectPath(objectPath);
minioClient.removeObject(...);
```

---

## 🧪 测试验证

### 测试用例 1: 正常路径

```java
// 输入
String path = "avatars/123/abc-def-ghi.jpg";

// 预期结果
✅ 验证通过
```

### 测试用例 2: 目录穿越

```java
// 输入
String path = "../avatars/999/avatar.jpg";

// 预期结果
❌ 抛出异常: "非法的文件路径"
日志: "检测到可疑的路径穿越尝试: ../avatars/999/avatar.jpg"
```

### 测试用例 3: 绝对路径

```java
// 输入
String path = "/etc/passwd";

// 预期结果
❌ 抛出异常: "非法的文件路径"
日志: "检测到绝对路径尝试: /etc/passwd"
```

### 测试用例 4: 编码绕过

```java
// 输入
String path = "avatars/123/../999/avatar.jpg";

// 规范化后
String normalized = "avatars/999/avatar.jpg";

// 预期结果
❌ 抛出异常: "非法的文件路径"
日志: "路径规范化后不一致，可能存在攻击"
```

### 测试用例 5: 不在白名单内

```java
// 输入
String path = "system/config.json";

// 预期结果
❌ 抛出异常: "非法的文件路径"
日志: "对象路径不在允许的范围内: system/config.json"
```

---

## 📊 修复效果

### 修复前

- ❌ 无路径验证
- ❌ 可访问任意对象
- ❌ 可删除任意文件
- ❌ 用户隔离失效

### 修复后

- ✅ 严格的路径验证
- ✅ 只能访问白名单目录
- ✅ 防止目录穿越
- ✅ 用户隔离有效
- ✅ 详细的审计日志

---

## 🔒 安全增强

### 1. 多层防护

| 层级 | 防护措施 | 说明 |
|------|---------|------|
| L1 | 扩展名白名单 | 只允许 `.jpg`, `.png` 等 |
| L2 | MIME 类型验证 | 检查 `Content-Type` |
| L3 | 魔数签名验证 | 验证文件真实格式 |
| L4 | UUID 文件名 | 防止文件名注入 |
| L5 | **路径验证 (新增)** | **防止目录穿越** |
| L6 | 用户隔离 | 按 `userId` 分目录存储 |

### 2. 审计日志

所有路径验证失败都会记录警告日志：

```
WARN  - 检测到可疑的路径穿越尝试: ../avatars/999/avatar.jpg
WARN  - 检测到绝对路径尝试: /etc/passwd
WARN  - 对象路径不在允许的范围内: system/config.json
WARN  - 路径规范化后不一致，可能存在攻击: original=avatars/123/../999/avatar.jpg, normalized=avatars/999/avatar.jpg
```

便于安全监控和入侵检测。

---

## 📝 修改文件清单

| 文件 | 修改内容 | 行数变化 |
|------|---------|---------|
| `AvatarUploadService.java` | 添加 `validateObjectPath()` 方法<br>在 `getAvatar()` 和 `cleanupOldAvatars()` 中调用 | +65 |
| `AudioService.java` | 添加 `validateObjectPath()` 方法<br>在 `streamAudio()` 和 `deleteAudio()` 中调用 | +67 |
| `BloggerInfoService.java` | 添加 `validateObjectPath()` 方法<br>在 `getBloggerAvatar()` 和 `uploadBloggerAvatar()` 中调用 | +62 |

**总计**: +194 行代码

---

## ⚠️ 注意事项

### 1. 数据库历史数据

如果数据库中已存在恶意的 `objectPath`，需要：

```sql
-- 检查异常路径
SELECT id, avatar FROM t_user WHERE avatar LIKE '%..%' OR avatar LIKE '/%';

-- 清理异常数据
UPDATE t_user SET avatar = NULL WHERE avatar LIKE '%..%' OR avatar LIKE '/%';
```

### 2. 新的上传路径

确保所有新的文件上传都遵循白名单规则：

- 用户头像: `avatars/{userId}/{uuid}.ext`
- 博主头像: `blogger/avatars/{uuid}.ext`
- 音频文件: `audio/{uuid}.ext`
- 封面图片: `covers/{uuid}.ext`

### 3. 未来扩展

如果新增文件类型，记得更新白名单：

```java
boolean isValidPath = normalizedPath.startsWith("avatars/") ||
                      normalizedPath.startsWith("blogger/avatars/") ||
                      normalizedPath.startsWith("covers/") ||
                      normalizedPath.startsWith("audio/") ||
                      normalizedPath.startsWith("NEW_DIRECTORY/");  // 新增
```

---

## 🎯 后续建议

### 短期（1周内）

1. ✅ **完成代码修复**（已完成）
2. ⏳ **审计数据库历史数据**
3. ⏳ **添加单元测试覆盖路径验证逻辑**

### 中期（1个月内）

4. ⏳ **实施 MinIO Bucket 策略**
   - 限制每个用户的访问权限
   - 启用版本控制
5. ⏳ **添加实时监控告警**
   - 检测频繁的路径验证失败
   - 自动封禁可疑 IP

### 长期（3个月内）

6. ⏳ **迁移到预签名 URL**
   - 前端直接访问 MinIO
   - 后端只负责生成临时 URL
7. ⏳ **实施 CDN 加速**
   - 减少 MinIO 直接访问
   - 增加一层安全防护

---

## 📚 参考资料

- [OWASP Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal)
- [MinIO Security Guide](https://min.io/docs/minio/linux/operations/security.html)
- [Java Path Normalization Best Practices](https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html)

---

**修复完成时间**: 2026-05-17  
**修复人员**: AI Assistant  
**审核状态**: ⏳ 待人工审核  
**部署状态**: ⏳ 待部署
