-- ====================================
-- 数据库迁移脚本
-- 功能：为 spring_ai_chat_memory 表添加缺失字段
-- ====================================

-- 1. 添加自增主键 id（如果不存在）
ALTER TABLE spring_ai_chat_memory 
ADD COLUMN IF NOT EXISTS id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- 2. 修改 conversation_id 类型为 varchar(255)
ALTER TABLE spring_ai_chat_memory 
MODIFY COLUMN conversation_id VARCHAR(255) NOT NULL;

-- 3. 添加 agent_name 字段
ALTER TABLE spring_ai_chat_memory 
ADD COLUMN IF NOT EXISTS agent_name VARCHAR(50) COMMENT 'Agent 名称' AFTER type;

-- 4. 添加注释（可选）
ALTER TABLE spring_ai_chat_memory 
MODIFY COLUMN content TEXT NOT NULL COMMENT '消息内容',
MODIFY COLUMN type VARCHAR(10) COMMENT '消息类型：user 或 assistant',
MODIFY COLUMN timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳';

-- ====================================
-- 验证表结构
-- ====================================
DESCRIBE spring_ai_chat_memory;

-- 查看示例数据
SELECT * FROM spring_ai_chat_memory ORDER BY timestamp DESC LIMIT 10;
