# 滑块验证码安全增强 - 前端接入指南

## 📋 概述

本次更新对滑块验证码进行了**安全增强**，主要改进包括：

1. **动态位置混淆**：服务端添加随机偏移量，防止图像识别攻击
2. **行为分析验证**：采集用户滑动轨迹和时间，检测自动化脚本
3. **向后兼容**：旧版接口仍然可用，建议逐步迁移

---

## 🔧 核心变更

### 1. API 接口变化

#### 获取验证码（无变化）
```typescript
GET /api/captcha/slider
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "nonceStr": "a1b2c3d4e5f6...",
    "backgroundSrc": "data:image/png;base64,...",
    "blockSrc": "data:image/png;base64,...",
    "canvasWidth": 310,
    "canvasHeight": 155,
    "blockWidth": 50,
    "blockHeight": 50,
    "blockY": 75,
    "imageSourceHint": "cloud_storage"
  }
}
```

⚠️ **重要**：`blockX` 字段**不再返回**，前端无需关心真实位置。

---

#### 验证验证码（增强版）
```typescript
POST /api/captcha/slider/verify
```

**请求体（新版）**：
```typescript
interface VerifyCaptchaRequest {
  nonceStr: string;        // 验证码标识
  x: number;               // 用户滑动的最终 X 坐标
  trajectory?: Array<{     // 可选：滑动轨迹点
    x: number;             // X 坐标
    y: number;             // Y 坐标
    t: number;             // 时间戳（相对开始时间的毫秒数）
  }>;
  totalTime?: number;      // 可选：总耗时（毫秒）
}
```

**请求示例**：
```json
{
  "nonceStr": "a1b2c3d4e5f6...",
  "x": 185,
  "trajectory": [
    {"x": 0, "y": 75, "t": 0},
    {"x": 15, "y": 76, "t": 50},
    {"x": 35, "y": 74, "t": 120},
    {"x": 60, "y": 75, "t": 200},
    {"x": 90, "y": 76, "t": 300},
    {"x": 120, "y": 75, "t": 400},
    {"x": 150, "y": 74, "t": 500},
    {"x": 175, "y": 75, "t": 600},
    {"x": 185, "y": 75, "t": 650}
  ],
  "totalTime": 650
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "验证通过",
  "data": true
}
```

---

## 💻 前端实现示例

### Vue 3 + TypeScript 实现

```vue
<template>
  <div class="slider-captcha">
    <!-- 背景图 -->
    <canvas 
      ref="backgroundCanvas" 
      :width="captcha.canvasWidth" 
      :height="captcha.canvasHeight"
    ></canvas>
    
    <!-- 滑块 -->
    <div 
      class="slider-block"
      :style="{
        left: sliderPosition + 'px',
        top: captcha.blockY + 'px'
      }"
      @mousedown="startDrag"
    >
      <img :src="captcha.blockSrc" alt="滑块" />
    </div>
    
    <!-- 滑动条 -->
    <div class="slider-track" @mousemove="onDrag" @mouseup="stopDrag">
      <div class="slider-bar" :style="{ width: sliderPosition + 'px' }"></div>
      <span v-if="!verified">{{ promptText }}</span>
      <span v-else class="success-text">✓ 验证通过</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';

// 类型定义
interface SliderCaptcha {
  nonceStr: string;
  backgroundSrc: string;
  blockSrc: string;
  canvasWidth: number;
  canvasHeight: number;
  blockWidth: number;
  blockHeight: number;
  blockY: number;
  imageSourceHint?: string;
}

interface TrajectoryPoint {
  x: number;
  y: number;
  t: number;
}

// 状态
const captcha = ref<SliderCaptcha | null>(null);
const sliderPosition = ref(0);
const verified = ref(false);
const promptText = ref('向右滑动完成拼图');

// 轨迹采集
const trajectory = ref<TrajectoryPoint[]>([]);
const startTime = ref<number>(0);
const isDragging = ref(false);

// Canvas 引用
const backgroundCanvas = ref<HTMLCanvasElement | null>(null);

// 初始化验证码
const initCaptcha = async () => {
  try {
    const response = await axios.get('/api/captcha/slider');
    if (response.data.code === 200) {
      captcha.value = response.data.data;
      drawBackground();
      resetSlider();
    }
  } catch (error) {
    console.error('获取验证码失败:', error);
  }
};

// 绘制背景图
const drawBackground = () => {
  if (!captcha.value || !backgroundCanvas.value) return;
  
  const ctx = backgroundCanvas.value.getContext('2d');
  if (!ctx) return;
  
  const img = new Image();
  img.onload = () => {
    ctx.clearRect(0, 0, captcha.value!.canvasWidth, captcha.value!.canvasHeight);
    ctx.drawImage(img, 0, 0);
  };
  img.src = captcha.value.backgroundSrc;
};

// 重置滑块
const resetSlider = () => {
  sliderPosition.value = 0;
  verified.value = false;
  trajectory.value = [];
  startTime.value = 0;
  isDragging.value = false;
  promptText.value = '向右滑动完成拼图';
};

// 开始拖动
const startDrag = (event: MouseEvent) => {
  if (verified.value || !captcha.value) return;
  
  isDragging.value = true;
  startTime.value = Date.now();
  trajectory.value = [{
    x: 0,
    y: event.offsetY,
    t: 0
  }];
};

// 拖动中
const onDrag = (event: MouseEvent) => {
  if (!isDragging.value || !captcha.value) return;
  
  const newX = event.offsetX;
  const maxY = captcha.value.canvasWidth - captcha.value.blockWidth;
  
  // 限制范围
  sliderPosition.value = Math.max(0, Math.min(newX, maxY));
  
  // 记录轨迹点
  const currentTime = Date.now() - startTime.value;
  trajectory.value.push({
    x: sliderPosition.value,
    y: event.offsetY,
    t: currentTime
  });
};

// 停止拖动
const stopDrag = async () => {
  if (!isDragging.value || !captcha.value) return;
  
  isDragging.value = false;
  
  const finalX = sliderPosition.value;
  const totalTime = Date.now() - startTime.value;
  
  // 验证
  await verifyCaptcha(finalX, trajectory.value, totalTime);
};

// 提交验证
const verifyCaptcha = async (x: number, trajectory: TrajectoryPoint[], totalTime: number) => {
  try {
    const response = await axios.post('/api/captcha/slider/verify', {
      nonceStr: captcha.value!.nonceStr,
      x: x,
      trajectory: trajectory,  // 发送轨迹数据
      totalTime: totalTime      // 发送总耗时
    });
    
    if (response.data.code === 200 && response.data.data === true) {
      verified.value = true;
      promptText.value = '✓ 验证通过';
      
      // 触发成功回调
      emit('verified', captcha.value!.nonceStr);
    } else {
      // 验证失败，重置
      promptText.value = '验证失败，请重试';
      setTimeout(() => {
        resetSlider();
        initCaptcha();  // 重新获取验证码
      }, 1000);
    }
  } catch (error) {
    console.error('验证失败:', error);
    promptText.value = '网络错误，请重试';
    setTimeout(() => {
      resetSlider();
      initCaptcha();
    }, 1000);
  }
};

// 组件挂载时初始化
onMounted(() => {
  initCaptcha();
});

// 暴露方法给父组件
defineExpose({
  reset: initCaptcha
});
</script>

<style scoped>
.slider-captcha {
  position: relative;
  user-select: none;
}

.slider-block {
  position: absolute;
  cursor: grab;
  transition: none;
}

.slider-block:active {
  cursor: grabbing;
}

.slider-track {
  position: relative;
  height: 40px;
  background: #f0f0f0;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
}

.slider-bar {
  position: absolute;
  left: 0;
  height: 100%;
  background: #409eff;
  border-radius: 20px;
  transition: width 0.1s;
}

.success-text {
  color: #67c23a;
  font-weight: bold;
}
</style>
```

---

### React 实现示例

```tsx
import React, { useState, useRef, useEffect, useCallback } from 'react';
import axios from 'axios';

interface SliderCaptcha {
  nonceStr: string;
  backgroundSrc: string;
  blockSrc: string;
  canvasWidth: number;
  canvasHeight: number;
  blockWidth: number;
  blockHeight: number;
  blockY: number;
}

interface TrajectoryPoint {
  x: number;
  y: number;
  t: number;
}

const SliderCaptchaComponent: React.FC<{
  onVerified: (nonceStr: string) => void;
}> = ({ onVerified }) => {
  const [captcha, setCaptcha] = useState<SliderCaptcha | null>(null);
  const [sliderPosition, setSliderPosition] = useState(0);
  const [verified, setVerified] = useState(false);
  const [promptText, setPromptText] = useState('向右滑动完成拼图');
  
  const trajectoryRef = useRef<TrajectoryPoint[]>([]);
  const startTimeRef = useRef<number>(0);
  const isDraggingRef = useRef(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // 初始化验证码
  const initCaptcha = useCallback(async () => {
    try {
      const response = await axios.get('/api/captcha/slider');
      if (response.data.code === 200) {
        setCaptcha(response.data.data);
      }
    } catch (error) {
      console.error('获取验证码失败:', error);
    }
  }, []);

  // 绘制背景
  useEffect(() => {
    if (!captcha || !canvasRef.current) return;
    
    const ctx = canvasRef.current.getContext('2d');
    if (!ctx) return;
    
    const img = new Image();
    img.onload = () => {
      ctx.clearRect(0, 0, captcha.canvasWidth, captcha.canvasHeight);
      ctx.drawImage(img, 0, 0);
    };
    img.src = captcha.backgroundSrc;
  }, [captcha]);

  // 开始拖动
  const handleMouseDown = (e: React.MouseEvent) => {
    if (verified || !captcha) return;
    
    isDraggingRef.current = true;
    startTimeRef.current = Date.now();
    trajectoryRef.current = [{
      x: 0,
      y: e.nativeEvent.offsetY,
      t: 0
    }];
  };

  // 拖动中
  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDraggingRef.current || !captcha) return;
    
    const newX = e.nativeEvent.offsetX;
    const maxX = captcha.canvasWidth - captcha.blockWidth;
    const position = Math.max(0, Math.min(newX, maxX));
    
    setSliderPosition(position);
    
    trajectoryRef.current.push({
      x: position,
      y: e.nativeEvent.offsetY,
      t: Date.now() - startTimeRef.current
    });
  };

  // 停止拖动
  const handleMouseUp = async () => {
    if (!isDraggingRef.current || !captcha) return;
    
    isDraggingRef.current = false;
    
    const finalX = sliderPosition;
    const totalTime = Date.now() - startTimeRef.current;
    
    try {
      const response = await axios.post('/api/captcha/slider/verify', {
        nonceStr: captcha.nonceStr,
        x: finalX,
        trajectory: trajectoryRef.current,
        totalTime: totalTime
      });
      
      if (response.data.code === 200 && response.data.data === true) {
        setVerified(true);
        setPromptText('✓ 验证通过');
        onVerified(captcha.nonceStr);
      } else {
        setPromptText('验证失败，请重试');
        setTimeout(() => {
          setSliderPosition(0);
          setVerified(false);
          initCaptcha();
        }, 1000);
      }
    } catch (error) {
      console.error('验证失败:', error);
      setPromptText('网络错误，请重试');
      setTimeout(() => {
        setSliderPosition(0);
        setVerified(false);
        initCaptcha();
      }, 1000);
    }
  };

  useEffect(() => {
    initCaptcha();
  }, [initCaptcha]);

  return (
    <div className="slider-captcha">
      <canvas
        ref={canvasRef}
        width={captcha?.canvasWidth}
        height={captcha?.canvasHeight}
      />
      
      <div
        className="slider-block"
        style={{
          left: `${sliderPosition}px`,
          top: `${captcha?.blockY}px`
        }}
        onMouseDown={handleMouseDown}
      >
        <img src={captcha?.blockSrc} alt="滑块" />
      </div>
      
      <div
        className="slider-track"
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
      >
        <div
          className="slider-bar"
          style={{ width: `${sliderPosition}px` }}
        />
        <span>{verified ? promptText : promptText}</span>
      </div>
    </div>
  );
};

export default SliderCaptchaComponent;
```

---

## 🎯 关键要点

### 1. 轨迹数据采集

**必须采集的数据**：
- ✅ 每个采样点的 X、Y 坐标
- ✅ 相对于开始时间的时间戳（毫秒）
- ✅ 至少 5 个采样点

**采样频率建议**：
- 每 50-100ms 采样一次
- 或每次鼠标移动事件都采样

### 2. 时间计算

```typescript
// 正确做法
const startTime = Date.now();  // 开始拖动时

// 拖动过程中
const currentTime = Date.now() - startTime;  // 相对时间

// 停止时
const totalTime = Date.now() - startTime;  // 总耗时
```

### 3. 兼容性处理

如果暂时无法实现轨迹采集，可以只发送基础参数：

```typescript
// 最小化请求（仍可享受位置混淆保护）
await axios.post('/api/captcha/slider/verify', {
  nonceStr: captcha.nonceStr,
  x: finalX
  // trajectory 和 totalTime 可选
});
```

⚠️ **注意**：不提供轨迹和时间会降低安全性，建议尽快完善。

---

## 🧪 测试清单

- [ ] 正常滑动能够验证通过
- [ ] 滑动过快（< 300ms）会被拒绝
- [ ] 轨迹点过少（< 5 个）会被拒绝
- [ ] 验证失败后能重新获取验证码
- [ ] 移动端触摸事件正常工作
- [ ] 网络错误有友好提示
- [ ] 验证成功后不能再次滑动

---

## 📊 安全性对比

| 特性 | 旧版 | 新版 |
|------|------|------|
| 位置混淆 | ❌ 无 | ✅ 随机偏移 |
| 行为分析 | ❌ 无 | ✅ 轨迹+时间 |
| 防图像识别 | ❌ 弱 | ✅ 强 |
| 防自动化脚本 | ❌ 弱 | ✅ 强 |
| 最小验证时间 | ❌ 无 | ✅ 300ms |
| 轨迹抖动检测 | ❌ 无 | ✅ 30% 变化率 |

---

## 🔗 相关文档

- 后端 API 文档：`/docs/api/SLIDER_CAPTCHA_API.md`
- 安全最佳实践：`/docs/security/CAPTCHA_SECURITY.md`

---

## ❓ 常见问题

### Q1: 为什么需要轨迹数据？
A: 人类滑动会有自然的抖动和速度变化，而机器脚本通常是匀速直线运动。通过分析轨迹特征可以区分人和机器。

### Q2: 如果不提供轨迹会怎样？
A: 仍然可以验证，但只检查位置偏差，安全性降低。建议至少提供 `totalTime`。

### Q3: 移动端如何采集轨迹？
A: 使用 `touchmove` 事件代替 `mousemove`，其他逻辑相同。

### Q4: 轨迹数据会影响性能吗？
A: 不会。轨迹数据很小（通常 10-20 个点），对网络传输影响可忽略。

---

## 📞 技术支持

如有问题，请联系后端开发团队或查看项目文档。

**更新日期**: 2026-05-17  
**版本**: v2.0 (安全增强版)
