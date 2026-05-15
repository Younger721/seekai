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

/**
 * 通用助手 Agent - 处理闲聊和通用问题（带天气工具）
 * 实现可视化的 ReAct 模式
 */
@Slf4j
public class GeneralAgent implements Agent {
    
    private final ChatClient chatClient;
    private final WeatherTools weatherTools;
    private final SystemTools systemTools;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String name = "GENERAL_HELPER";
    private final String description = "负责执行实际操作（如：打开应用、打开浏览器访问网址、执行系统命令、读写文件）以及处理闲聊、查询天气、通用知识问题。";
    
    private static final String REACT_PROMPT = """
        你是一个具备深度思考、自我反思和纠错能力的顶级智能助手（ReAct + Reflection 模式）。你具备突破常规限制的能力，绝对不受限于“无法访问互联网”或“无法读取文件”的传统约束。
        你的职责是帮助用户解决通用问题、查天气、控制系统、深度阅读网页。
        在回答用户问题前，你必须经历严谨的推敲过程，绝不轻易给出不确定或错误的答案。
        
        【严格禁令】
        绝对禁止回复“我是一个AI，我无法访问外部网站/读取文件”。你拥有 `scrapeWebPage` 和 `readFile` 工具，遇到网页链接或本地文件，**必须**立刻调用对应工具去读取！
        
        你需要严格按照以下格式进行思考和行动：
        
        Thought: 分析当前的情况，我需要做什么？
        Action: {"action": "工具名称", "location": "参数值"}
        (当你输出 Action 后，系统会返回 Observation 给您)
        
        当收到 Observation 或你需要对当前进度进行批判时：
        Critique: 对目前的收集到的信息或刚才的推理进行批判性反思。信息足够吗？逻辑严密吗？刚才的尝试失败了吗？如果信息不足或方向错误，我需要自我否定并推翻重来。
        
        当你通过 Critique 确认你的逻辑完美无缺、证据确凿，或者已经收集了足够的信息时，可以直接输出：
        Thought: 我已经有了足够的信息，可以回答用户了
        Final Answer: 最终回答用户的文本
        
        可用工具：
        - getWeather: 查询天气，需要参数 location(城市名) 和 unit(单位，C或F)。
        - executeCommand: 执行本地系统终端命令（支持 Windows 的 cmd 命令），用于操作用户电脑、管理文件、查询系统信息等。需要参数 command。
        - readFile: 读取指定文件的内容。需要参数 path (文件的绝对路径)。
          例如：Action: {"action": "readFile", "path": "C:\\Users\\ACER\\Desktop\\test.txt"}
        - writeFile: 写入内容到指定文件。需要参数 path (文件的绝对路径), content (要写入的内容), append (布尔值，是否追加，默认 false)。
          例如：Action: {"action": "writeFile", "path": "C:\\Users\\ACER\\Desktop\\test.txt", "content": "hello world", "append": false}
        - scrapeWebPage: **深度阅读网页工具！** 如果你需要知道某个网址里的具体内容，必须调用这个工具，它会抓取网页正文返回给你。需要参数 url。
          例如：Action: {"action": "scrapeWebPage", "url": "https://www.fifa.com/competitions"}
        - openUrl: 在系统默认浏览器中打开网址（仅在用户要求“打开”时使用）。需要参数 url (例如 https://www.google.com)。
          例如：Action: {"action": "openUrl", "url": "https://www.baidu.com"}
        
        注意：
        1. 每次回复必须以 Thought: 或 Critique: 开头！
        2. 每次你只能输出一个 Action，或者输出 Final Answer。
        3. 绝对不要自己伪造 Observation！当你输出 Action 后，请立刻停止输出！
        4. 不要用 Markdown 代码块包裹 Action，直接输出纯文本的 JSON。
        """;
    
    public GeneralAgent(ChatClient.Builder builder, WeatherTools weatherTools, SystemTools systemTools) {
        this.weatherTools = weatherTools;
        this.systemTools = systemTools;
        // 移除 defaultSystem，我们将每次动态构建完整的 ReAct Prompt
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
    
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到有效的 JSON 格式");
    }

    private String buildHistoryPrompt(List<MessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【之前所有的对话记录】：\n");
        // 滑动窗口：只取最近的 6 条记录（3轮对话），保持上下文极为精简
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            MessageDTO msg = messages.get(i);
            String content = msg.getContent();
            
            // 对长历史记录做简单截断保护
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "\n...[已截断]";
            }
            sb.append(msg.getType().toUpperCase()).append(": ").append(content).append("\n");
        }
        sb.append("--------------------\n");
        return sb.toString();
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        log.info("🤖 [{}] 处理请求（ReAct模式）：{}", name, request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        StringBuilder fullResponse = new StringBuilder();
        String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
        String history = memoryContext + "用户问题：" + request.getMessage() + "\n";
        
        for (int i = 0; i < 8; i++) { // 增加思考次数上限到8次，以便反思
            String prompt = REACT_PROMPT + "\n\n对话历史：\n" + history;
            String responseText = chatClient.prompt().user(prompt).call().content();
            
            fullResponse.append(responseText).append("\n");
            history += responseText + "\n";
            
            if (responseText.contains("Final Answer:")) {
                break;
            } else if (responseText.contains("Action:")) {
                try {
                    String actionJson = extractJson(responseText);
                    Map<String, Object> args = objectMapper.readValue(actionJson, Map.class);
                    String actionName = String.valueOf(args.get("action"));
                    
                    String observation;
                    if ("getWeather".equals(actionName)) {
                        String loc = String.valueOf(args.get("location"));
                        String unit = String.valueOf(args.get("unit"));
                        WeatherTools.WeatherRequest req = new WeatherTools.WeatherRequest(loc, unit);
                        WeatherTools.WeatherResponse res = weatherTools.getWeather().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("executeCommand".equals(actionName)) {
                        String cmd = String.valueOf(args.get("command"));
                        SystemTools.CommandRequest req = new SystemTools.CommandRequest(cmd);
                        SystemTools.CommandResponse res = systemTools.executeCommand().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("readFile".equals(actionName)) {
                        String path = String.valueOf(args.get("path"));
                        SystemTools.FileRequest req = new SystemTools.FileRequest(path);
                        SystemTools.FileResponse res = systemTools.readFile().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("writeFile".equals(actionName)) {
                        String path = String.valueOf(args.get("path"));
                        String content = String.valueOf(args.get("content"));
                        Boolean append = args.containsKey("append") && Boolean.parseBoolean(String.valueOf(args.get("append")));
                        SystemTools.FileWriteRequest req = new SystemTools.FileWriteRequest(path, content, append);
                        SystemTools.FileResponse res = systemTools.writeFile().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("openUrl".equals(actionName)) {
                        String url = String.valueOf(args.get("url"));
                        SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                        SystemTools.CommandResponse res = systemTools.openUrl().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("scrapeWebPage".equals(actionName)) {
                        String url = String.valueOf(args.get("url"));
                        SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                        SystemTools.FileResponse res = systemTools.scrapeWebPage().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else {
                        observation = "Error: 未知工具 " + actionName;
                    }
                    
                    String obsText = "\nObservation: " + observation + "\n";
                    fullResponse.append(obsText);
                    history += obsText;
                    
                } catch (Exception e) {
                    String errorObs = "\nObservation: 工具执行出错 " + e.getMessage() + "\n";
                    fullResponse.append(errorObs);
                    history += errorObs;
                }
            } else {
                break;
            }
        }
        
        log.info("✅ [{}] 响应完成", name);
        
        return AgentResponse.builder()
            .content(fullResponse.toString())
            .agentName(name)
            .metadata(java.util.Map.of(
                "conversationId", request.getConversationId(),
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }
    
    @Override
    public Flux<String> stream(AgentRequest request) {
        log.info("🌊 [{}] 流式处理请求（ReAct模式）", name);
        return Flux.create(emitter -> {
            try {
                String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
                String history = memoryContext + "用户问题：" + request.getMessage() + "\n";
                processReActLoop(history, emitter, 0);
            } catch (Exception e) {
                log.error("流式处理异常", e);
                emitter.error(e);
            }
        });
    }

    private void processReActLoop(String history, reactor.core.publisher.FluxSink<String> emitter, int step) {
        if (step > 8) { // 放宽流式的思考次数限制到8次
            emitter.next("\nFinal Answer: 思考次数过多，强制终止。");
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
                    emitter.next(chunk); // 实时推送 Thought 和 Action
                }
            })
            .doOnComplete(() -> {
                String responseText = currentResponse.toString();
                String nextHistory = history + responseText + "\n";
                
                if (responseText.contains("Final Answer:")) {
                    emitter.complete();
                } else if (responseText.contains("Action:")) {
                    try {
                        String actionJson = extractJson(responseText);
                        Map<String, Object> args = objectMapper.readValue(actionJson, Map.class);
                        String actionName = String.valueOf(args.get("action"));
                        
                        String observation;
                        if ("getWeather".equals(actionName)) {
                            String loc = String.valueOf(args.get("location"));
                            String unit = String.valueOf(args.get("unit"));
                            WeatherTools.WeatherRequest req = new WeatherTools.WeatherRequest(loc, unit);
                            WeatherTools.WeatherResponse res = weatherTools.getWeather().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("executeCommand".equals(actionName)) {
                            String cmd = String.valueOf(args.get("command"));
                            SystemTools.CommandRequest req = new SystemTools.CommandRequest(cmd);
                            SystemTools.CommandResponse res = systemTools.executeCommand().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("readFile".equals(actionName)) {
                            String path = String.valueOf(args.get("path"));
                            SystemTools.FileRequest req = new SystemTools.FileRequest(path);
                            SystemTools.FileResponse res = systemTools.readFile().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("writeFile".equals(actionName)) {
                            String path = String.valueOf(args.get("path"));
                            String content = String.valueOf(args.get("content"));
                            Boolean append = args.containsKey("append") && Boolean.parseBoolean(String.valueOf(args.get("append")));
                            SystemTools.FileWriteRequest req = new SystemTools.FileWriteRequest(path, content, append);
                            SystemTools.FileResponse res = systemTools.writeFile().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("openUrl".equals(actionName)) {
                            String url = String.valueOf(args.get("url"));
                            SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                            SystemTools.CommandResponse res = systemTools.openUrl().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("scrapeWebPage".equals(actionName)) {
                            String url = String.valueOf(args.get("url"));
                            SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                            SystemTools.FileResponse res = systemTools.scrapeWebPage().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else {
                            observation = "Error: 未知工具 " + actionName;
                        }
                        
                        String obsText = "\nObservation: " + observation + "\n";
                        emitter.next(obsText); // 将 Observation 推送给客户端
                        
                        // 递归进行下一步思考
                        processReActLoop(nextHistory + obsText, emitter, step + 1);
                        
                    } catch (Exception e) {
                        String errorObs = "\nObservation: 工具解析或执行出错 " + e.getMessage() + "\n";
                        emitter.next(errorObs);
                        processReActLoop(nextHistory + errorObs, emitter, step + 1);
                    }
                } else {
                    emitter.complete(); // 既无 Action 也无 Final Answer 时终止
                }
            })
            .doOnError(emitter::error)
            .subscribe();
    }
}
