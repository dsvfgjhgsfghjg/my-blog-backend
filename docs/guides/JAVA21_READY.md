# Java 21 配置完成 - 使用说明

## ✅ 配置状态

Java 21 已成功安装并配置！

**当前版本**: OpenJDK 21.0.2 (Temurin)  
**管理工具**: SDKMAN!  
**项目配置**: .sdkmanrc 已创建

## 🚀 快速开始

### 方法1：使用快捷脚本（推荐）

每次打开新终端时，运行：

```bash
cd /home/leo/IdeaProjects/myblog
source use-java21.sh
```

### 方法2：手动切换

```bash
# 1. 加载 SDKMAN
source ~/.bashrc

# 2. 进入项目目录
cd /home/leo/IdeaProjects/myblog

# 3. 应用项目 Java 版本
sdk env

# 4. 验证
java -version  # 应该显示 21.0.2
```

## 📝 构建项目

```bash
# 确保使用 Java 21
source ~/.bashrc
cd /home/leo/IdeaProjects/myblog
sdk env

# 清理并构建
./gradlew clean bootJar

# 或者只编译
./gradlew compileJava
```

## 🔧 常用命令

```bash
# 查看当前 Java 版本
sdk current java

# 查看所有已安装的 Java 版本
sdk list java | grep installed

# 临时切换到其他版本
sdk use java 17.0.9-tem

# 设置全局默认版本
sdk default java 21.0.2-tem
```

## ⚙️ IDE 配置

### IntelliJ IDEA

1. **添加 JDK**:
   - File → Project Structure → SDKs
   - 点击 + → Add JDK
   - 选择路径: `~/.sdkman/candidates/java/21.0.2-tem`

2. **设置项目 SDK**:
   - File → Project Structure → Project
   - SDK: 选择 "21"
   - Language level: 21

### VS Code

在 `.vscode/settings.json` 中添加：

```json
{
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-21",
            "path": "~/.sdkman/candidates/java/21.0.2-tem",
            "default": true
        }
    ]
}
```

## ❓ 常见问题

### Q1: 新终端打开后 Java 版本不对？

**A**: 运行以下命令：
```bash
source ~/.bashrc
cd /home/leo/IdeaProjects/myblog
sdk env
```

或者使用快捷脚本：
```bash
source use-java21.sh
```

### Q2: 如何永久启用自动切换？

**A**: 已在 ~/.bashrc 中添加了 `export sdkman_auto_env=true`，重新打开终端即可。

### Q3: Gradle 仍然报错？

**A**: 清理 Gradle 缓存：
```bash
rm -rf ~/.gradle/caches
./gradlew clean bootJar --no-daemon
```

### Q4: 如何查看已安装的 Java 版本？

**A**: 
```bash
sdk list java | grep installed
```

## 📊 当前配置

| 项目 | 值 |
|------|-----|
| Java 版本 | 21.0.2 (Temurin) |
| 安装位置 | ~/.sdkman/candidates/java/21.0.2-tem |
| 项目配置 | .sdkmanrc (java=21.0.2-tem) |
| 自动切换 | 已启用 (sdkman_auto_env=true) |
| Gradle 版本 | 8.13 |

## 🎯 下一步

1. ✅ Java 21 已配置
2. ⏭️ 构建项目: `./gradlew clean bootJar`
3. ⏭️ 运行项目: `java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar`

---

**提示**: 每次打开新终端，记得先运行 `source ~/.bashrc` 或 `source use-java21.sh`！
