-- V15: 添加文章审计数据一致性保护触发器
-- 目的：在数据库层面防止文章状态与审计状态不一致

-- 创建触发器函数
CREATE OR REPLACE FUNCTION check_post_audit_consistency()
RETURNS TRIGGER AS $$
BEGIN
    -- 如果文章有待审核的审计记录，检查状态是否匹配
    IF NEW.status = 3 THEN
        -- 待审核的文章必须有对应的待审核审计记录
        IF NOT EXISTS (
            SELECT 1 FROM t_post_audit 
            WHERE post_id = NEW.id AND audit_status = 0
        ) THEN
            RAISE EXCEPTION '待审核文章必须有待审核的审计记录';
        END IF;
    ELSIF NEW.status = 1 THEN
        -- 已发布的文章必须有已通过或无审计记录（历史数据）
        IF EXISTS (
            SELECT 1 FROM t_post_audit 
            WHERE post_id = NEW.id AND audit_status = 2
        ) THEN
            RAISE EXCEPTION '不能将已拒绝的文章直接改为已发布，请先重新提交审核';
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
DROP TRIGGER IF EXISTS trg_check_post_audit_consistency ON t_post;
CREATE TRIGGER trg_check_post_audit_consistency
    BEFORE UPDATE OF status ON t_post
    FOR EACH ROW
    EXECUTE FUNCTION check_post_audit_consistency();

COMMENT ON FUNCTION check_post_audit_consistency() IS '检查文章状态与审计状态的一致性';
COMMENT ON TRIGGER trg_check_post_audit_consistency ON t_post IS '防止文章状态与审计状态不一致';

-- 验证触发器创建成功
SELECT 
    trigger_name,
    event_manipulation,
    event_object_table,
    action_timing,
    action_statement
FROM information_schema.triggers
WHERE trigger_name = 'trg_check_post_audit_consistency';
