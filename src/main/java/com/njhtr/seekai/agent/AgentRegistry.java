package com.njhtr.seekai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心
 */
@Slf4j
@Component
public class AgentRegistry {
    
    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();
    
    /**
     * 注册 Agent
     */
    public void register(Agent agent) {
        agentMap.put(agent.getName(), agent);
        log.info("✅ Agent 已注册：{} - {}", agent.getName(), agent.getDescription());
    }
    
    /**
     * 获取指定 Agent
     */
    public Agent getAgent(String name) {
        Agent agent = agentMap.get(name);
        if (agent == null) {
            log.warn("⚠️ Agent 不存在：{}", name);
        }
        return agent;
    }
    
    /**
     * 获取所有已注册的 Agent
     */
    public List<Agent> getAllAgents() {
        return List.copyOf(agentMap.values());
    }
    
    /**
     * 检查 Agent 是否存在
     */
    public boolean hasAgent(String name) {
        return agentMap.containsKey(name);
    }
}
