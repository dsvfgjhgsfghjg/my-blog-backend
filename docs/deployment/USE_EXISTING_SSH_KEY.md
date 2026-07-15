# 使用现有 SSH 密钥配置后端部署

## 🚀 快速开始

### 方法 1: 交互式配置（推荐）

```bash
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

脚本会提示你选择：
- **选项 1**: 使用现有的 SSH 密钥（会自动列出可用的密钥）
- **选项 2**: 生成新的密钥对
- **选项 3**: 手动指定密钥路径

### 方法 2: 直接指定密钥路径

如果你想直接使用某个特定的密钥，可以先创建符号链接：

```bash
# 假设你的密钥是 ~/.ssh/id_ed25519
ln -s ~/.ssh/id_ed25519 ~/.ssh/gitea_deploy_key
ln -s ~/.ssh/id_ed25519.pub ~/.ssh/gitea_deploy_key.pub

# 然后运行脚本
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy
```

---

## 📋 常见 SSH 密钥位置

脚本会自动检测以下位置的密钥：

```bash
~/.ssh/id_rsa          # RSA 密钥
~/.ssh/id_ed25519      # Ed25519 密钥（推荐）
~/.ssh/id_ecdsa        # ECDSA 密钥
~/.ssh/github          # GitHub 专用密钥
~/.ssh/gitlab          # GitLab 专用密钥
```

---

## 🔍 查看你的 SSH 密钥

```bash
# 列出所有私钥
ls -la ~/.ssh/id_* | grep -v ".pub$"

# 示例输出：
# -rw------- 1 leo leo  411 May 16 10:00 /home/leo/.ssh/id_ed25519
# -rw------- 1 leo leo 3381 May 16 10:00 /home/leo/.ssh/id_rsa
```

---

## ✅ 验证密钥是否可用

```bash
# 测试 SSH 连接（替换为你的实际密钥和服务器）
ssh -i ~/.ssh/your_key_name ubuntu@113.31.105.30 echo "SSH OK"

# 如果输出 "SSH OK"，说明密钥可用
```

---

## 🔧 如果公钥未添加到服务器

### 方法 1: 使用 ssh-copy-id（推荐）

```bash
ssh-copy-id -i ~/.ssh/your_key_name.pub ubuntu@113.31.105.30
```

### 方法 2: 手动添加

```bash
# 1. 复制公钥内容
cat ~/.ssh/your_key_name.pub

# 2. SSH 登录服务器
ssh ubuntu@113.31.105.30

# 3. 在服务器上执行
mkdir -p ~/.ssh
echo "<粘贴公钥内容>" >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
exit
```

---

## 🎯 完整配置流程示例

假设你的密钥是 `~/.ssh/id_ed25519`：

```bash
# 步骤 1: 运行配置助手
./scripts/setup/configure-backend-secrets.sh 113.31.105.30 ubuntu /home/ubuntu/myblog-deploy

# 当提示时，选择选项 1（使用现有密钥）
# 然后输入: /home/leo/.ssh/id_ed25519

# 步骤 2: 脚本会自动：
# ✓ 测试 SSH 连接
# ✓ 如果失败，提示添加公钥
# ✓ 检查 Docker 环境
# ✓ 生成强密码
# ✓ Base64 编码私钥
# ✓ 输出 Secrets 配置清单

# 步骤 3: 复制输出的 Secrets，在 Gitea 中配置

# 步骤 4: 触发部署
git push origin master
```

---

## ⚠️ 注意事项

1. **密钥权限**
   ```bash
   # 私钥必须是 600 权限
   chmod 600 ~/.ssh/your_key_name
   
   # 公钥可以是 644 权限
   chmod 644 ~/.ssh/your_key_name.pub
   ```

2. **不要使用加密的密钥**
   - 如果密钥有 passphrase，Gitea Actions 无法使用
   - 建议专门为 CI/CD 创建一个无密码的密钥

3. **Base64 编码**
   - 脚本会自动对你的私钥进行 Base64 编码
   - 不要手动编码，让脚本处理

---

## 🆘 常见问题

### Q: 我有多个 SSH 密钥，应该用哪个？

A: 建议使用专门用于 CI/CD 的密钥，或者创建一个新密钥：
```bash
ssh-keygen -t ed25519 -C "gitea_ci_cd" -f ~/.ssh/gitea_deploy_key -N ""
```

### Q: 密钥有密码怎么办？

A: 为 CI/CD 创建一个无密码的新密钥：
```bash
ssh-keygen -t ed25519 -C "gitea_deploy" -f ~/.ssh/gitea_deploy_key -N ""
```

### Q: 如何查看公钥内容？

A: 
```bash
cat ~/.ssh/your_key_name.pub
```

### Q: 测试 SSH 连接失败？

A: 确保公钥已添加到服务器：
```bash
ssh-copy-id -i ~/.ssh/your_key_name.pub ubuntu@YOUR_SERVER_IP
```

---

## 📞 需要帮助？

查看详细文档：
- [BACKEND_SECRETS_GUIDE.md](./BACKEND_SECRETS_GUIDE.md)
- [BACKEND_SECRETS_QUICKSTART.md](./BACKEND_SECRETS_QUICKSTART.md)
