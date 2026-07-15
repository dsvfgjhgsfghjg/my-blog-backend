-- V16: 优化文章审计数据一致性触发器
-- 目的：只拦截非法的状态转换，允许正常的更新操作

-- 删除旧的触发器和函数
DROP TRIGGER IF EXISTS trg_check_post_audit_consistency ON t_post;
DROP FUNCTION IF EXISTS check_post_audit_consistency();

-- 创建优化后的触发器函数
CREATE OR REPLACE FUNCTION check_post_audit_consistency()
RETURNS TRIGGER AS $$
DECLARE
    old_audit_status INTEGER;
BEGIN
    -- 获取旧的审计状态
    SELECT audit_status INTO old_audit_status
    FROM t_post_audit
    WHERE post_id = NEW.id;
    
    -- 规则1: 如果要改为待审核（status=3）
    IF NEW.status = 3 AND OLD.status != 3 THEN
        -- 必须有待审核的审计记录（audit_status=0）
        IF old_audit_status IS NULL OR old_audit_status != 0 THEN
            RAISE EXCEPTION '文章必须先提交审核才能变为待审核状态。请使用重新提交审核接口。';
        END IF;
    END IF;
    
    -- 规则2: 如果要改为已发布（status=1）
    IF NEW.status = 1 AND OLD.status != 1 THEN
        -- 不能有已拒绝的审计记录
        IF old_audit_status = 2 THEN
            RAISE EXCEPTION '已拒绝的文章不能直接发布，请先重新提交审核并通过。';
        END IF;
    END IF;
    
    -- 规则3: 如果从待审核改为其他状态（非管理员操作）
    IF OLD.status = 3 AND NEW.status != 3 THEN
        -- 这种情况应该由审计接口处理，不允许直接修改
        -- 但为了灵活性，这里不强制拦截，由应用层控制
        RAISE NOTICE '文章从待审核状态变更: % -> %', OLD.status, NEW.status;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trg_check_post_audit_consistency
    BEFORE UPDATE OF status ON t_post
    FOR EACH ROW
    EXECUTE FUNCTION check_post_audit_consistency();

COMMENT ON FUNCTION check_post_audit_consistency() IS '检查文章状态转换的合法性（优化版）';
COMMENT ON TRIGGER trg_check_post_audit_consistency ON t_post IS '防止非法的文章状态转换';

-- 验证触发器创建成功
SELECT 
    trigger_name,
    event_manipulation,
    event_object_table,
    action_timing
FROM information_schema.triggers
WHERE trigger_name = 'trg_check_post_audit_consistency';
