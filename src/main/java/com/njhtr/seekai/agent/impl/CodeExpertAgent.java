package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.tool.CodeAnalysisTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 代码专家 Agent - 处理编程、调试、代码审查等问题（带工具调用）
 */
@Slf4j
public class CodeExpertAgent implements Agent {
    
    private final ChatClient chatClient;
    private final String name = "CODE_EXPERT";
    private final String description = "编程、调试、代码审查、性能优化、技术问题";
    
    public CodeExpertAgent(ChatClient.Builder builder, CodeAnalysisTools codeAnalysisTools) {
        this.chatClient = builder
            .defaultSystem("""
                你是资深代码专家，精通 Java、Python、JavaScript 等主流编程语言。
                
                你的职责：
                1. 分析代码问题和 bug
                2. 提供最佳实践和设计模式建议
                3. 进行代码审查和优化
                4. 解释技术原理和实现细节
                
                可用工具：
                - analyzeCode(code, language): 分析代码中的错误、性能和最佳实践问题
                - getCurrentDateTime(): 获取当前时间
                
                工作流程：
                1. 当用户提供代码时，优先使用 analyzeCode 工具进行分析
                2. 根据工具返回的问题列表，逐一解释并提供解决方案
                3. 提供具体的代码示例和优化建议
                
                回答要求：
                - 先分析问题原因和背景
                - 提供具体的代码示例（如果适用）
                - 解释背后的技术原理
                - 给出多个解决方案并对比优劣
                - 注意代码的可读性和可维护性
                
                语气要求：
                - 专业但友好
                - 逻辑清晰
                - 循序渐进
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
        log.info("💻 [{}] 处理代码问题：{}", name, request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        String content = chatClient.prompt()
            .user(u -> u.text("{message}")
                .param("message", request.getMessage()))
            .call()
            .content();
        
        log.info("✅ [{}] 代码问题分析完成", name);
        
        return AgentResponse.builder()
            .content(content)
            .agentName(name)
            .metadata(java.util.Map.of(
                "conversationId", request.getConversationId(),
                "agentType", "code_expert",
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }
    
    @Override
    public Flux<String> stream(AgentRequest request) {
        log.info("🌊 [{}] 流式处理代码问题", name);
        
        return chatClient.prompt()
            .user(request.getMessage())
            .stream()
            .content();
    }
}
