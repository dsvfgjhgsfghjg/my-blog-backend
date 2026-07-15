#!/bin/bash

# 文章审计数据一致性检查和自动修复脚本
# 定期检查并修复文章状态与审计状态的不一致

DB_CONTAINER="myblog-postgres"
DB_USER="Leoxiaoblog"
DB_NAME="myblog"

echo "======================================"
echo "文章审计数据一致性检查"
echo "======================================"
echo ""

# 1. 检查不一致的数据
echo "1. 检查数据不一致..."
INCONSISTENT=$(docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -A -c "
SELECT COUNT(*)
FROM t_post p
JOIN t_post_audit pa ON p.id = pa.post_id
WHERE p.is_deleted = false
  AND (
    (p.status = 3 AND pa.audit_status IN (1, 2)) OR  -- 待审核但已审计
    (p.status = 0 AND pa.audit_status = 1) OR        -- 草稿但已通过
    (p.status = 1 AND pa.audit_status = 2)           -- 已发布但被拒绝
  );
")

if [ "$INCONSISTENT" -eq 0 ]; then
    echo "✅ 数据一致，无需修复"
    exit 0
else
    echo "❌ 发现 $INCONSISTENT 条不一致的记录"
fi

echo ""
echo "2. 显示不一致的详细信息..."
docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    p.id AS post_id,
    p.title,
    CASE p.status
        WHEN 0 THEN '草稿'
        WHEN 1 THEN '已发布'
        WHEN 2 THEN '私密'
        WHEN 3 THEN '待审核'
    END AS current_status,
    pa.audit_status,
    CASE pa.audit_status
        WHEN 0 THEN '待审核'
        WHEN 1 THEN '已通过'
        WHEN 2 THEN '已拒绝'
    END AS audit_status,
    CASE 
        WHEN p.status = 3 AND pa.audit_status = 1 THEN '应改为: 已发布'
        WHEN p.status = 3 AND pa.audit_status = 2 THEN '应改为: 草稿'
        WHEN p.status = 0 AND pa.audit_status = 1 THEN '应改为: 已发布'
        WHEN p.status = 1 AND pa.audit_status = 2 THEN '应改为: 草稿'
    END AS suggested_fix
FROM t_post p
JOIN t_post_audit pa ON p.id = pa.post_id
WHERE p.is_deleted = false
  AND (
    (p.status = 3 AND pa.audit_status IN (1, 2)) OR
    (p.status = 0 AND pa.audit_status = 1) OR
    (p.status = 1 AND pa.audit_status = 2)
  )
ORDER BY p.create_time DESC;
"

echo ""
read -p "是否自动修复这些不一致？(y/n): " CONFIRM

if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "取消修复"
    exit 0
fi

echo ""
echo "3. 开始自动修复..."

docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME << 'EOF'
BEGIN;

-- 修复1: 待审核但审计已通过 → 改为已发布
UPDATE t_post 
SET status = 1, update_time = NOW()
WHERE id IN (
    SELECT pa.post_id 
    FROM t_post_audit pa
    WHERE pa.audit_status = 1
) AND status = 3;

-- 修复2: 待审核但审计已拒绝 → 改为草稿
UPDATE t_post 
SET status = 0, update_time = NOW()
WHERE id IN (
    SELECT pa.post_id 
    FROM t_post_audit pa
    WHERE pa.audit_status = 2
) AND status = 3;

-- 修复3: 草稿但审计已通过 → 改为已发布
UPDATE t_post 
SET status = 1, update_time = NOW()
WHERE id IN (
    SELECT pa.post_id 
    FROM t_post_audit pa
    WHERE pa.audit_status = 1
) AND status = 0;

-- 修复4: 已发布但审计被拒绝 → 改为草稿
UPDATE t_post 
SET status = 0, update_time = NOW()
WHERE id IN (
    SELECT pa.post_id 
    FROM t_post_audit pa
    WHERE pa.audit_status = 2
) AND status = 1;

COMMIT;
EOF

echo "✅ 修复完成"

echo ""
echo "4. 验证修复结果..."
REMAINING=$(docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -A -c "
SELECT COUNT(*)
FROM t_post p
JOIN t_post_audit pa ON p.id = pa.post_id
WHERE p.is_deleted = false
  AND (
    (p.status = 3 AND pa.audit_status IN (1, 2)) OR
    (p.status = 0 AND pa.audit_status = 1) OR
    (p.status = 1 AND pa.audit_status = 2)
  );
")

if [ "$REMAINING" -eq 0 ]; then
    echo "✅ 所有数据已修复，现在完全一致"
else
    echo "⚠️  仍有 $REMAINING 条记录未修复，请手动检查"
fi

echo ""
echo "======================================"
echo "检查完成"
echo "======================================"
