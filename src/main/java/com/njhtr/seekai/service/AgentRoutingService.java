package com.njhtr.seekai.service;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.service.MultiAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 路由服务 - 支持 @AgentName 显式指定和自动路由
 *
 * 使用方式：
 * - @爬虫 帮我抓取 https://example.com → 使用爬虫 Agent
 * - @图表 生成销售趋势图 → 使用图表 Agent
 * - 直接说话 "帮我分析数据" → 自动路由
 */
@Slf4j
@Service
public class AgentRoutingService {

    private final AgentRegistry agentRegistry;
    private final IntelligentRoutingService autoRoutingService;
    private final MultiAgentService multiAgentService;

    // @AgentName 格式的正则表达式
    // 支持中文和英文别名，使用 Unicode 属性匹配字母
    private static final Pattern MENTION_PATTERN = Pattern.compile("^@(\\p{L}+)\\s+(.+)$", Pattern.UNICODE_CHARACTER_CLASS);

    // Agent 名称别名映射（支持中文别名）
    private static final Map<String, String> AGENT_ALIASES;
    static {
        AGENT_ALIASES = new HashMap<>();
        // 爬虫相关
        AGENT_ALIASES.put("爬虫", "CRAWLER_AGENT");
        AGENT_ALIASES.put("crawler", "CRAWLER_AGENT");
        AGENT_ALIASES.put("搜索", "SEARCH_AGENT");
        AGENT_ALIASES.put("search", "SEARCH_AGENT");
        // 图表相关
        AGENT_ALIASES.put("图表", "CHART_AGENT");
        AGENT_ALIASES.put("chart", "CHART_AGENT");
        AGENT_ALIASES.put("画图", "CHART_AGENT");
        // 数据相关
        AGENT_ALIASES.put("数据", "DATA_AGENT");
        AGENT_ALIASES.put("data", "DATA_AGENT");
        AGENT_ALIASES.put("分析", "DATA_AGENT");
        AGENT_ALIASES.put("数据库", "DATA_AGENT");
        // 代码相关
        AGENT_ALIASES.put("代码", "CODE_EXPERT");
        AGENT_ALIASES.put("code", "CODE_EXPERT");
        AGENT_ALIASES.put("编程", "AUTO_CODER_AGENT");
        AGENT_ALIASES.put("写代码", "AUTO_CODER_AGENT");
        // 文档相关
        AGENT_ALIASES.put("文档", "DOCUMENT_AGENT");
        AGENT_ALIASES.put("document", "DOCUMENT_AGENT");
        // 通用助手
        AGENT_ALIASES.put("助手", "GENERAL_HELPER");
        AGENT_ALIASES.put("helper", "GENERAL_HELPER");
        AGENT_ALIASES.put("通用", "GENERAL_HELPER");
        // 多步骤任务
        AGENT_ALIASES.put("多步骤", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("执行", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("帮我", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("帮我打开", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("帮我搜索", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("steps", "MULTI_STEP_AGENT");
        AGENT_ALIASES.put("auto", "MULTI_STEP_AGENT");
    }

    public AgentRoutingService(AgentRegistry agentRegistry,
                               IntelligentRoutingService autoRoutingService,
                               MultiAgentService multiAgentService) {
        this.agentRegistry = agentRegistry;
        this.autoRoutingService = autoRoutingService;
        this.multiAgentService = multiAgentService;
    }

    /**
     * 解析消息，提取指定的 Agent 名称和实际消息内容
     *
     * @param message 用户消息
     * @return 解析结果
     */
    public ParsedMessage parseMessage(String message) {
        if (message == null || message.isBlank()) {
            return new ParsedMessage(null, message, false);
        }

        Matcher matcher = MENTION_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            String agentName = matcher.group(1);
            String actualMessage = matcher.group(2);

            // 转换别名
            String canonicalName = AGENT_ALIASES.getOrDefault(agentName, agentName.toUpperCase());

            log.info("📌 检测到 @ 提及: {} -> {}", agentName, canonicalName);
            return new ParsedMessage(canonicalName, actualMessage.trim(), true);
        }

        // 没有 @ 标记，返回原消息
        return new ParsedMessage(null, message, false);
    }

    /**
     * 处理请求（统一入口）
     * - 有 @ 提及 → 使用指定 Agent
     * - 无 @ 提及 → 自动路由
     */
    public AgentResponse processRequest(String message, String conversationId, List<MessageDTO> historyMessages) {
        ParsedMessage parsed = parseMessage(message);

        if (parsed.hasMention()) {
            // 使用指定 Agent
            return processWithExplicitAgent(parsed.agentName(), parsed.actualMessage(), conversationId, historyMessages);
        } else {
            // 自动路由
            return autoRoutingService.processRequest(message, conversationId, historyMessages);
        }
    }

    /**
     * 流式处理（统一入口）
     */
    public Flux<String> processStream(String message, String conversationId, List<MessageDTO> historyMessages) {
        ParsedMessage parsed = parseMessage(message);

        if (parsed.hasMention()) {
            return processStreamWithExplicitAgent(parsed.agentName(), parsed.actualMessage(), conversationId, historyMessages);
        } else {
            return autoRoutingService.processStream(message, conversationId, historyMessages);
        }
    }

    /**
     * 使用指定 Agent 处理请求
     */
    private AgentResponse processWithExplicitAgent(String agentName, String actualMessage,
                                                    String conversationId, List<MessageDTO> historyMessages) {
        log.info("🎯 使用指定 Agent: {}", agentName);

        Agent agent = resolveAgent(agentName);
        if (agent == null) {
            return AgentResponse.builder()
                    .content("❌ 未找到指定的 Agent: " + agentName + "\n可用的 Agent: " + getAvailableAgentNames())
                    .agentName("SYSTEM")
                    .build();
        }

        AgentRequest request = AgentRequest.builder()
                .message(actualMessage)
                .conversationId(conversationId)
                .historyMessages(historyMessages)
                .build();

        return agent.chat(request);
    }

    /**
     * 使用指定 Agent 流式处理
     */
    private Flux<String> processStreamWithExplicitAgent(String agentName, String actualMessage,
                                                         String conversationId, List<MessageDTO> historyMessages) {
        log.info("🌊 使用指定 Agent 流式处理: {}", agentName);

        Agent agent = resolveAgent(agentName);
        if (agent == null) {
            return Flux.just("❌ 未找到指定的 Agent: " + agentName + "\n可用的 Agent: " + getAvailableAgentNames());
        }

        AgentRequest request = AgentRequest.builder()
                .message(actualMessage)
                .conversationId(conversationId)
                .historyMessages(historyMessages)
                .build();

        return agent.stream(request);
    }

    /**
     * 根据名称解析 Agent（支持别名和大小写不敏感）
     */
    private Agent resolveAgent(String agentName) {
        if (agentName == null) return null;

        // 直接查找
        Agent agent = agentRegistry.getAgent(agentName);
        if (agent != null) return agent;

        // 大小写转换后查找
        agent = agentRegistry.getAgent(agentName.toUpperCase());
        if (agent != null) return agent;

        // 别名查找
        String canonicalName = AGENT_ALIASES.get(agentName.toLowerCase());
        if (canonicalName != null) {
            agent = agentRegistry.getAgent(canonicalName);
            if (agent != null) return agent;
        }

        // 模糊匹配（检查是否包含关键词）
        String lowerName = agentName.toLowerCase();
        for (Agent a : agentRegistry.getAllAgents()) {
            if (a.getName().toLowerCase().contains(lowerName) ||
                a.getDescription().toLowerCase().contains(lowerName)) {
                return a;
            }
        }

        return null;
    }

    /**
     * 获取所有可用的 Agent 名称
     */
    private String getAvailableAgentNames() {
        return agentRegistry.getAllAgents().stream()
                .map(Agent::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("无");
    }

    /**
     * 获取可用 Agent 列表
     */
    public List<MultiAgentService.AgentInfo> getAvailableAgents() {
        return multiAgentService.getAvailableAgents();
    }

    /**
     * 解析后的消息
     */
    public record ParsedMessage(
            String agentName,      // 指定的 Agent 名称（如果有）
            String actualMessage, // 实际要处理的消息
            boolean hasMention    // 是否有 @ 提及
    ) {
        public boolean hasMention() {
            return hasMention && agentName != null && !agentName.isEmpty();
        }
    }
}