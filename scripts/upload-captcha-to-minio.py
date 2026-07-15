#!/usr/bin/env python3
"""
滑块验证码背景图下载并上传到 MinIO
依赖: pip install boto3 requests
"""

import os
import sys
import time
import requests
import boto3
from botocore.client import Config

# ==================== 配置区域 ====================
# MinIO 配置
MINIO_ENDPOINT = os.getenv('MINIO_ENDPOINT', 'http://localhost:9000')
MINIO_ACCESS_KEY = os.getenv('MINIO_ROOT_USER', 'Leoxiao')
MINIO_SECRET_KEY = os.getenv('MINIO_ROOT_PASSWORD', 'your-password-here')
MINIO_BUCKET = 'captcha-backgrounds'

# 图片配置
IMAGE_COUNT = 20
IMAGE_WIDTH = 310
IMAGE_HEIGHT = 155

# 临时目录
TEMP_DIR = '/tmp/captcha-backgrounds-download'
# ================================================

def setup_minio_client():
    """创建 MinIO 客户端"""
    s3_client = boto3.client(
        's3',
        endpoint_url=MINIO_ENDPOINT,
        aws_access_key_id=MINIO_ACCESS_KEY,
        aws_secret_access_key=MINIO_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='us-east-1'
    )
    return s3_client

def ensure_bucket(s3_client):
    """确保 bucket 存在"""
    try:
        s3_client.head_bucket(Bucket=MINIO_BUCKET)
        print(f"✅ Bucket '{MINIO_BUCKET}' 已存在")
    except:
        print(f"📦 创建 Bucket: {MINIO_BUCKET}")
        s3_client.create_bucket(Bucket=MINIO_BUCKET)
        
        # 设置公开读取策略
        policy = {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": ["s3:GetObject"],
                    "Resource": [f"arn:aws:s3:::{MINIO_BUCKET}/*"]
                }
            ]
        }
        s3_client.put_bucket_policy(Bucket=MINIO_BUCKET, Policy=str(policy).replace("'", '"'))
        print(f"🔓 设置 Bucket 为公开读取")

def download_images():
    """下载图片"""
    print("\n📥 开始下载图片...\n")
    
    os.makedirs(TEMP_DIR, exist_ok=True)
    
    success_count = 0
    fail_count = 0
    
    for i in range(1, IMAGE_COUNT + 1):
        filename = f"bg_{i:03d}.jpg"
        filepath = os.path.join(TEMP_DIR, filename)
        
        # 如果文件已存在，跳过
        if os.path.exists(filepath):
            print(f"⏭️  跳过已存在的文件: {filename}")
            success_count += 1
            continue
        
        print(f"⬇️  下载第 {i}/{IMAGE_COUNT} 张图片: {filename}")
        
        # 使用 Picsum Photos API
        url = f"https://picsum.photos/{IMAGE_WIDTH}/{IMAGE_HEIGHT}?random={i}"
        
        try:
            response = requests.get(url, timeout=10)
            if response.status_code == 200:
                with open(filepath, 'wb') as f:
                    f.write(response.content)
                print(f"✅ 成功: {filename}")
                success_count += 1
            else:
                print(f"❌ 失败 (HTTP {response.status_code}): {filename}")
                fail_count += 1
        except Exception as e:
            print(f"❌ 错误: {filename} - {str(e)}")
            fail_count += 1
        
        # 避免请求过快
        time.sleep(1)
    
    print(f"\n{'='*40}")
    print("下载完成！")
    print(f"{'='*40}")
    print(f"✅ 成功: {success_count} 张")
    print(f"❌ 失败: {fail_count} 张\n")
    
    return success_count > 0

def upload_to_minio(s3_client):
    """上传到 MinIO"""
    print("\n📤 开始上传到 MinIO...\n")
    
    upload_success = 0
    upload_fail = 0
    
    # 获取所有图片文件
    files = sorted([f for f in os.listdir(TEMP_DIR) if f.endswith('.jpg')])
    
    for filename in files:
        filepath = os.path.join(TEMP_DIR, filename)
        print(f"⬆️  上传: {filename}")
        
        try:
            s3_client.upload_file(
                filepath,
                MINIO_BUCKET,
                filename,
                ExtraArgs={'ContentType': 'image/jpeg'}
            )
            print(f"✅ 上传成功: {filename}")
            upload_success += 1
        except Exception as e:
            print(f"❌ 上传失败: {filename} - {str(e)}")
            upload_fail += 1
    
    print(f"\n{'='*40}")
    print("上传完成！")
    print(f"{'='*40}")
    print(f"✅ 上传成功: {upload_success} 张")
    print(f"❌ 上传失败: {upload_fail} 张\n")

def list_files(s3_client):
    """列出 MinIO 中的文件"""
    print("📋 MinIO Bucket 内容:")
    try:
        response = s3_client.list_objects_v2(Bucket=MINIO_BUCKET)
        if 'Contents' in response:
            for obj in response['Contents']:
                print(f"  - {obj['Key']} ({obj['Size']} bytes)")
        else:
            print("  (空)")
    except Exception as e:
        print(f"  无法列出文件: {str(e)}")
    print()

def cleanup():
    """清理临时文件"""
    print("🧹 清理临时文件...")
    import shutil
    if os.path.exists(TEMP_DIR):
        shutil.rmtree(TEMP_DIR)
    print("✅ 临时文件已清理\n")

def main():
    print("="*40)
    print("滑块验证码背景图 - 下载并上传到 MinIO")
    print("="*40)
    print(f"MinIO 地址: {MINIO_ENDPOINT}")
    print(f"Bucket 名称: {MINIO_BUCKET}")
    print(f"图片数量: {IMAGE_COUNT}")
    print(f"图片尺寸: {IMAGE_WIDTH}x{IMAGE_HEIGHT}")
    print()
    
    # 检查依赖
    try:
        import boto3
        import requests
    except ImportError:
        print("❌ 错误：缺少必要的 Python 库")
        print("请安装: pip install boto3 requests")
        sys.exit(1)
    
    # 创建 MinIO 客户端
    print("⚙️  初始化 MinIO 客户端...")
    s3_client = setup_minio_client()
    
    # 确保 bucket 存在
    ensure_bucket(s3_client)
    
    # 下载图片
    if not download_images():
        print("❌ 没有成功下载任何图片，退出")
        cleanup()
        sys.exit(1)
    
    # 上传到 MinIO
    upload_to_minio(s3_client)
    
    # 列出文件
    list_files(s3_client)
    
    print("="*40)
    print("全部完成！")
    print("="*40)
    print()
    print("📊 统计信息：")
    print(f"  - Bucket: {MINIO_BUCKET}")
    print(f"  - 访问地址示例: {MINIO_ENDPOINT}/{MINIO_BUCKET}/bg_001.jpg")
    print()
    print("💡 提示：")
    print("1. 图片已存储在 MinIO 中")
    print("2. 需要在应用中配置从 MinIO 加载图片")
    print()
    
    # 清理
    cleanup()

if __name__ == '__main__':
    main()
