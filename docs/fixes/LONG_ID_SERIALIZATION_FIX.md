# Long类型ID序列化修复 - 避免JavaScript精度丢失

## 问题描述

雪花算法生成的Long类型ID（如`2000000000000000001`）超过了JavaScript的`Number.MAX_SAFE_INTEGER`（2^53-1 = 9007199254740991），导致前端接收时出现精度丢失。

例如：
- 后端返回：`2000000000000000001`
- 前端接收：`2000000000000000000`（精度丢失）

## 解决方案

采用**方案2：将Long类型ID序列化为String**，这是最简单且不需要修改数据库的方案。

### 修改内容

#### 1. RedisConfig.java
在Redis的ObjectMapper中注册Long转String的序列化器，确保存入Redis的对象中的Long类型ID也会被序列化为String。

```java
// 注册Long转String的序列化器，避免JavaScript精度丢失
SimpleModule simpleModule = new SimpleModule();
simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
objectMapper.registerModule(simpleModule);
```

#### 2. JacksonConfig.java（已存在）
项目中已有配置，将HTTP响应中的Long类型序列化为String：

```java
SimpleModule simpleModule = new SimpleModule();
simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
objectMapper.registerModule(simpleModule);
```

#### 3. UserCacheService.java（已修复）
添加智能类型判断和JSON转换逻辑，处理Redis反序列化时的类型问题：

```java
Object cachedValue = redisTemplate.opsForValue().get(key);
if (cachedValue != null) {
    User cachedUser;
    // 如果已经是User类型，直接返回
    if (cachedValue instanceof User) {
        cachedUser = (User) cachedValue;
    } else {
        // 如果是LinkedHashMap等其他类型，通过JSON转换
        String json = objectMapper.writeValueAsString(cachedValue);
        cachedUser = objectMapper.readValue(json, User.class);
    }
    return cachedUser;
}
```

## 效果

### 修改前
```json
{
  "id": 2000000000000000001,  // JavaScript会丢失精度
  "title": "文章标题"
}
```

### 修改后
```json
{
  "id": "2000000000000000001",  // 字符串形式，无精度丢失
  "title": "文章标题"
}
```

## 注意事项

1. **前端适配**：前端需要将ID作为字符串处理，不要进行数学运算
2. **数据库不变**：数据库中仍然使用Long类型存储，只在序列化时转为String
3. **兼容性**：所有Long类型字段都会转为String，包括userId、postId、categoryId等
4. **Redis缓存**：需要清除旧的缓存数据，让新序列化的数据生效

## 清除Redis缓存

修复后建议清除Redis中的旧缓存：

```bash
# 连接Redis
redis-cli

# 清除所有用户缓存
KEYS user:cache:*
DEL user:cache:1
DEL user:cache:2
# ... 或直接清空所有
FLUSHDB
```

## 相关文件

- `src/main/java/com/leo/myblog/config/RedisConfig.java` - Redis序列化配置
- `src/main/java/com/leo/myblog/config/JacksonConfig.java` - HTTP响应序列化配置
- `src/main/java/com/leo/myblog/service/UserCacheService.java` - 用户缓存服务
