-- 会话管理功能数据库初始化脚本
-- 执行此脚本更新数据库表结构

-- 如果表已存在，先删除（生产环境请谨慎使用）
DROP TABLE IF EXISTS spring_ai_chat_memory;

-- 创建会话记忆表
CREATE TABLE spring_ai_chat_memory(
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    conversation_id VARCHAR(36) NOT NULL COMMENT '会话 ID',
    content TEXT COMMENT '消息内容',
    type VARCHAR(10) COMMENT '消息类型：user/assistant/system',
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='AI 聊天会话记忆表';

-- 添加索引提升查询性能
CREATE INDEX idx_conversation_timestamp ON spring_ai_chat_memory(conversation_id, timestamp);
CREATE INDEX idx_conversation_id ON spring_ai_chat_memory(conversation_id);
