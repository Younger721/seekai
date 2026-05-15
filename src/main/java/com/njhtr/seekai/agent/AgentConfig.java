package com.njhtr.seekai.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 配置
 */
@Data
@Builder
public class AgentConfig {
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 启用的工具函数名称列表
     */
    @Builder.Default
    private List<String> functionNames = List.of();
}
