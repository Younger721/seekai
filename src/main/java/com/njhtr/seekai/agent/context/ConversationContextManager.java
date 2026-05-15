package com.njhtr.seekai.agent.context;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话上下文管理器
 */
@Slf4j
@Component
public class ConversationContextManager {
    
    private final Map<String, ConversationContext> contexts = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建上下文
     */
    public ConversationContext getContext(String conversationId) {
        return contexts.computeIfAbsent(conversationId, 
            id -> new ConversationContext(id));
    }
    
    /**
     * 更新当前 Agent
     */
    public void setCurrentAgent(String conversationId, String agentName) {
        ConversationContext context = getContext(conversationId);
        context.setCurrentAgent(agentName);
        log.debug("📝 更新上下文：{} 的当前 Agent 为 {}", conversationId, agentName);
    }
    
    /**
     * 获取当前 Agent
     */
    public String getCurrentAgent(String conversationId) {
        ConversationContext context = getContext(conversationId);
        return context.getCurrentAgent();
    }
    
    /**
     * 添加共享数据
     */
    public void addSharedData(String conversationId, String key, Object value) {
        ConversationContext context = getContext(conversationId);
        context.getSharedData().put(key, value);
    }
    
    /**
     * 获取共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String conversationId, String key) {
        ConversationContext context = getContext(conversationId);
        return (T) context.getSharedData().get(key);
    }
    
    /**
     * 清除上下文
     */
    public void clearContext(String conversationId) {
        contexts.remove(conversationId);
        log.info("🗑️ 已清除上下文：{}", conversationId);
    }
    
    /**
     * 对话上下文
     */
    @Data
    public static class ConversationContext {
        private final String conversationId;
        private String currentAgent = "GENERAL_HELPER";
        private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
        
        public ConversationContext(String conversationId) {
            this.conversationId = conversationId;
        }
    }
}
