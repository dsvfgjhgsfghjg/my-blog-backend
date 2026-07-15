# Docker 环境部署指南

## 服务概览

当前环境包含以下服务，全部运行在 `myblog_myblog-network` 网络中：

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| PostgreSQL | myblog-postgres | 5432 | 数据库服务 |
| Redis | myblog-redis | 6379 | 缓存服务 |
| MinIO | myblog-minio | 9000, 9001 | 对象存储（API: 9000, Console: 9001） |
| Gitea | myblog-gitea | 3000, 222 | 代码托管平台（Web: 3000, SSH: 222） |

## 快速启动

```bash
# 启动所有服务
docker compose up -d

# 查看服务状态
docker ps

# 查看服务日志
docker compose logs -f [service_name]

# 停止所有服务
docker compose down
```

## 访问地址

- **Gitea**: http://localhost:3000
- **MinIO Console**: http://localhost:9001
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **MinIO API**: http://localhost:9000

## 配置说明

### 数据库配置
- 用户名: `Leoxiaoblog`
- 密码: `your-password-here`
- 数据库: `myblog` (应用数据), `gitea` (Gitea 数据)

### MinIO 配置
- Access Key: `Leoxiao`
- Secret Key: `your-password-here`

## 镜像源

所有镜像均使用轩辕加速源：`04eh77udwizz5vqdbk.xuanyuan.run`

## 数据持久化

数据通过 Docker volumes 持久化：
- `postgres-data`: PostgreSQL 数据
- `redis-data`: Redis 数据
- `minio-data`: MinIO 数据
- `./gitea`: Gitea 数据（本地目录挂载）

## 注意事项

1. PostgreSQL 18+ 使用 `/var/lib/postgresql` 作为数据目录
2. 所有服务都配置了健康检查
3. Gitea 依赖 PostgreSQL，会在数据库就绪后自动启动
