-- 完整的建表语句（包含所有字段）
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory(
    id bigint auto_increment primary key,
    conversation_id varchar(255) not null COMMENT '会话 ID',
    content text not null COMMENT '消息内容',
    type varchar(10) COMMENT '消息类型：user 或 assistant',
    agent_name varchar(50) COMMENT 'Agent 名称（仅 assistant 类型有值）',
    timestamp timestamp default current_timestamp COMMENT '时间戳'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spring AI 对话记忆表';

-- 添加索引提升查询性能
CREATE INDEX idx_conversation_timestamp ON spring_ai_chat_memory(conversation_id, timestamp);