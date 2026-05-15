package com.njhtr.seekai.memory;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户习惯 - 记录用户的操作习惯和高频行为
 */
@Data
public class UserHabit {
    private Long id;
    private String userId;
    private String habitType;       // 习惯类型: frequent_agent, common_query, time_pattern, workflow
    private String habitKey;        // 习惯键
    private String habitValue;      // 习惯值 (如: 常用的 agent 名称)
    private Integer frequency;      // 使用频率
    private String lastContext;     // 最后一次使用的上下文
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserHabit() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.frequency = 1;
    }
}