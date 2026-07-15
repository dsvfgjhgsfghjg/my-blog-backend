#!/bin/bash
# MinIO 初始化脚本 - 创建 bucket 并设置权限

set -e

echo "等待 MinIO 服务启动..."
sleep 5

# 配置 MinIO 客户端
mc alias set local http://localhost:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}

# 创建 bucket（如果不存在）
echo "创建 bucket: ${MINIO_BUCKET_NAME}"
mc mb local/${MINIO_BUCKET_NAME} || true

# 设置 bucket 为公开访问（可选）
echo "设置 bucket 访问权限为 public"
mc anonymous set public local/${MINIO_BUCKET_NAME} || true

echo "MinIO 初始化完成！"
