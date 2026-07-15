-- V31: 清理 flyway_schema_history 中的重复记录
-- 问题：之前的部署导致 V21 和 V22 在 flyway_schema_history 中出现了重复条目
-- 此迁移删除重复条目，只保留每个版本的最新记录

-- 删除 V21 的重复条目（保留 installed_rank 最大的那条）
DELETE FROM flyway_schema_history
WHERE version = '21'
AND installed_rank < (
    SELECT MAX(installed_rank) FROM flyway_schema_history WHERE version = '21'
);

-- 删除 V22 的重复条目（保留 installed_rank 最大的那条）
DELETE FROM flyway_schema_history
WHERE version = '22'
AND installed_rank < (
    SELECT MAX(installed_rank) FROM flyway_schema_history WHERE version = '22'
);
