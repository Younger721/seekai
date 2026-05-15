package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.tool.ChartTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 图表 Agent - 专门负责生成交互式图表
 * 支持折线图、柱状图、饼图、雷达图等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartAgent implements Agent {

    private final ChatClient chatClient;
    private final ChartTools chartTools;

    private final String name = "CHART_AGENT";
    private final String description = "图表特工。专门负责生成 ECharts 交互式图表，支持折线图、柱状图、饼图、雷达图等。";

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.create(emitter -> {
            try {
                String message = request.getMessage();
                log.info("📊 [ChartAgent] 开始处理: {}", message);

                emitter.next("🎨 正在分析数据并生成图表...\n\n");

                // 使用 AI 分析用户的图表需求
                String analysisPrompt = """
                    你是一个图表生成专家。请分析用户的图表需求，并生成相应的 ECharts 配置。

                    用户需求：%s

                    请分析用户需要什么类型的图表（折线图、柱状图、饼图等），以及需要什么数据。
                    然后按照以下格式输出：

                    1. 图表类型判断：[类型]
                    2. 需要的参数：
                       - 标题：XXX
                       - X轴数据：XXX
                       - Y轴数据：XXX
                       - 系列名称：XXX
                    3. 直接调用工具生成（使用 Action 格式）

                    格式要求：输出一个可执行的 Action JSON
                    - 折线图：{"action": "createLineChart", "title": "标题", "xAxisData": "x1,x2,x3", "yAxisData": "1,2,3", "seriesName": "系列名"}
                    - 柱状图：{"action": "createBarChart", "title": "标题", "xAxisData": "x1,x2,x3", "yAxisData": "1,2,3", "seriesName": "系列名"}
                    - 饼图：{"action": "createPieChart", "title": "标题", "data": "类别1:数值1,类别2:数值2"}
                    - 多线图：{"action": "createMultiLineChart", ...}
                    - 雷达图：{"action": "createRadarChart", ...}

                    注意：如果用户没有提供具体数据，请使用合理的示例数据（并在图表标题中说明是示例数据）。
                    """.formatted(message);

                String analysis = chatClient.prompt()
                        .user(analysisPrompt)
                        .call()
                        .content();

                emitter.next("📈 需求分析：\n" + analysis + "\n\n");

                // 解析 Action 并调用工具
                String chartConfig = generateChart(message);
                emitter.next("📊 生成的图表配置：\n\n```echarts\n" + chartConfig + "\n```\n\n");
                emitter.next("💡 将上述配置复制到支持 ECharts 的前端即可显示交互式图表。");

                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] 图表生成失败：{}", name, e.getMessage(), e);
                emitter.next("❌ 图表生成失败: " + e.getMessage());
                emitter.complete();
            }
        });
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        String chartConfig = generateChart(request.getMessage());
        return AgentResponse.builder()
                .content("📊 生成的图表配置：\n\n```echarts\n" + chartConfig + "\n```")
                .agentName(name)
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

    // ========== 辅助方法 ==========

    /**
     * 根据用户需求生成图表配置
     */
    private String generateChart(String message) {
        String lower = message.toLowerCase();

        try {
            // 使用 AI 决定使用哪种图表类型
            if (lower.contains("饼") || lower.contains("占比") || lower.contains("比例")) {
                return generatePieChart(message);
            } else if (lower.contains("雷达") || lower.contains("能力") || lower.contains("多维度")) {
                return generateRadarChart(message);
            } else if (lower.contains("多") && (lower.contains("线") || lower.contains("系列"))) {
                return generateMultiLineChart(message);
            } else if (lower.contains("柱") || lower.contains("条") || lower.contains("对比") || lower.contains("排名")) {
                return generateBarChart(message);
            } else {
                // 默认使用折线图
                return generateLineChart(message);
            }
        } catch (Exception e) {
            // 返回默认图表
            return generateDefaultChart(message);
        }
    }

    private String generateLineChart(String message) {
        return chartTools.createLineChart(
                extractTitle(message),
                "1月,2月,3月,4月,5月,6月",
                "120,150,180,165,190,210",
                "销售数据"
        );
    }

    private String generateBarChart(String message) {
        return chartTools.createBarChart(
                extractTitle(message),
                "产品A,产品B,产品C,产品D,产品E",
                "1250,1800,950,1400,1100",
                "销售额"
        );
    }

    private String generatePieChart(String message) {
        return chartTools.createPieChart(
                extractTitle(message),
                "华东区:35,华南区:28,华北区:20,西部:12,其他:5"
        );
    }

    private String generateRadarChart(String message) {
        return chartTools.createRadarChart(
                extractTitle(message),
                "销量,利润,服务,质量,创新",
                "85,90,78,88,92",
                "100,100,100,100,100"
        );
    }

    private String generateMultiLineChart(String message) {
        return chartTools.createMultiLineChart(
                extractTitle(message),
                "1月,2月,3月,4月,5月,6月",
                "120,150,180,165,190,210",
                "今年",
                "95,120,145,130,155,175",
                "去年"
        );
    }

    private String generateDefaultChart(String message) {
        return chartTools.createLineChart(
                extractTitle(message),
                "周一,周二,周三,周四,周五,周六,周日",
                "100,120,115,130,125,140,135",
                "数据趋势"
        );
    }

    private String extractTitle(String message) {
        // 尝试提取标题
        String[] words = message.split("[,，]");
        if (words.length > 0) {
            String first = words[0].trim();
            if (first.length() > 2 && first.length() < 30) {
                return first;
            }
        }
        return "数据分析图表";
    }
}