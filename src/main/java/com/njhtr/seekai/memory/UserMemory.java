package com.njhtr.seekai.memory;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户记忆实体 - 存储重要的用户信息
 */
@Data
public class UserMemory {
    private Long id;
    private String userId;
    private String memoryType; // PERSONA(人物), PREFERENCE(偏好), HABIT(习惯), FACTS(事实), IMPORTANT(重要)
    private String content;    // 记忆内容
    private String embedding;  // 向量表示 (JSON format)
    private Double importance; // 重要性评分 0-1
    private Integer accessCount; // 访问次数
    private LocalDateTime lastAccessed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserMemory() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.importance = 0.5;
        this.accessCount = 0;
    }
}