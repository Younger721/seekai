package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 响应封装
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {
    private boolean success;
    private String message;
    private String content;
    private String agentName;
    private List<ToolCall> toolCalls;
    private Map<String, Object> metadata;
    private TokenUsage tokenUsage;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCall {
        private String name;
        private String arguments;
        private Object result;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TokenUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
