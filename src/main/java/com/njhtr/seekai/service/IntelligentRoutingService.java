package com.njhtr.seekai.service;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.dto.RoutingDecision;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能路由服务 - 使用 AI 自动选择合适的 Agent
 */
@Slf4j
@Service
public class IntelligentRoutingService {
    
    private final ChatClient routerClient;
    private final AgentRegistry agentRegistry;
    private final double confidenceThreshold = 0.5; // 置信度阈值
    
    public IntelligentRoutingService(ChatClient.Builder builder, 
                                    AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        
        // 配置专门用于路由决策的 ChatClient（独立实例，不共享记忆）
        this.routerClient = builder.build();
    }
    
    /**
     * 智能路由到最合适的 Agent
     */
    public Agent routeToIntelligentAgent(String message) {
        log.info("🧠 开始智能路由分析：{}", message.substring(0, Math.min(50, message.length())));
        
        // 使用 AI 进行路由决策
        RoutingDecision decision = analyzeIntent(message);
        
        log.info("✅ 路由决策结果：Agent={}, 置信度={}, 理由={}", 
            decision.getSelectedAgent(), 
            decision.getConfidence(), 
            decision.getReason());
        
        // 如果置信度低于阈值，降级到通用助手
        if (decision.getConfidence() < confidenceThreshold) {
            log.warn("⚠️ 置信度过低 ({})，降级到通用助手", decision.getConfidence());
            return agentRegistry.getAgent("GENERAL_HELPER");
        }
        
        // 获取目标 Agent
        Agent selectedAgent = agentRegistry.getAgent(decision.getSelectedAgent());
        
        if (selectedAgent == null) {
            log.error("❌ 选中的 Agent 不存在：{}，使用通用助手", decision.getSelectedAgent());
            return agentRegistry.getAgent("GENERAL_HELPER");
        }
        
        return selectedAgent;
    }
    
    /**
     * 使用 AI 分析用户意图
     */
    private RoutingDecision analyzeIntent(String message) {
        String agentsDescription = buildAgentsDescription();
        
        try {
            // 获取 AI 原始响应
            String promptText = """
                请作为一个智能调度中心，分析用户的请求应该由哪个领域的专家处理：
                
                用户请求：%s
                
                可用专家列表及能力边界：
                %s
                
                路由核心准则：
                1. 意图区分：注意区分“查询信息”和“执行动作”。例如，“查询 GitHub”是搜索行为，应该给 SEARCH_EXPERT；而“打开浏览器访问 GitHub”或“阅读某个URL网页内容”是执行物理动作或深度阅读，必须交给 GENERAL_HELPER（它拥有 SystemTools 可以抓取网页和控制电脑）。
                2. 项目构建与全自动编程：任何涉及到“搭建项目”、“写一个XX放到桌面”、“写完整代码并保存”的请求，必须交给 AUTO_CODER_AGENT。它具备连续执行复杂构建任务的能力。
                3. 文件操作：简单的“读文件”、“写文件”、“保存到本地”操作，可以交给 GENERAL_HELPER 或 AUTO_CODER_AGENT。
                4. 网页深度阅读：如果用户提供了一个具体的 http 链接并要求“阅读”、“分析”或“总结”该网页内容，交给 SEARCH_EXPERT 或 GENERAL_HELPER。
                5. 代码分析：纯粹的口头代码解释、答疑、代码片段分析交给 CODE_EXPERT。
                6. 商业智能与数据分析：如果用户要求“统计数据”、“画图表”、“查询数据库里的数据”、“分析销售趋势/用户增长等业务数据”，**必须**交给 DATA_EXPERT，它拥有直连数据库执行 NL2SQL 和生成图表的能力。
                
                返回 JSON 格式，必须使用以下字段名：
                {
                    "selectedAgent": "AGENT_NAME",
                    "confidence": 0.0-1.0,
                    "reason": "请详细解释选择该专家而不是其他专家的理由"
                }
                """.formatted(message, agentsDescription);
            
            String rawResponse = routerClient.prompt()
                .user(promptText)
                .call()
                .content();
            
            // 提取 JSON 内容（移除 Markdown 代码块标记）
            String jsonContent = extractJson(rawResponse);
            
            // 手动解析为 RoutingDecision
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            RoutingDecision decision = mapper.readValue(jsonContent, RoutingDecision.class);
            
            return decision;
            
        } catch (Exception e) {
            log.error("❌ 路由分析失败，使用默认 Agent", e);
            // 失败时返回通用助手
            return RoutingDecision.builder()
                .selectedAgent("GENERAL_HELPER")
                .confidence(0.0)
                .reason("路由分析失败，使用默认 Agent")
                .build();
        }
    }
    
    /**
     * 从响应中提取 JSON 内容（移除 ```json ... ``` 标记）
     */
    private String extractJson(String response) {
        // 查找 JSON 开始和结束位置
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        // 如果没有找到 JSON，返回原始响应（可能会失败）
        return response.trim();
    }
    
    /**
     * 构建 Agent 描述文本
     */
    private String buildAgentsDescription() {
        List<Agent> agents = agentRegistry.getAllAgents();
        return agents.stream()
            .map(agent -> String.format("- %s: %s", 
                agent.getName(), agent.getDescription()))
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    
    /**
     * 处理请求（自动路由）
     */
    public AgentResponse processRequest(String message, String conversationId, List<MessageDTO> historyMessages) {
        // 1. 智能路由
        Agent targetAgent = routeToIntelligentAgent(message);
        
        log.info("🎯 已路由到 Agent: {}", targetAgent.getName());
        
        // 2. 执行 Agent
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(conversationId)
            .historyMessages(historyMessages)
            .build();
        
        return targetAgent.chat(request);
    }
    
    /**
     * 流式处理（自动路由）
     */
    public Flux<String> processStream(String message, String conversationId, List<MessageDTO> historyMessages) {
        Agent targetAgent = routeToIntelligentAgent(message);
        
        log.info("🌊 流式路由到 Agent: {}", targetAgent.getName());
        
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(conversationId)
            .historyMessages(historyMessages)
            .build();
        
        return targetAgent.stream(request);
    }
    
    /**
     * 获取所有可用的 Agent 信息
     */
    public List<MultiAgentService.AgentInfo> getAvailableAgents() {
        return agentRegistry.getAllAgents().stream()
            .map(agent -> new MultiAgentService.AgentInfo(
                agent.getName(), 
                agent.getDescription()))
            .toList();
    }
}
