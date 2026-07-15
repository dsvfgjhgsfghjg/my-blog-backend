# 上传验证码背景图到 MinIO 指南

## 📋 前置条件

1. MinIO 服务正在运行
2. 已下载 20 张 310x155 的背景图片

---

## 🎯 方法一：使用 MinIO Web 控制台（最简单）

### 步骤 1：访问 MinIO 控制台

打开浏览器访问：http://localhost:9001

登录信息：
- **Username**: `Leoxiao`
- **Password**: `your-password-here`

### 步骤 2：创建 Bucket

1. 点击左侧菜单的 "Buckets"
2. 点击 "Create Bucket"
3. Bucket Name: `captcha-backgrounds`
4. 点击 "Create Bucket"

### 步骤 3：设置公开访问策略

1. 选择刚创建的 `captcha-backgrounds` bucket
2. 点击 "Access Policy"
3. 设置为 `public` 或添加以下策略：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::captcha-backgrounds/*"]
    }
  ]
}
```

### 步骤 4：上传图片

1. 进入 `captcha-backgrounds` bucket
2. 点击 "Upload" → "Upload file"
3. 选择所有 20 张图片（bg_001.jpg 到 bg_020.jpg）
4. 点击 "Upload"

### 步骤 5：验证上传

在浏览器中访问任意图片测试：
```
http://localhost:9000/captcha-backgrounds/bg_001.jpg
```

应该能看到图片显示。

---

## 🎯 方法二：使用 mc (MinIO Client)

### 安装 mc

```bash
# 下载 mc
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/
```

### 配置 MinIO

```bash
# 添加 MinIO 服务器
mc alias set myminio http://localhost:9000 Leoxiao your-password-here

# 创建 bucket
mc mb myminio/captcha-backgrounds

# 设置公开访问
mc anonymous set public myminio/captcha-backgrounds
```

### 上传图片

```bash
# 假设图片在 /tmp/captcha-backgrounds-download/ 目录
cd /tmp/captcha-backgrounds-download

# 批量上传
for i in {001..020}; do
  mc cp bg_${i}.jpg myminio/captcha-backgrounds/bg_${i}.jpg
done
```

### 验证

```bash
# 列出文件
mc ls myminio/captcha-backgrounds/
```

---

## 🎯 方法三：使用 curl 命令

如果你已经下载了图片到 `/tmp/captcha-backgrounds-download/`：

```bash
#!/bin/bash

MINIO_ENDPOINT="http://localhost:9000"
ACCESS_KEY="Leoxiao"
SECRET_KEY="your-password-here"
BUCKET="captcha-backgrounds"
IMAGE_DIR="/tmp/captcha-backgrounds-download"

for i in {001..020}; do
  FILE="$IMAGE_DIR/bg_${i}.jpg"
  if [ -f "$FILE" ]; then
    echo "上传: bg_${i}.jpg"
    curl -X PUT "$MINIO_ENDPOINT/$BUCKET/bg_${i}.jpg" \
      -u "$ACCESS_KEY:$SECRET_KEY" \
      -H "Content-Type: image/jpeg" \
      --data-binary "@$FILE"
    echo ""
  fi
done
```

---

## 🎯 方法四：先下载图片再上传

### 步骤 1：下载图片

```bash
cd /home/leo/IdeaProjects/myblog

# 创建临时目录
mkdir -p /tmp/captcha-backgrounds-download

# 下载 20 张图片
for i in {1..20}; do
  filename=$(printf "bg_%03d.jpg" $i)
  echo "下载: $filename"
  curl -L -o "/tmp/captcha-backgrounds-download/$filename" \
    "https://picsum.photos/310/155?random=$i"
  sleep 1
done
```

### 步骤 2：使用上述任一方法上传

推荐使用 **方法一（Web 控制台）**，最简单直观。

---

## ✅ 验证上传成功

### 测试 1：浏览器访问

访问以下 URL，应该能看到图片：
```
http://localhost:9000/captcha-backgrounds/bg_001.jpg
http://localhost:9000/captcha-backgrounds/bg_010.jpg
http://localhost:9000/captcha-backgrounds/bg_020.jpg
```

### 测试 2：使用 curl

```bash
curl -I http://localhost:9000/captcha-backgrounds/bg_001.jpg
```

应该返回 `HTTP/1.1 200 OK`

### 测试 3：检查应用日志

启动应用后，查看日志：
```
MinIO 背景图服务初始化完成，共加载 20 张图片
```

---

## 🔧 配置应用从 MinIO 加载

### 修改 CaptchaBackgroundService

编辑 `CaptchaBackgroundService.java`，优先从 MinIO 加载：

```java
@Autowired(required = false)
private MinioBackgroundService minioBackgroundService;

public BufferedImage getRandomBackground() {
    // 1. 优先从 MinIO 加载
    if (minioBackgroundService != null && minioBackgroundService.getImageCount() > 0) {
        BufferedImage img = minioBackgroundService.getRandomBackground();
        if (img != null) {
            return img;
        }
    }
    
    // 2. 其次使用本地图片
    if (!backgroundCache.isEmpty()) {
        int index = random.nextInt(backgroundCache.size());
        return backgroundCache.get(index);
    }
    
    // 3. 降级：程序生成
    return ProceduralBackgroundGenerator.generateArtisticBackground();
}
```

---

## 📊 图片要求回顾

- **尺寸**: 310 × 155 像素
- **格式**: JPG
- **数量**: 20 张
- **命名**: bg_001.jpg, bg_002.jpg, ..., bg_020.jpg
- **内容**: 风景、建筑、抽象图案等（避免人脸和文字）

---

## 💡 推荐流程

**最简单的完整流程**：

1. ✅ 使用脚本下载图片：
   ```bash
   ./scripts/download-captcha-backgrounds.sh
   ```

2. ✅ 打开 MinIO Web 控制台：http://localhost:9001

3. ✅ 创建 bucket `captcha-backgrounds`

4. ✅ 通过 Web 界面上传所有图片

5. ✅ 重启应用，验证日志

---

## 🎨 后续优化

一旦图片上传成功，你可以：

1. **更新 CaptchaBackgroundService** 支持从 MinIO 加载
2. **添加缓存机制** 避免每次都从 MinIO 读取
3. **实现自动刷新** 定期从 MinIO 重新加载图片列表
4. **添加监控** 统计图片加载成功率

---

**最后更新**: 2026-05-17  
**相关服务**: 
- MinIO: http://localhost:9000
- MinIO Console: http://localhost:9001
