#!/bin/bash

# 测试前端公开文章API

BASE_URL="http://localhost:8080/api/posts"

echo "========================================="
echo "测试前端公开文章API"
echo "========================================="
echo ""

# 1. 测试获取软件开发分类文章列表（默认第1页，20条/页）
echo "1. 测试获取软件开发分类文章列表（默认分页）"
echo "URL: GET $BASE_URL/category/software-development"
curl -s -X GET "$BASE_URL/category/software-development" | jq '.'
echo ""
echo ""

# 1.1 测试自定义分页参数
echo "1.1 测试获取软件开发分类文章列表（第2页，10条/页）"
echo "URL: GET $BASE_URL/category/software-development?pageNum=2&pageSize=10"
curl -s -X GET "$BASE_URL/category/software-development?pageNum=2&pageSize=10" | jq '.'
echo ""
echo ""

# 2. 测试获取AI分类文章列表（默认第1页，20条/页）
echo "2. 测试获取AI分类文章列表（默认分页）"
echo "URL: GET $BASE_URL/category/ai"
curl -s -X GET "$BASE_URL/category/ai" | jq '.'
echo ""
echo ""

# 3. 测试获取网络安全分类文章列表（默认第1页，20条/页）
echo "3. 测试获取网络安全分类文章列表（默认分页）"
echo "URL: GET $BASE_URL/category/cybersecurity"
curl -s -X GET "$BASE_URL/category/cybersecurity" | jq '.'
echo ""
echo ""

# 4. 测试获取文章详情（需要替换为实际的文章ID）
echo "4. 测试获取文章详情（请先从上面的列表中获取一个文章ID）"
echo "示例: GET $BASE_URL/{postId}"
echo "请手动测试，替换{postId}为实际的文章ID"
echo ""
echo ""

# 5. 测试获取不存在的文章（应该返回403）
echo "5. 测试获取不存在的文章（应该返回403）"
echo "URL: GET $BASE_URL/999999999"
curl -s -X GET "$BASE_URL/999999999" | jq '.'
echo ""
echo ""

echo "========================================="
echo "测试完成"
echo "========================================="
