--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';


--
-- Name: check_post_audit_consistency(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_post_audit_consistency() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION check_post_audit_consistency(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.check_post_audit_consistency() IS '检查文章状态转换的合法性（优化版）';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: oauth2_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth2_authorizations (
    id bigint NOT NULL,
    client_id character varying(64),
    user_id bigint,
    authorization_code character varying(128),
    access_token_hash character varying(128),
    refresh_token_hash character varying(128),
    grant_type character varying(32) NOT NULL,
    scope text,
    redirect_uri character varying(512),
    auth_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    access_token_expires_at timestamp without time zone,
    refresh_token_expires_at timestamp without time zone,
    is_revoked boolean DEFAULT false,
    revoked_at timestamp without time zone
);


--
-- Name: oauth2_clients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth2_clients (
    id bigint NOT NULL,
    client_id character varying(64) NOT NULL,
    client_secret character varying(256) NOT NULL,
    client_name character varying(128) NOT NULL,
    description character varying(512),
    redirect_uris text NOT NULL,
    grant_types text NOT NULL,
    scopes text NOT NULL,
    logo_url character varying(512),
    homepage_url character varying(512),
    is_confidential boolean DEFAULT true,
    is_enabled boolean DEFAULT true,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: t_ability; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_ability (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(50) NOT NULL,
    description text,
    icon character varying(255),
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: COLUMN t_ability.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_ability.name IS '能力名称（如：管理用户、编辑文章）';


--
-- Name: COLUMN t_ability.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_ability.description IS '能力描述（可选）';


--
-- Name: COLUMN t_ability.icon; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_ability.icon IS '图标（存 MinIO URL，可选）';


--
-- Name: COLUMN t_ability.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_ability.sort_order IS '排序（前端展示顺序）';


--
-- Name: t_app_device; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_app_device (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    device_id character varying(128) NOT NULL,
    device_name character varying(256),
    device_type character varying(64) DEFAULT 'ANDROID'::character varying,
    public_key text,
    cipher_pref character varying(32) DEFAULT 'AES-256-GCM'::character varying,
    last_active_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    device_secret character varying(64) DEFAULT NULL::character varying,
    device_secret_hint character varying(20) DEFAULT NULL::character varying
);


--
-- Name: COLUMN t_app_device.device_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_app_device.device_secret IS '设备密钥（BCrypt 哈希，服务端生成）';


--
-- Name: COLUMN t_app_device.device_secret_hint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_app_device.device_secret_hint IS '设备密钥提示（前4位+后4位，如 a3f8...e5d6）';


--
-- Name: t_article; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_article (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(100) NOT NULL,
    content text NOT NULL,
    summary character varying(255),
    cover_image character varying(255),
    view_count integer DEFAULT 0,
    status smallint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: COLUMN t_article.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_article.status IS '0草稿 1发布';


--
-- Name: t_audio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_audio (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    artist character varying(100),
    album character varying(100),
    file_path character varying(500) NOT NULL,
    format character varying(10) NOT NULL,
    file_size bigint NOT NULL,
    duration integer,
    cover_url character varying(500),
    user_id bigint NOT NULL,
    is_public boolean DEFAULT true,
    play_count bigint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_audio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_audio IS '音频文件表';


--
-- Name: COLUMN t_audio.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.title IS '音频标题';


--
-- Name: COLUMN t_audio.artist; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.artist IS '艺术家/歌手';


--
-- Name: COLUMN t_audio.album; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.album IS '专辑名称';


--
-- Name: COLUMN t_audio.file_path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.file_path IS '文件路径（MinIO中的路径）';


--
-- Name: COLUMN t_audio.format; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.format IS '文件格式（mp3, flac等）';


--
-- Name: COLUMN t_audio.file_size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.file_size IS '文件大小（字节）';


--
-- Name: COLUMN t_audio.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.duration IS '时长（秒）';


--
-- Name: COLUMN t_audio.cover_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.cover_url IS '封面图片URL';


--
-- Name: COLUMN t_audio.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.user_id IS '上传用户ID';


--
-- Name: COLUMN t_audio.is_public; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.is_public IS '是否公开';


--
-- Name: COLUMN t_audio.play_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audio.play_count IS '播放次数';


--
-- Name: t_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_audit_log (
    id bigint NOT NULL,
    user_id bigint,
    username character varying(100),
    operation_type character varying(50) NOT NULL,
    module character varying(50) NOT NULL,
    description text,
    ip_address character varying(100),
    user_agent text,
    request_url character varying(500),
    request_method character varying(10),
    request_params text,
    response_status integer,
    response_message character varying(500),
    execution_time bigint,
    success boolean DEFAULT true,
    error_message text,
    extra_data text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_audit_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_audit_log IS '审计日志表，记录所有敏感操作和系统事件';


--
-- Name: COLUMN t_audit_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.id IS '主键ID';


--
-- Name: COLUMN t_audit_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.user_id IS '操作用户ID';


--
-- Name: COLUMN t_audit_log.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.username IS '操作用户名';


--
-- Name: COLUMN t_audit_log.operation_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.operation_type IS '操作类型（LOGIN, LOGOUT, CREATE, UPDATE, DELETE, RESET_PASSWORD, CHANGE_ROLE等）';


--
-- Name: COLUMN t_audit_log.module; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.module IS '操作模块（USER, POST, COMMENT, CATEGORY, TAG, SYSTEM等）';


--
-- Name: COLUMN t_audit_log.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.description IS '操作描述';


--
-- Name: COLUMN t_audit_log.ip_address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.ip_address IS '请求IP地址';


--
-- Name: COLUMN t_audit_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.user_agent IS '请求User-Agent';


--
-- Name: COLUMN t_audit_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.request_url IS '请求URL';


--
-- Name: COLUMN t_audit_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.request_method IS '请求方法（GET, POST, PUT, DELETE等）';


--
-- Name: COLUMN t_audit_log.request_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.request_params IS '请求参数（JSON格式，敏感信息已脱敏）';


--
-- Name: COLUMN t_audit_log.response_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.response_status IS '响应状态码';


--
-- Name: COLUMN t_audit_log.response_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.response_message IS '响应消息';


--
-- Name: COLUMN t_audit_log.execution_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.execution_time IS '执行时长（毫秒）';


--
-- Name: COLUMN t_audit_log.success; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.success IS '是否成功';


--
-- Name: COLUMN t_audit_log.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.error_message IS '错误信息（失败时记录）';


--
-- Name: COLUMN t_audit_log.extra_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.extra_data IS '额外数据（JSON格式，存储业务相关数据）';


--
-- Name: COLUMN t_audit_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_audit_log.create_time IS '创建时间';


--
-- Name: t_auth_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_auth_history (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    device_id character varying(128),
    device_name character varying(256),
    challenge_id character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    numbers json,
    requested_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    responded_at timestamp without time zone,
    correct_number integer
);


--
-- Name: t_blogger_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_blogger_info (
    id bigint NOT NULL,
    name character varying(100) DEFAULT '博主'::character varying NOT NULL,
    avatar character varying(500),
    email character varying(200),
    github character varying(500),
    introduction text,
    tech_stack text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE t_blogger_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_blogger_info IS '博主信息表';


--
-- Name: COLUMN t_blogger_info.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.id IS '主键ID（固定为1）';


--
-- Name: COLUMN t_blogger_info.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.name IS '博主名字';


--
-- Name: COLUMN t_blogger_info.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.avatar IS '头像URL（MinIO对象路径）';


--
-- Name: COLUMN t_blogger_info.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.email IS '邮箱';


--
-- Name: COLUMN t_blogger_info.github; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.github IS 'GitHub地址';


--
-- Name: COLUMN t_blogger_info.introduction; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.introduction IS '简介';


--
-- Name: COLUMN t_blogger_info.tech_stack; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.tech_stack IS '技术栈（JSON格式）';


--
-- Name: COLUMN t_blogger_info.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.create_time IS '创建时间';


--
-- Name: COLUMN t_blogger_info.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_blogger_info.update_time IS '更新时间';


--
-- Name: t_bug_report; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_bug_report (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    app_version character varying(32),
    device_model character varying(128),
    os_version character varying(32),
    log_summary character varying(512),
    minio_path character varying(512) NOT NULL,
    log_size integer DEFAULT 0 NOT NULL,
    compressed_size integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'unread'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp without time zone,
    resolve_note text
);


--
-- Name: t_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_category (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    description character varying(255),
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_category IS '文章分类表';


--
-- Name: COLUMN t_category.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_category.name IS '分类名称';


--
-- Name: COLUMN t_category.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_category.description IS '分类描述';


--
-- Name: COLUMN t_category.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_category.sort_order IS '排序顺序';


--
-- Name: t_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_category_id_seq OWNED BY public.t_category.id;


--
-- Name: t_comment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_comment (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    user_id bigint NOT NULL,
    parent_id bigint,
    reply_to_user_id bigint,
    content text NOT NULL,
    like_count integer DEFAULT 0,
    status smallint DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_comment IS '评论表';


--
-- Name: COLUMN t_comment.post_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.post_id IS '文章ID';


--
-- Name: COLUMN t_comment.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.user_id IS '评论者ID';


--
-- Name: COLUMN t_comment.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.parent_id IS '父评论ID（NULL表示顶级评论）';


--
-- Name: COLUMN t_comment.reply_to_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.reply_to_user_id IS '被回复的用户ID';


--
-- Name: COLUMN t_comment.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.content IS '评论内容';


--
-- Name: COLUMN t_comment.like_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.like_count IS '点赞数';


--
-- Name: COLUMN t_comment.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.status IS '状态：1正常 0隐藏 -1已删除';


--
-- Name: COLUMN t_comment.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.create_time IS '创建时间';


--
-- Name: COLUMN t_comment.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment.update_time IS '更新时间';


--
-- Name: t_comment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_comment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_comment_id_seq OWNED BY public.t_comment.id;


--
-- Name: t_comment_like; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_comment_like (
    id bigint NOT NULL,
    comment_id bigint NOT NULL,
    user_id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_comment_like; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_comment_like IS '评论点赞记录表';


--
-- Name: COLUMN t_comment_like.comment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment_like.comment_id IS '评论ID';


--
-- Name: COLUMN t_comment_like.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment_like.user_id IS '点赞用户ID';


--
-- Name: COLUMN t_comment_like.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_comment_like.create_time IS '点赞时间';


--
-- Name: t_comment_like_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_comment_like_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_comment_like_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_comment_like_id_seq OWNED BY public.t_comment_like.id;


--
-- Name: t_ip_ban; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_ip_ban (
    id bigint NOT NULL,
    ip_address character varying(50) NOT NULL,
    reason character varying(500),
    ban_until timestamp without time zone NOT NULL,
    trigger_count integer DEFAULT 1,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE t_ip_ban; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_ip_ban IS 'IP临时封禁表';


--
-- Name: t_login_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_login_log (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    username character varying(50) NOT NULL,
    ip_address character varying(50) NOT NULL,
    device_type character varying(20),
    os_name character varying(50),
    os_version character varying(50),
    browser_name character varying(50),
    browser_version character varying(50),
    user_agent text,
    login_time timestamp without time zone DEFAULT now() NOT NULL,
    login_result character varying(20) DEFAULT 'SUCCESS'::character varying NOT NULL,
    failure_reason character varying(200)
);


--
-- Name: TABLE t_login_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_login_log IS '用户登录日志表';


--
-- Name: COLUMN t_login_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.id IS '主键ID（雪花算法）';


--
-- Name: COLUMN t_login_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.user_id IS '用户ID';


--
-- Name: COLUMN t_login_log.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.username IS '用户名';


--
-- Name: COLUMN t_login_log.ip_address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.ip_address IS '登录IP地址';


--
-- Name: COLUMN t_login_log.device_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.device_type IS '设备类型：DESKTOP/MOBILE/TABLET/UNKNOWN';


--
-- Name: COLUMN t_login_log.os_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.os_name IS '操作系统名称';


--
-- Name: COLUMN t_login_log.os_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.os_version IS '操作系统版本';


--
-- Name: COLUMN t_login_log.browser_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.browser_name IS '浏览器名称';


--
-- Name: COLUMN t_login_log.browser_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.browser_version IS '浏览器版本';


--
-- Name: COLUMN t_login_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.user_agent IS '原始User-Agent字符串';


--
-- Name: COLUMN t_login_log.login_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.login_time IS '登录时间';


--
-- Name: COLUMN t_login_log.login_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.login_result IS '登录结果：SUCCESS/FAILED';


--
-- Name: COLUMN t_login_log.failure_reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_login_log.failure_reason IS '失败原因';


--
-- Name: t_particle_effect; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_particle_effect (
    id bigint NOT NULL,
    effect_code character varying(50) NOT NULL,
    effect_name character varying(100) NOT NULL,
    description text,
    is_enabled boolean DEFAULT true,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_particle_effect; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_particle_effect IS '粒子特效配置表（白名单）';


--
-- Name: COLUMN t_particle_effect.effect_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_particle_effect.effect_code IS '特效代码（唯一标识，用于API传输和数据库存储）';


--
-- Name: COLUMN t_particle_effect.effect_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_particle_effect.effect_name IS '特效名称（中文显示，用于前端展示）';


--
-- Name: COLUMN t_particle_effect.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_particle_effect.description IS '特效描述（可选）';


--
-- Name: COLUMN t_particle_effect.is_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_particle_effect.is_enabled IS '是否启用（false则用户不能选择此特效）';


--
-- Name: COLUMN t_particle_effect.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_particle_effect.sort_order IS '排序（前端展示顺序，数字越小越靠前）';


--
-- Name: t_particle_effect_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_particle_effect_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_particle_effect_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_particle_effect_id_seq OWNED BY public.t_particle_effect.id;


--
-- Name: t_playlist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_playlist (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(100) NOT NULL,
    description character varying(255),
    cover character varying(255),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: t_playlist_song; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_playlist_song (
    playlist_id bigint NOT NULL,
    song_id bigint NOT NULL
);


--
-- Name: t_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_post (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    category_id bigint,
    title character varying(200) NOT NULL,
    slug character varying(200),
    summary character varying(500),
    content text,
    word_count integer DEFAULT 0,
    cover_image character varying(500),
    status smallint DEFAULT 0,
    is_top boolean DEFAULT false,
    view_count bigint DEFAULT 0,
    is_deleted boolean DEFAULT false,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    publish_time timestamp without time zone,
    content_path character varying(500)
);


--
-- Name: TABLE t_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_post IS '文章表';


--
-- Name: COLUMN t_post.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.user_id IS '作者ID';


--
-- Name: COLUMN t_post.category_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.category_id IS '分类ID';


--
-- Name: COLUMN t_post.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.title IS '文章标题';


--
-- Name: COLUMN t_post.slug; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.slug IS 'SEO友好的URL标识';


--
-- Name: COLUMN t_post.summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.summary IS '文章摘要（前150字符）';


--
-- Name: COLUMN t_post.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.content IS 'Markdown正文';


--
-- Name: COLUMN t_post.word_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.word_count IS '字数统计';


--
-- Name: COLUMN t_post.cover_image; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.cover_image IS '封面图片URL';


--
-- Name: COLUMN t_post.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.status IS '状态：0-草稿, 1-已发布, 2-私密, 3-待审核';


--
-- Name: COLUMN t_post.is_top; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.is_top IS '是否置顶';


--
-- Name: COLUMN t_post.view_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.view_count IS '阅读量';


--
-- Name: COLUMN t_post.is_deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.is_deleted IS '软删除标记';


--
-- Name: COLUMN t_post.publish_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post.publish_time IS '发布时间';


--
-- Name: t_post_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_post_audit (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    auditor_id bigint,
    audit_status smallint DEFAULT 0,
    audit_comment text,
    audit_time timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_post_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_post_audit IS '文章审计记录表';


--
-- Name: COLUMN t_post_audit.post_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_audit.post_id IS '文章ID';


--
-- Name: COLUMN t_post_audit.auditor_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_audit.auditor_id IS '审计员ID（NULL表示未审计）';


--
-- Name: COLUMN t_post_audit.audit_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_audit.audit_status IS '审计状态：0-待审核, 1-已通过, 2-已拒绝';


--
-- Name: COLUMN t_post_audit.audit_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_audit.audit_comment IS '审计意见/拒绝原因';


--
-- Name: COLUMN t_post_audit.audit_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_audit.audit_time IS '审计时间';


--
-- Name: t_post_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_post_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_post_favorite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_post_favorite (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    post_id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_post_favorite; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_post_favorite IS '文章收藏表';


--
-- Name: COLUMN t_post_favorite.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_favorite.id IS '主键ID';


--
-- Name: COLUMN t_post_favorite.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_favorite.user_id IS '用户ID';


--
-- Name: COLUMN t_post_favorite.post_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_favorite.post_id IS '文章ID';


--
-- Name: COLUMN t_post_favorite.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_post_favorite.create_time IS '收藏时间';


--
-- Name: t_post_favorite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_post_favorite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_post_favorite_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_post_favorite_id_seq OWNED BY public.t_post_favorite.id;


--
-- Name: t_post_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_post_tag (
    post_id bigint NOT NULL,
    tag_id bigint NOT NULL
);


--
-- Name: TABLE t_post_tag; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_post_tag IS '文章-标签关联表';


--
-- Name: t_security_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_security_event (
    id bigint NOT NULL,
    event_type character varying(50) NOT NULL,
    ip_address character varying(50) NOT NULL,
    user_agent text,
    request_url character varying(500),
    request_method character varying(10),
    matched_pattern character varying(200),
    threat_level character varying(20),
    action_taken character varying(20),
    trigger_count integer DEFAULT 1,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE t_security_event; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_security_event IS '安全事件日志表';


--
-- Name: t_song; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_song (
    id bigint NOT NULL,
    title character varying(100) NOT NULL,
    artist character varying(100),
    url character varying(255) NOT NULL,
    cover character varying(255),
    duration integer NOT NULL,
    play_count bigint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: COLUMN t_song.play_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_song.play_count IS '播放量';


--
-- Name: t_system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_system_config (
    id bigint NOT NULL,
    config_key character varying(100) NOT NULL,
    config_value text,
    config_type character varying(50) DEFAULT 'STRING'::character varying,
    description text,
    category character varying(50),
    is_public boolean DEFAULT false,
    is_editable boolean DEFAULT true,
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: t_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_tag (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    color character varying(20),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE t_tag; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_tag IS '文章标签表';


--
-- Name: COLUMN t_tag.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_tag.name IS '标签名称';


--
-- Name: COLUMN t_tag.color; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_tag.color IS '标签颜色（可选，用于前端展示）';


--
-- Name: t_tag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.t_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: t_tag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.t_tag_id_seq OWNED BY public.t_tag.id;


--
-- Name: t_ua_blacklist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_ua_blacklist (
    id bigint NOT NULL,
    pattern character varying(200) NOT NULL,
    description character varying(500),
    threat_level character varying(20) DEFAULT 'MEDIUM'::character varying,
    action_type character varying(20) DEFAULT 'BLOCK'::character varying,
    is_enabled boolean DEFAULT true,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE t_ua_blacklist; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_ua_blacklist IS 'User-Agent黑名单表';


--
-- Name: t_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(100) NOT NULL,
    nickname character varying(50),
    avatar character varying(255),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_admin boolean DEFAULT false,
    enabled boolean DEFAULT true,
    last_login_ip character varying(45),
    last_login_time timestamp without time zone,
    login_count bigint DEFAULT 0,
    phone character varying(20),
    email character varying(100),
    is_super_admin boolean DEFAULT false
);


--
-- Name: COLUMN t_user.enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.enabled IS '账户是否可用（true可用，false禁用）';


--
-- Name: COLUMN t_user.last_login_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.last_login_ip IS '上次登录IP地址';


--
-- Name: COLUMN t_user.last_login_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.last_login_time IS '上次登录时间';


--
-- Name: COLUMN t_user.login_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.login_count IS '登录次数';


--
-- Name: COLUMN t_user.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.phone IS '手机号码';


--
-- Name: COLUMN t_user.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.email IS '电子邮箱';


--
-- Name: COLUMN t_user.is_super_admin; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.t_user.is_super_admin IS '是否为超级管理员（可管理能力分配）';


--
-- Name: third_party_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.third_party_accounts (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    provider character varying(32) NOT NULL,
    provider_user_id character varying(128) NOT NULL,
    provider_username character varying(128),
    provider_avatar character varying(512),
    provider_email character varying(256),
    access_token text,
    refresh_token text,
    token_expires_at timestamp without time zone,
    linked_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: user_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_preferences (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    particle_mode character varying(20) DEFAULT 'stardust'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE user_preferences; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_preferences IS '用户偏好设置表';


--
-- Name: COLUMN user_preferences.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_preferences.id IS '主键ID';


--
-- Name: COLUMN user_preferences.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_preferences.user_id IS '用户ID';


--
-- Name: COLUMN user_preferences.particle_mode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_preferences.particle_mode IS '粒子特效模式（引用 t_particle_effect.effect_code）';


--
-- Name: COLUMN user_preferences.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_preferences.created_at IS '创建时间';


--
-- Name: COLUMN user_preferences.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_preferences.updated_at IS '更新时间';


--
-- Name: user_preferences_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_preferences_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_preferences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_preferences_id_seq OWNED BY public.user_preferences.id;


--
-- Name: t_category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_category ALTER COLUMN id SET DEFAULT nextval('public.t_category_id_seq'::regclass);


--
-- Name: t_comment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment ALTER COLUMN id SET DEFAULT nextval('public.t_comment_id_seq'::regclass);


--
-- Name: t_comment_like id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment_like ALTER COLUMN id SET DEFAULT nextval('public.t_comment_like_id_seq'::regclass);


--
-- Name: t_particle_effect id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_particle_effect ALTER COLUMN id SET DEFAULT nextval('public.t_particle_effect_id_seq'::regclass);


--
-- Name: t_post_favorite id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_favorite ALTER COLUMN id SET DEFAULT nextval('public.t_post_favorite_id_seq'::regclass);


--
-- Name: t_tag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tag ALTER COLUMN id SET DEFAULT nextval('public.t_tag_id_seq'::regclass);


--
-- Name: user_preferences id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences ALTER COLUMN id SET DEFAULT nextval('public.user_preferences_id_seq'::regclass);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: oauth2_authorizations oauth2_authorizations_access_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_authorizations
    ADD CONSTRAINT oauth2_authorizations_access_token_hash_key UNIQUE (access_token_hash);


--
-- Name: oauth2_authorizations oauth2_authorizations_authorization_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_authorizations
    ADD CONSTRAINT oauth2_authorizations_authorization_code_key UNIQUE (authorization_code);


--
-- Name: oauth2_authorizations oauth2_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_authorizations
    ADD CONSTRAINT oauth2_authorizations_pkey PRIMARY KEY (id);


--
-- Name: oauth2_authorizations oauth2_authorizations_refresh_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_authorizations
    ADD CONSTRAINT oauth2_authorizations_refresh_token_hash_key UNIQUE (refresh_token_hash);


--
-- Name: oauth2_clients oauth2_clients_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients
    ADD CONSTRAINT oauth2_clients_client_id_key UNIQUE (client_id);


--
-- Name: oauth2_clients oauth2_clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients
    ADD CONSTRAINT oauth2_clients_pkey PRIMARY KEY (id);


--
-- Name: t_ability t_ability_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ability
    ADD CONSTRAINT t_ability_pkey PRIMARY KEY (id);


--
-- Name: t_app_device t_app_device_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_app_device
    ADD CONSTRAINT t_app_device_pkey PRIMARY KEY (id);


--
-- Name: t_article t_article_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_article
    ADD CONSTRAINT t_article_pkey PRIMARY KEY (id);


--
-- Name: t_audio t_audio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_audio
    ADD CONSTRAINT t_audio_pkey PRIMARY KEY (id);


--
-- Name: t_audit_log t_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_audit_log
    ADD CONSTRAINT t_audit_log_pkey PRIMARY KEY (id);


--
-- Name: t_auth_history t_auth_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_auth_history
    ADD CONSTRAINT t_auth_history_pkey PRIMARY KEY (id);


--
-- Name: t_blogger_info t_blogger_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_blogger_info
    ADD CONSTRAINT t_blogger_info_pkey PRIMARY KEY (id);


--
-- Name: t_bug_report t_bug_report_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_bug_report
    ADD CONSTRAINT t_bug_report_pkey PRIMARY KEY (id);


--
-- Name: t_category t_category_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_category
    ADD CONSTRAINT t_category_name_key UNIQUE (name);


--
-- Name: t_category t_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_category
    ADD CONSTRAINT t_category_pkey PRIMARY KEY (id);


--
-- Name: t_comment_like t_comment_like_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment_like
    ADD CONSTRAINT t_comment_like_pkey PRIMARY KEY (id);


--
-- Name: t_comment t_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment
    ADD CONSTRAINT t_comment_pkey PRIMARY KEY (id);


--
-- Name: t_ip_ban t_ip_ban_ip_address_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ip_ban
    ADD CONSTRAINT t_ip_ban_ip_address_key UNIQUE (ip_address);


--
-- Name: t_ip_ban t_ip_ban_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ip_ban
    ADD CONSTRAINT t_ip_ban_pkey PRIMARY KEY (id);


--
-- Name: t_login_log t_login_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_login_log
    ADD CONSTRAINT t_login_log_pkey PRIMARY KEY (id);


--
-- Name: t_particle_effect t_particle_effect_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_particle_effect
    ADD CONSTRAINT t_particle_effect_pkey PRIMARY KEY (id);


--
-- Name: t_playlist t_playlist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_playlist
    ADD CONSTRAINT t_playlist_pkey PRIMARY KEY (id);


--
-- Name: t_playlist_song t_playlist_song_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_playlist_song
    ADD CONSTRAINT t_playlist_song_pkey PRIMARY KEY (playlist_id, song_id);


--
-- Name: t_post_audit t_post_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_audit
    ADD CONSTRAINT t_post_audit_pkey PRIMARY KEY (id);


--
-- Name: t_post_favorite t_post_favorite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_favorite
    ADD CONSTRAINT t_post_favorite_pkey PRIMARY KEY (id);


--
-- Name: t_post t_post_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post
    ADD CONSTRAINT t_post_pkey PRIMARY KEY (id);


--
-- Name: t_post_tag t_post_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_tag
    ADD CONSTRAINT t_post_tag_pkey PRIMARY KEY (post_id, tag_id);


--
-- Name: t_security_event t_security_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_security_event
    ADD CONSTRAINT t_security_event_pkey PRIMARY KEY (id);


--
-- Name: t_song t_song_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_song
    ADD CONSTRAINT t_song_pkey PRIMARY KEY (id);


--
-- Name: t_system_config t_system_config_config_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_system_config
    ADD CONSTRAINT t_system_config_config_key_key UNIQUE (config_key);


--
-- Name: t_system_config t_system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_system_config
    ADD CONSTRAINT t_system_config_pkey PRIMARY KEY (id);


--
-- Name: t_tag t_tag_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tag
    ADD CONSTRAINT t_tag_name_key UNIQUE (name);


--
-- Name: t_tag t_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_tag
    ADD CONSTRAINT t_tag_pkey PRIMARY KEY (id);


--
-- Name: t_ua_blacklist t_ua_blacklist_pattern_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ua_blacklist
    ADD CONSTRAINT t_ua_blacklist_pattern_key UNIQUE (pattern);


--
-- Name: t_ua_blacklist t_ua_blacklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ua_blacklist
    ADD CONSTRAINT t_ua_blacklist_pkey PRIMARY KEY (id);


--
-- Name: t_user t_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user
    ADD CONSTRAINT t_user_pkey PRIMARY KEY (id);


--
-- Name: t_user t_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user
    ADD CONSTRAINT t_user_username_key UNIQUE (username);


--
-- Name: third_party_accounts third_party_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.third_party_accounts
    ADD CONSTRAINT third_party_accounts_pkey PRIMARY KEY (id);


--
-- Name: third_party_accounts third_party_accounts_provider_provider_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.third_party_accounts
    ADD CONSTRAINT third_party_accounts_provider_provider_user_id_key UNIQUE (provider, provider_user_id);


--
-- Name: third_party_accounts third_party_accounts_user_id_provider_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.third_party_accounts
    ADD CONSTRAINT third_party_accounts_user_id_provider_key UNIQUE (user_id, provider);


--
-- Name: t_app_device uk_app_device_device_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_app_device
    ADD CONSTRAINT uk_app_device_device_id UNIQUE (device_id);


--
-- Name: t_auth_history uk_auth_history_challenge_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_auth_history
    ADD CONSTRAINT uk_auth_history_challenge_id UNIQUE (challenge_id);


--
-- Name: t_comment_like uk_comment_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment_like
    ADD CONSTRAINT uk_comment_user UNIQUE (comment_id, user_id);


--
-- Name: t_particle_effect uk_effect_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_particle_effect
    ADD CONSTRAINT uk_effect_code UNIQUE (effect_code);


--
-- Name: user_preferences uk_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences
    ADD CONSTRAINT uk_user_id UNIQUE (user_id);


--
-- Name: t_post_favorite uk_user_post; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_favorite
    ADD CONSTRAINT uk_user_post UNIQUE (user_id, post_id);


--
-- Name: user_preferences user_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences
    ADD CONSTRAINT user_preferences_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_app_device_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_device_user_id ON public.t_app_device USING btree (user_id);


--
-- Name: idx_article_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_article_create_time ON public.t_article USING btree (create_time);


--
-- Name: idx_audio_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audio_create_time ON public.t_audio USING btree (create_time DESC);


--
-- Name: idx_audio_is_public; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audio_is_public ON public.t_audio USING btree (is_public);


--
-- Name: idx_audio_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audio_user_id ON public.t_audio USING btree (user_id);


--
-- Name: idx_audit_log_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_create_time ON public.t_audit_log USING btree (create_time);


--
-- Name: idx_audit_log_module; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_module ON public.t_audit_log USING btree (module);


--
-- Name: idx_audit_log_module_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_module_time ON public.t_audit_log USING btree (module, create_time);


--
-- Name: idx_audit_log_operation_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_operation_type ON public.t_audit_log USING btree (operation_type);


--
-- Name: idx_audit_log_success; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_success ON public.t_audit_log USING btree (success);


--
-- Name: idx_audit_log_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_user_id ON public.t_audit_log USING btree (user_id);


--
-- Name: idx_audit_log_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_username ON public.t_audit_log USING btree (username);


--
-- Name: idx_auth_history_requested_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_history_requested_at ON public.t_auth_history USING btree (requested_at DESC);


--
-- Name: idx_auth_history_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_history_user_id ON public.t_auth_history USING btree (user_id);


--
-- Name: idx_auth_history_user_id_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_history_user_id_status ON public.t_auth_history USING btree (user_id, status);


--
-- Name: idx_bug_report_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bug_report_created_at ON public.t_bug_report USING btree (created_at DESC);


--
-- Name: idx_bug_report_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bug_report_status ON public.t_bug_report USING btree (status);


--
-- Name: idx_bug_report_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bug_report_user_id ON public.t_bug_report USING btree (user_id);


--
-- Name: idx_category_sort_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_category_sort_order ON public.t_category USING btree (sort_order);


--
-- Name: idx_comment_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_create_time ON public.t_comment USING btree (create_time DESC);


--
-- Name: idx_comment_like_comment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_like_comment_id ON public.t_comment_like USING btree (comment_id);


--
-- Name: idx_comment_like_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_like_user_id ON public.t_comment_like USING btree (user_id);


--
-- Name: idx_comment_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_parent_id ON public.t_comment USING btree (parent_id);


--
-- Name: idx_comment_post_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_post_id ON public.t_comment USING btree (post_id);


--
-- Name: idx_comment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_status ON public.t_comment USING btree (status);


--
-- Name: idx_comment_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_user_id ON public.t_comment USING btree (user_id);


--
-- Name: idx_effect_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_effect_enabled ON public.t_particle_effect USING btree (is_enabled);


--
-- Name: idx_effect_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_effect_sort ON public.t_particle_effect USING btree (sort_order);


--
-- Name: idx_favorite_post_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_favorite_post_id ON public.t_post_favorite USING btree (post_id);


--
-- Name: idx_favorite_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_favorite_user_id ON public.t_post_favorite USING btree (user_id);


--
-- Name: idx_ip_ban_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ip_ban_expiry ON public.t_ip_ban USING btree (ban_until);


--
-- Name: idx_login_log_ip_address; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_log_ip_address ON public.t_login_log USING btree (ip_address);


--
-- Name: idx_login_log_login_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_log_login_time ON public.t_login_log USING btree (login_time DESC);


--
-- Name: idx_login_log_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_log_user_id ON public.t_login_log USING btree (user_id);


--
-- Name: idx_login_log_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_log_username ON public.t_login_log USING btree (username);


--
-- Name: idx_oauth2_auth_access_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_auth_access_hash ON public.oauth2_authorizations USING btree (access_token_hash);


--
-- Name: idx_oauth2_auth_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_auth_client_id ON public.oauth2_authorizations USING btree (client_id);


--
-- Name: idx_oauth2_auth_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_auth_code ON public.oauth2_authorizations USING btree (authorization_code);


--
-- Name: idx_oauth2_auth_refresh_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_auth_refresh_hash ON public.oauth2_authorizations USING btree (refresh_token_hash);


--
-- Name: idx_oauth2_auth_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_auth_user_id ON public.oauth2_authorizations USING btree (user_id);


--
-- Name: idx_oauth2_clients_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_clients_client_id ON public.oauth2_clients USING btree (client_id);


--
-- Name: idx_particle_mode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_particle_mode ON public.user_preferences USING btree (particle_mode);


--
-- Name: idx_post_audit_auditor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_audit_auditor_id ON public.t_post_audit USING btree (auditor_id);


--
-- Name: idx_post_audit_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_audit_create_time ON public.t_post_audit USING btree (create_time DESC);


--
-- Name: idx_post_audit_post_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_audit_post_id ON public.t_post_audit USING btree (post_id);


--
-- Name: idx_post_audit_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_audit_status ON public.t_post_audit USING btree (audit_status);


--
-- Name: idx_post_category_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_category_id ON public.t_post USING btree (category_id);


--
-- Name: idx_post_create_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_create_time ON public.t_post USING btree (create_time DESC);


--
-- Name: idx_post_is_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_is_deleted ON public.t_post USING btree (is_deleted);


--
-- Name: idx_post_is_top; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_is_top ON public.t_post USING btree (is_top);


--
-- Name: idx_post_publish_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_publish_time ON public.t_post USING btree (publish_time DESC);


--
-- Name: idx_post_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_slug ON public.t_post USING btree (slug);


--
-- Name: idx_post_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_status ON public.t_post USING btree (status);


--
-- Name: idx_post_tag_tag_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_tag_tag_id ON public.t_post_tag USING btree (tag_id);


--
-- Name: idx_post_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_user_id ON public.t_post USING btree (user_id);


--
-- Name: idx_security_event_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_event_ip ON public.t_security_event USING btree (ip_address);


--
-- Name: idx_security_event_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_event_time ON public.t_security_event USING btree (create_time DESC);


--
-- Name: idx_security_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_event_type ON public.t_security_event USING btree (event_type);


--
-- Name: idx_system_config_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_config_category ON public.t_system_config USING btree (category);


--
-- Name: idx_system_config_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_config_key ON public.t_system_config USING btree (config_key);


--
-- Name: idx_tag_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tag_name ON public.t_tag USING btree (name);


--
-- Name: idx_third_party_provider; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_third_party_provider ON public.third_party_accounts USING btree (provider, provider_user_id);


--
-- Name: idx_third_party_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_third_party_user_id ON public.third_party_accounts USING btree (user_id);


--
-- Name: idx_user_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_phone ON public.t_user USING btree (phone) WHERE (phone IS NOT NULL);


--
-- Name: t_post_slug_unique; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX t_post_slug_unique ON public.t_post USING btree (slug) WHERE (is_deleted = false);


--
-- Name: t_post trg_check_post_audit_consistency; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_post_audit_consistency BEFORE UPDATE OF status ON public.t_post FOR EACH ROW EXECUTE FUNCTION public.check_post_audit_consistency();


--
-- Name: TRIGGER trg_check_post_audit_consistency ON t_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TRIGGER trg_check_post_audit_consistency ON public.t_post IS '防止非法的文章状态转换';


--
-- Name: t_ability fk_ability_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_ability
    ADD CONSTRAINT fk_ability_user FOREIGN KEY (user_id) REFERENCES public.t_user(id) ON DELETE CASCADE;


--
-- Name: t_app_device fk_app_device_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_app_device
    ADD CONSTRAINT fk_app_device_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_article fk_article_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_article
    ADD CONSTRAINT fk_article_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_audio fk_audio_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_audio
    ADD CONSTRAINT fk_audio_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_auth_history fk_auth_history_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_auth_history
    ADD CONSTRAINT fk_auth_history_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_bug_report fk_bug_report_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_bug_report
    ADD CONSTRAINT fk_bug_report_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_comment_like fk_comment_like_comment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment_like
    ADD CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES public.t_comment(id) ON DELETE CASCADE;


--
-- Name: t_comment_like fk_comment_like_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment_like
    ADD CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES public.t_user(id) ON DELETE CASCADE;


--
-- Name: t_comment fk_comment_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment
    ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES public.t_comment(id) ON DELETE CASCADE;


--
-- Name: t_comment fk_comment_post; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment
    ADD CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES public.t_post(id) ON DELETE CASCADE;


--
-- Name: t_comment fk_comment_reply_to_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment
    ADD CONSTRAINT fk_comment_reply_to_user FOREIGN KEY (reply_to_user_id) REFERENCES public.t_user(id);


--
-- Name: t_comment fk_comment_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_comment
    ADD CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_post_favorite fk_favorite_post; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_favorite
    ADD CONSTRAINT fk_favorite_post FOREIGN KEY (post_id) REFERENCES public.t_post(id) ON DELETE CASCADE;


--
-- Name: t_post_favorite fk_favorite_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_favorite
    ADD CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES public.t_user(id) ON DELETE CASCADE;


--
-- Name: t_login_log fk_login_log_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_login_log
    ADD CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES public.t_user(id) ON DELETE CASCADE;


--
-- Name: t_playlist fk_playlist_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_playlist
    ADD CONSTRAINT fk_playlist_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- Name: t_post_audit fk_post_audit_auditor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_audit
    ADD CONSTRAINT fk_post_audit_auditor FOREIGN KEY (auditor_id) REFERENCES public.t_user(id);


--
-- Name: t_post_audit fk_post_audit_post; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_audit
    ADD CONSTRAINT fk_post_audit_post FOREIGN KEY (post_id) REFERENCES public.t_post(id);


--
-- Name: t_post fk_post_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post
    ADD CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES public.t_category(id);


--
-- Name: t_post_tag fk_post_tag_post; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_tag
    ADD CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES public.t_post(id) ON DELETE CASCADE;


--
-- Name: t_post_tag fk_post_tag_tag; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post_tag
    ADD CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES public.t_tag(id) ON DELETE CASCADE;


--
-- Name: t_post fk_post_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_post
    ADD CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES public.t_user(id);


--
-- PostgreSQL database dump complete
--


