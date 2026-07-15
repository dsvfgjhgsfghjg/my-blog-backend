-- 插入主页三个分类的测试文章数据

-- ==================== 软件开发分类 (category_id=4) ====================
INSERT INTO t_post (id, user_id, category_id, title, slug, summary, content, word_count, cover_image, status, is_top, view_count, is_deleted, create_time, update_time, publish_time) VALUES 
(2000000000000000001, 1, 4, 'Spring Boot 微服务架构实战', 'spring-boot-microservices', '使用 Spring Boot 构建可扩展的微服务架构，包含服务发现、配置管理、负载均衡等核心组件', $$# Spring Boot 微服务架构实战

## 什么是微服务？

微服务是一种将单个应用程序开发为一组小型服务的方法，每个服务运行在自己的进程中，并通过轻量级机制（通常是 HTTP 资源 API）进行通信。

## 核心技术栈

- **Spring Boot**: 快速构建微服务
- **Spring Cloud**: 微服务治理
- **Eureka**: 服务注册与发现
- **Ribbon**: 客户端负载均衡
- **Feign**: 声明式 REST 客户端
- **Hystrix**: 熔断器模式
- **Zuul/Gateway**: API 网关

## 项目结构

```
microservice-demo/
├── eureka-server/          # 服务注册中心
├── config-server/          # 配置中心
├── user-service/           # 用户服务
├── order-service/          # 订单服务
├── product-service/        # 产品服务
└── api-gateway/            # API 网关
```

## 服务注册与发现

使用 Eureka 实现服务的自动注册和发现，服务提供者启动时向 Eureka Server 注册自己的信息。

## 配置管理

通过 Spring Cloud Config 统一管理所有微服务的配置文件，支持动态刷新。

## 负载均衡

Ribbon 提供客户端负载均衡，结合 RestTemplate 或 Feign 实现服务调用。

## 熔断保护

Hystrix 实现熔断器模式，防止雪崩效应，提高系统容错能力。

## API 网关

Zuul 或 Spring Cloud Gateway 作为统一入口，提供路由转发、权限校验、限流等功能。

## 部署建议

- 使用 Docker 容器化部署
- Kubernetes 编排管理
- CI/CD 自动化流程
- 分布式日志收集（ELK）
- 链路追踪（Sleuth + Zipkin）$$, 456, '/covers/springboot-microservices.jpg', 1, false, 1580, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000002, 1, 4, 'Vue 3 + TypeScript 最佳实践', 'vue3-typescript-best-practices', '深入探讨 Vue 3 Composition API 与 TypeScript 的结合使用，提升代码质量和开发效率', $$# Vue 3 + TypeScript 最佳实践

## 为什么选择 Vue 3 + TypeScript？

Vue 3 使用 TypeScript 重写，提供了更好的类型推断和支持，让大型项目更易维护。

## 项目初始化

```bash
npm init vue@latest
# 选择 TypeScript 选项
```

## Composition API 优势

- 更好的逻辑复用
- 更灵活的代码组织
- 完整的 TypeScript 类型支持

## 类型定义示例

```typescript
interface User {
  id: number
  name: string
  email: string
}

const state = reactive<{
  users: User[]
  loading: boolean
}>({
  users: [],
  loading: false
})
```

## 自定义 Hooks

提取可复用的组合式函数：

```typescript
// useFetch.ts
export function useFetch<T>(url: string) {
  const data = ref<T | null>(null)
  const error = ref<Error | null>(null)
  const loading = ref(true)
  
  fetch(url)
    .then(res => res.json())
    .then(result => {
      data.value = result
      loading.value = false
    })
    .catch(err => {
      error.value = err
      loading.value = false
    })
    
  return { data, error, loading }
}
```

## 组件 Props 类型化

```typescript
const props = defineProps<{
  title: string
  count?: number
  items: Array<{ id: number; name: string }>
}>()
```

## 性能优化技巧

- 使用 `shallowRef` 优化大对象
- `v-memo` 指令缓存渲染结果
- 懒加载路由和组件
- 虚拟列表处理大数据

## 状态管理

Pinia 是 Vue 3 推荐的狀態管理方案，完美支持 TypeScript。$$, 398, '/covers/vue3-typescript.jpg', 1, true, 2340, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000003, 1, 4, 'React Native 跨平台移动开发', 'react-native-cross-platform', '使用 React Native 构建 iOS 和 Android 应用，一套代码多端运行', $$# React Native 跨平台移动开发

## React Native 简介

React Native 让你使用 JavaScript 和 React 构建原生移动应用，支持 iOS 和 Android 平台。

## 环境搭建

- Node.js 和 npm
- React Native CLI 或 Expo
- Xcode（iOS 开发）
- Android Studio（Android 开发）

## 核心组件

- View - 视图容器
- Text - 文本显示
- Image - 图片展示
- ScrollView - 滚动视图
- FlatList - 高性能列表
- TextInput - 输入框

## 导航方案

使用 React Navigation 实现页面跳转：

```javascript
import { createStackNavigator } from '@react-navigation/stack';

const Stack = createStackNavigator();

function App() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="Home" component={HomeScreen} />
      <Stack.Screen name="Detail" component={DetailScreen} />
    </Stack.Navigator>
  );
}
```

## 状态管理

- Redux / Redux Toolkit
- MobX
- Context API
- Zustand

## 原生模块集成

当需要访问原生功能时，可以编写原生模块或使用第三方库。

## 性能优化

- 使用 FlatList 代替 ScrollView
- 避免不必要的重渲染
- 图片优化和懒加载
- 启用 Hermes 引擎

## 调试工具

- React Native Debugger
- Flipper
- Chrome DevTools

## 发布流程

- iOS: 提交到 App Store
- Android: 发布到 Google Play$$, 367, '/covers/react-native.jpg', 1, false, 1920, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000004, 1, 4, 'Node.js 后端开发完整指南', 'nodejs-backend-guide', '从入门到精通 Node.js 后端开发，包括 Express、数据库集成、认证授权等', $$# Node.js 后端开发完整指南

## 为什么选择 Node.js？

- 事件驱动、非阻塞 I/O 模型
- 使用 JavaScript 全栈开发
- 丰富的 npm 生态系统
- 高并发处理能力

## 技术栈选择

### Web 框架
- Express - 轻量灵活
- Koa - 更现代的中间件机制
- Fastify - 高性能
- NestJS - 企业级框架

### 数据库
- MongoDB - NoSQL
- PostgreSQL - 关系型
- MySQL - 关系型
- Redis - 缓存

## Express 基础

```javascript
const express = require('express');
const app = express();

app.get('/api/users', async (req, res) => {
  const users = await User.find();
  res.json(users);
});

app.listen(3000);
```

## 中间件使用

- body-parser - 请求体解析
- cors - 跨域支持
- helmet - 安全头设置
- morgan - 日志记录
- compression - 响应压缩

## JWT 认证

```javascript
const jwt = require('jsonwebtoken');

function authenticate(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ message: '未授权' });
  }
  
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded;
    next();
  } catch (error) {
    res.status(403).json({ message: '令牌无效' });
  }
}
```

## 错误处理

统一的错误处理中间件，返回标准化的错误响应。

## 测试

- Jest - 测试框架
- Supertest - HTTP 断言
- 单元测试和集成测试

## 部署

- PM2 进程管理
- Docker 容器化
- Nginx 反向代理
- 云平台部署（AWS、阿里云等）$$, 423, '/covers/nodejs-backend.jpg', 1, false, 1750, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ==================== AI分类 (category_id=5) ====================
(2000000000000000005, 1, 5, '深度学习入门：神经网络基础', 'deep-learning-neural-networks', '从零开始理解神经网络的工作原理，包括前向传播、反向传播和梯度下降算法', $$# 深度学习入门：神经网络基础

## 什么是神经网络？

神经网络是受生物神经系统启发的计算模型，由大量相互连接的节点（神经元）组成。

## 基本概念

### 神经元
- 输入权重
- 偏置项
- 激活函数

### 常见激活函数
- Sigmoid
- Tanh
- ReLU
- Leaky ReLU
- Softmax

## 网络结构

- 输入层
- 隐藏层（可以有多层）
- 输出层

## 前向传播

数据从输入层经过各层处理，最终得到输出结果。

## 损失函数

衡量预测值与真实值的差距：
- 均方误差（MSE）- 回归问题
- 交叉熵损失 - 分类问题

## 反向传播

通过链式法则计算梯度，更新网络参数。

## 优化算法

- SGD（随机梯度下降）
- Momentum
- Adam
- RMSprop

## 正则化技术

- L1/L2 正则化
- Dropout
- Batch Normalization
- Early Stopping

## 实践框架

- TensorFlow
- PyTorch
- Keras

## 学习建议

1. 理解数学基础（线性代数、微积分、概率论）
2. 从简单网络开始
3. 多做项目实践
4. 阅读经典论文$$, 389, '/covers/deep-learning-basics.jpg', 1, false, 2150, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000006, 1, 5, '自然语言处理实战：Transformer 模型', 'nlp-transformer-model', '深入理解 Transformer 架构及其在 NLP 任务中的应用，包括 BERT、GPT 等预训练模型', $$# 自然语言处理实战：Transformer 模型

## Transformer 革命

2017 年提出的 Transformer 架构彻底改变了 NLP 领域，成为现代大语言模型的基础。

## 核心组件

### Self-Attention 机制

允许模型在处理序列时关注不同位置的信息。

### Multi-Head Attention

多个注意力头并行工作，捕捉不同子空间的信息。

### Positional Encoding

为序列中的每个位置添加位置信息。

### Encoder-Decoder 结构

- Encoder: 编码输入序列
- Decoder: 生成输出序列

## 著名模型

### BERT
- 双向编码器
- Masked Language Model
- 适合理解类任务

### GPT 系列
- 单向解码器
- 自回归语言模型
- 适合生成类任务

## 应用场景

- 机器翻译
- 文本分类
- 命名实体识别
- 问答系统
- 文本生成
- 情感分析

## 微调实践

```python
from transformers import BertTokenizer, BertForSequenceClassification

tokenizer = BertTokenizer.from_pretrained('bert-base-chinese')
model = BertForSequenceClassification.from_pretrained('bert-base-chinese')

# 准备数据
inputs = tokenizer("你好世界", return_tensors="pt")

# 训练
outputs = model(**inputs, labels=labels)
loss = outputs.loss
```

## Hugging Face Transformers

提供数千个预训练模型，简化 NLP 开发流程。

## 性能优化

- 模型蒸馏
- 量化
- 剪枝
- ONNX 转换

## 最新趋势

- 大语言模型（LLM）
- 提示工程（Prompt Engineering）
- RAG（检索增强生成）
- 多模态模型$$, 445, '/covers/transformer-nlp.jpg', 1, true, 2680, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000007, 1, 5, '计算机视觉：目标检测算法详解', 'computer-vision-object-detection', '探索 YOLO、R-CNN 等主流目标检测算法的原理和实现', $$# 计算机视觉：目标检测算法详解

## 目标检测概述

目标检测不仅要识别图像中的物体，还要定位它们的位置（边界框）。

## 两阶段检测器

### R-CNN 系列
- R-CNN: 区域提议 + CNN 分类
- Fast R-CNN: 共享卷积特征
- Faster R-CNN: 引入 RPN 网络

优点：精度高
缺点：速度较慢

## 单阶段检测器

### YOLO 系列
- YOLOv1-v8 持续演进
- 实时检测能力强
- 端到端训练

### SSD
- 多尺度特征图
- 默认框机制

优点：速度快
缺点：小物体检测相对较弱

## YOLO 工作原理

1. 将图像划分为 S×S 网格
2. 每个网格预测 B 个边界框
3. 每个边界框包含置信度
4. 同时预测类别概率

## 评估指标

- IoU（交并比）
- Precision
- Recall
- mAP（平均精度均值）

## 实践框架

- PyTorch
- TensorFlow
- MMDetection
- Detectron2

## 数据标注

使用 LabelImg、CVAT 等工具标注训练数据。

## 数据增强

- 随机裁剪
- 颜色变换
- 旋转翻转
- Mosaic 增强

## 部署优化

- TensorRT 加速
- OpenVINO
- ONNX Runtime
- 移动端部署（NCNN、MNN）

## 应用场景

- 自动驾驶
- 安防监控
- 工业质检
- 医疗影像
- 零售分析$$, 412, '/covers/object-detection.jpg', 1, false, 1890, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000008, 1, 5, '强化学习：从 Q-Learning 到深度强化学习', 'reinforcement-learning-deep-rl', '理解强化学习的基本概念，掌握 DQN、PPO 等经典算法', $$# 强化学习：从 Q-Learning 到深度强化学习

## 什么是强化学习？

强化学习是智能体通过与环境交互，学习如何采取行动以最大化累积奖励的学习范式。

## 核心概念

- **Agent**: 智能体
- **Environment**: 环境
- **State**: 状态
- **Action**: 动作
- **Reward**: 奖励
- **Policy**: 策略
- **Value Function**: 价值函数

## Markov 决策过程（MDP）

强化学习问题通常建模为 MDP。

## Q-Learning

经典的无模型强化学习算法，学习状态-动作价值函数。

## Deep Q-Network (DQN)

将深度学习与 Q-Learning 结合：
- 经验回放
- 目标网络
- 深度神经网络近似 Q 值

## Policy Gradient 方法

直接优化策略，适用于连续动作空间。

## Actor-Critic 架构

结合价值方法和策略方法的优点：
- Actor: 决定采取什么动作
- Critic: 评估动作的好坏

## 先进算法

### PPO (Proximal Policy Optimization)
- 稳定训练
- 广泛应用

### DDPG
- 适用于连续控制

### SAC (Soft Actor-Critic)
- 最大熵强化学习
- 样本效率高

## 训练技巧

- 奖励塑造
- 课程学习
- 超参数调优
- 并行训练

## 仿真环境

- OpenAI Gym
- Unity ML-Agents
- MuJoCo
- Atari Games

## 实际应用

- 游戏 AI（AlphaGo、AlphaStar）
- 机器人控制
- 自动驾驶
- 推荐系统
- 资源调度

## 挑战与未来

- 样本效率
- 泛化能力
- 安全性
- 多智能体系统$$, 398, '/covers/reinforcement-learning.jpg', 1, false, 1650, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ==================== 网络安全分类 (category_id=6) ====================
(2000000000000000009, 1, 6, 'Web 安全攻防：OWASP Top 10 详解', 'web-security-owasp-top10', '深入解析 OWASP Top 10 Web 应用安全风险及防御策略', $$# Web 安全攻防：OWASP Top 10 详解

## OWASP 是什么？

开放 Web 应用安全项目（OWASP）是一个专注于软件安全的国际非营利组织。

## OWASP Top 10 2021

### 1. Broken Access Control（失效的访问控制）
- 未授权访问敏感数据
- 越权操作
- **防御**: 实施最小权限原则，服务端验证权限

### 2. Cryptographic Failures（加密失败）
- 敏感数据明文传输
- 弱加密算法
- **防御**: 使用 TLS、强加密算法、密钥管理

### 3. Injection（注入攻击）
- SQL 注入
- XSS（跨站脚本）
- 命令注入
- **防御**: 参数化查询、输入验证、输出编码

### 4. Insecure Design（不安全设计）
- 缺乏安全设计模式
- 业务逻辑漏洞
- **防御**: 威胁建模、安全设计原则

### 5. Security Misconfiguration（安全配置错误）
- 默认密码
- 不必要的服务开启
- **防御**: 安全基线、自动化配置检查

### 6. Vulnerable and Outdated Components（易受攻击的过时组件）
- 使用有已知漏洞的库
- **防御**: 依赖扫描、及时更新

### 7. Identification and Authentication Failures（身份认证失败）
- 弱密码策略
- 会话固定
- **防御**: 多因素认证、安全会话管理

### 8. Software and Data Integrity Failures（软件和数据完整性失败）
- 不安全的反序列化
- **防御**: 完整性检查、数字签名

### 9. Security Logging and Monitoring Failures（安全日志和监控失败）
- 缺乏审计日志
- **防御**: 全面日志记录、实时监控

### 10. Server-Side Request Forgery (SSRF)
- 服务器端请求伪造
- **防御**: URL 白名单、网络隔离

## 渗透测试流程

1. 信息收集
2. 漏洞扫描
3. 漏洞利用
4. 权限提升
5. 报告编写

## 安全工具

- Burp Suite - Web 渗透测试
- Nmap - 网络扫描
- SQLMap - SQL 注入测试
- Metasploit - 渗透测试框架
- Wireshark - 网络分析

## 防御最佳实践

- 输入验证和过滤
- 输出编码
- CSRF Token
- Content Security Policy
- HTTPS 强制
- 安全头设置
- 定期安全审计$$, 478, '/covers/web-security-owasp.jpg', 1, true, 2450, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000010, 1, 6, '渗透测试方法论与实践', 'penetration-testing-methodology', '系统化的渗透测试流程、常用工具和实战技巧', $$# 渗透测试方法论与实践

## 渗透测试类型

- 黑盒测试（无先验知识）
- 白盒测试（完全了解系统）
- 灰盒测试（部分信息）

## PTES 标准

渗透测试执行标准包含 7 个阶段：

### 1. 前期交互
- 确定测试范围
- 制定规则
- 签署授权书

### 2. 情报收集
**被动收集:**
- WHOIS 查询
- DNS 枚举
- 搜索引擎挖掘
- 社交媒体分析

**主动收集:**
- 端口扫描
- 服务识别
- 操作系统指纹识别

### 3. 威胁建模
- 识别关键资产
- 评估潜在威胁
- 确定攻击路径

### 4. 漏洞分析
- 自动化扫描（Nessus、OpenVAS）
- 手动验证
- 漏洞优先级排序

### 5. 渗透攻击
**常见攻击向量:**
- Web 应用漏洞
- 社会工程学
- 物理安全
- 无线网络安全

**Metasploit 使用:**
```bash
msfconsole
use exploit/multi/handler
set payload windows/meterpreter/reverse_tcp
exploit
```

### 6. 后渗透
- 权限维持
- 横向移动
- 数据收集
- 痕迹清理

### 7. 报告
- 执行摘要
- 技术细节
- 风险评估
- 修复建议

## 常用工具集

### 信息收集
- Nmap
- Recon-ng
- theHarvester
- Shodan

### 漏洞扫描
- Nessus
- OpenVAS
- Nikto
- SQLMap

### 漏洞利用
- Metasploit
- Burp Suite
- Cobalt Strike

### 密码破解
- John the Ripper
- Hashcat
- Hydra

### WiFi 测试
- Aircrack-ng
- Wifite

## 法律合规

- 必须获得书面授权
- 遵守测试范围
- 保护客户数据
- 负责任披露

## 职业发展

- OSCP 认证
- CEH 认证
- GPEN 认证
- 持续学习和实践$$, 456, '/covers/penetration-testing.jpg', 1, false, 2100, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000011, 1, 6, '逆向工程基础：从汇编到恶意代码分析', 'reverse-engineering-basics', '学习逆向工程基础知识，掌握静态分析和动态调试技术', $$# 逆向工程基础：从汇编到恶意代码分析

## 什么是逆向工程？

逆向工程是通过分析程序的二进制代码来理解其功能和实现原理的过程。

## 前置知识

### 汇编语言
- x86/x64 架构
- ARM 架构
- 寄存器
- 指令集
- 调用约定

### 操作系统
- 内存管理
- 进程线程
- 系统调用
- PE/ELF 文件格式

## 分析工具

### 静态分析
- IDA Pro - 交互式反汇编器
- Ghidra - NSA 开源工具
- Radare2 - 命令行框架
- Binary Ninja

### 动态分析
- x64dbg / OllyDbg - Windows 调试器
- GDB - Linux 调试器
- Frida - 动态插桩框架
- Process Monitor

### 其他工具
- PEStudio
- Strings
- Dependency Walker
- Wireshark

## 静态分析技术

1. 文件类型识别
2. 字符串提取
3. 导入导出表分析
4. 控制流图分析
5. 反编译代码阅读

## 动态分析技术

1. 断点设置
2. 单步执行
3. 内存查看
4. 寄存器监控
5. API 调用追踪

## 加壳与脱壳

### 常见壳
- UPX
- ASPack
- Themida
- VMProtect

### 脱壳方法
- 通用脱壳
- 内存转储
- 模拟执行

## 恶意代码分析

### 分析环境
- 虚拟机隔离
- REMnux 发行版
- 网络监控

### 分析步骤
1. 初步 triage
2. 静态分析
3. 动态分析
4. 行为分析
5. 代码分析

### 常见恶意软件
- 病毒
- 蠕虫
- 特洛伊木马
- 勒索软件
- Rootkit

## CTF 逆向题目

-  CrackMe 练习
-  Reverse Me 挑战
-  CTF competitions

## 法律道德

- 仅分析自己拥有或授权的软件
- 遵守相关法律法规
- 尊重知识产权
- 负责任披露漏洞

## 学习资源

- "逆向工程核心原理"
- "恶意代码分析实战"
- CrackMes.one
- Reverse Engineering Stack Exchange$$, 434, '/covers/reverse-engineering.jpg', 1, false, 1780, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(2000000000000000012, 1, 6, '区块链安全：智能合约漏洞与审计', 'blockchain-security-smart-contracts', '深入理解智能合约常见漏洞，学习安全审计方法和最佳实践', $$# 区块链安全：智能合约漏洞与审计

## 智能合约安全重要性

智能合约一旦部署就难以修改，漏洞可能导致巨额资金损失。

## 常见漏洞类型

### 1. 重入攻击（Reentrancy）
最著名的案例：The DAO 事件（损失 6000 万美元）

```solidity
// 漏洞示例
function withdraw(uint amount) public {
    require(balances[msg.sender] >= amount);
    msg.sender.call.value(amount)("");  // 危险！
    balances[msg.sender] -= amount;
}
```

**防御**: Checks-Effects-Interactions 模式

### 2. 整数溢出
Solidity 0.8.0 之前需要手动检查

**防御**: 使用 SafeMath 库或升级编译器版本

### 3. 访问控制不当
- 缺少权限检查
- 硬编码地址
- 所有权管理不当

**防御**: 使用 OpenZeppelin 的 Ownable

### 4. 预言机操纵
依赖单一价格源导致被操控

**防御**: 使用多个预言机、时间加权平均价格

### 5. 前端运行（Front-running）
交易被矿工优先打包

**防御**: 承诺-揭示方案、时间锁

### 6. 拒绝服务
- Gas 限制导致的循环失败
- 外部调用失败未处理

### 7. 随机数可预测
使用区块哈希作为随机数不安全

**防御**: Chainlink VRF

## 审计流程

### 1. 理解业务逻辑
- 阅读文档
- 理解代币经济学
- 绘制架构图

### 2. 静态分析
- 手动代码审查
- Slither 自动化工具
- Mythril 符号执行

### 3. 动态分析
- 单元测试
- 模糊测试
- 主网分叉测试

### 4. 形式化验证
- 数学证明正确性
- Certora、K Framework

### 5. 审计报告
- 漏洞分类和评级
- 修复建议
- 残留风险提示

## 安全开发最佳实践

- 使用经过审计的库（OpenZeppelin）
- 最小化合约复杂度
- 实施升级模式（Proxy Pattern）
- 多重签名钱包
- 时间锁机制
- 紧急暂停功能
- 全面的测试覆盖
- Bug Bounty 计划

## 审计工具

- Slither
- Mythril
- Echidna - 模糊测试
- Manticore - 符号执行
- Securify
- SmartCheck

## 著名安全事件

- The DAO (2016)
- Parity Wallet (2017)
- Poly Network (2021)
- Wormhole Bridge (2022)

## 学习资源

- Ethernaut CTF
- Damn Vulnerable DeFi
- Solidity by Example
- Smart Contract Weakness Classification$$, 467, '/covers/blockchain-security.jpg', 1, false, 1560, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 插入文章-标签关联数据
INSERT INTO t_post_tag (post_id, tag_id) VALUES 
-- 软件开发分类文章
(2000000000000000001, 2),    -- Spring Boot
(2000000000000000001, 17),   -- 后端开发
(2000000000000000001, 15),   -- 微服务

(2000000000000000002, 3),    -- Vue.js
(2000000000000000002, 7),    -- TypeScript
(2000000000000000002, 16),   -- 前端开发

(2000000000000000003, 4),    -- React
(2000000000000000003, 6),    -- JavaScript
(2000000000000000003, 27),   -- 移动开发

(2000000000000000004, 6),    -- JavaScript
(2000000000000000004, 17),   -- 后端开发
(2000000000000000004, 1),    -- Java

-- AI分类文章
(2000000000000000005, 23),   -- 人工智能
(2000000000000000005, 24),   -- 机器学习
(2000000000000000005, 19),   -- 算法

(2000000000000000006, 23),   -- 人工智能
(2000000000000000006, 24),   -- 机器学习
(2000000000000000006, 19),   -- 算法

(2000000000000000007, 23),   -- 人工智能
(2000000000000000007, 24),   -- 机器学习
(2000000000000000007, 19),   -- 算法

(2000000000000000008, 23),   -- 人工智能
(2000000000000000008, 24),   -- 机器学习
(2000000000000000008, 19),   -- 算法

-- 网络安全分类文章
(2000000000000000009, 26),   -- 网络安全
(2000000000000000009, 16),   -- 前端开发
(2000000000000000009, 17),   -- 后端开发

(2000000000000000010, 26),   -- 网络安全
(2000000000000000010, 28),   -- 开源项目
(2000000000000000010, 13),   -- Linux

(2000000000000000011, 26),   -- 网络安全
(2000000000000000011, 19),   -- 算法
(2000000000000000011, 13),   -- Linux

(2000000000000000012, 26),   -- 网络安全
(2000000000000000012, 25),   -- 云计算
(2000000000000000012, 28)    -- 开源项目
ON CONFLICT (post_id, tag_id) DO NOTHING;
