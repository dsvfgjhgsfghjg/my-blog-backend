# 验证码图片上传到云端 MinIO 记录

## 📅 操作时间
**2026-05-17 12:12**

---

## ✅ 完成的工作

### 1. 上传验证码图片到云端

```bash
# 本地打包
tar czf captcha-images.tar.gz captcha-images/bg_*.jpg

# 上传到云端
scp captcha-images.tar.gz ubuntu@113.31.105.30:~/myblog-deploy/

# 云端解压
tar xzf captcha-images.tar.gz -C captcha-images --strip-components=1
```

**结果**：
- ✅ 200 张图片已上传到 `~/myblog-deploy/captcha-images/`
- ✅ 总大小：1.7MB

---

### 2. 安装 MinIO 客户端

```bash
# 下载 MinIO 客户端（使用国内镜像）
wget https://dl.minio.org.cn/client/mc/release/linux-amd64/mc -O /tmp/mc
chmod +x /tmp/mc
sudo mv /tmp/mc /usr/local/bin/mc

# 验证安装
mc --version
```

**版本信息**：
- mc version: RELEASE.2025-07-21T05-28-08Z
- Runtime: go1.24.5 linux/amd64

---

### 3. 配置 MinIO 客户端

```bash
# 配置 MinIO 别名（注意：用户名是小写）
mc alias set myminio http://localhost:9000 leoxiao your-password-here

# 验证配置
mc ls myminio
```

**⚠️ 重要提示**：
- Access Key: `leoxiao`（小写，不是 Leoxiao）
- Secret Key: `your-password-here`

---

### 4. 创建 Bucket

```bash
# 创建 bucket
mc mb myminio/captcha-backgrounds

# 设置为私有（防止被恶意利用）
mc anonymous set private myminio/captcha-backgrounds
```

**Bucket 信息**：
- 名称：`captcha-backgrounds`
- 权限：**私有**（只能通过后端 API 访问）
- 位置：MinIO 服务器 localhost:9000

---

### 5. 上传验证码图片到 MinIO

```bash
cd ~/myblog-deploy/captcha-images
mc cp bg_*.jpg myminio/captcha-backgrounds/
```

**上传统计**：
- 文件数量：**200 张**
- 总大小：**1.77 MiB**
- 传输速度：**5.05 MiB/s**
- 耗时：< 1 秒

---

## 📊 验证结果

### 检查上传的图片

```bash
# 统计数量
mc ls myminio/captcha-backgrounds/ | wc -l
# 输出: 200

# 查看前 10 张
mc ls myminio/captcha-backgrounds/ | head -10
```

**示例输出**：
```
[2026-05-17 12:12:49 CST] 5.8KiB STANDARD bg_001.jpg
[2026-05-17 12:12:49 CST] 8.6KiB STANDARD bg_002.jpg
[2026-05-17 12:12:49 CST]  12KiB STANDARD bg_003.jpg
...
```

### 检查 Bucket 权限

```bash
mc anonymous get myminio/captcha-backgrounds/
# 输出: Access permission for `myminio/captcha-backgrounds/` is `private`
```

✅ **确认**：Bucket 为私有，外部无法直接访问

---

## 🔧 技术细节

### MinIO 连接信息

| 参数 | 值 |
|------|-----|
| **Endpoint** | http://localhost:9000 |
| **Access Key** | leoxiao |
| **Secret Key** | your-password-here |
| **Bucket** | captcha-backgrounds |
| **权限** | private |

### 图片命名规则

- 格式：`bg_XXX.jpg`
- 范围：`bg_001.jpg` ~ `bg_200.jpg`
- 尺寸：310×155 像素
- 格式：JPEG

---

## 📝 后续步骤

### 1. 后端服务需要重新加载图片

后端服务会从 MinIO 加载验证码图片，可能需要重启：

```bash
ssh ubuntu@113.31.105.30
cd ~/myblog-deploy
docker restart myblog-backend
```

### 2. 验证后端能否正常加载

```bash
# 查看后端日志
docker logs myblog-backend | grep -E "背景图|captcha|MinIO"

# 应该看到类似：
# 成功加载背景图: captcha-backgrounds/bg_001.jpg (耗时=XXms, 尺寸=310x155)
# 共加载 200 张背景图片
```

### 3. 测试验证码功能

```bash
# 获取验证码
curl -s -H "User-Agent: Mozilla/5.0" \
  http://localhost:8080/api/captcha/slider | jq '.data.backgroundSrc' | head -c 100
```

---

## ⚠️ 注意事项

### 1. 安全性

- ✅ Bucket 设置为**私有**
- ✅ 只能通过后端 API 访问
- ✅ 防止被恶意扫描和滥用

### 2. 性能优化

- 图片总大小仅 1.77MB，加载速度快
- MinIO 在本地 Docker 网络中，延迟低
- 后端会缓存图片列表，减少重复查询

### 3. 备份建议

定期备份 MinIO 数据：

```bash
# 备份 bucket
mc mirror myminio/captcha-backgrounds /backup/captcha-backgrounds

# 或导出为压缩包
mc zip myminio/captcha-backgrounds > captcha-backup-$(date +%Y%m%d).zip
```

---

## 🔍 常用命令

### 查看 Bucket 中的文件

```bash
mc ls myminio/captcha-backgrounds/
```

### 下载单个文件

```bash
mc cp myminio/captcha-backgrounds/bg_001.jpg ./
```

### 删除文件

```bash
mc rm myminio/captcha-backgrounds/bg_001.jpg
```

### 查看 Bucket 信息

```bash
mc stat myminio/captcha-backgrounds/
```

---

## 📞 故障排查

### 问题 1: MinIO 认证失败

**错误**：`The Access Key Id you provided does not exist in our records`

**解决**：
```bash
# 检查正确的 Access Key（注意大小写）
docker inspect myblog-minio | grep MINIO_ROOT_USER

# 重新配置
mc alias set myminio http://localhost:9000 leoxiao your-password-here
```

---

### 问题 2: Bucket 不存在

**错误**：`Bucket 'captcha-backgrounds' does not exist`

**解决**：
```bash
mc mb myminio/captcha-backgrounds
mc anonymous set private myminio/captcha-backgrounds
```

---

### 问题 3: 后端无法加载图片

**检查**：
```bash
# 1. 确认图片存在
mc ls myminio/captcha-backgrounds/ | wc -l

# 2. 检查后端日志
docker logs myblog-backend | grep -i "minio\|captcha"

# 3. 验证 MinIO 连接
mc stat myminio/captcha-backgrounds/bg_001.jpg
```

---

**操作完成时间**: 2026-05-17 12:12  
**操作人员**: Leo  
**相关文档**: 
- [CAPTCHA_SECURITY_ENHANCEMENT.md](./CAPTCHA_SECURITY_ENHANCEMENT.md)
- [MINIO_CAPTCHA_SECURITY.md](./MINIO_CAPTCHA_SECURITY.md)
