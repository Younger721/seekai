package com.njhtr.seekai.context;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 上下文项 - 带重要性评分的消息单元
 */
@Data
public class ContextItem {
    private String id;
    private String role;        // user/assistant/system
    private String content;
    private double importance;  // 重要性 0-1
    private LocalDateTime timestamp;
    private String agentName;   // 哪个 Agent 处理
    private boolean isSummarized; // 是否已被摘要
    private String summary;     // 摘要内容（如果有）

    public ContextItem(String role, String content) {
        this.id = java.util.UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.importance = 0.5;
        this.timestamp = LocalDateTime.now();
        this.isSummarized = false;
    }

    /**
     * 根据角色计算基础重要性
     */
    public double getBaseImportance() {
        return switch (role) {
            case "system" -> 1.0;      // 系统提示最重要
            case "user" -> 0.8;        // 用户意图重要
            case "assistant" -> 0.6;   // AI 回复相对次要
            default -> 0.5;
        };
    }
}