# 滑块验证码背景图指南

## 📐 图片尺寸要求

### 标准尺寸
- **宽度**: 310 像素
- **高度**: 155 像素
- **宽高比**: 2:1

### 为什么是这个尺寸？
```java
// SliderCaptchaUtils.java 中定义
DEFAULT_CANVAS_WIDTH = 310   // 画布宽度
DEFAULT_CANVAS_HEIGHT = 155  // 画布高度
DEFAULT_BLOCK_WIDTH = 50     // 滑块宽度
DEFAULT_BLOCK_HEIGHT = 50    // 滑块高度
```

这个尺寸经过精心设计：
- ✅ 足够大，可以清晰显示缺口
- ✅ 足够小，加载速度快（Base64 后约 10-20KB）
- ✅ 适合移动端和桌面端显示

---

## 🎨 图片选择建议

### ✅ 推荐的图片类型

#### 1. 风景照片
- 山川、湖泊、海洋
- 森林、草原、沙漠
- 天空、云朵、日落

**示例**：
```
https://picsum.photos/310/155?random=1  # 随机风景
https://source.unsplash.com/310x155/?nature,landscape
```

#### 2. 建筑照片
- 城市天际线
- 桥梁、塔楼
- 现代建筑外观

**注意**：避免有明显文字的建筑标识

#### 3. 抽象图案
- 渐变色背景
- 几何图形
- 纹理图案（木纹、石纹、布料）

#### 4. 自然元素
- 花朵特写
- 树叶纹理
- 水波纹

---

### ❌ 避免使用的图片类型

#### 1. 包含人脸的照片
- **原因**：隐私问题，可能被裁剪得支离破碎
- **替代**：使用纯风景或无人物场景

#### 2. 有文字的图片
- **原因**：文字被滑块遮挡会影响识别
- **示例**：路牌、广告牌、书籍封面

#### 3. 对比度过高的图片
- **原因**：可能导致缺口不明显
- **示例**：黑白分明的图案、强烈光影

#### 4. 过于复杂的图片
- **原因**：用户难以找到缺口位置
- **示例**：密集的人群、杂乱的街道

#### 5. 纯色或单一颜色
- **原因**：无法体现滑块的立体感
- **替代**：至少有两种以上颜色

---

## 📥 快速下载图片

### 方法一：使用自动脚本（推荐）

```bash
# 在项目根目录执行
./scripts/download-captcha-backgrounds.sh
```

这会自动从 Picsum Photos 下载 20 张 310x155 的图片。

### 方法二：手动下载

#### 1. Picsum Photos（最简单）
访问以下链接直接下载：
```
https://picsum.photos/310/155?random=1
https://picsum.photos/310/155?random=2
...
https://picsum.photos/310/155?random=20
```

#### 2. Unsplash Source
```
https://source.unsplash.com/310x155/?nature
https://source.unsplash.com/310x155/?landscape
https://source.unsplash.com/310x155/?architecture
```

#### 3. Lorem Flickr
```
https://loremflickr.com/310/155/nature
https://loremflickr.com/310/155/landscape
```

### 方法三：自己准备图片

如果你有高清图片（如 1920x1080），程序会自动缩放到 310x155。

**步骤**：
1. 将图片放入 `src/main/resources/static/captcha-backgrounds/` 目录
2. 命名为 `bg_001.jpg`, `bg_002.jpg` 等格式
3. 支持 JPG、PNG 格式
4. 重启应用即可

---

## 🔧 技术实现

### 图片加载流程

```
应用启动
  ↓
CaptchaBackgroundService.init()
  ↓
从 classpath:/static/captcha-backgrounds/ 加载图片
  ↓
自动缩放到 310x155
  ↓
缓存到内存列表
  ↓
每次生成验证码时随机选择一张
```

### 降级策略

如果本地图片加载失败，系统会自动降级：
```java
BufferedImage background = backgroundService.getRandomBackground();
if (background == null) {
    // 降级：使用程序生成的渐变背景
    background = SliderCaptchaUtils.generateBackground();
}
```

这样即使没有图片，验证码仍然可用。

---

## 📊 性能考虑

### 文件大小
- **JPG 格式**：310x155 约 5-15 KB
- **Base64 编码后**：约 7-20 KB
- **建议**：使用 JPG 格式，质量 80% 左右

### 内存占用
- 每张图片约 200 KB（BufferedImage 原始数据）
- 20 张图片约 4 MB
- **完全可以接受**

### 加载速度
- 首次启动：加载所有图片（< 1 秒）
- 运行时：从内存读取（微秒级）

---

## 🎯 最佳实践

### 1. 图片数量
- **最少**：5 张（避免重复感太强）
- **推荐**：20-30 张（平衡多样性和内存）
- **最多**：100 张（一般不需要这么多）

### 2. 图片更新
- 每隔几个月更换一批图片
- 保持新鲜感，防止被破解

### 3. 图片分类
建议混合使用不同类型：
```
bg_001.jpg - bg_007.jpg  → 风景
bg_008.jpg - bg_014.jpg  → 建筑
bg_015.jpg - bg_020.jpg  → 抽象图案
```

### 4. 测试验证
下载图片后，手动测试几个场景：
- [ ] 缺口是否清晰可见？
- [ ] 滑块是否能正确提取？
- [ ] 图片加载速度是否满意？
- [ ] 是否有不合适的图片内容？

---

## 🚀 快速开始

### 完整操作流程

```bash
# 1. 运行下载脚本
cd /home/leo/IdeaProjects/myblog
./scripts/download-captcha-backgrounds.sh

# 2. 检查下载的图片
ls -lh src/main/resources/static/captcha-backgrounds/

# 3. 重新编译项目
./gradlew build

# 4. 启动应用
./gradlew bootRun

# 5. 测试验证码
curl http://localhost:18080/api/captcha/slider
```

### 验证图片是否生效

查看日志输出：
```
滑块验证码背景图服务初始化完成，共加载 20 张图片
```

如果看到这条日志，说明图片加载成功！

---

## 📝 常见问题

### Q1: 图片必须是 310x155 吗？
**A**: 不是必须的。程序会自动缩放任何尺寸的图片到 310x155。但直接使用正确尺寸可以避免缩放失真。

### Q2: 可以使用 PNG 格式吗？
**A**: 可以，但 JPG 更推荐，因为文件更小。PNG 适合需要透明度的场景（但验证码不需要）。

### Q3: 图片放在哪里？
**A**: `src/main/resources/static/captcha-backgrounds/`

### Q4: 如何添加新图片？
**A**: 
1. 将图片放入上述目录
2. 按照 `bg_XXX.jpg` 格式命名
3. 在 `CaptchaBackgroundService.java` 中添加文件名
4. 重启应用

### Q5: 图片会被缓存吗？
**A**: 是的，应用启动时一次性加载到内存，后续直接从内存读取，速度非常快。

---

## 🎨 图片示例

以下是一些适合的图片类型示例（描述）：

### 优秀示例 ✅
- 蓝天白云下的山脉
- 平静的湖面倒影
- 秋天的森林小径
- 现代化的玻璃建筑
- 柔和的渐变色背景

### 不佳示例 ❌
- 人群密集的广场
- 满是文字的招牌
- 强烈的黑白对比
- 模糊不清的照片
- 纯白色背景

---

**最后更新**: 2026-05-17  
**相关文件**: 
- `CaptchaBackgroundService.java`
- `SliderCaptchaService.java`
- `scripts/download-captcha-backgrounds.sh`
