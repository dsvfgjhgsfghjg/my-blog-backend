# JAR 包重构指南

## ✅ 已完成的重构

### 1. 修改 build.gradle
- ✅ 移除了 `war` 插件
- ✅ 保留 `java` 和 `spring-boot` 插件
- ✅ 现在会生成可执行的 JAR 包

### 2. 删除 ServletInitializer
- ✅ 删除了 `ServletInitializer.java`（WAR 包专用）
- ✅ JAR 包不需要此类

### 3. 创建 Dockerfile
- ✅ 创建了 `Dockerfile.app` 用于构建应用镜像
- ✅ 使用多阶段构建（Gradle + JRE）
- ✅ 包含健康检查和非 root 用户

## ⚠️ 当前问题

### Java 版本不匹配

**问题**: 系统安装的是 Java 25，但项目配置的是 Java 21

**错误信息**:
```
Unsupported class file major version 69
```

**原因**: 
- Java 25 的 class file major version 是 69
- Gradle 8.x 不支持 Java 25
- Gradle 9.0 尚未正式发布

## 🔧 解决方案

### 方案一：降级到 Java 21（推荐）

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# 设置默认 Java
sudo update-alternatives --config java
sudo update-alternatives --config javac

# 验证
java -version  # 应该显示 21.x.x
```

然后在项目中设置 JAVA_HOME:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew clean bootJar
```

### 方案二：等待 Gradle 9.0 发布

Gradle 9.0 将支持 Java 25，但目前尚未正式发布。

### 方案三：使用 SDKMAN 管理多版本 Java

```bash
# 安装 SDKMAN
curl -s "https://get.sdkman.io" | bash

# 安装 Java 21
sdk install java 21.0.2-tem

# 切换到 Java 21
sdk use java 21.0.2-tem

# 构建项目
./gradlew clean bootJar
```

## 📦 构建命令

### 本地构建（使用 Java 21）

```bash
# 清理并构建
./gradlew clean bootJar

# JAR 包位置
ls -lh build/libs/*.jar
```

### Docker 构建

```bash
# 使用 Dockerfile.app 构建
docker build -f Dockerfile.app -t myblog:latest .

# 运行容器
docker run -p 8080:8080 myblog:latest
```

## 🎯 JAR vs WAR 对比

| 特性 | JAR | WAR |
|------|-----|-----|
| 部署方式 | 独立运行 | 需要 Servlet 容器 |
| 启动命令 | `java -jar app.jar` | 部署到 Tomcat/Jetty |
| 内置服务器 | Tomcat（内嵌） | 使用外部容器 |
| 适用场景 | 微服务、云原生 | 传统企业应用 |
| Docker 友好 | ✅ 优秀 | ⚠️ 一般 |

## 📝 配置文件说明

### build.gradle
```groovy
plugins {
    id 'java'                    // Java 插件
    id 'org.springframework.boot' version '3.2.5'  // Spring Boot 插件
    id 'io.spring.dependency-management' version '1.1.7'
}
// 注意：已移除 'war' 插件
```

### Dockerfile.app
```dockerfile
# 构建阶段
FROM gradle:8.13-jdk21 AS builder
# ... 构建逻辑 ...

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
# ... 运行配置 ...
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 🚀 运行 JAR 包

### 直接运行
```bash
java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar
```

### 指定配置文件
```bash
java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 后台运行
```bash
nohup java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

## 📊 验证构建

```bash
# 1. 检查 JAR 包
ls -lh build/libs/

# 2. 查看 JAR 内容
jar tf build/libs/myblog-0.0.1-SNAPSHOT.jar | head -20

# 3. 测试运行
java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar

# 4. 访问应用
curl http://localhost:8080/api/posts/public
```

## 💡 建议

1. **立即行动**: 降级到 Java 21（项目原本就配置为 Java 21）
2. **长期规划**: 考虑使用 SDKMAN 管理多版本 Java
3. **CI/CD**: 在构建服务器上使用 Java 21
4. **文档更新**: 在 README 中明确说明需要 Java 21

## 🔗 相关资源

- [Gradle Java 兼容性](https://docs.gradle.org/current/userguide/compatibility.html)
- [Spring Boot 可执行 JAR](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html)
- [Java 版本历史](https://en.wikipedia.org/wiki/Java_version_history)

---

**更新时间**: 2026-05-06  
**状态**: 代码已重构，需要 Java 21 才能构建
