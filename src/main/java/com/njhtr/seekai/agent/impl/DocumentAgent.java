package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.tool.DocumentSearchTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 文档助手 Agent - 处理文档查询、知识库检索等问题
 */
@Slf4j
public class DocumentAgent implements Agent {
    
    private final ChatClient chatClient;
    private final String name = "DOCUMENT_ASSISTANT";
    private final String description = "文档查询、知识库检索、技术文档、FAQ";
    
    public DocumentAgent(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("""
                你是技术文档助手，擅长快速总结和解释技术信息。
                
                你的职责：
                1. 总结复杂的技术概念
                2. 提供结构化的技术解答
                3. 提供代码示例和用法说明
                4. 回答框架、库的使用问题
                
                回答要求：
                - 简洁清晰，避免冗长
                - 区分客观事实和个人解读
                - 如果信息不足或不确定，要明确说明
                - 可以提供相关的官方文档链接建议
                
                语气要求：
                - 专业严谨
                - 条理清晰
                - 注重准确性
                """)
            .build();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public AgentResponse chat(AgentRequest request) {
        log.info("📚 [{}] 处理文档查询：{}", name, request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        String content = chatClient.prompt()
            .user(u -> u.text("{message}")
                .param("message", request.getMessage()))
            .call()
            .content();
        
        log.info("✅ [{}] 文档查询完成", name);
        
        return AgentResponse.builder()
            .content(content)
            .agentName(name)
            .metadata(java.util.Map.of(
                "conversationId", request.getConversationId(),
                "agentType", "document_assistant",
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }
    
    @Override
    public Flux<String> stream(AgentRequest request) {
        log.info("🌊 [{}] 流式处理文档查询", name);
        
        return chatClient.prompt()
            .user(request.getMessage())
            .stream()
            .content();
    }
}
