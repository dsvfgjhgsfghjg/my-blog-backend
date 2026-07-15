-- =====================
-- App 设备表：新增设备码字段
-- =====================

-- device_secret: 服务端生成的设备密钥（BCrypt 哈希存储）
-- device_secret_hint: 设备密钥提示（前4位 + 后4位，用于 UI 展示）
ALTER TABLE t_app_device 
ADD COLUMN IF NOT EXISTS device_secret VARCHAR(64) DEFAULT NULL,
ADD COLUMN IF NOT EXISTS device_secret_hint VARCHAR(20) DEFAULT NULL;

COMMENT ON COLUMN t_app_device.device_secret IS '设备密钥（BCrypt 哈希，服务端生成）';
COMMENT ON COLUMN t_app_device.device_secret_hint IS '设备密钥提示（前4位+后4位，如 a3f8...e5d6）';
