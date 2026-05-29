package com.njhtr.seekai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.dto.RoutingDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;

/**
 * Hybrid router: choose obvious agents locally, and call the model only when the
 * request is genuinely ambiguous.
 */
@Slf4j
@Service
public class IntelligentRoutingService {

    private static final String GENERAL_AGENT = "GENERAL_HELPER";
    private static final String CODE_AGENT = "CODE_EXPERT";
    private static final String DOCUMENT_AGENT = "DOCUMENT_ASSISTANT";
    private static final String SEARCH_AGENT = "SEARCH_EXPERT";
    private static final String DATA_AGENT = "DATA_EXPERT";
    private static final String AUTO_CODER_AGENT = "AUTO_CODER_AGENT";

    private static final String ROUTER_AGENT_LIST = """
        GENERAL_HELPER: greetings, casual chat, general questions.
        CODE_EXPERT: code explanation, debugging, code review, programming questions.
        DOCUMENT_ASSISTANT: document writing, summarization, document Q&A.
        SEARCH_EXPERT: web search, latest/current information, online lookup.
        DATA_EXPERT: SQL, database query, chart, data/business analysis.
        AUTO_CODER_AGENT: create or modify project files, implement features, multi-step coding work.
        """;

    private final ChatClient routerClient;
    private final AgentRegistry agentRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final double confidenceThreshold = 0.5;

    public IntelligentRoutingService(ChatClient.Builder builder, AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        this.routerClient = builder.build();
    }

    public Agent routeToIntelligentAgent(String message) {
        String preview = preview(message);
        log.info("Start intelligent routing: {}", preview);

        String localAgentName = localRoute(message);
        if (localAgentName != null) {
            log.info("Local route selected: {}", localAgentName);
            return requireAgent(localAgentName);
        }

        RoutingDecision decision = analyzeIntent(message);
        log.info("Model route selected: agent={}, confidence={}, reason={}",
            decision.getSelectedAgent(), decision.getConfidence(), decision.getReason());

        if (decision.getConfidence() == null || decision.getConfidence() < confidenceThreshold) {
            log.warn("Routing confidence is low ({}), fallback to {}", decision.getConfidence(), GENERAL_AGENT);
            return requireAgent(GENERAL_AGENT);
        }

        Agent selectedAgent = agentRegistry.getAgent(decision.getSelectedAgent());
        if (selectedAgent == null) {
            throw new IllegalStateException("Router selected an unregistered agent: " + decision.getSelectedAgent());
        }

        return selectedAgent;
    }

    private String localRoute(String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return GENERAL_AGENT;
        }

        if (isSearchRequest(text)) {
            return SEARCH_AGENT;
        }
        if (isAutoCoderRequest(text)) {
            return AUTO_CODER_AGENT;
        }
        if (isCodeRequest(text)) {
            return CODE_AGENT;
        }
        if (isDataRequest(text)) {
            return DATA_AGENT;
        }
        if (isDocumentRequest(text)) {
            return DOCUMENT_AGENT;
        }
        if (isSimpleGeneralChat(text)) {
            return GENERAL_AGENT;
        }
        if (!looksLikeSpecialAgentTask(text)) {
            return GENERAL_AGENT;
        }

        return null;
    }

    private boolean isSimpleGeneralChat(String text) {
        if (text.length() <= 24 && containsAny(text,
            "\u4f60\u597d", "\u60a8\u597d", "\u55e8", "\u54c8\u55bd", "\u65e9\u4e0a\u597d", "\u665a\u4e0a\u597d",
            "hello", "hi", "hey", "\u4f60\u662f\u8c01", "\u4f60\u80fd\u505a\u4ec0\u4e48")) {
            return true;
        }
        return containsAny(text,
            "\u4ec0\u4e48\u662f", "\u4e3a\u4ec0\u4e48", "\u600e\u4e48\u7406\u89e3", "\u89e3\u91ca\u4e00\u4e0b",
            "\u8bb2\u4e00\u4e0b", "\u804a\u804a", "\u95ee\u4e2a\u95ee\u9898", "what is", "why", "explain");
    }

    private boolean isSearchRequest(String text) {
        return containsAny(text,
            "\u641c\u7d22", "\u8054\u7f51", "\u4e0a\u7f51\u67e5", "\u67e5\u4e00\u4e0b", "\u6700\u65b0", "\u4eca\u5929",
            "\u65b0\u95fb", "\u70ed\u70b9", "\u7f51\u9875", "\u7f51\u5740", "search", "google", "bing", "latest", "news");
    }

    private boolean isAutoCoderRequest(String text) {
        boolean codingObject = containsAny(text,
            "\u9879\u76ee", "\u4ee3\u7801\u5e93", "\u5de5\u7a0b", "\u6587\u4ef6", "\u63a5\u53e3", "\u529f\u80fd",
            "\u7ec4\u4ef6", "\u914d\u7f6e", "project", "repo", "codebase", "file", "feature");
        boolean codingAction = containsAny(text,
            "\u5b9e\u73b0", "\u4fee\u6539", "\u4fee\u590d", "\u6539\u4e00\u4e0b", "\u5199\u4e00\u4e2a", "\u52a0\u4e00\u4e2a",
            "\u5220\u9664", "\u91cd\u6784", "\u7f16\u8bd1", "\u8fd0\u884c", "\u5206\u6790\u8fd9\u4e2a\u9879\u76ee",
            "\u9879\u76ee\u7ed3\u6784", "implement", "modify", "fix", "refactor", "compile", "run");
        return codingObject && codingAction;
    }

    private boolean isCodeRequest(String text) {
        return containsAny(text,
            "\u4ee3\u7801", "\u62a5\u9519", "\u5f02\u5e38", "\u8c03\u8bd5", "\u4ee3\u7801\u8bc4\u5ba1", "\u89e3\u91ca\u8fd9\u6bb5",
            "\u7b97\u6cd5", "java", "spring", "vue", "javascript", "typescript", "python", "bug", "debug",
            "exception", "stack trace", "code review");
    }

    private boolean isDataRequest(String text) {
        return containsAny(text,
            "sql", "\u6570\u636e\u5e93", "\u8868", "\u56fe\u8868", "\u7edf\u8ba1", "\u6307\u6807", "\u6570\u636e\u5206\u6790",
            "\u67e5\u8be2\u6570\u636e", "mysql", "postgres", "chart", "database", "analytics");
    }

    private boolean isDocumentRequest(String text) {
        return containsAny(text,
            "\u6587\u6863", "\u603b\u7ed3", "\u6458\u8981", "\u5199\u4f5c", "\u5927\u7eb2", "\u62a5\u544a", "\u5408\u540c",
            "\u7b80\u5386", "document", "summary", "summarize", "outline", "report");
    }

    private boolean looksLikeSpecialAgentTask(String text) {
        return containsAny(text,
            "\u5e2e\u6211", "\u5206\u6790", "\u5904\u7406", "\u751f\u6210", "\u521b\u5efa", "\u5236\u4f5c", "\u8bbe\u8ba1",
            "\u67e5\u8be2", "\u6574\u7406", "\u5bfc\u51fa", "\u8bfb\u53d6", "\u6253\u5f00", "\u6293\u53d6",
            "analyze", "generate", "create", "design", "query", "export", "read", "open", "crawl");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private RoutingDecision analyzeIntent(String message) {
        String promptText = """
            You are a router for a multi-agent AI system.
            Choose exactly one registered agent for the user request.

            Agents:
            %s

            User request:
            %s

            Return strict JSON only:
            {"selectedAgent":"AGENT_NAME","confidence":0.0,"reason":"short reason"}
            """.formatted(ROUTER_AGENT_LIST, message == null ? "" : message);

        long start = System.currentTimeMillis();
        try {
            String rawResponse = routerClient.prompt()
                .user(promptText)
                .call()
                .content();

            long elapsed = System.currentTimeMillis() - start;
            log.info("Router model returned in {} ms. Raw response: {}", elapsed, rawResponse);

            String jsonContent = extractJson(rawResponse);
            RoutingDecision decision = objectMapper.readValue(jsonContent, RoutingDecision.class);
            validateDecision(decision);
            return decision;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Routing model call failed after {} ms", elapsed, e);
            throw new IllegalStateException("Intelligent routing model call failed: " + e.getMessage(), e);
        }
    }

    private void validateDecision(RoutingDecision decision) {
        if (decision == null || decision.getSelectedAgent() == null || decision.getSelectedAgent().isBlank()) {
            throw new IllegalStateException("Routing model did not return selectedAgent");
        }
        if (!agentRegistry.hasAgent(decision.getSelectedAgent())) {
            throw new IllegalStateException("Routing model returned an unregistered agent: " + decision.getSelectedAgent());
        }
        if (decision.getConfidence() == null) {
            decision.setConfidence(0.0);
        }
        if (decision.getReason() == null) {
            decision.setReason("");
        }
    }

    private String extractJson(String response) {
        if (response == null) {
            throw new IllegalStateException("Model returned empty response");
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        throw new IllegalStateException("Model did not return JSON: " + response);
    }

    public AgentResponse processRequest(String message, String conversationId, List<MessageDTO> historyMessages) {
        Agent targetAgent = routeToIntelligentAgent(message);
        log.info("Routed to agent: {}", targetAgent.getName());

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(conversationId)
            .historyMessages(historyMessages)
            .build();

        return targetAgent.chat(request);
    }

    public Flux<String> processStream(String message, String conversationId, List<MessageDTO> historyMessages) {
        try {
            Agent targetAgent = routeToIntelligentAgent(message);
            log.info("Streaming routed to agent: {}", targetAgent.getName());

            AgentRequest request = AgentRequest.builder()
                .message(message)
                .conversationId(conversationId)
                .historyMessages(historyMessages)
                .build();

            return targetAgent.stream(request)
                .onErrorResume(error -> {
                    log.error("Agent stream failed", error);
                    return Flux.just("\n\nModel call failed: " + error.getMessage());
                });
        } catch (Exception e) {
            log.error("Streaming route failed", e);
            return Flux.just("Model call failed: " + e.getMessage());
        }
    }

    public List<MultiAgentService.AgentInfo> getAvailableAgents() {
        return agentRegistry.getAllAgents().stream()
            .map(agent -> new MultiAgentService.AgentInfo(agent.getName(), agent.getDescription()))
            .toList();
    }

    private Agent requireAgent(String agentName) {
        Agent agent = agentRegistry.getAgent(agentName);
        if (agent == null) {
            throw new IllegalStateException("Required agent is not registered: " + agentName);
        }
        return agent;
    }

    private String preview(String message) {
        return message == null ? "" : message.substring(0, Math.min(50, message.length()));
    }
}
