-- 记忆系统数据库表
-- 创建时间: 2026-04-14

-- 用户记忆表
CREATE TABLE IF NOT EXISTS user_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    memory_type VARCHAR(50) NOT NULL COMMENT 'PERSONA|PREFERENCE|HABIT|FACTS|IMPORTANT',
    content TEXT NOT NULL,
    embedding TEXT COMMENT '向量表示(JSON)',
    importance DOUBLE DEFAULT 0.5 COMMENT '重要性 0-1',
    access_count INT DEFAULT 0,
    last_accessed DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_memory_type (memory_type),
    INDEX idx_importance (importance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户偏好表
CREATE TABLE IF NOT EXISTS user_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    preference_key VARCHAR(100) NOT NULL COMMENT 'language_style|response_length|tone|detail_level',
    preference_value VARCHAR(500),
    confidence DOUBLE DEFAULT 0.0 COMMENT '置信度 0-1',
    sample_count INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_preference (user_id, preference_key),
    INDEX idx_user_id (user_id),
    INDEX idx_confidence (confidence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户习惯表
CREATE TABLE IF NOT EXISTS user_habit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    habit_type VARCHAR(50) NOT NULL COMMENT 'frequent_agent|common_query|time_pattern|workflow',
    habit_key VARCHAR(255) NOT NULL,
    habit_value VARCHAR(500),
    frequency INT DEFAULT 1,
    last_context TEXT,
    last_used_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_user_habit (user_id, habit_type),
    INDEX idx_frequency (frequency),
    INDEX idx_last_used (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;