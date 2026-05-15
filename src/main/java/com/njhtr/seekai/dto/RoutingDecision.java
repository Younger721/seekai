package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路由决策结果（结构化输出）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoutingDecision {
    
    /**
     * 选中的 Agent 名称
     */
    private String selectedAgent;
    
    /**
     * 置信度 (0.0-1.0)
     */
    private Double confidence;
    
    /**
     * 选择理由
     */
    private String reason;
}
