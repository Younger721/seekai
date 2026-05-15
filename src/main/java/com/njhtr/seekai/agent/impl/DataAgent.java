package com.njhtr.seekai.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.tool.DatabaseTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 商业智能数据分析 Agent (NL2SQL + ECharts)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAgent implements Agent {

    private final ChatClient chatClient;
    private final DatabaseTools databaseTools;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String name = "DATA_EXPERT";
    private final String description = "数据分析与商业智能特工。能够直连数据库，通过 NL2SQL 将用户的自然语言查询转换为 SQL，执行并提取业务数据，最后生成专业的数据分析报告和 ECharts 动态图表配置。";

    private String buildHistoryPrompt(List<MessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【历史上下文】：\n");
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            MessageDTO msg = messages.get(i);
            sb.append(msg.getType().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
        }
        sb.append("--------------------\n");
        return sb.toString();
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到有效的 JSON 格式");
    }

    private void processReActLoop(String promptContext, reactor.core.publisher.FluxSink<String> emitter, int loopCount) {
        if (loopCount >= 8) {
            emitter.next("\n\nFinal Answer: (强制结束) 经过多次数据分析，我无法得出最终结论，请提供更具体的需求。");
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
                if ("getDatabaseSchema".equals(actionName)) {
                    emitter.next("\n> 📊 正在拉取业务数据库表结构...\n");
                    DatabaseTools.SchemaResponse res = databaseTools.getDatabaseSchema().apply(new DatabaseTools.SchemaRequest());
                    observation = objectMapper.writeValueAsString(res);
                } else if ("executeSQL".equals(actionName)) {
                    String sql = String.valueOf(args.get("sql"));
                    emitter.next("\n> 💻 正在执行数据查询 SQL: " + sql + "\n");
                    DatabaseTools.ExecuteSqlResponse res = databaseTools.executeSQL().apply(new DatabaseTools.ExecuteSqlRequest(sql));
                    observation = objectMapper.writeValueAsString(res);
                } else {
                    observation = "Error: 未知工具 " + actionName;
                }

                String obsText = "\nObservation: " + observation + "\n";
                promptContext += "\n" + rawResponse + obsText;
                
                // 递归继续思考
                processReActLoop(promptContext, emitter, loopCount + 1);
            } else {
                emitter.next("\n\nFinal Answer: " + rawResponse);
                emitter.complete();
            }

        } catch (Exception e) {
            log.error("DataAgent 内部循环异常", e);
            emitter.next("\n[系统异常]: " + e.getMessage());
            emitter.complete();
        }
    }

    @Override
    public reactor.core.publisher.Flux<String> stream(AgentRequest request) {
        return Flux.create(emitter -> {
            try {
                String memoryContext = buildHistoryPrompt(request.getHistoryMessages());
                
                String initialPrompt = """
                    你是企业级商业智能(BI)与数据分析特工 (DataAgent)。
                    你具备直连业务数据库查询数据的能力，并能为用户生成专业的数据分析报告和可视化图表。
                    
                    【可用工具】
                    - getDatabaseSchema: 读取当前数据库的所有表结构和字段信息。当用户提出分析需求时，你**必须首先调用此工具**了解表结构，绝不能凭空猜测字段名！不需要参数。
                      例如：Action: {"action": "getDatabaseSchema"}
                    - executeSQL: 执行基于表结构生成的 MySQL 查询语句。只允许执行 SELECT！你必须写出高效的聚合/统计 SQL 获取真实数据。需要参数 sql。
                      例如：Action: {"action": "executeSQL", "sql": "SELECT DATE_FORMAT(create_time, '%%Y-%%m') as month, COUNT(*) as cnt FROM users GROUP BY month"}
                      
                    【工作流要求 (ReAct)】
                    你需要严格按照以下格式进行思考和行动：
                    
                    Thought: 用户想看什么数据？我应该先去查表结构。
                    Action: {"action": "工具名称", "参数名": "参数值"}
                    (等待系统返回 Observation)
                    
                    收到 Observation 后：
                    Critique: 我拿到表结构了吗？如果拿到了，我应该写什么样的 SQL 去查数据？如果 SQL 执行报错了，我必须仔细阅读报错信息并修正 SQL 重新执行！
                    
                    当获取到真实的数据后：
                    Thought: 我已经拿到了分析所需的数据。
                    Action: 不要再输出 Action。
                    Final Answer: 为用户撰写分析报告。注意：只有在 Final Answer 之后的文本才会被展示给用户作为最终结果，所以你必须把图表配置和分析结论都写在 Final Answer 之后。绝对不要在 Final Answer 之前生成 ```echarts``` 或者 ```sql``` 代码块！
                    
                    【高级能力：图表生成】
                    如果在获取数据后，你认为该数据适合用图表展示（如折线图看趋势、饼图看占比、柱状图看对比），或者用户明确要求画图，请在 Final Answer 的最后，用特殊的代码块包裹一段标准的 ECharts JSON 配置。
                    格式如下：
                    ```echarts
                    {
                      "title": {"text": "图表标题"},
                      "tooltip": {},
                      "xAxis": {"type": "category", "data": ["周一", "周二"]},
                      "yAxis": {"type": "value"},
                      "series": [{"data": [120, 200], "type": "line"}]
                    }
                    ```
                    注意：JSON 配置里的数据(data)必须是你刚刚通过 `executeSQL` 真实查出来的！绝不要编造假数据。
                    
                    【历史上下文】
                    %s
                    
                    【用户分析需求】
                    %s
                    """.formatted(memoryContext, request.getMessage());
                
                processReActLoop(initialPrompt, emitter, 0);
                    
            } catch (Exception e) {
                log.error("[{}] 流式处理失败：{}", name, e.getMessage(), e);
                emitter.error(e);
            }
        });
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
        // 对于纯 API 调用，暂时用降级回复代替（推荐优先使用 stream 展现推演过程）
        return AgentResponse.builder()
            .content("DataAgent 更适合使用 Stream 流式模式运行以展现查库和分析全过程。")
            .agentName(name)
            .build();
    }
}