# Java 多版本管理 - 快速指南

## 🚀 一键安装（推荐）

```bash
cd /home/leo/IdeaProjects/myblog
./setup-java21.sh
```

脚本会自动：
1. ✅ 安装 SDKMAN!
2. ✅ 安装 Java 21 (Temurin)
3. ✅ 设置默认 Java 版本为 21
4. ✅ 创建项目配置文件 `.sdkmanrc`
5. ✅ 启用自动切换功能

## 📋 手动安装步骤

如果不想使用自动化脚本，可以手动执行：

### 1. 安装 SDKMAN!

```bash
# 安装依赖
sudo apt update
sudo apt install -y zip unzip curl

# 安装 SDKMAN!
curl -s "https://get.sdkman.io" | bash

# 加载 SDKMAN!
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

### 2. 安装 Java 21

```bash
# 查看可用的 Java 21 版本
sdk list java | grep 21

# 安装 Java 21 (Temurin)
sdk install java 21.0.2-tem

# 设置为默认版本
sdk default java 21.0.2-tem
```

### 3. 验证安装

```bash
java -version
# 应该显示: openjdk version "21.0.2"
```

## 🎯 常用命令

### 查看和管理 Java 版本

```bash
# 查看所有可安装的 Java 版本
sdk list java

# 查看已安装的 Java 版本
sdk list java | grep installed

# 查看当前使用的 Java 版本
sdk current java

# 临时切换到 Java 21
sdk use java 21.0.2-tem

# 设置默认 Java 版本为 21
sdk default java 21.0.2-tem

# 卸载某个 Java 版本
sdk uninstall java 25.0.3-ea
```

### 项目级别自动切换

```bash
# 进入项目目录
cd /home/leo/IdeaProjects/myblog

# 根据 .sdkmanrc 自动切换 Java 版本
sdk env

# 或者启用自动切换（在 ~/.bashrc 中添加）
echo "export sdkman_auto_env=true" >> ~/.bashrc
source ~/.bashrc
```

## 🔧 配置说明

### .sdkmanrc 文件

项目根目录的 `.sdkmanrc` 文件定义了项目所需的 Java 版本：

```properties
# MyBlog 项目 Java 版本配置
java=21.0.2-tem
```

当进入项目目录时，SDKMAN 会自动切换到指定的 Java 版本。

### 启用自动切换

在 `~/.bashrc` 或 `~/.zshrc` 中添加：

```bash
# SDKMAN! 自动切换
export sdkman_auto_env=true
```

然后重新加载配置：

```bash
source ~/.bashrc
```

## 💡 使用场景

### 场景1：构建项目

```bash
cd /home/leo/IdeaProjects/myblog

# SDKMAN 会自动切换到 Java 21（如果启用了自动切换）
# 或者手动切换
sdk env

# 构建项目
./gradlew clean bootJar
```

### 场景2：同时开发多个项目

```bash
# 项目A需要Java 21
cd ~/projects/project-a
sdk env  # 自动切换到 Java 21

# 项目B需要Java 17
cd ~/projects/project-b
sdk env  # 自动切换到 Java 17

# 每个终端会话独立管理 Java 版本
```

### 场景3：临时测试其他版本

```bash
# 当前使用 Java 21
java -version  # 21.0.2

# 临时切换到 Java 17 测试
sdk use java 17.0.9-tem
java -version  # 17.0.9

# 关闭终端后自动恢复默认版本
```

## 🎨 IDE 配置

### IntelliJ IDEA

1. **File → Project Structure → Project**
2. **SDK**: 选择 Java 21
3. **Language level**: 21

如果看不到 Java 21：
1. **File → Project Structure → SDKs**
2. 点击 **+** → **Add JDK**
3. 选择路径：`~/.sdkman/candidates/java/21.0.2-tem`

### VS Code

在 `.vscode/settings.json` 中配置：

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

## 🔍 故障排查

### 问题1：sdk 命令找不到

**解决方案**：
```bash
# 重新加载 SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 检查是否添加到 ~/.bashrc
grep sdkman-init ~/.bashrc

# 如果没有，添加它
echo 'source "$HOME/.sdkman/bin/sdkman-init.sh"' >> ~/.bashrc
source ~/.bashrc
```

### 问题2：Java 版本没有切换

**解决方案**：
```bash
# 检查当前版本
sdk current java

# 手动切换
sdk use java 21.0.2-tem

# 检查 JAVA_HOME
echo $JAVA_HOME
# 应该指向: ~/.sdkman/candidates/java/21.0.2-tem
```

### 问题3：Gradle 仍然使用旧版本

**解决方案**：
```bash
# 清理 Gradle 缓存
rm -rf ~/.gradle/caches

# 重新构建
./gradlew clean bootJar --no-daemon
```

### 问题4：自动切换不工作

**解决方案**：
```bash
# 检查是否启用了自动切换
grep sdkman_auto_env ~/.bashrc

# 如果没有，添加它
echo "export sdkman_auto_env=true" >> ~/.bashrc
source ~/.bashrc

# 检查 .sdkmanrc 文件是否存在
cat /home/leo/IdeaProjects/myblog/.sdkmanrc
```

## 📊 Java 版本对比

| 版本 | 状态 | LTS | 适用场景 |
|------|------|-----|---------|
| Java 8 | 长期支持 | ✅ | 老项目维护 |
| Java 11 | 长期支持 | ✅ | 企业应用 |
| Java 17 | 长期支持 | ✅ | 现代应用（推荐备选） |
| **Java 21** | **长期支持** | **✅** | **最新LTS（本项目使用）** |
| Java 25 | 非LTS | ❌ | 实验性功能 |

## 🚀 快速开始

```bash
# 1. 运行安装脚本
cd /home/leo/IdeaProjects/myblog
./setup-java21.sh

# 2. 重新加载配置
source ~/.bashrc

# 3. 验证 Java 版本
java -version

# 4. 构建项目
./gradlew clean bootJar

# 5. 运行项目
java -jar build/libs/myblog-0.0.1-SNAPSHOT.jar
```

## 📚 更多资源

- [SDKMAN! 官方文档](https://sdkman.io/)
- [Eclipse Temurin (Adoptium)](https://adoptium.net/)
- [Java 版本历史](https://en.wikipedia.org/wiki/Java_version_history)

---

**提示**: 使用 SDKMAN! 可以轻松管理多个 Java 版本，在不同项目间无缝切换！
