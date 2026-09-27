-- V38: 站内通知表
--
-- 背景：有两件事此前都不会告知当事人 ——
--   1. 文章审核结果。作者提交后只能靠自己反复刷新文章列表去猜过没过，
--      被拒绝时连原因（t_post_audit.audit_comment）都看不到。
--   2. 管理员重置密码。用户完全不知情，直到下次登录失败才发现。
-- 本表是这两条链路的落点。
--
-- 为什么不做邮件：项目里没有 spring-boot-starter-mail 依赖，也没有任何 SMTP 配置，
-- 引入邮件等于新增一个"部署时必须配好、配错就报错"的外部依赖。
-- 站内信自包含，先把「能通知到」这件事落地。
--
-- type 取值由应用层枚举 com.leo.myblog.enums.NotificationType 保证
-- （与项目其他枚举一致，不加 CHECK 约束）。
--
-- 主键用雪花算法（IdType.ASSIGN_ID），与 t_post / t_bug_report 一致，不需要序列。

CREATE TABLE IF NOT EXISTS t_notification (
    id          BIGINT       PRIMARY KEY,                        -- 主键（雪花算法）
    user_id     BIGINT       NOT NULL,                           -- 接收者 ID
    type        VARCHAR(32)  NOT NULL,                           -- 通知类型，见 NotificationType
    title       VARCHAR(200) NOT NULL,                           -- 标题（列表页直接显示）
    content     TEXT,                                            -- 正文，可空
    link        VARCHAR(500),                                    -- 站内跳转路径，可空
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,             -- 是否已读
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES t_user(id)
);

-- 两种查询：① 某用户的通知按时间倒序；② 某用户的未读数。
-- 复合索引把 is_read 放在第二列：单用户的行数有限，(user_id, is_read, create_time)
-- 足以让上面两种查询都只走索引，不必回表。
CREATE INDEX IF NOT EXISTS idx_notification_user_read_time
    ON t_notification(user_id, is_read, create_time DESC);
