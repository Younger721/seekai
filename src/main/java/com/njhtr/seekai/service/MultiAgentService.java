package com.njhtr.seekai.service;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 多 Agent 服务
 */
@Slf4j
@Service
public class MultiAgentService {
    
    private final AgentRegistry agentRegistry;
    
    public MultiAgentService(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }
    
    /**
     * 使用指定 Agent 处理请求
     */
    public AgentResponse processWithAgent(String agentName, AgentRequest request) {
        log.info("🎯 路由到 Agent: {}", agentName);
        
        Agent agent = agentRegistry.getAgent(agentName);
        if (agent == null) {
            log.error("❌ Agent 不存在：{}", agentName);
            throw new IllegalArgumentException("Agent 不存在：" + agentName);
        }
        
        return agent.chat(request);
    }
    
    /**
     * 使用指定 Agent 流式处理请求
     */
    public Flux<String> streamWithAgent(String agentName, AgentRequest request) {
        log.info("🌊 流式路由到 Agent: {}", agentName);
        
        Agent agent = agentRegistry.getAgent(agentName);
        if (agent == null) {
            log.error("❌ Agent 不存在：{}", agentName);
            throw new IllegalArgumentException("Agent 不存在：" + agentName);
        }
        
        return agent.stream(request);
    }
    
    /**
     * 获取所有可用的 Agent 列表
     */
    public java.util.List<AgentInfo> getAvailableAgents() {
        return agentRegistry.getAllAgents().stream()
            .map(agent -> new AgentInfo(agent.getName(), agent.getDescription()))
            .toList();
    }
    
    /**
     * Agent 信息
     */
    public record AgentInfo(String name, String description) {}
}
