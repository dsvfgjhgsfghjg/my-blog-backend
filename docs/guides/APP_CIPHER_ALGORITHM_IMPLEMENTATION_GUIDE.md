# App 加密算法实现指南

## 概述

本文档指导 App 开发者如何实现完整的 4 种加密算法切换支持。后端已完整支持所有算法，App 端需要实现 UI 切换和对应的加解密逻辑。

## 支持的加密算法

| 算法名称 | 密钥长度 | 密钥派生取前N字节 | 说明 |
|---------|---------|-----------------|------|
| AES-256-GCM | 256 bit | SHA-256 前 32 字节 | 默认算法，安全性最高 |
| AES-192-GCM | 192 bit | SHA-256 前 24 字节 | 兼容性较好 |
| AES-128-GCM | 128 bit | SHA-256 前 16 字节 | 性能最优 |
| SM4-GCM | 128 bit | SHA-256 前 16 字节 | 国密标准 |

## 密钥派生规则

DH 密钥交换产生的 2048 位共享密钥 → SHA-256 哈希 → 取前 N 字节作为加密密钥：

```
sharedSecret(2048bit) → SHA-256 → takeFirst(N bytes) → AES/SM4 Key
```

- AES-256-GCM: N = 32 (256/8)
- AES-192-GCM: N = 24 (192/8)
- AES-128-GCM: N = 16 (128/8)
- SM4-GCM: N = 16 (128/8)

## 后端 API

### 获取可用算法列表

```
GET /api/auth/app/cipher-info
Authorization: Bearer <token>
```

返回示例：
```json
{
  "code": 200,
  "data": {
    "keyExchange": {
      "algorithm": "DH",
      "keySize": 2048,
      "group": "MODP Group (RFC 3526)",
      "hash": "SHA-256"
    },
    "dataEncryption": {
      "algorithm": "AES-256-GCM",
      "keySize": 256,
      "ivLength": 12,
      "tagLength": 16,
      "mode": "GCM",
      "padding": "NoPadding"
    },
    "availableAlgorithms": [
      { "name": "DH-2048", "type": "key_exchange", "keySize": 2048, "description": "..." },
      { "name": "AES-256-GCM", "type": "data_encryption", "keySize": 256, "description": "..." },
      { "name": "AES-192-GCM", "type": "data_encryption", "keySize": 192, "description": "..." },
      { "name": "AES-128-GCM", "type": "data_encryption", "keySize": 128, "description": "..." },
      { "name": "SM4-GCM", "type": "data_encryption", "keySize": 128, "description": "..." }
    ],
    "deviceCipherPref": "AES-256-GCM"
  }
}
```

### 切换设备加密算法

```
PUT /api/auth/app/devices/{deviceId}/cipher
Authorization: Bearer <token>
Content-Type: application/json

{
  "cipherPref": "AES-192-GCM"
}
```

## App 端实现步骤

### 1. 添加 UI 选项

在 `strings.xml` 的 `cipher_options` 数组中添加新算法：

```xml
<string-array name="cipher_options">
    <item>AES-256-GCM</item>
    <item>AES-192-GCM</item>
    <item>AES-128-GCM</item>
    <item>SM4-GCM</item>
</string-array>
```

### 2. 更新 Spinner 位置映射

将硬编码的算法位置判断改为通用映射函数：

```kotlin
private fun getCipherPosition(cipher: String?): Int {
    return when (cipher) {
        "AES-256-GCM" -> 0
        "AES-192-GCM" -> 1
        "AES-128-GCM" -> 2
        "SM4-GCM" -> 3
        else -> 0
    }
}
```

### 3. DH 密钥派生动态选择 keySize

在 `handleDhInit()` 中，根据 `cipherAlgo` 动态选择密钥长度：

```kotlin
val keySize = when (cipherAlgo) {
    "AES-128-GCM" -> 128
    "AES-192-GCM" -> 192
    "AES-256-GCM" -> 256
    "SM4-GCM" -> 128
    else -> 256
}
aesKey = KeyDerivation.deriveAesKey(sharedSecret, keySize)
```

### 4. 加解密分支支持所有 AES 变体

解密时：
```kotlin
val decryptedBytes = when (cipherAlgo) {
    "AES-256-GCM", "AES-192-GCM", "AES-128-GCM" -> AesGcmCrypto.decrypt(aesKey!!, iv, ciphertext)
    "SM4-GCM" -> Sm4GcmCrypto.decrypt(aesKey!!, iv, ciphertext)
    else -> throw IllegalStateException("不支持的加密算法: $cipherAlgo")
}
```

加密时同理：
```kotlin
val iv = when (cipherAlgo) {
    "AES-256-GCM", "AES-192-GCM", "AES-128-GCM" -> AesGcmCrypto.generateIv()
    "SM4-GCM" -> Sm4GcmCrypto.generateIv()
    else -> throw IllegalStateException("不支持的加密算法: $cipherAlgo")
}

val encryptedBytes = when (cipherAlgo) {
    "AES-256-GCM", "AES-192-GCM", "AES-128-GCM" -> AesGcmCrypto.encrypt(aesKey!!, iv, plaintext.toByteArray(Charsets.UTF_8))
    "SM4-GCM" -> Sm4GcmCrypto.encrypt(aesKey!!, iv, plaintext.toByteArray(Charsets.UTF_8))
    else -> throw IllegalStateException("不支持的加密算法: $cipherAlgo")
}
```

## 本地部署测试

### 后端本地运行

```bash
# 1. 克隆后端项目
git clone http://113.31.105.30:3000/leoxiao/backend.git
cd backend

# 2. 配置数据库连接（修改 application.yml）
# 确保 MySQL 和 Redis 已启动

# 3. 运行
./gradlew bootRun
```

### Android App 本地构建

```bash
# 1. 克隆 Android 项目
git clone http://113.31.105.30:3000/leoxiao/my-authenticator.git
cd my-authenticator

# 2. 修改 NetworkConfig.kt 中的服务器地址为本地地址
# 例如: const val BASE_URL = "http://10.0.2.2:8080"  (Android 模拟器访问宿主机)

# 3. 使用 Android Studio 打开项目
# File → Open → 选择 my-authenticator 目录

# 4. 构建并运行
# Run → Run 'app'
```

### 验证步骤

1. 打开 App 设置页面
2. 在加密算法下拉框中应看到 4 个选项：AES-256-GCM、AES-192-GCM、AES-128-GCM、SM4-GCM
3. 切换到 AES-192-GCM 或 AES-128-GCM
4. 断开 WebSocket 重新连接
5. 验证连接成功，且服务端 dh_ready 消息返回的 cipher 与选择的算法一致
6. 触发一次认证挑战，验证加解密正常

## 常见问题

### Q: 切换算法后需要重新连接 WebSocket 吗？
A: 是的。切换算法后，需要断开 WebSocket 重新连接，新的 DH 密钥交换会使用新算法派生对应长度的密钥。

### Q: AES-128/192/256-GCM 的加解密代码是否通用？
A: 是的。Java/Kotlin 的 `Cipher.getInstance("AES/GCM/NoPadding")` 会自动根据密钥长度选择对应的 AES 变体，无需修改加解密代码。

### Q: SM4-GCM 和 AES-128-GCM 密钥长度都是 128 位，有什么区别？
A: SM4 是中国国家密码标准算法，与 AES 算法结构不同。SM4 使用专门的 SM4 加密实现，不能使用 Java 内置的 AES 加密库。
