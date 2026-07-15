-- 音频文件表
CREATE TABLE IF NOT EXISTS t_audio (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(100),
    album VARCHAR(100),
    file_path VARCHAR(500) NOT NULL,
    format VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    duration INT,
    cover_url VARCHAR(500),
    user_id BIGINT NOT NULL,
    is_public BOOLEAN DEFAULT TRUE,
    play_count BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audio_user FOREIGN KEY (user_id) REFERENCES t_user(id)
);

-- 添加注释
COMMENT ON TABLE t_audio IS '音频文件表';
COMMENT ON COLUMN t_audio.title IS '音频标题';
COMMENT ON COLUMN t_audio.artist IS '艺术家/歌手';
COMMENT ON COLUMN t_audio.album IS '专辑名称';
COMMENT ON COLUMN t_audio.file_path IS '文件路径（MinIO中的路径）';
COMMENT ON COLUMN t_audio.format IS '文件格式（mp3, flac等）';
COMMENT ON COLUMN t_audio.file_size IS '文件大小（字节）';
COMMENT ON COLUMN t_audio.duration IS '时长（秒）';
COMMENT ON COLUMN t_audio.cover_url IS '封面图片URL';
COMMENT ON COLUMN t_audio.user_id IS '上传用户ID';
COMMENT ON COLUMN t_audio.is_public IS '是否公开';
COMMENT ON COLUMN t_audio.play_count IS '播放次数';

-- 创建索引
CREATE INDEX idx_audio_user_id ON t_audio(user_id);
CREATE INDEX idx_audio_is_public ON t_audio(is_public);
CREATE INDEX idx_audio_create_time ON t_audio(create_time DESC);
