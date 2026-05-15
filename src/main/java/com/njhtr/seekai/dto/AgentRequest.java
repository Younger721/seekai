package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 请求封装
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRequest {
    private String message;
    private String conversationId;
    private String userId;
    
    // 新增：携带历史对话上下文
    private List<MessageDTO> historyMessages;
}
