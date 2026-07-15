# 配置文件同步说明

## 📁 项目结构

- **前端项目**: `/home/leo/WebstormProjects/myblog` （主配置）
- **后端项目**: `/home/leo/IdeaProjects/myblog` （通过软链接同步）

---

## 🔗 软链接配置

### 已创建的软链接

| 文件 | 源文件（WebstormProjects） | 软链接（IdeaProjects） |
|------|--------------------------|---------------------|
| Dockerfile.frontend | `/home/leo/WebstormProjects/myblog/Dockerfile.frontend` | `/home/leo/IdeaProjects/myblog/Dockerfile.frontend` → 源文件 |
| apache.conf | `/home/leo/WebstormProjects/myblog/frontend/apache.conf` | `/home/leo/IdeaProjects/myblog/frontend/apache.conf` → 源文件 |

---

## ✅ 优势

1. **单一数据源** - 所有配置修改在 WebstormProjects 进行
2. **自动同步** - IdeaProjects 自动反映最新更改
3. **避免冲突** - 不会出现两个项目配置不一致的问题
4. **便于维护** - 只需维护一份配置文件

---

## 📝 使用指南

### 修改配置文件

**始终在 WebstormProjects 中修改**：

```bash
# 正确的做法
cd /home/leo/WebstormProjects/myblog
vim Dockerfile.frontend
vim frontend/apache.conf

# 修改后，IdeaProjects 会自动同步
```

### 验证同步

```bash
# 检查软链接
ls -la /home/leo/IdeaProjects/myblog/Dockerfile.frontend
ls -la /home/leo/IdeaProjects/myblog/frontend/apache.conf

# 应该显示：
# Dockerfile.frontend -> /home/leo/WebstormProjects/myblog/Dockerfile.frontend
# apache.conf -> /home/leo/WebstormProjects/myblog/frontend/apache.conf
```

---

## ⚠️ 注意事项

1. **不要直接修改 IdeaProjects 的配置文件**
   - 这些是软链接，直接修改会影响源文件
   - 建议在 WebstormProjects 中统一修改

2. **Git 提交**
   - WebstormProjects: 正常提交配置文件
   - IdeaProjects: 软链接不会被 Git 跟踪（通常已加入 .gitignore）

3. **删除软链接**
   ```bash
   # 如果需要删除软链接（不影响源文件）
   rm /home/leo/IdeaProjects/myblog/Dockerfile.frontend
   rm /home/leo/IdeaProjects/myblog/frontend/apache.conf
   
   # 重新创建
   ln -s /home/leo/WebstormProjects/myblog/Dockerfile.frontend /home/leo/IdeaProjects/myblog/Dockerfile.frontend
   ln -s /home/leo/WebstormProjects/myblog/frontend/apache.conf /home/leo/IdeaProjects/myblog/frontend/apache.conf
   ```

---

## 🔧 当前配置状态

### Dockerfile.frontend
- ✅ 基于 `httpd:2.4.67-trixie`
- ✅ 安装 Certbot（Let's Encrypt）
- ✅ 启用 SSL 模块
- ✅ 暴露 80 和 443 端口
- ✅ 包含自定义 VirtualHost 配置

### apache.conf
- ✅ HTTP VirtualHost（80端口）- 重定向到 HTTPS
- ✅ HTTPS VirtualHost（443端口）- SSL 配置
- ✅ ACME 挑战路径支持
- ✅ 反向代理到后端
- ✅ SPA 路由支持
- ✅ 安全头配置（HSTS、XSS保护等）

---

## 📊 同步状态

最后更新：2026-05-17  
状态：✅ 正常同步

---

**相关文档**:
- [LETSENCRYPT_SSL_SETUP.md](./docs/deployment/LETSENCRYPT_SSL_SETUP.md)
- [SSL_CONFIGURATION_SUMMARY.md](./docs/deployment/SSL_CONFIGURATION_SUMMARY.md)
