#!/bin/bash

# 文章搜索功能测试脚本
# 使用前请确保应用已启动在 http://localhost:8080

BASE_URL="http://localhost:8080/api/posts/public"

echo "=========================================="
echo "   文章搜索功能增强 - 测试脚本"
echo "=========================================="
echo ""

# 测试1: 基础搜索
echo "📝 测试1: 基础搜索（标题+摘要）"
echo "搜索关键字: Spring"
curl -s "${BASE_URL}?keyword=Spring&pageSize=5" | jq '.data.total'
echo "篇文章找到"
echo ""

# 测试2: 全文搜索
echo "📝 测试2: 全文搜索（标题+摘要+内容）"
echo "搜索关键字: 微服务，启用全文搜索"
curl -s "${BASE_URL}?keyword=微服务&searchContent=true&pageSize=5" | jq '.data.total'
echo "篇文章找到"
echo ""

# 测试3: 按分类搜索
echo "📝 测试3: 按分类搜索"
echo "软件开发分类 (categoryId=4)"
curl -s "${BASE_URL}?categoryId=4&pageSize=3" | jq '{total: .data.total, titles: [.data.records[].title]}'
echo ""

# 测试4: AI分类搜索
echo "📝 测试4: AI分类搜索"
echo "AI分类 (categoryId=5)"
curl -s "${BASE_URL}?categoryId=5&pageSize=3" | jq '{total: .data.total, titles: [.data.records[].title]}'
echo ""

# 测试5: 网络安全分类搜索
echo "📝 测试5: 网络安全分类搜索"
echo "网络安全分类 (categoryId=6)"
curl -s "${BASE_URL}?categoryId=6&pageSize=3" | jq '{total: .data.total, titles: [.data.records[].title]}'
echo ""

# 测试6: 按标签搜索
echo "📝 测试6: 按标签搜索"
echo "人工智能标签 (tagId=23)"
curl -s "${BASE_URL}?tagId=23&pageSize=3" | jq '{total: .data.total, titles: [.data.records[].title]}'
echo ""

# 测试7: 按阅读量排序
echo "📝 测试7: 按阅读量排序（从高到低）"
curl -s "${BASE_URL}?sortBy=view_count&sortOrder=desc&pageSize=5" | jq '[.data.records[] | {title: .title, views: .viewCount}]'
echo ""

# 测试8: 按发布时间排序
echo "📝 测试8: 按发布时间排序（从新到旧）"
curl -s "${BASE_URL}?sortBy=publish_time&sortOrder=desc&pageSize=5" | jq '[.data.records[] | {title: .title, time: .publishTime}]'
echo ""

# 测试9: 组合搜索 - 分类 + 关键字
echo "📝 测试9: 组合搜索 - AI分类中包含'学习'的文章"
curl -s "${BASE_URL}?categoryId=5&keyword=学习&pageSize=5" | jq '{total: .data.total, titles: [.data.records[].title]}'
echo ""

# 测试10: 组合搜索 - 时间范围
echo "📝 测试10: 时间范围筛选（2026年5月）"
curl -s "${BASE_URL}?startTime=2026-05-01%2000:00:00&endTime=2026-05-31%2023:59:59&pageSize=5" | jq '{total: .data.total}'
echo "篇文章找到"
echo ""

# 测试11: 阅读量范围筛选
echo "📝 测试11: 阅读量范围筛选（1000-2500）"
curl -s "${BASE_URL}?minViewCount=1000&maxViewCount=2500&pageSize=5" | jq '[.data.records[] | {title: .title, views: .viewCount}]'
echo ""

# 测试12: 复杂组合搜索
echo "📝 测试12: 复杂组合搜索"
echo "软件开发分类 + 关键字Vue + 阅读量>1500 + 按阅读量排序"
curl -s "${BASE_URL}?categoryId=4&keyword=Vue&minViewCount=1500&sortBy=view_count&sortOrder=desc&pageSize=5" | jq '{total: .data.total, results: [.data.records[] | {title: .title, views: .viewCount}]}'
echo ""

# 测试13: 分页测试
echo "📝 测试13: 分页测试"
echo "第1页:"
curl -s "${BASE_URL}?pageNum=1&pageSize=3" | jq '{pageNum: .data.pageNum, pageSize: .data.pageSize, total: .data.total, pages: .data.pages}'
echo "第2页:"
curl -s "${BASE_URL}?pageNum=2&pageSize=3" | jq '{pageNum: .data.pageNum, pageSize: .data.pageSize, total: .data.total, pages: .data.pages}'
echo ""

# 测试14: 置顶文章测试
echo "📝 测试14: 验证置顶文章排在前面"
curl -s "${BASE_URL}?pageSize=10" | jq '[.data.records[] | {title: .title, isTop: .isTop}]' | head -20
echo ""

echo "=========================================="
echo "   测试完成！"
echo "=========================================="
