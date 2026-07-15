#!/bin/bash
set -e

# MyBlog 数据库初始化脚本
# 此脚本由 PostgreSQL 容器首次启动时自动执行
echo "===== MyBlog Database Initialization ====="
echo "Database: ${POSTGRES_DB}"
echo "User: ${POSTGRES_USER}"

# 检查并执行建表脚本
SQL_FILE="/docker-entrypoint-initdb.d/myblog_schema.sql"
if [ -f "$SQL_FILE" ]; then
    echo "发现建表脚本，正在执行..."
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -f "$SQL_FILE" 2>&1
    echo "✅ 表结构创建完成"
else
    echo "⚠️ 未找到 myblog_schema.sql，表结构将由 Flyway 自动管理"
fi

echo "===== Initialization Complete ====="