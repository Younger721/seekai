package com.njhtr.seekai.agent;

import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import reactor.core.publisher.Flux;

/**
 * Agent 统一接口
 */
public interface Agent {
    
    /**
     * Agent 名称（唯一标识）
     */
    String getName();
    
    /**
     * Agent 描述（用于路由选择）
     */
    String getDescription();
    
    /**
     * 处理请求（非流式）
     */
    AgentResponse chat(AgentRequest request);
    
    /**
     * 处理请求（流式）
     */
    Flux<String> stream(AgentRequest request);
}
