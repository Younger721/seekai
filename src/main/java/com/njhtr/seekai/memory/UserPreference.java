package com.njhtr.seekai.memory;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户偏好 - 学习用户的沟通风格和偏好
 */
@Data
public class UserPreference {
    private Long id;
    private String userId;
    private String preferenceKey;   // 偏好键: language_style, response_length, detail_level, tone 等
    private String preferenceValue; // 偏好值
    private Double confidence;      // 置信度 0-1
    private Integer sampleCount;    // 样本数量
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserPreference() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.confidence = 0.0;
        this.sampleCount = 0;
    }
}