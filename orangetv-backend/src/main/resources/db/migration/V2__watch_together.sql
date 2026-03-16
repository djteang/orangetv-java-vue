-- 共同观影房间表
-- V2__watch_together.sql

CREATE TABLE watch_rooms (
    id VARCHAR(36) PRIMARY KEY,
    host_id VARCHAR(50) NOT NULL COMMENT '主持人用户名',
    guest_id VARCHAR(50) COMMENT '嘉宾用户名',
    video_title VARCHAR(255) COMMENT '视频标题',
    video_source VARCHAR(100) COMMENT '视频源',
    video_id VARCHAR(100) COMMENT '视频ID',
    episode_index INT COMMENT '集数索引',
    video_url VARCHAR(1024) COMMENT '视频URL',
    video_cover VARCHAR(512) COMMENT '视频封面',
    status VARCHAR(20) NOT NULL DEFAULT 'waiting' COMMENT '房间状态: waiting, active, ended',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_host_id (host_id),
    INDEX idx_guest_id (guest_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='共同观影房间表';
