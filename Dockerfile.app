# 构建阶段
FROM gradle:8.10-jdk21 AS builder

WORKDIR /app

# 复制构建文件
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# 下载依赖（利用Docker缓存层）
RUN ./gradlew dependencies --no-daemon

# 复制源代码
COPY src ./src

# 构建应用
RUN ./gradlew bootJar --no-daemon

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非root用户
RUN addgroup -S spring && adduser -S spring -G spring

# 从构建阶段复制JAR包
COPY --from=builder /app/build/libs/*.jar app.jar

# 修改所有者
RUN chown -R spring:spring /app

# 切换到非root用户
USER spring:spring

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
