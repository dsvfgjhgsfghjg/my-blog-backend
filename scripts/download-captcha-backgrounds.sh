#!/bin/bash

# 滑块验证码背景图下载脚本
# 用途：从在线服务下载 310x155 尺寸的背景图片

set -e

# 配置
OUTPUT_DIR="src/main/resources/static/captcha-backgrounds"
IMAGE_COUNT=20
IMAGE_WIDTH=310
IMAGE_HEIGHT=155

echo "======================================"
echo "滑块验证码背景图下载工具"
echo "======================================"
echo "目标目录: $OUTPUT_DIR"
echo "图片数量: $IMAGE_COUNT"
echo "图片尺寸: ${IMAGE_WIDTH}x${IMAGE_HEIGHT}"
echo ""

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

# 检查目录是否存在
if [ ! -d "$OUTPUT_DIR" ]; then
    echo "❌ 错误：无法创建目录 $OUTPUT_DIR"
    exit 1
fi

echo "📥 开始下载图片..."
echo ""

# 下载图片（使用 Picsum Photos）
for i in $(seq 1 $IMAGE_COUNT); do
    filename=$(printf "bg_%03d.jpg" $i)
    filepath="$OUTPUT_DIR/$filename"
    
    # 如果文件已存在，跳过
    if [ -f "$filepath" ]; then
        echo "⏭️  跳过已存在的文件: $filename"
        continue
    fi
    
    echo "⬇️  下载第 $i/$IMAGE_COUNT 张图片: $filename"
    
    # 使用 Picsum Photos API（无需 API Key）
    url="https://picsum.photos/${IMAGE_WIDTH}/${IMAGE_HEIGHT}?random=$i"
    
    # 下载图片
    if curl -s -o "$filepath" -w "%{http_code}" "$url" | grep -q "200"; then
        echo "✅ 成功: $filename"
    else
        echo "❌ 失败: $filename"
        rm -f "$filepath"  # 删除失败的文件
    fi
    
    # 避免请求过快，等待 1 秒
    sleep 1
done

echo ""
echo "======================================"
echo "下载完成！"
echo "======================================"

# 统计成功下载的图片数量
success_count=$(ls -1 "$OUTPUT_DIR"/bg_*.jpg 2>/dev/null | wc -l)
echo "✅ 成功下载: $success_count 张图片"
echo "📁 保存位置: $OUTPUT_DIR"
echo ""
echo "提示："
echo "1. 如果某些图片下载失败，可以重新运行此脚本"
echo "2. 也可以手动从以下网站下载图片并放入该目录："
echo "   - https://picsum.photos/"
echo "   - https://unsplash.com/"
echo "   - https://www.pexels.com/"
echo ""
echo "推荐的图片类型："
echo "- 风景照片（山川、海洋、森林）"
echo "- 建筑照片（城市、桥梁、地标）"
echo "- 抽象图案（纹理、渐变、几何）"
echo "- 自然元素（花朵、树叶、云朵）"
echo ""
echo "注意事项："
echo "- 避免使用包含人脸的照片（隐私问题）"
echo "- 避免使用有文字的图片（干扰验证）"
echo "- 选择对比度适中的图片（便于识别缺口）"
echo "- 图片内容应该相对简单（不要太复杂）"
