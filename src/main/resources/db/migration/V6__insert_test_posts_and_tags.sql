-- 插入测试文章数据（使用美元引用避免转义问题）
INSERT INTO t_post (id, user_id, category_id, title, slug, summary, content, word_count, cover_image, status, is_top, view_count, is_deleted, create_time, update_time, publish_time) VALUES 
(1000000000000000001, 1, 1, 'Java Spring Boot 入门指南', 'java-spring-boot-guide', 'Spring Boot 是一个用于简化 Spring 应用初始搭建以及开发过程的框架', $$# Java Spring Boot 入门指南

## 什么是 Spring Boot？

Spring Boot 是由 Pivotal 团队提供的全新框架，其设计目的是用来简化新 Spring 应用的初始搭建以及开发过程。

## 主要特性

- 独立运行的 Spring 项目
- 内嵌 Servlet 容器
- 提供 starter 简化 Maven 配置
- 自动配置 Spring
- 无代码生成和 XML 配置

## 快速开始

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

这就是一个简单的 Spring Boot 应用！$$, 256, 'https://example.com/cover1.jpg', 1, false, 100, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000002, 1, 2, 'Vue.js 3.0 Composition API 详解', 'vuejs-composition-api', 'Vue 3.0 引入了 Composition API，为组件逻辑复用提供了更灵活的方案', $$# Vue.js 3.0 Composition API 详解

## 为什么需要 Composition API？

在 Vue 2.x 中，我们使用 Options API 组织代码。但随着组件变得复杂，相关逻辑会被分散到不同的选项中。

## Composition API 优势

- 更好的逻辑复用
- 更好的 TypeScript 支持
- 更灵活的代码组织

## 基本用法

使用 ref、computed、onMounted 等 API 来组织组件逻辑。$$, 312, 'https://example.com/cover2.jpg', 1, true, 250, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000003, 1, 1, 'Python 数据分析实战', 'python-data-analysis', '使用 Python 进行数据分析和可视化的完整指南', $$# Python 数据分析实战

## 常用库介绍

- NumPy: 数值计算
- Pandas: 数据处理
- Matplotlib: 数据可视化
- Seaborn: 统计图表

## 数据加载

使用 pandas 读取 CSV 文件并进行基本信息查看。

## 数据清洗

处理缺失值、重复值和异常值是数据分析的重要步骤。$$, 198, 'https://example.com/cover3.jpg', 1, false, 180, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000004, 1, 2, 'Docker 容器化部署最佳实践', 'docker-deployment-best-practices', '学习如何使用 Docker 进行应用容器化和部署', $$# Docker 容器化部署最佳实践

## 什么是 Docker？

Docker 是一个开源的应用容器引擎，让开发者可以打包他们的应用以及依赖包到一个可移植的容器中。

## Dockerfile 示例

创建 Dockerfile 定义应用镜像，使用 node:16-alpine 作为基础镜像。

## Docker Compose

使用 Docker Compose 管理多容器应用非常简单。$$, 225, 'https://example.com/cover4.jpg', 1, false, 320, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000005, 1, 3, 'React Hooks 完全指南', 'react-hooks-guide', '深入理解 React Hooks 的使用方法和最佳实践', $$# React Hooks 完全指南

## useState

最基本的 Hook，用于在函数组件中添加状态。

## useEffect

处理副作用，如数据获取、订阅等。

## 自定义 Hooks

可以提取组件逻辑到可重用的函数中。$$, 189, 'https://example.com/cover5.jpg', 1, false, 150, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000006, 1, 1, 'MySQL 性能优化技巧', 'mysql-performance-optimization', '提升 MySQL 数据库性能的实用技巧和策略', $$# MySQL 性能优化技巧

## 索引优化

- 为查询频繁的列添加索引
- 避免在索引列上使用函数
- 使用覆盖索引

## 查询优化

避免 SELECT *，使用 EXPLAIN 分析查询性能。

## 缓存策略

合理使用查询缓存和应用层缓存可以显著提升性能。$$, 167, 'https://example.com/cover6.jpg', 0, false, 0, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),

(1000000000000000007, 1, 2, 'Kubernetes 基础教程', 'kubernetes-basics', 'Kubernetes 容器编排平台的入门教程', $$# Kubernetes 基础教程

## 核心概念

- Pod: 最小的部署单元
- Service: 服务发现和负载均衡
- Deployment: 声明式更新
- Namespace: 资源隔离

## 创建 Deployment

使用 YAML 文件定义 Deployment，设置副本数和容器配置。$$, 234, 'https://example.com/cover7.jpg', 1, false, 200, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000008, 1, 3, 'TypeScript 高级类型技巧', 'typescript-advanced-types', '探索 TypeScript 中的高级类型系统和实用技巧', $$# TypeScript 高级类型技巧

## 泛型

使用泛型创建可重用的组件。

## 联合类型和交叉类型

组合多个类型创建更灵活的类型定义。

## 条件类型

根据条件选择不同的类型。

## 工具类型

TypeScript 提供了许多实用的工具类型，如 Partial、Pick、Omit 等。$$, 178, 'https://example.com/cover8.jpg', 1, false, 90, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000009, 1, 1, 'Redis 缓存策略详解', 'redis-caching-strategies', '如何使用 Redis 实现高效的缓存策略', $$# Redis 缓存策略详解

## 常见缓存模式

### Cache-Aside
最常用的缓存模式，应用先查缓存，未命中再查数据库。

### Write-Through
写数据时同时更新缓存和数据库。

### Write-Behind
异步写入数据库，提高写入性能。

## 缓存失效策略

- TTL（Time To Live）
- LRU（Least Recently Used）
- 主动失效

## Redis 数据结构

String、Hash、List、Set、Sorted Set 等。$$, 195, 'https://example.com/cover9.jpg', 1, false, 175, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

(1000000000000000010, 1, 2, 'Git 工作流最佳实践', 'git-workflow-best-practices', '团队协作中的 Git 工作流和版本控制策略', $$# Git 工作流最佳实践

## Git Flow

- master: 生产环境分支
- develop: 开发分支
- feature: 功能分支
- release: 发布分支
- hotfix: 热修复分支

## 提交规范

使用规范的提交消息格式：feat、fix、docs、style、refactor、test、chore。

## 合并策略

- Merge Commit
- Squash and Merge
- Rebase and Merge$$, 201, 'https://example.com/cover10.jpg', 1, false, 280, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 插入文章-标签关联数据
INSERT INTO t_post_tag (post_id, tag_id) VALUES 
-- 文章1: Java Spring Boot 入门指南
(1000000000000000001, 1),   -- Java
(1000000000000000001, 2),   -- Spring Boot
(1000000000000000001, 17),  -- 后端开发

-- 文章2: Vue.js 3.0 Composition API 详解
(1000000000000000002, 3),   -- Vue.js
(1000000000000000002, 6),   -- JavaScript
(1000000000000000002, 16),  -- 前端开发

-- 文章3: Python 数据分析实战
(1000000000000000003, 5),   -- Python
(1000000000000000003, 18),  -- 数据库
(1000000000000000003, 23),  -- 人工智能

-- 文章4: Docker 容器化部署最佳实践
(1000000000000000004, 13),  -- Docker
(1000000000000000004, 14),  -- Kubernetes
(1000000000000000004, 20),  -- DevOps

-- 文章5: React Hooks 完全指南
(1000000000000000005, 4),   -- React
(1000000000000000005, 6),   -- JavaScript
(1000000000000000005, 16),  -- 前端开发

-- 文章6: MySQL 性能优化技巧 (草稿)
(1000000000000000006, 10),  -- MySQL
(1000000000000000006, 18),  -- 数据库
(1000000000000000006, 17),  -- 后端开发

-- 文章7: Kubernetes 基础教程
(1000000000000000007, 14),  -- Kubernetes
(1000000000000000007, 13),  -- Docker
(1000000000000000007, 20),  -- DevOps

-- 文章8: TypeScript 高级类型技巧
(1000000000000000008, 7),   -- TypeScript
(1000000000000000008, 6),   -- JavaScript
(1000000000000000008, 16),  -- 前端开发

-- 文章9: Redis 缓存策略详解
(1000000000000000009, 12),  -- Redis
(1000000000000000009, 17),  -- 后端开发
(1000000000000000009, 18),  -- 数据库

-- 文章10: Git 工作流最佳实践
(1000000000000000010, 15),  -- Git
(1000000000000000010, 20),  -- DevOps
(1000000000000000010, 28)   -- 开源项目
ON CONFLICT (post_id, tag_id) DO NOTHING;
