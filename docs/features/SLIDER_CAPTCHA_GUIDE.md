# 滑块拼图验证码实现指南

## 概述

本文档说明如何在 MyBlog 项目中集成滑块拼图验证码功能，用于防止机器人自动登录和暴力破解。

## 后端 API

### 1. 获取滑块验证码

**接口**: `GET /api/captcha/slider`

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "nonceStr": "a1b2c3d4e5f6...",
    "backgroundSrc": "data:image/png;base64,iVBORw0KGgo...",
    "blockSrc": "data:image/png;base64,iVBORw0KGgo...",
    "canvasWidth": 310,
    "canvasHeight": 155,
    "blockWidth": 50,
    "blockHeight": 50,
    "blockY": 50
  }
}
```

**字段说明**:
- `nonceStr`: 验证码唯一标识（验证时需要传回）
- `backgroundSrc`: 背景图 Base64（带缺口）
- `blockSrc`: 滑块图 Base64
- `canvasWidth/Height`: 画布尺寸
- `blockWidth/Height`: 滑块尺寸
- `blockY`: 滑块的 Y 轴位置（固定不变）

### 2. 验证滑块位置

**接口**: `POST /api/captcha/slider/verify`

**请求体**:
```json
{
  "nonceStr": "a1b2c3d4e5f6...",
  "x": 150
}
```

**响应**:
```json
{
  "code": 200,
  "message": "验证通过",
  "data": true
}
```

### 3. 登录时携带验证码

在登录请求中添加验证码参数：

```json
{
  "username": "admin",
  "password": "123456",
  "captchaNonceStr": "a1b2c3d4e5f6...",
  "captchaX": 150
}
```

## 前端实现

### Vue 3 组件示例

创建文件：`src/components/SliderCaptcha.vue`

```vue
<template>
  <div v-if="visible" class="slider-captcha-overlay" @click.self="close">
    <div class="slider-captcha-modal">
      <div class="captcha-header">
        <span>请完成安全验证</span>
        <button class="close-btn" @click="close">×</button>
      </div>
      
      <!-- 验证码图片区域 -->
      <div class="captcha-container">
        <div 
          class="background-canvas" 
          :style="{ width: captcha.canvasWidth + 'px', height: captcha.canvasHeight + 'px' }"
        >
          <img :src="captcha.backgroundSrc" alt="背景图" />
          
          <!-- 滑块 -->
          <div
            class="slider-block"
            :style="{
              left: sliderX + 'px',
              top: captcha.blockY + 'px',
              width: captcha.blockWidth + 'px',
              height: captcha.blockHeight + 'px'
            }"
          >
            <img :src="captcha.blockSrc" alt="滑块" />
          </div>
        </div>
      </div>
      
      <!-- 滑动条 -->
      <div class="slider-bar">
        <div 
          class="slider-track" 
          :style="{ width: sliderX + 'px' }"
        ></div>
        <div
          class="slider-button"
          :style="{ left: sliderX + 'px' }"
          @mousedown="startDrag"
          @touchstart="startDrag"
        >
          →
        </div>
        <span class="slider-text">{{ sliderText }}</span>
      </div>
      
      <!-- 刷新按钮 -->
      <div class="captcha-footer">
        <button class="refresh-btn" @click="refreshCaptcha">
          🔄 刷新
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['success', 'close'])

const captcha = ref({
  nonceStr: '',
  backgroundSrc: '',
  blockSrc: '',
  canvasWidth: 310,
  canvasHeight: 155,
  blockWidth: 50,
  blockHeight: 50,
  blockY: 50
})

const sliderX = ref(0)
const isDragging = ref(false)
const startX = ref(0)

const sliderText = computed(() => {
  return sliderX.value > 0 ? '释放以验证' : '向右滑动完成验证'
})

// 获取验证码
const fetchCaptcha = async () => {
  try {
    const response = await axios.get('/api/captcha/slider')
    if (response.data.code === 200) {
      captcha.value = response.data.data
      sliderX.value = 0
    }
  } catch (error) {
    console.error('获取验证码失败:', error)
  }
}

// 开始拖动
const startDrag = (e) => {
  isDragging.value = true
  startX.value = e.type === 'mousedown' ? e.clientX : e.touches[0].clientX
  
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag)
  document.addEventListener('touchend', stopDrag)
}

// 拖动中
const onDrag = (e) => {
  if (!isDragging.value) return
  
  const currentX = e.type === 'mousemove' ? e.clientX : e.touches[0].clientX
  const deltaX = currentX - startX.value
  
  const maxX = captcha.value.canvasWidth - captcha.value.blockWidth
  sliderX.value = Math.max(0, Math.min(deltaX, maxX))
}

// 停止拖动
const stopDrag = async () => {
  if (!isDragging.value) return
  isDragging.value = false
  
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
  
  // 验证滑块位置
  await verifyCaptcha()
}

// 验证验证码
const verifyCaptcha = async () => {
  try {
    const response = await axios.post('/api/captcha/slider/verify', {
      nonceStr: captcha.value.nonceStr,
      x: sliderX.value
    })
    
    if (response.data.code === 200 && response.data.data) {
      // 验证成功
      emit('success', {
        nonceStr: captcha.value.nonceStr,
        x: sliderX.value
      })
      close()
    } else {
      // 验证失败，刷新验证码
      alert('验证失败，请重试')
      refreshCaptcha()
    }
  } catch (error) {
    console.error('验证失败:', error)
    alert('验证失败，请重试')
    refreshCaptcha()
  }
}

// 刷新验证码
const refreshCaptcha = () => {
  fetchCaptcha()
}

// 关闭
const close = () => {
  emit('close')
}

onMounted(() => {
  if (props.visible) {
    fetchCaptcha()
  }
})
</script>

<style scoped>
.slider-captcha-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.slider-captcha-modal {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.captcha-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-size: 16px;
  font-weight: bold;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
}

.captcha-container {
  margin-bottom: 15px;
}

.background-canvas {
  position: relative;
  background: #f0f0f0;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
}

.background-canvas img {
  width: 100%;
  height: 100%;
  display: block;
}

.slider-block {
  position: absolute;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  cursor: grab;
}

.slider-block img {
  width: 100%;
  height: 100%;
  display: block;
}

.slider-bar {
  position: relative;
  height: 40px;
  background: #f0f0f0;
  border: 1px solid #ddd;
  border-radius: 20px;
  margin-bottom: 10px;
}

.slider-track {
  position: absolute;
  height: 100%;
  background: linear-gradient(90deg, #52c41a, #73d13d);
  border-radius: 20px;
  transition: width 0.1s;
}

.slider-button {
  position: absolute;
  top: 0;
  width: 40px;
  height: 40px;
  background: white;
  border: 2px solid #ddd;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: left 0.1s;
  user-select: none;
}

.slider-button:active {
  cursor: grabbing;
}

.slider-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #999;
  font-size: 14px;
  pointer-events: none;
}

.captcha-footer {
  text-align: right;
}

.refresh-btn {
  background: #1890ff;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.refresh-btn:hover {
  background: #40a9ff;
}
</style>
```

### 在登录页面使用

```vue
<template>
  <div class="login-page">
    <!-- 登录表单 -->
    <el-form :model="loginForm" @submit.prevent="handleLogin">
      <el-form-item label="用户名">
        <el-input v-model="loginForm.username" />
      </el-form-item>
      
      <el-form-item label="密码">
        <el-input v-model="loginForm.password" type="password" />
      </el-form-item>
      
      <el-button type="primary" @click="showCaptcha">登录</el-button>
    </el-form>
    
    <!-- 滑块验证码组件 -->
    <SliderCaptcha
      :visible="captchaVisible"
      @success="onCaptchaSuccess"
      @close="captchaVisible = false"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import axios from 'axios'

const loginForm = ref({
  username: '',
  password: ''
})

const captchaVisible = ref(false)
const captchaData = ref(null)

// 显示验证码
const showCaptcha = () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  captchaVisible.value = true
}

// 验证码验证成功
const onCaptchaSuccess = (data) => {
  captchaData.value = data
  performLogin()
}

// 执行登录
const performLogin = async () => {
  try {
    const response = await axios.post('/api/users/login', {
      username: loginForm.value.username,
      password: loginForm.value.password,
      captchaNonceStr: captchaData.value.nonceStr,
      captchaX: captchaData.value.x
    })
    
    if (response.data.code === 200) {
      ElMessage.success('登录成功')
      // 保存 Token，跳转首页等
      localStorage.setItem('token', response.data.data.token)
      window.location.href = '/'
    } else {
      ElMessage.error(response.data.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  }
}
</script>
```

## 配置建议

### 1. 启用/禁用验证码

可以通过配置文件控制是否启用验证码：

**application.yml**:
```yaml
security:
  captcha:
    enabled: true  # 是否启用滑块验证码
    threshold: 3   # 连续失败多少次后强制要求验证码
```

### 2. 智能验证码策略

建议实现以下策略：
- 首次登录不需要验证码
- 连续失败 3 次后，下次登录必须通过验证码
- 异地登录需要验证码
- 管理员可以配置全局强制验证码

## 注意事项

1. **安全性**：
   - ✅ 正确的 X 坐标只存储在 Redis 中，不返回给前端
   - ✅ 验证码使用后自动删除，防止重复使用
   - ✅ 验证码 5 分钟后自动过期

2. **用户体验**：
   - 允许 ±5 像素的误差
   - 验证失败自动刷新验证码
   - 提供刷新按钮

3. **性能优化**：
   - 背景图可以预生成并缓存
   - 使用 CDN 加速图片加载
   - 考虑使用 WebP 格式减小体积

## 测试

测试验证码功能：

```bash
# 1. 获取验证码
curl http://localhost:18080/api/captcha/slider

# 2. 验证滑块位置（假设正确位置是 150）
curl -X POST http://localhost:18080/api/captcha/slider/verify \
  -H "Content-Type: application/json" \
  -d '{"nonceStr":"xxx","x":150}'

# 3. 登录时携带验证码
curl -X POST http://localhost:18080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "username":"admin",
    "password":"123456",
    "captchaNonceStr":"xxx",
    "captchaX":150
  }'
```

---

**最后更新**: 2026-05-17  
**相关 Commit**: 待提交
