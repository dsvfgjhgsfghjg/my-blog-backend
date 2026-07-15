#!/bin/bash

# 滑块验证码背景图下载并上传到 MinIO 脚本
# 用途：从 Picsum 下载图片，然后上传到 MinIO 存储

set -e

# ==================== 配置区域 ====================
# MinIO 配置
MINIO_ENDPOINT="http://localhost:9000"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="captcha-backgrounds"

# 图片配置
IMAGE_COUNT=20
IMAGE_WIDTH=310
IMAGE_HEIGHT=155

# 临时目录
TEMP_DIR="/tmp/captcha-backgrounds-download"
# ================================================

echo "======================================"
echo "滑块验证码背景图 - 下载并上传到 MinIO"
echo "======================================"
echo "MinIO 地址: $MINIO_ENDPOINT"
echo "Bucket 名称: $MINIO_BUCKET"
echo "图片数量: $IMAGE_COUNT"
echo "图片尺寸: ${IMAGE_WIDTH}x${IMAGE_HEIGHT}"
echo ""

# 检查 mc (MinIO Client) 是否安装
if ! command -v mc &> /dev/null; then
    echo "❌ 错误：未找到 mc (MinIO Client)"
    echo "请先安装 mc："
    echo "  wget https://dl.min.io/client/mc/release/linux-amd64/mc"
    echo "  chmod +x mc"
    echo "  sudo mv mc /usr/local/bin/"
    exit 1
fi

# 创建临时目录
echo "📁 创建临时目录: $TEMP_DIR"
mkdir -p "$TEMP_DIR"

# 配置 MinIO Client
echo "⚙️  配置 MinIO Client..."
mc alias set myminio "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" --api S3v4 > /dev/null 2>&1

# 检查 bucket 是否存在，不存在则创建
echo "🔍 检查 Bucket: $MINIO_BUCKET"
if ! mc ls myminio/"$MINIO_BUCKET" > /dev/null 2>&1; then
    echo "📦 创建 Bucket: $MINIO_BUCKET"
    mc mb myminio/"$MINIO_BUCKET"
else
    echo "✅ Bucket 已存在"
fi

# 设置 bucket 为公开读取（可选，根据需要调整）
echo "🔓 设置 Bucket 访问策略为 public..."
mc anonymous set public myminio/"$MINIO_BUCKET"

echo ""
echo "📥 开始下载图片..."
echo ""

# 下载图片
SUCCESS_COUNT=0
FAIL_COUNT=0

for i in $(seq 1 $IMAGE_COUNT); do
    filename=$(printf "bg_%03d.jpg" $i)
    filepath="$TEMP_DIR/$filename"
    
    # 如果文件已存在，跳过
    if [ -f "$filepath" ]; then
        echo "⏭️  跳过已存在的文件: $filename"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        continue
    fi
    
    echo "⬇️  下载第 $i/$IMAGE_COUNT 张图片: $filename"
    
    # 使用 Picsum Photos API
    url="https://picsum.photos/${IMAGE_WIDTH}/${IMAGE_HEIGHT}?random=$i"
    
    # 下载图片
    HTTP_CODE=$(curl -s -o "$filepath" -w "%{http_code}" "$url")
    
    if [ "$HTTP_CODE" = "200" ]; then
        echo "✅ 成功: $filename"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo "❌ 失败 (HTTP $HTTP_CODE): $filename"
        rm -f "$filepath"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    
    # 避免请求过快
    sleep 1
done

echo ""
echo "======================================"
echo "下载完成！"
echo "======================================"
echo "✅ 成功: $SUCCESS_COUNT 张"
echo "❌ 失败: $FAIL_COUNT 张"
echo ""

# 如果没有成功下载的图片，退出
if [ $SUCCESS_COUNT -eq 0 ]; then
    echo "❌ 没有成功下载任何图片，退出"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# 上传到 MinIO
echo "📤 开始上传到 MinIO..."
echo ""

UPLOAD_SUCCESS=0
UPLOAD_FAIL=0

for filepath in "$TEMP_DIR"/bg_*.jpg; do
    if [ ! -f "$filepath" ]; then
        continue
    fi
    
    filename=$(basename "$filepath")
    echo "⬆️  上传: $filename"
    
    if mc cp "$filepath" myminio/"$MINIO_BUCKET"/"$filename" > /dev/null 2>&1; then
        echo "✅ 上传成功: $filename"
        UPLOAD_SUCCESS=$((UPLOAD_SUCCESS + 1))
    else
        echo "❌ 上传失败: $filename"
        UPLOAD_FAIL=$((UPLOAD_FAIL + 1))
    fi
done

echo ""
echo "======================================"
echo "上传完成！"
echo "======================================"
echo "✅ 上传成功: $UPLOAD_SUCCESS 张"
echo "❌ 上传失败: $UPLOAD_FAIL 张"
echo ""

# 显示 MinIO 中的文件列表
echo "📋 MinIO Bucket 内容:"
mc ls myminio/"$MINIO_BUCKET"

echo ""
echo "======================================"
echo "全部完成！"
echo "======================================"
echo ""
echo "📊 统计信息："
echo "  - 下载成功: $SUCCESS_COUNT 张"
echo "  - 上传成功: $UPLOAD_SUCCESS 张"
echo "  - Bucket: $MINIO_BUCKET"
echo "  - 访问地址: $MINIO_ENDPOINT/$MINIO_BUCKET/"
echo ""
echo "💡 提示："
echo "1. 图片已存储在 MinIO 中"
echo "2. 可以通过 API 直接访问: $MINIO_ENDPOINT/$MINIO_BUCKET/bg_001.jpg"
echo "3. 如需修改应用以从 MinIO 加载图片，请更新 CaptchaBackgroundService"
echo ""

# 清理临时文件
echo "🧹 清理临时文件..."
rm -rf "$TEMP_DIR"
echo "✅ 临时文件已清理"
echo ""
