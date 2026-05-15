package com.njhtr.seekai.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.service.BingSearchService;
import com.njhtr.seekai.tool.SystemTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 联网搜索 Agent - 使用必应搜索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchAgent implements Agent {
    
    private final ChatClient chatClient;
    private final BingSearchService searchService;
    private final SystemTools systemTools;
    private final String name = "SEARCH_EXPERT";
    private final String description = "纯信息查询专家。专门负责通过搜索引擎查询外部实时信息、新闻、百科，或者深度抓取指定网页的正文内容进行阅读分析。";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class SearchSessionState {
        private BingSearchService.SearchResults lastSearchResults;
        private final Set<String> attemptedUrls = new LinkedHashSet<>();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    private String buildHistoryPrompt(List<MessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【历史上下文】：\n");
        // 取最近的 6 条记录
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            MessageDTO msg = messages.get(i);
            sb.append(msg.getType().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
        }
        sb.append("--------------------\n");
        return sb.toString();
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        log.info("[{}] 处理请求：{}", name, request.getMessage());
        
        try {
            // 优化搜索关键词，去除口语化表达
            String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
            String searchQuery = optimizeSearchQuery(request.getMessage(), memoryContext);
            
            // 1. 先从必应搜索获取实时信息
            BingSearchService.SearchResults searchResults = searchService.search(
                searchQuery, 
                5 // 最多 5 条结果
            );
            
            // 2. 将搜索结果交给 AI 整理
            String context = searchResults.toTextSummary();
            
            String response = chatClient.prompt()
                .user("""
                    你是一个智能的AI搜索助手。请根据以下抓取到的互联网搜索结果（包含摘要和正文片段），为用户提供一个清晰、准确、结构化的回答。
                    
                    %s
                    
                    【搜索上下文信息】
                    搜索关键词：%s
                    %s
                    
                    【用户最新问题】
                    %s
                    
                    【回答要求】
                    1. 事实准确性：严格基于提供的搜索结果，整合多个来源的信息，提供全面准确的解答。
                    2. 溯源标注：当你在回答中引用了某个搜索结果的具体信息时，必须在相关句子末尾加上引用标记（如 [1]、[2]），并在回答的最末尾统一列出这些引用对应的标题和链接。
                    3. 客观中立：如果不同搜索来源存在冲突或矛盾，请客观列出各方观点。
                    4. 异常处理：如果搜索结果质量差或完全不相关，请明确告知用户，并仅基于你的内部知识提供可能的帮助，同时说明“此回答未找到相关的联网结果”。
                    5. 格式友好：保持排版清晰，必要时使用列表或表格展示信息。
                    """.formatted(memoryContext, searchQuery, context, request.getMessage()))
                .call()
                .content();
            
            log.info("[{}] 响应完成", name);
            
            return AgentResponse.builder()
                .content(response)
                .agentName(name)
                .build();
                
        } catch (Exception e) {
            log.error("[{}] 处理失败：{}", name, e.getMessage(), e);
            
            // 降级处理：直接让 AI 回答
            String fallbackResponse = chatClient.prompt()
                .user("请回答这个问题（注意：无法联网搜索最新信息）：" + request.getMessage())
                .call()
                .content();
            
            return AgentResponse.builder()
                .content(fallbackResponse + "\n\n⚠️ 注意：搜索服务暂时不可用，以上回答基于已有知识。")
                .agentName(name)
                .build();
        }
    }
    
    /**
     * 优化搜索关键词，去除口语化表达 (Query Refinement)
     */
    private String optimizeSearchQuery(String userMessage, String memoryContext) {
        try {
            log.info("[{}] 正在通过大模型结合上下文优化搜索词...", name);
            String optimized = chatClient.prompt()
                .user("""
                    你是一个搜索意图识别与查询优化专家。请结合用户的历史上下文，将用户的最新问题转化为最适合搜索引擎的关键词。
                    规则：
                    1. 补充代词：如果用户的最新问题是“他在哪里出生”或“还有什么信息”，你必须从历史上下文中找到“他”是谁，补充完整（例如“姚澎豪 出生地点”）。
                    2. 去除口语化、无意义的前缀和冗余词汇。
                    3. 提取核心实体和动作，精简为搜索关键词，用空格隔开。
                    4. 对于时间敏感的问题（如“最新”、“今天”），自动补充具体时间背景（当前年份/月份）。
                    5. 仅输出优化后的搜索关键词，不要包含任何标点符号、解释或其他多余字符。
                    
                    %s
                    
                    用户最新问题：%s
                    """.formatted(memoryContext, userMessage))
                .call()
                .content();
            
            if (optimized != null && !optimized.trim().isEmpty()) {
                log.info("[{}] 关键词优化完成: [{}] -> [{}]", name, userMessage, optimized.trim());
                return optimized.trim();
            }
        } catch (Exception e) {
            log.warn("[{}] 关键词优化失败，降级使用原句: {}", name, e.getMessage());
        }
        return userMessage;
    }
    
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到有效的 JSON 格式");
    }

    private String selectFallbackUrl(SearchSessionState state, String failedUrl) {
        if (state.lastSearchResults == null || state.lastSearchResults.results().isEmpty()) {
            return null;
        }

        for (BingSearchService.SearchResult result : state.lastSearchResults.results()) {
            String candidate = systemTools.normalizeUrl(result.link());
            if (!candidate.isBlank() && !candidate.equals(failedUrl) && !state.attemptedUrls.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String buildScrapeFailureObservation(String attemptedUrl, SystemTools.FileResponse response, String fallbackUrl, SystemTools.FileResponse fallbackResponse) throws Exception {
        if (fallbackUrl == null || fallbackResponse == null) {
            return objectMapper.writeValueAsString(response)
                + "\n请不要再次尝试这个网址。请改用其他搜索结果链接，或基于现有搜索摘要直接回答用户。";
        }

        return """
            首次抓取结果：%s
            自动回退网址：%s
            回退抓取结果：%s
            如果回退结果仍失败，请不要重复抓取同一个网址，直接基于当前搜索结果作答，并明确说明官网正文暂时无法读取。
            """.formatted(
                objectMapper.writeValueAsString(response),
                fallbackUrl,
                objectMapper.writeValueAsString(fallbackResponse)
            );
    }

    private void processReActLoop(String promptContext, reactor.core.publisher.FluxSink<String> emitter, int loopCount, SearchSessionState state) {
        if (loopCount >= 5) {
            emitter.next("\n\nFinal Answer: (强制结束) 经过多次搜索和阅读，我无法得出最终结论，请提供更具体的信息。");
            emitter.complete();
            return;
        }

        try {
            String rawResponse = chatClient.prompt()
                .user(promptContext)
                .call()
                .content();

            if (rawResponse == null) return;
            emitter.next(rawResponse + "\n");

            if (rawResponse.contains("Final Answer:")) {
                emitter.complete();
                return;
            }

            if (rawResponse.contains("Action:")) {
                String actionLine = rawResponse.substring(rawResponse.indexOf("Action:"));
                String jsonStr = extractJson(actionLine);
                Map<String, Object> actionJson = objectMapper.readValue(jsonStr, Map.class);
                String actionName = (String) actionJson.get("action");
                Map<String, Object> args = actionJson;

                String observation;
                if ("scrapeWebPage".equals(actionName)) {
                    String requestedUrl = String.valueOf(args.get("url"));
                    String url = systemTools.normalizeUrl(requestedUrl);

                    if (url.isBlank()) {
                        observation = "Error: 模型提供了空网址。请从搜索结果里重新选择一个有效链接。";
                    } else {
                        String targetUrl = url;
                        if (state.attemptedUrls.contains(targetUrl)) {
                            String fallbackUrl = selectFallbackUrl(state, targetUrl);
                            if (fallbackUrl != null) {
                                targetUrl = fallbackUrl;
                                emitter.next("\n> ↪ 检测到重复网址，自动切换候选网页: " + targetUrl + "\n");
                            }
                        }

                        emitter.next("\n> 🌐 正在深度阅读网页: " + targetUrl + "\n");
                        state.attemptedUrls.add(targetUrl);
                        SystemTools.FileResponse res = systemTools.scrapeWebPage().apply(new SystemTools.UrlRequest(targetUrl));

                        if (res.success()) {
                            observation = objectMapper.writeValueAsString(res);
                        } else {
                            String fallbackUrl = selectFallbackUrl(state, targetUrl);
                            SystemTools.FileResponse fallbackResponse = null;
                            if (fallbackUrl != null) {
                                emitter.next("\n> ↪ 首个网址抓取失败，自动尝试候选网页: " + fallbackUrl + "\n");
                                state.attemptedUrls.add(fallbackUrl);
                                fallbackResponse = systemTools.scrapeWebPage().apply(new SystemTools.UrlRequest(fallbackUrl));
                            }
                            observation = buildScrapeFailureObservation(targetUrl, res, fallbackUrl, fallbackResponse);
                        }
                    }
                } else if ("searchBing".equals(actionName)) {
                    String query = String.valueOf(args.get("query"));
                    emitter.next("\n> 🔍 正在搜索: " + query + "\n");
                    BingSearchService.SearchResults res = searchService.search(query, 5);
                    state.lastSearchResults = res;
                    observation = res.toTextSummary();
                } else {
                    observation = "Error: 未知工具 " + actionName;
                }

                String obsText = "\nObservation: " + observation + "\n";
                promptContext += "\n" + rawResponse + obsText;
                
                // 递归继续思考
                processReActLoop(promptContext, emitter, loopCount + 1, state);
            } else {
                emitter.next("\n\nFinal Answer: " + rawResponse);
                emitter.complete();
            }

        } catch (Exception e) {
            log.error("SearchAgent 内部循环异常", e);
            emitter.next("\n[系统异常]: " + e.getMessage());
            emitter.complete();
        }
    }

    @Override
    public reactor.core.publisher.Flux<String> stream(AgentRequest request) {
        return Flux.create(emitter -> {
            try {
                String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
                SearchSessionState state = new SearchSessionState();
                
                String initialPrompt = """
                    你是专业的联网搜索与信息分析专家 (Search Agent)。
                    你擅长通过搜索引擎查找信息，并且能够**深度抓取和阅读**某个具体的网页来获取详细内容。
                    
                    【可用工具】
                    - searchBing: 使用搜索引擎查询最新信息。会返回搜索结果列表（含标题、摘要和网址）。需要参数 query。
                      例如：Action: {"action": "searchBing", "query": "2026世界杯举办地"}
                    - scrapeWebPage: 深度阅读网页工具。如果你从搜索结果中找到了一个有价值的链接，或者用户直接给你提供了一个 URL，你必须使用此工具抓取网页的全文内容进行阅读。需要参数 url。
                      例如：Action: {"action": "scrapeWebPage", "url": "https://www.fifa.com/xxx"}
                      
                    【工作流要求】
                    你需要严格按照以下格式进行思考和行动（ReAct 模式）：
                    
                    Thought: 我需要先搜什么？或者我需要阅读哪个网址？
                    Action: {"action": "工具名称", "参数名": "参数值"}
                    (等待系统返回 Observation)

                    额外规则：
                    1. 输出给 scrapeWebPage 的 url 必须是干净的纯链接，不能带反引号、空格或 markdown 包裹符号。
                    2. 如果 Observation 已说明某个网址抓取失败，不要再次抓取同一个网址，改用其他搜索结果或直接总结现有信息。
                    
                    收到 Observation 后：
                    Critique: 我拿到的信息足够回答用户了吗？如果搜索摘要不够详细，我是否应该调用 scrapeWebPage 去阅读某个具体的网页？
                    
                    当信息充足时：
                    Thought: 我已经掌握了足够的信息。
                    Final Answer: 向用户提供准确、详细的回答，并在引用处标注链接。
                    
                    【历史上下文】
                    %s
                    
                    【用户问题】
                    %s
                    """.formatted(memoryContext, request.getMessage());
                
                processReActLoop(initialPrompt, emitter, 0, state);
                    
            } catch (Exception e) {
                log.error("[{}] 流式处理失败：{}", name, e.getMessage(), e);
                emitter.error(e);
            }
        });
    }
}
