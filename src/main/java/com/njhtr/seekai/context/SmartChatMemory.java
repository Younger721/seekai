package com.njhtr.seekai.context;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能聊天内存 - 适配 Spring AI ChatMemory 接口
 * 使用 SmartContextManager 作为底层存储
 */
@Component
public class SmartChatMemory implements ChatMemory {

    private final SmartContextManager contextManager;

    public SmartChatMemory(SmartContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message msg : messages) {
            String role = extractRole(msg);
            String content = msg.getText();
            String agentName = extractAgentName(msg);

            contextManager.addMessage(conversationId, role, content, agentName);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        // 获取最近 15 条消息
        return get(conversationId, 15);
    }

    /**
     * 获取最近 N 条消息
     */
    public List<Message> get(String conversationId, int lastN) {
        List<ContextItem> items = contextManager.getContext(conversationId);

        // 转换为 Message 对象
        List<Message> messages = new ArrayList<>();
        for (ContextItem item : items) {
            messages.add(convertToMessage(item));
        }

        // 只返回最近 N 条
        if (lastN > 0 && messages.size() > lastN) {
            return messages.subList(messages.size() - lastN, messages.size());
        }

        return messages;
    }

    @Override
    public void clear(String conversationId) {
        contextManager.clear(conversationId);
    }

    /**
     * 获取优化后的上下文 (字符串格式)
     */
    public String getOptimizedContext(String conversationId) {
        return contextManager.getOptimizedContext(conversationId);
    }

    /**
     * 检索相关上下文
     */
    public List<Message> retrieve(String conversationId, String query) {
        return contextManager.retrieveRelevantContext(conversationId, query)
            .stream()
            .map(this::convertToMessage)
            .toList();
    }

    /**
     * 归档重要信息到 L3
     */
    public void archive(String conversationId, String key, String value) {
        contextManager.archiveToL3(conversationId, key, value);
    }

    /**
     * 获取统计信息
     */
    public SmartContextManager.ContextStats getStats(String conversationId) {
        return contextManager.getStats(conversationId);
    }

    private String extractRole(Message msg) {
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AssistantMessage) return "assistant";
        return "system";
    }

    private String extractAgentName(Message msg) {
        // Spring AI 1.0 AssistantMessage 没有 getMetadata(String) 方法
        // 直接返回默认值
        return "AI";
    }

    private Message convertToMessage(ContextItem item) {
        return switch (item.getRole()) {
            case "user" -> new UserMessage(item.getContent());
            case "assistant" -> new AssistantMessage(item.getContent());
            default -> new UserMessage(item.getContent()); // summary/archive 当作用户消息
        };
    }
}