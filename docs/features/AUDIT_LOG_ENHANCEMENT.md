# 审计日志系统增强 - 设备信息展示

## 📅 更新时间：2026-05-14

---

## ⚠️ 状态说明（2026-09-26 核对）

本文是 **2026-05-14「设备信息展示」这次改造的记录**，不是审计日志的最新全貌。
最新的现状说明以 [AUDIT_LOG_SYSTEM.md](AUDIT_LOG_SYSTEM.md) 为准。

| 部分 | 状态 |
|---|---|
| 设备字段（`deviceType`/`osName`/`browserName`）、空值转 `N/A`、`N/A` 展示约定 | ✅ 仍有效 |
| `responseStatus` 的含义 | ❌ 已变：现在是**业务返回码**，不再是 HTTP 状态码（下方示例中的 `401` 属旧语义） |
| `extraData` 的内容 | ❌ 已变：**不再包含整串 `userAgent`**（`user_agent` 列已有），另外新增了 `androidRemark`/`androidDescription` |
| 「密码脱敏」相关描述 | ❌ 已重写：原先只按形参名判断、实际完全失效，现由 `AuditParamSanitizer` 递归按字段名打码 |
| 请求参数记录范围 | ⚠️ 已扩大/收缩：新增「等级」维度、`@RequireTotp` 接口纳入审计、`LoginController.pollResult` 刻意排除 |
| 下方「🔮 未来优化」 | ⏳ 仍是**设想**，尚未落地 |

---

## 🎯 功能概述

本次更新增强了审计日志系统的显示字段，主要改进包括：

1. **添加设备信息字段** - 显示客户端类型、操作系统、浏览器名称
2. **空值处理** - 所有空字段统一显示为 "N/A"
3. **完整User-Agent展示** - 保留原始UA字符串供详细分析
4. **请求参数记录** - 显示API调用的具体参数

---

## ✨ 新增字段

### AuditLogResponse 响应字段

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `deviceType` | String | 客户端类型 | DESKTOP / MOBILE / TABLET / N/A |
| `osName` | String | 操作系统名称 | Windows 10 / macOS / Linux / N/A |
| `browserName` | String | 浏览器/客户端名称 | Chrome / Firefox / Safari / N/A |
| `userAgent` | String | 完整User-Agent字符串 | Mozilla/5.0... |
| `requestParams` | String | 请求参数（JSON，已脱敏） | {"username":"admin"} |
| `extraData` | String | 额外数据（JSON） | 仅含解析后的设备信息，**不含** `userAgent` |

> 2026-09 起 `AuditLogResponse` 还多了 `level`（等级）与 `responseMessage`（响应消息），
> 见 [AUDIT_LOG_SYSTEM.md](AUDIT_LOG_SYSTEM.md)。

### 空值处理规则

所有可能为空的字段，如果值为 `null` 或空字符串，统一返回 **"N/A"**：

- ✅ `username` → "N/A"（匿名用户操作）
- ✅ `ipAddress` → "N/A"（无法获取IP）
- ✅ `description` → "N/A"（无描述信息）
- ✅ `requestUrl` → "N/A"（无URL信息）
- ✅ `requestMethod` → "N/A"（无请求方法）
- ✅ `userAgent` → "N/A"（无UA信息）
- ✅ `deviceType` → "N/A"（未记录设备类型）
- ✅ `osName` → "N/A"（未记录操作系统）
- ✅ `browserName` → "N/A"（未记录浏览器）

---

## 🔧 技术实现

### 1. AuditLogResponse 增强

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {
    // 基础字段
    private Long id;
    private Long userId;
    private String username;
    private String operationType;
    private String module;
    private String description;
    
    // 网络信息
    private String ipAddress;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    
    // 设备信息（新增）
    private String deviceType;      // DESKTOP / MOBILE / TABLET
    private String osName;          // Windows / macOS / Linux
    private String browserName;     // Chrome / Firefox / Safari
    private String userAgent;       // 完整UA字符串
    
    // 响应信息
    private Integer responseStatus;
    private Boolean success;
    private Long executionTime;
    private String errorMessage;
    
    // 额外数据
    private String extraData;       // JSON格式，包含设备详情
    
    private LocalDateTime createTime;
}
```

### 2. 空值处理逻辑

```java
private AuditLogResponse convertToResponse(AuditLog auditLog) {
    AuditLogResponse response = new AuditLogResponse();
    BeanUtils.copyProperties(auditLog, response);
    
    // 处理空值，将null转换为"N/A"
    if (response.getUsername() == null || response.getUsername().isEmpty()) {
        response.setUsername("N/A");
    }
    if (response.getIpAddress() == null || response.getIpAddress().isEmpty()) {
        response.setIpAddress("N/A");
    }
    // ... 其他字段类似处理
    
    // 从extraData中解析设备信息
    parseDeviceInfo(response, auditLog.getExtraData());
    
    return response;
}
```

### 3. 设备信息解析

```java
private void parseDeviceInfo(AuditLogResponse response, String extraData) {
    if (extraData == null || extraData.isEmpty()) {
        response.setDeviceType("N/A");
        response.setOsName("N/A");
        response.setBrowserName("N/A");
        return;
    }
    
    try {
        // 解析JSON格式的extraData
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> extraMap = objectMapper.readValue(extraData, Map.class);
        
        // 提取设备信息
        String deviceType = (String) extraMap.get("deviceType");
        String osName = (String) extraMap.get("osName");
        String browserName = (String) extraMap.get("browserName");
        
        response.setDeviceType(deviceType != null && !deviceType.isEmpty() ? deviceType : "N/A");
        response.setOsName(osName != null && !osName.isEmpty() ? osName : "N/A");
        response.setBrowserName(browserName != null && !browserName.isEmpty() ? browserName : "N/A");
    } catch (Exception e) {
        log.debug("解析设备信息失败: {}", extraData);
        response.setDeviceType("N/A");
        response.setOsName("N/A");
        response.setBrowserName("N/A");
    }
}
```

---

## 📊 API 响应示例

### 1. 登录操作（包含完整设备信息）

**请求**:
```
GET /api/system/audit-logs/1234567890
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "1234567890",
    "userId": "1001",
    "username": "admin",
    "operationType": "LOGIN",
    "module": "USER",
    "description": "用户登录成功",
    "ipAddress": "192.168.1.100",
    "deviceType": "DESKTOP",
    "osName": "Windows 10",
    "browserName": "Chrome 120.0",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "requestUrl": "/api/users/login",
    "requestMethod": "POST",
    "requestParams": "{\"username\":\"admin\"}",
    "responseStatus": 200,
    "success": true,
    "executionTime": 45,
    "errorMessage": null,
    "extraData": "{\"deviceType\":\"DESKTOP\",\"osName\":\"Windows 10\",\"browserName\":\"Chrome 120.0\",\"userAgent\":\"Mozilla/5.0...\"}",
    "createTime": "2026-05-14T10:30:00"
  }
}
```

### 2. 匿名操作（部分字段为N/A）

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "1234567891",
    "userId": null,
    "username": "N/A",
    "operationType": "LOGIN",
    "module": "USER",
    "description": "登录失败：用户名或密码错误",
    "ipAddress": "10.0.0.50",
    "deviceType": "N/A",
    "osName": "N/A",
    "browserName": "N/A",
    "userAgent": "N/A",
    "requestUrl": "/api/users/login",
    "requestMethod": "POST",
    "requestParams": null,
    "responseStatus": 401,
    "success": false,
    "executionTime": 12,
    "errorMessage": "用户名或密码错误",
    "extraData": null,
    "createTime": "2026-05-14T10:35:00"
  }
}
```

### 3. 分页查询列表

**请求**:
```
GET /api/system/audit-logs?pageNum=1&pageSize=10&username=admin
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": "1234567890",
        "username": "admin",
        "operationType": "LOGIN",
        "ipAddress": "192.168.1.100",
        "deviceType": "DESKTOP",
        "osName": "Windows 10",
        "browserName": "Chrome 120.0",
        "success": true,
        "createTime": "2026-05-14T10:30:00"
      },
      {
        "id": "1234567889",
        "username": "admin",
        "operationType": "UPDATE",
        "module": "POST",
        "description": "修改了文章标题",
        "ipAddress": "192.168.1.100",
        "deviceType": "DESKTOP",
        "osName": "Windows 10",
        "browserName": "Chrome 120.0",
        "success": true,
        "createTime": "2026-05-14T09:15:00"
      }
    ],
    "total": 2,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

## 🔍 使用场景

### 1. 安全审计

管理员可以通过设备信息识别异常登录：

```json
{
  "username": "admin",
  "operationType": "LOGIN",
  "ipAddress": "203.0.113.50",
  "deviceType": "MOBILE",
  "osName": "Android 12",
  "browserName": "Chrome Mobile",
  "createTime": "2026-05-14T03:00:00"
}
```

⚠️ **异常检测**：如果管理员通常在桌面端登录，突然出现移动端登录，可能是账号被盗。

### 2. 故障排查

通过完整的请求信息定位问题：

```json
{
  "username": "user123",
  "operationType": "CREATE",
  "module": "POST",
  "requestUrl": "/api/posts",
  "requestMethod": "POST",
  "requestParams": "{\"title\":\"测试文章\",\"content\":\"...\"}",
  "responseStatus": 500,
  "success": false,
  "errorMessage": "数据库连接超时",
  "executionTime": 30000
}
```

### 3. 用户行为分析

统计不同设备的使用情况：

```sql
-- 查询各设备类型的登录次数
SELECT 
    extra_data->>'deviceType' as device_type,
    COUNT(*) as login_count
FROM t_audit_log
WHERE operation_type = 'LOGIN'
GROUP BY device_type
ORDER BY login_count DESC;
```

结果：
```
device_type | login_count
------------+------------
DESKTOP     | 1250
MOBILE      | 430
TABLET      | 85
N/A         | 12
```

---

## 📝 相关文档

- [AUDIT_LOG_SYSTEM.md](AUDIT_LOG_SYSTEM.md) - 审计日志系统完整指南
- [LOGIN_DEVICE_TRACKING.md](../security/LOGIN_DEVICE_TRACKING.md) - 登录设备信息记录
- [COMPLETED_FEATURES.md](COMPLETED_FEATURES.md) - 已完成功能清单

---

## 🎯 最佳实践

### 1. 前端展示建议

```javascript
// Vue.js 示例
<template>
  <el-table :data="auditLogs">
    <el-table-column prop="username" label="用户名" />
    <el-table-column prop="operationType" label="操作类型" />
    <el-table-column label="设备信息">
      <template #default="{ row }">
        <el-tag v-if="row.deviceType !== 'N/A'" size="small">
          {{ row.deviceType }}
        </el-tag>
        <span v-if="row.osName !== 'N/A'">{{ row.osName }}</span>
        <span v-if="row.browserName !== 'N/A'">{{ row.browserName }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="ipAddress" label="IP地址" />
    <el-table-column prop="createTime" label="时间" />
  </el-table>
</template>
```

### 2. 后端查询优化

```java
// 按设备类型筛选
@GetMapping("/audit-logs")
public Result<Page<AuditLogResponse>> getAuditLogs(
        @RequestParam(required = false) String deviceType) {
    
    GetAuditLogRequest request = new GetAuditLogRequest();
    // ... 其他参数
    
    Page<AuditLogResponse> logs = auditLogService.getAuditLogs(request);
    
    // 如果需要按设备类型过滤，在Service层处理
    if (deviceType != null) {
        logs.setRecords(logs.getRecords().stream()
            .filter(log -> deviceType.equals(log.getDeviceType()))
            .collect(Collectors.toList()));
    }
    
    return Result.success(logs);
}
```

### 3. 数据导出

```java
// 导出CSV时处理N/A值
public String exportToCsv(List<AuditLogResponse> logs) {
    StringBuilder csv = new StringBuilder();
    csv.append("ID,Username,Operation,IP,Device,OS,Browser,Time\n");
    
    for (AuditLogResponse log : logs) {
        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
            log.getId(),
            log.getUsername(),
            log.getOperationType(),
            log.getIpAddress(),
            log.getDeviceType().equals("N/A") ? "" : log.getDeviceType(),
            log.getOsName().equals("N/A") ? "" : log.getOsName(),
            log.getBrowserName().equals("N/A") ? "" : log.getBrowserName(),
            log.getCreateTime()
        ));
    }
    
    return csv.toString();
}
```

---

## ⚙️ 配置说明

### 1. Jackson 配置

确保 `application.yml` 中配置了正确的JSON序列化：

```yaml
spring:
  jackson:
    default-property-inclusion: non_null  # 不输出null字段
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
```

### 2. 设备信息记录

在 `AuditLogAspect` 中，登录操作会自动记录设备信息到 `extraData`。
注意 `userAgent` **不再写进 `extraData`**（`user_agent` 列已有，重复写会让每行膨胀一倍），
且写库前会统一过一遍脱敏：

```java
/** 只有登录类操作才需要设备信息 */
private boolean isDeviceInfoRelevant(AuditLog auditLog) {
    return "LOGIN".equals(auditLog.getOperationType());
}

private String buildDeviceInfo(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null || userAgent.isEmpty()) {
        return null;
    }
    Map<String, String> deviceInfo = new HashMap<>();
    UserAgentParser.DeviceInfo parsedInfo = userAgentParser.parseUserAgent(userAgent);
    deviceInfo.put("deviceType", parsedInfo.getDeviceType());
    deviceInfo.put("osName", parsedInfo.getOsName());
    deviceInfo.put("browserName", parsedInfo.getBrowserName());

    if ("Android".equals(parsedInfo.getOsName())) {
        AndroidUaDetector.AndroidDeviceInfo androidInfo = androidUaDetector.detect(userAgent);
        if (androidInfo != null) {
            deviceInfo.put("androidRemark", androidInfo.getRemark());
            deviceInfo.put("androidDescription", androidInfo.getDescription());
        }
    }
    // 统一过一遍脱敏，防止将来有人往这里塞凭据
    return objectMapper.writeValueAsString(paramSanitizer.mask(objectMapper.valueToTree(deviceInfo)));
}
```

---

## 📊 数据统计

### 字段覆盖率统计

> 下表是 2026-05-14 当时的**估算值**，非实时统计。要准确的分布请查
> `GET /api/system/audit-logs/stats`（`moduleStats`/`levelStats`）。

| 字段 | 有值比例 | N/A比例 | 说明 |
|------|---------|---------|------|
| username | 95% | 5% | 匿名操作时为N/A |
| ipAddress | 98% | 2% | 代理场景可能获取失败 |
| deviceType | 85% | 15% | 仅登录操作记录 |
| osName | 85% | 15% | 仅登录操作记录 |
| browserName | 85% | 15% | 仅登录操作记录 |
| userAgent | 90% | 10% | 部分请求无UA |

---

## 🔮 未来优化

> 以下均为**设想**，尚未实现（2026-09-26 核对）。

1. **实时设备识别** - 集成更精准的UA解析库（如ua-parser）
2. **地理位置追踪** - 根据IP地址显示登录地点
3. **异常检测告警** - 异地登录、新设备登录自动告警
4. **可视化展示** - 设备分布饼图、登录热力图
5. **行为分析** - 用户操作习惯分析、风险评分

---

**维护者**: Leo  
**最后更新**: 2026-05-14（内容）/ 2026-09-26（加状态说明并修正已失效之处）  
**版本**: v1.1.0
