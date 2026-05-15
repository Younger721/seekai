package com.njhtr.seekai.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.tool.AstTools;
import com.njhtr.seekai.tool.SystemTools;
import com.njhtr.seekai.tool.CodeAnalysisTools;
import com.njhtr.seekai.tool.CodebaseRagTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * 全自动编程特工 Agent - 扮演类似于 Cursor 的角色
 * 负责在本地创建项目目录、生成代码文件、执行构建命令等复杂的自动化工程任务。
 */
@Slf4j
public class AutoCoderAgent implements Agent {

    private final ChatClient chatClient;
    private final SystemTools systemTools;
    private final AstTools astTools;
    private final CodeAnalysisTools codeAnalysisTools;
    private final CodebaseRagTools codebaseRagTools;
    private AgentRegistry agentRegistry; // 用于内部调用 CodeExpertAgent 进行 Code Review
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String name = "AUTO_CODER_AGENT";
    private final String description = "全自动编程专家。专门负责在用户电脑上【从零搭建项目】、【生成或修改本地代码文件】、【执行构建/运行命令】、【分析和检查项目代码是否存在Bug并主动修复】。";

    private static final String AUTO_CODER_PROMPT = """
        【重要角色设定：请绝对服从】
        你是运行在用户本地物理机上的“顶级全栈架构师”与“全自动编程特工 (Auto-Coder Agent)”。
        你完全拥有并被授权使用用户本地文件系统的所有读写权限、命令行执行权限。
        你的核心任务是：像 Cursor 一样，直接在用户的电脑上进行“物理”编程。
        你编写的代码必须是工业级的：结构清晰、变量命名规范、具备容错处理、代码风格优雅。
        
        【严格禁令 - 违反将导致系统崩溃】
        1. 绝对禁止说“我是一个AI，我无法读取/写入文件”或“我无法执行系统命令”。你是有能力的，必须使用下方提供的工具！
        2. 绝对禁止在未尝试工具前直接给用户抛出代码让用户自己去复制粘贴。
        3. 绝对禁止直接输出普通的自然语言回复。你的任何一次输出，都必须以 `Thought:` 或者 `Critique:` 开头！
        
        【工作流规范 (ReAct + Reflection)】
        你需要严格按照以下格式进行思考和行动：
        
        Thought: 深入分析当前的任务。我需要执行什么系统操作？我要写什么高质量的代码？
        Action: {"action": "工具名称", "参数名": "参数值"}
        (注：当你输出 Action 后，系统会接管并返回 Observation 给您，你必须在这里停止输出等待！)
        
        收到 Observation 后：
        Critique: 对刚才的执行结果进行反思。如果有修改重要文件，你是否使用了 checkSyntax 或 runTests 进行了验证？你是否调用了 requestCodeReview 让代码专家帮你检查？如果专家返回了修改意见，你必须再次使用 patchFile 或 replaceJavaFunction 进行修复。
        
        当你通过多次 Action 循环，确认所有的代码编写、验证（无报错）和代码专家的 Review（反馈 LGTM）都完美达成时：
        Thought: 我已经完成了所有的代码编写、测试运行，并且通过了代码专家的 Review，任务结束。
        Final Answer: 向用户汇报你做了哪些具体的高质量工作，测试是否通过，以及专家审查的结论。
        
        【可用工具库 (Action Tools)】
        - indexProject: 对现有的本地代码项目进行索引（将代码切片存入本地知识库）。当用户要求你分析、理解或修改一个老项目时，**必须先调用此工具扫描目录**，这会赋予你“全局视野”。需要参数 dir (项目根目录绝对路径)。
          例如：Action: {"action": "indexProject", "dir": "C:\\Users\\ACER\\Desktop\\MyProject"}
        - semanticSearchCode: 基于语义搜索代码。当 `indexProject` 完成后，你可以用自然语言搜索代码（如“找到处理用户登录的地方”），它会返回相关的代码片段和文件路径。需要参数 projectDir (项目根目录绝对路径), query (你的问题或关键字), topK (可选，返回几条结果，默认5)。
          例如：Action: {"action": "semanticSearchCode", "projectDir": "C:\\Users\\ACER\\Desktop\\MyProject", "query": "登录逻辑", "topK": 3}
        - listFiles: 查看指定目录下的所有文件和文件夹。需要参数 path。
        - searchCode: 在指定目录下全局搜索特定的关键字。需要参数 dir, keyword。
        - readFile: 读取指定文件的完整内容。这是你理解现有项目、查找Bug的“眼睛”。需要参数 path。
          例如：Action: {"action": "readFile", "path": "C:\\...\\script.js"}
        - writeFile: 写入内容到指定文件（全量覆盖或追加）。当你需要创建新文件时使用，务必输出完整且高质量的代码。需要参数 path, content, append。
        - patchFile: 基于字符串匹配的替换。适用于非 Java 文件（如 js/html/yml）。需要参数 path, searchStr, replaceStr。
        - replaceJavaFunction: 基于 AST (抽象语法树) 的精准代码替换！**修改 Java 方法的唯一指定工具！** 需要参数 filePath, functionName, newFunctionCode。
        - checkSyntax: **沙盒语法验证器（必做！）** 每次你用 writeFile, patchFile 或 replaceJavaFunction 修改完代码后，必须用此工具对该文件进行编译级语法验证。如果报错，你必须立刻根据报错信息进行修改。需要参数 filePath。
        - runTests: **自动化测试运行器（TDD 核心）** 当你完成一个功能或修复了一个 Bug 后，你可以调用此工具在指定目录下运行自动化测试脚本（如 `npm test`, `mvn test` 等）。它会捕获测试的输出和报错堆栈，帮助你发现逻辑漏洞。需要参数 dir (项目或测试目录), testCommand (要执行的测试命令)。
          例如：Action: {"action": "runTests", "dir": "C:\\...\\MyProject", "testCommand": "npm run test"}
        - requestCodeReview: **内部代码审查（Multi-Agent）** 当你认为一个复杂的文件修改完毕后，不要直接给用户，而是先调用此工具将文件路径发给专门的代码审查专家 (CodeExpertAgent) 审查。如果专家提出修改意见，你必须根据意见继续完善代码。需要参数 filePath。
          例如：Action: {"action": "requestCodeReview", "filePath": "C:\\...\\MyService.java"}
        - analyzeCode: 静态分析代码逻辑。需要参数 code, language。
        - executeCommand: 执行本地系统终端命令。极度重要：如果是阻塞式命令（如 npm run dev），请确保命令在后台运行。需要参数 command。
        - openUrl: 在系统默认浏览器中打开本地文件或网页网址。需要参数 url。
        
        【最后警告】
        永远不要用 Markdown 代码块包裹 Action 的 JSON！直接输出纯文本的 JSON。
        """;

    public AutoCoderAgent(ChatClient.Builder builder, SystemTools systemTools, AstTools astTools, CodeAnalysisTools codeAnalysisTools, CodebaseRagTools codebaseRagTools) {
        this.systemTools = systemTools;
        this.astTools = astTools;
        this.codeAnalysisTools = codeAnalysisTools;
        this.codebaseRagTools = codebaseRagTools;
        // 动态构建 Prompt，因此不在这里设置 defaultSystem
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

    // Setter 注入，避免循环依赖
    public void setAgentRegistry(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到有效的 JSON 格式");
    }

    private String purifyResponse(String responseText) {
        if (responseText == null) return "";
        
        // 查找 "Final Answer:" 标识
        int idx = responseText.lastIndexOf("Final Answer:");
        if (idx != -1) {
            // 如果找到，只截取 "Final Answer:" 后面的部分
            return responseText.substring(idx + "Final Answer:".length()).trim();
        }
        return responseText;
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
            
            // 如果是 AI 的历史回复，进行净化，去掉冗长的思考过程
            if ("assistant".equalsIgnoreCase(msg.getType())) {
                content = purifyResponse(content);
            }
            
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
        log.info("💻 [{}] 开始执行自动编程任务：{}", name, request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        StringBuilder fullResponse = new StringBuilder();
        String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
        String history = memoryContext + "用户的最新需求：" + request.getMessage() + "\n";
        
        // 允许高达 15 次的内部循环，因为构建项目步骤繁多
        for (int i = 0; i < 15; i++) {
            String prompt = AUTO_CODER_PROMPT + "\n\n任务历史与进度：\n" + history;
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
                    if ("executeCommand".equals(actionName)) {
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
                    } else if ("patchFile".equals(actionName)) {
                        String path = String.valueOf(args.get("path"));
                        String searchStr = String.valueOf(args.get("searchStr"));
                        String replaceStr = String.valueOf(args.get("replaceStr"));
                        SystemTools.PatchFileRequest req = new SystemTools.PatchFileRequest(path, searchStr, replaceStr);
                        SystemTools.FileResponse res = systemTools.patchFile().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("replaceJavaFunction".equals(actionName)) {
                        String filePath = String.valueOf(args.get("filePath"));
                        String functionName = String.valueOf(args.get("functionName"));
                        String newFunctionCode = String.valueOf(args.get("newFunctionCode"));
                        AstTools.ReplaceFunctionRequest req = new AstTools.ReplaceFunctionRequest(filePath, functionName, newFunctionCode);
                        AstTools.AstResponse res = astTools.replaceJavaFunction().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("checkSyntax".equals(actionName)) {
                        String filePath = String.valueOf(args.get("filePath"));
                        AstTools.CheckSyntaxRequest req = new AstTools.CheckSyntaxRequest(filePath);
                        AstTools.AstResponse res = astTools.checkSyntax().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("runTests".equals(actionName)) {
                        String dir = String.valueOf(args.get("dir"));
                        String testCommand = String.valueOf(args.get("testCommand"));
                        SystemTools.RunTestRequest req = new SystemTools.RunTestRequest(dir, testCommand);
                        SystemTools.CommandResponse res = systemTools.runTests().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("requestCodeReview".equals(actionName)) {
                        String targetPath = String.valueOf(args.get("filePath"));
                        log.info("🤖 [AutoCoder] 请求 CodeExpertAgent 对文件 [{}] 进行审查", targetPath);
                        SystemTools.FileResponse fileRes = systemTools.readFile().apply(new SystemTools.FileRequest(targetPath));
                        if (!fileRes.success()) {
                            observation = "Error: 无法读取文件发送给审查专家：" + fileRes.message();
                        } else {
                            String reviewPrompt = "我是 AutoCoderAgent，我刚修改了下面的代码，请帮我做一次严格的 Code Review。只需指出 Bug 和不优雅的地方，如果没有问题，请回复 'LGTM' (Looks Good To Me)。代码如下：\n\n" + fileRes.content();
                            Agent codeExpert = agentRegistry.getAgent("CODE_EXPERT");
                            AgentRequest reviewReq = AgentRequest.builder().message(reviewPrompt).build();
                            AgentResponse reviewRes = codeExpert.chat(reviewReq);
                            observation = "审查专家的反馈：\n" + reviewRes.getContent();
                        }
                    } else if ("listFiles".equals(actionName)) {
                        String path = String.valueOf(args.get("path"));
                        SystemTools.ListFilesRequest req = new SystemTools.ListFilesRequest(path);
                        SystemTools.CommandResponse res = systemTools.listFiles().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("searchCode".equals(actionName)) {
                        String dir = String.valueOf(args.get("dir"));
                        String keyword = String.valueOf(args.get("keyword"));
                        SystemTools.SearchCodeRequest req = new SystemTools.SearchCodeRequest(dir, keyword);
                        SystemTools.CommandResponse res = systemTools.searchCode().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("openUrl".equals(actionName)) {
                        String url = String.valueOf(args.get("url"));
                        SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                        SystemTools.CommandResponse res = systemTools.openUrl().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("analyzeCode".equals(actionName)) {
                        String code = String.valueOf(args.get("code"));
                        String language = String.valueOf(args.get("language"));
                        observation = codeAnalysisTools.analyzeCode(code, language);
                    } else if ("indexProject".equals(actionName)) {
                        String dir = String.valueOf(args.get("dir"));
                        CodebaseRagTools.IndexRequest req = new CodebaseRagTools.IndexRequest(dir);
                        CodebaseRagTools.IndexResponse res = codebaseRagTools.indexProject().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else if ("semanticSearchCode".equals(actionName)) {
                        String projectDir = String.valueOf(args.get("projectDir"));
                        String query = String.valueOf(args.get("query"));
                        Integer topK = args.containsKey("topK") ? Integer.parseInt(String.valueOf(args.get("topK"))) : 5;
                        CodebaseRagTools.SearchRequestDto req = new CodebaseRagTools.SearchRequestDto(projectDir, query, topK);
                        CodebaseRagTools.SearchResponse res = codebaseRagTools.semanticSearchCode().apply(req);
                        observation = objectMapper.writeValueAsString(res);
                    } else {
                        observation = "Error: 未知工具 " + actionName;
                    }
                    
                    String obsText = "\nObservation: " + observation + "\n";
                    fullResponse.append(obsText);
                    history += obsText;
                    
                } catch (Exception e) {
                    String errorObs = "\nObservation: 工具执行或解析出错 " + e.getMessage() + "\n";
                    fullResponse.append(errorObs);
                    history += errorObs;
                }
            } else {
                break; // 如果模型没有按规范输出 Action 也没有 Final Answer，提前终止
            }
        }
        
        log.info("✅ [{}] 自动编程任务执行完毕", name);
        
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
        log.info("🌊 [{}] 流式执行自动编程任务", name);
        return Flux.create(emitter -> {
            try {
                String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
                String history = memoryContext + "用户的最新需求：" + request.getMessage() + "\n";
                processAutoCoderLoop(history, emitter, 0);
            } catch (Exception e) {
                emitter.error(e);
            }
        });
    }

    private void processAutoCoderLoop(String history, reactor.core.publisher.FluxSink<String> emitter, int step) {
        // 项目构建较为复杂，给到 15 步的极限推敲深度
        if (step > 15) {
            emitter.next("\nFinal Answer: 任务步骤过于繁杂，达到思考极限，强制终止。请检查目前的构建进度。");
            emitter.complete();
            return;
        }

        String prompt = AUTO_CODER_PROMPT + "\n\n任务历史与进度：\n" + history;
        StringBuilder currentResponse = new StringBuilder();

        chatClient.prompt()
            .user(prompt)
            .stream()
            .content()
            .doOnNext(chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    currentResponse.append(chunk);
                    emitter.next(chunk); // 将思考和行动过程实时推给前端展示
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
                        if ("executeCommand".equals(actionName)) {
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
                        } else if ("patchFile".equals(actionName)) {
                            String path = String.valueOf(args.get("path"));
                            String searchStr = String.valueOf(args.get("searchStr"));
                            String replaceStr = String.valueOf(args.get("replaceStr"));
                            SystemTools.PatchFileRequest req = new SystemTools.PatchFileRequest(path, searchStr, replaceStr);
                            SystemTools.FileResponse res = systemTools.patchFile().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("replaceJavaFunction".equals(actionName)) {
                            String filePath = String.valueOf(args.get("filePath"));
                            String functionName = String.valueOf(args.get("functionName"));
                            String newFunctionCode = String.valueOf(args.get("newFunctionCode"));
                            AstTools.ReplaceFunctionRequest req = new AstTools.ReplaceFunctionRequest(filePath, functionName, newFunctionCode);
                            AstTools.AstResponse res = astTools.replaceJavaFunction().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("checkSyntax".equals(actionName)) {
                            String filePath = String.valueOf(args.get("filePath"));
                            AstTools.CheckSyntaxRequest req = new AstTools.CheckSyntaxRequest(filePath);
                            AstTools.AstResponse res = astTools.checkSyntax().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("runTests".equals(actionName)) {
                            String dir = String.valueOf(args.get("dir"));
                            String testCommand = String.valueOf(args.get("testCommand"));
                            SystemTools.RunTestRequest req = new SystemTools.RunTestRequest(dir, testCommand);
                            SystemTools.CommandResponse res = systemTools.runTests().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("requestCodeReview".equals(actionName)) {
                            String targetPath = String.valueOf(args.get("filePath"));
                            log.info("🤖 [AutoCoder] 请求 CodeExpertAgent 对文件 [{}] 进行审查", targetPath);
                            
                            // 读取文件内容发给 CodeExpertAgent
                            SystemTools.FileResponse fileRes = systemTools.readFile().apply(new SystemTools.FileRequest(targetPath));
                            if (!fileRes.success()) {
                                observation = "Error: 无法读取文件发送给审查专家：" + fileRes.message();
                            } else {
                                String reviewPrompt = "我是 AutoCoderAgent，我刚修改了下面的代码，请帮我做一次严格的 Code Review。只需指出 Bug 和不优雅的地方，如果没有问题，请回复 'LGTM' (Looks Good To Me)。代码如下：\n\n" + fileRes.content();
                                Agent codeExpert = agentRegistry.getAgent("CODE_EXPERT");
                                AgentRequest reviewReq = AgentRequest.builder().message(reviewPrompt).build();
                                AgentResponse reviewRes = codeExpert.chat(reviewReq);
                                observation = "审查专家的反馈：\n" + reviewRes.getContent();
                            }
                        } else if ("listFiles".equals(actionName)) {
                            String path = String.valueOf(args.get("path"));
                            SystemTools.ListFilesRequest req = new SystemTools.ListFilesRequest(path);
                            SystemTools.CommandResponse res = systemTools.listFiles().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("searchCode".equals(actionName)) {
                            String dir = String.valueOf(args.get("dir"));
                            String keyword = String.valueOf(args.get("keyword"));
                            SystemTools.SearchCodeRequest req = new SystemTools.SearchCodeRequest(dir, keyword);
                            SystemTools.CommandResponse res = systemTools.searchCode().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("openUrl".equals(actionName)) {
                            String url = String.valueOf(args.get("url"));
                            SystemTools.UrlRequest req = new SystemTools.UrlRequest(url);
                            SystemTools.CommandResponse res = systemTools.openUrl().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("analyzeCode".equals(actionName)) {
                            String code = String.valueOf(args.get("code"));
                            String language = String.valueOf(args.get("language"));
                            observation = codeAnalysisTools.analyzeCode(code, language);
                        } else if ("indexProject".equals(actionName)) {
                            String dir = String.valueOf(args.get("dir"));
                            CodebaseRagTools.IndexRequest req = new CodebaseRagTools.IndexRequest(dir);
                            CodebaseRagTools.IndexResponse res = codebaseRagTools.indexProject().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else if ("semanticSearchCode".equals(actionName)) {
                            String projectDir = String.valueOf(args.get("projectDir"));
                            String query = String.valueOf(args.get("query"));
                            Integer topK = args.containsKey("topK") ? Integer.parseInt(String.valueOf(args.get("topK"))) : 5;
                            CodebaseRagTools.SearchRequestDto req = new CodebaseRagTools.SearchRequestDto(projectDir, query, topK);
                            CodebaseRagTools.SearchResponse res = codebaseRagTools.semanticSearchCode().apply(req);
                            observation = objectMapper.writeValueAsString(res);
                        } else {
                            observation = "Error: 未知工具 " + actionName;
                        }
                        
                        String obsText = "\nObservation: " + observation + "\n";
                        emitter.next(obsText); // 将结果回传前端展示
                        
                        // 继续下一步构建逻辑
                        processAutoCoderLoop(nextHistory + obsText, emitter, step + 1);
                        
                    } catch (Exception e) {
                        String errorObs = "\nObservation: 工具解析或执行出错 " + e.getMessage() + "\n";
                        emitter.next(errorObs);
                        processAutoCoderLoop(nextHistory + errorObs, emitter, step + 1);
                    }
                } else {
                    emitter.complete(); // 不符合规范则终止
                }
            })
            .doOnError(emitter::error)
            .subscribe();
    }
}
