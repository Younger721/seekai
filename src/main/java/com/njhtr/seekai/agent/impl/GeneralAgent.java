package com.njhtr.seekai.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.tool.SystemTools;
import com.njhtr.seekai.tool.WeatherTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class GeneralAgent implements Agent {

    private static final int MAX_REACT_STEPS = 4;

    private final ChatClient chatClient;
    private final WeatherTools weatherTools;
    private final SystemTools systemTools;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String name = "GENERAL_HELPER";
    private final String description = "Handles general chat, common questions, weather, web reading, and file/system tool tasks.";

    private static final String SIMPLE_CHAT_PROMPT = """
        你是 SeekAI，一个多智能体协同平台里的通用助手。
        请直接、自然地回答用户问题。
        如果用户只是打招呼，请友好回应并简短说明你能做什么。
        不要输出 Thought、Action、Observation、Critique、Final Answer 这些 ReAct 标记。
        """;

    private static final String REACT_PROMPT = """
        你是 SeekAI 的通用工具 Agent，可以通过工具读取文件、写文件、打开网页、抓取网页、查询天气和执行安全命令。
        你必须使用下面的 ReAct 格式工作。

        当需要调用工具时，只输出：
        Thought: 简短说明下一步
        Action: {"action":"工具名","参数名":"参数值"}

        当已经能回答用户时，只输出：
        Final Answer: 给用户的最终答案

        可用工具：
        - getWeather: 参数 location, unit
        - executeCommand: 参数 command
        - readFile: 参数 path
        - writeFile: 参数 path, content, append
        - openUrl: 参数 url
        - scrapeWebPage: 参数 url

        规则：
        - 每次只能输出一个 Action 或一个 Final Answer。
        - 不要伪造 Observation。
        - Action 必须是纯 JSON，不要用 markdown 代码块。
        """;

    private static final Set<String> TOOL_INTENT_KEYWORDS = Set.of(
        "文件", "目录", "读取", "写入", "保存", "打开", "网页", "网址", "链接", "http", "https",
        "天气", "命令", "终端", "执行", "电脑", "系统", "盘", "路径", "抓取", "访问", "列出",
        "file", "directory", "read", "write", "save", "open", "url", "weather", "command"
    );

    public GeneralAgent(ChatClient.Builder builder, WeatherTools weatherTools, SystemTools systemTools) {
        this.weatherTools = weatherTools;
        this.systemTools = systemTools;
        this.chatClient = builder.build();
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
        String message = request.getMessage() == null ? "" : request.getMessage();
        log.info("[{}] Handling request: {}", name, message.substring(0, Math.min(50, message.length())));

        String content = shouldUseTools(message)
            ? runReAct(request)
            : runSimpleChat(request);

        return AgentResponse.builder()
            .success(true)
            .message("success")
            .content(content)
            .agentName(name)
            .metadata(java.util.Map.of(
                "conversationId", request.getConversationId(),
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        log.info("[{}] Streaming request: {}", name, message.substring(0, Math.min(50, message.length())));

        if (shouldUseTools(message)) {
            return Flux.create(emitter -> {
                try {
                    String history = buildHistoryPrompt(request.getHistoryMessages()) + "用户问题：" + message + "\n";
                    processReActLoop(history, emitter, 0);
                } catch (Exception e) {
                    log.error("ReAct stream failed", e);
                    emitter.next("\n\n模型调用失败: " + e.getMessage());
                    emitter.complete();
                }
            });
        }

        String prompt = SIMPLE_CHAT_PROMPT + "\n\n" + buildHistoryPrompt(request.getHistoryMessages())
            + "用户问题：" + message;

        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content()
            .onErrorResume(error -> {
                log.error("Simple chat stream failed", error);
                return Flux.just("模型调用失败: " + error.getMessage());
            });
    }

    private String runSimpleChat(AgentRequest request) {
        String prompt = SIMPLE_CHAT_PROMPT + "\n\n" + buildHistoryPrompt(request.getHistoryMessages())
            + "用户问题：" + request.getMessage();

        long start = System.currentTimeMillis();
        try {
            String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            log.info("[{}] Simple chat returned in {} ms", name, System.currentTimeMillis() - start);
            return content;
        } catch (Exception e) {
            log.error("Simple chat failed after {} ms", System.currentTimeMillis() - start, e);
            throw new IllegalStateException("通用聊天模型调用失败: " + e.getMessage(), e);
        }
    }

    private String runReAct(AgentRequest request) {
        StringBuilder fullResponse = new StringBuilder();
        String history = buildHistoryPrompt(request.getHistoryMessages()) + "用户问题：" + request.getMessage() + "\n";

        for (int i = 0; i < MAX_REACT_STEPS; i++) {
            String prompt = REACT_PROMPT + "\n\n对话历史：\n" + history;
            String responseText = chatClient.prompt().user(prompt).call().content();

            fullResponse.append(responseText).append("\n");
            history += responseText + "\n";

            if (responseText.contains("Final Answer:")) {
                break;
            }
            if (!responseText.contains("Action:")) {
                break;
            }

            String observation = executeTool(responseText);
            String obsText = "\nObservation: " + observation + "\n";
            fullResponse.append(obsText);
            history += obsText;
        }

        return fullResponse.toString();
    }

    private void processReActLoop(String history, reactor.core.publisher.FluxSink<String> emitter, int step) {
        if (step > MAX_REACT_STEPS) {
            emitter.next("\nFinal Answer: 工具调用次数过多，已停止。");
            emitter.complete();
            return;
        }

        String prompt = REACT_PROMPT + "\n\n对话历史：\n" + history;
        StringBuilder currentResponse = new StringBuilder();

        chatClient.prompt()
            .user(prompt)
            .stream()
            .content()
            .doOnNext(chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    currentResponse.append(chunk);
                    emitter.next(chunk);
                }
            })
            .doOnComplete(() -> {
                String responseText = currentResponse.toString();
                String nextHistory = history + responseText + "\n";

                if (responseText.contains("Final Answer:")) {
                    emitter.complete();
                } else if (responseText.contains("Action:")) {
                    String observation = executeTool(responseText);
                    String obsText = "\nObservation: " + observation + "\n";
                    emitter.next(obsText);
                    processReActLoop(nextHistory + obsText, emitter, step + 1);
                } else {
                    emitter.complete();
                }
            })
            .doOnError(error -> {
                log.error("ReAct model stream failed", error);
                emitter.next("\n\n模型调用失败: " + error.getMessage());
                emitter.complete();
            })
            .subscribe();
    }

    private boolean shouldUseTools(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return TOOL_INTENT_KEYWORDS.stream().anyMatch(text::contains);
    }

    private String executeTool(String responseText) {
        try {
            String actionJson = extractJson(responseText);
            Map<String, Object> args = objectMapper.readValue(actionJson, Map.class);
            String actionName = String.valueOf(args.get("action"));

            if ("getWeather".equals(actionName)) {
                WeatherTools.WeatherRequest req = new WeatherTools.WeatherRequest(
                    String.valueOf(args.get("location")),
                    String.valueOf(args.getOrDefault("unit", "C"))
                );
                return objectMapper.writeValueAsString(weatherTools.getWeather().apply(req));
            }
            if ("executeCommand".equals(actionName)) {
                return objectMapper.writeValueAsString(
                    systemTools.executeCommand().apply(new SystemTools.CommandRequest(String.valueOf(args.get("command"))))
                );
            }
            if ("readFile".equals(actionName)) {
                return objectMapper.writeValueAsString(
                    systemTools.readFile().apply(new SystemTools.FileRequest(String.valueOf(args.get("path"))))
                );
            }
            if ("writeFile".equals(actionName)) {
                SystemTools.FileWriteRequest req = new SystemTools.FileWriteRequest(
                    String.valueOf(args.get("path")),
                    String.valueOf(args.get("content")),
                    Boolean.parseBoolean(String.valueOf(args.getOrDefault("append", false)))
                );
                return objectMapper.writeValueAsString(systemTools.writeFile().apply(req));
            }
            if ("openUrl".equals(actionName)) {
                return objectMapper.writeValueAsString(
                    systemTools.openUrl().apply(new SystemTools.UrlRequest(String.valueOf(args.get("url"))))
                );
            }
            if ("scrapeWebPage".equals(actionName)) {
                return objectMapper.writeValueAsString(
                    systemTools.scrapeWebPage().apply(new SystemTools.UrlRequest(String.valueOf(args.get("url"))))
                );
            }

            return "Error: unknown tool " + actionName;
        } catch (Exception e) {
            log.error("Tool execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到有效 JSON Action");
    }

    private String buildHistoryPrompt(List<MessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("历史对话：\n");
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            MessageDTO msg = messages.get(i);
            String content = msg.getContent() == null ? "" : msg.getContent();
            if (content.length() > 1200) {
                content = content.substring(0, 1200) + "\n...[已截断]";
            }
            sb.append(msg.getType()).append(": ").append(content).append("\n");
        }
        sb.append("--------------------\n");
        return sb.toString();
    }
}
