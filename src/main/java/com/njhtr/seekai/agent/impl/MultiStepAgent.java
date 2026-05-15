package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.tool.ComputerControlTools;
import com.njhtr.seekai.tool.WebCrawlerTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多步骤执行 Agent - 支持复杂任务分解与顺序执行
 * 能力：任务规划、步骤执行、结果传递、上下文理解
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiStepAgent implements Agent {

    private final ChatClient chatClient;
    private final ComputerControlTools computerControl;
    private final WebCrawlerTools webCrawlerTools;

    private final String name = "MULTI_STEP_AGENT";
    private final String description = "多步骤任务特工。擅长将复杂任务分解为多个步骤顺序执行，如：打开浏览器→搜索→读取数据→分析→给出建议。";

    // 缓存各城市的天气数据，用于对比
    private final Map<String, String> weatherCache = new HashMap<>();
    private final List<String> executionHistory = new ArrayList<>();

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.create(emitter -> {
            try {
                String message = request.getMessage();
                log.info("📋 [MultiStepAgent] 开始处理: {}", message);

                // Step 1: 任务规划 - 分解任务为步骤
                emitter.next("🎯 开始分析任务...\n\n");
                List<TaskStep> steps = planSteps(message);

                emitter.next("📝 任务计划：\n");
                for (int i = 0; i < steps.size(); i++) {
                    TaskStep step = steps.get(i);
                    emitter.next("   " + (i + 1) + ". [" + step.type + "] " + step.description + "\n");
                }
                emitter.next("\n");

                // Step 2: 逐步执行
                executionHistory.clear();
                weatherCache.clear();

                Map<String, Object> context = new HashMap<>();

                for (int i = 0; i < steps.size(); i++) {
                    TaskStep step = steps.get(i);
                    emitter.next("🔄 执行步骤 " + (i + 1) + "/" + steps.size() + ": " + step.description + "\n");

                    Object result = executeStep(step, context, emitter);

                    // 保存结果到上下文，供后续步骤使用
                    if (result != null) {
                        context.put(step.id, result);
                        if (step.type.equals("weather")) {
                            String city = (String) step.params.get("city");
                            if (city != null) {
                                weatherCache.put(city, result.toString());
                            }
                        }
                    }

                    executionHistory.add("步骤" + (i + 1) + ": " + step.description + " → " + (result != null ? "成功" : "失败"));

                    emitter.next("\n");
                }

                // Step 3: 汇总结果并给出最终建议
                emitter.next("🤔 正在整合所有信息...\n\n");
                String finalAnswer = generateFinalAnswer(message, context);

                emitter.next("📊 最终结果：\n\n" + finalAnswer);

                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] 多步骤执行失败：{}", name, e.getMessage(), e);
                emitter.next("❌ 执行失败: " + e.getMessage());
                emitter.complete();
            }
        });
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        return AgentResponse.builder()
                .content("多步骤任务 Agent 更适合使用流式模式运行以展示完整的执行过程。请使用流式接口。")
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

    // ========== 任务规划 ==========

    /**
     * 规划任务步骤
     */
    private List<TaskStep> planSteps(String userMessage) {
        try {
            // 使用 AI 分析并规划步骤
            String prompt = """
                分析用户需求，将其分解为可执行的步骤。

                用户需求：%s

                可用的工具类型：
                - browser: 打开浏览器或搜索
                - fetch: 直接获取网页内容
                - analyze: 分析数据
                - compare: 对比分析
                - advice: 给出建议

                请按以下 JSON 格式输出步骤规划：
                [
                  {"id": "step1", "type": "browser/fetch/analyze/compare/advice", "description": "步骤描述", "params": {"key": "value"}},
                  ...
                ]

                注意：
                1. 步骤要符合逻辑顺序
                2. 如果需要获取数据，必须先获取再分析
                3. 对比需要先分别获取数据
                4. 建议步骤必须基于已有数据
                5. 直接输出 JSON，不要其他内容
                """.formatted(userMessage);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析 JSON
            List<TaskStep> steps = parseSteps(response);
            if (!steps.isEmpty()) {
                return steps;
            }

        } catch (Exception e) {
            log.warn("AI 规划失败，使用默认规划: {}", e.getMessage());
        }

        // 回退：简单任务规划
        return defaultPlan(userMessage);
    }

    private List<TaskStep> parseSteps(String response) {
        List<TaskStep> steps = new ArrayList<>();

        try {
            // 提取 JSON 数组
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start < 0 || end < 0) return steps;

            String json = response.substring(start, end + 1);

            // 简单解析（使用正则）
            Pattern pattern = Pattern.compile("\\{\"id\":\\s*\"([^\"]+)\",\\s*\"type\":\\s*\"([^\"]+)\",\\s*\"description\":\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                TaskStep step = new TaskStep();
                step.id = matcher.group(1);
                step.type = matcher.group(2);
                step.description = matcher.group(3);
                step.params = new HashMap<>();
                steps.add(step);
            }

        } catch (Exception e) {
            log.warn("解析步骤失败: {}", e.getMessage());
        }

        return steps;
    }

    /**
     * 默认任务规划（基于关键词匹配）
     */
    private List<TaskStep> defaultPlan(String message) {
        List<TaskStep> steps = new ArrayList<>();
        String lower = message.toLowerCase();

        // 天气查询场景
        if (lower.contains("天气")) {
            // 提取城市
            List<String> cities = extractCities(message);

            // 步骤1: 获取各地天气
            for (String city : cities) {
                TaskStep step = new TaskStep();
                step.id = "weather_" + city;
                step.type = "fetch";
                step.description = "获取" + city + "的天气信息";
                step.params = Map.of("city", city, "type", "weather");
                steps.add(step);
            }

            // 步骤2: 对比分析（如果有多个城市）
            if (cities.size() > 1) {
                TaskStep step = new TaskStep();
                step.id = "compare_weather";
                step.type = "compare";
                step.description = "对比" + String.join("和", cities) + "的天气";
                step.params = Map.of("cities", cities);
                steps.add(step);
            }

            // 步骤3: 出行建议（如果有相关关键词）
            if (lower.contains("去") || lower.contains("建议") || lower.contains("明天")) {
                TaskStep step = new TaskStep();
                step.id = "travel_advice";
                step.type = "advice";
                step.description = "给出出行建议";
                step.params = Map.of("cities", cities);
                steps.add(step);
            }
        } else {
            // 默认：直接搜索
            TaskStep step = new TaskStep();
            step.id = "search";
            step.type = "browser";
            step.description = "搜索相关信息";
            step.params = Map.of("keyword", message);
            steps.add(step);
        }

        return steps;
    }

    private List<String> extractCities(String message) {
        List<String> cities = new ArrayList<>();

        // 已知城市列表
        String[] knownCities = {"北京", "上海", "广州", "深圳", "杭州", "南京", "成都", "重庆",
                "武汉", "西安", "天津", "苏州", "郑州", "长沙", "沈阳", "青岛", "济南", "大连", "哈尔滨"};

        for (String city : knownCities) {
            if (message.contains(city)) {
                cities.add(city);
            }
        }

        // 默认添加北京
        if (cities.isEmpty()) {
            cities.add("北京");
        }

        return cities;
    }

    // ========== 步骤执行 ==========

    /**
     * 执行单个步骤
     */
    private Object executeStep(TaskStep step, Map<String, Object> context, reactor.core.publisher.FluxSink<String> emitter) {
        try {
            switch (step.type) {
                case "browser" -> {
                    String keyword = (String) step.params.getOrDefault("keyword", step.description);
                    String url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(keyword, "UTF-8");
                    String result = computerControl.openBrowser(url);
                    emitter.next("   ✅ 已打开浏览器搜索: " + keyword + "\n");
                    return result;
                }

                case "fetch" -> {
                    String fetchType = (String) step.params.get("type");
                    if ("weather".equals(fetchType)) {
                        String city = (String) step.params.get("city");
                        String result = fetchWeather(city, emitter);
                        return result;
                    } else {
                        String keyword = (String) step.params.getOrDefault("keyword", step.description);
                        String url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(keyword, "UTF-8");
                        return webCrawlerTools.crawlWebPage(url);
                    }
                }

                case "compare" -> {
                    // 对比已经在 context 中的数据
                    return "对比分析完成";
                }

                case "analyze" -> {
                    return "分析完成";
                }

                case "advice" -> {
                    return generateTravelAdvice(context, emitter);
                }

                default -> {
                    emitter.next("   ⚠️ 未知步骤类型: " + step.type + "\n");
                    return null;
                }
            }
        } catch (Exception e) {
            emitter.next("   ❌ 步骤执行失败: " + e.getMessage() + "\n");
            return null;
        }
    }

    /**
     * 获取天气信息
     */
    private String fetchWeather(String city, reactor.core.publisher.FluxSink<String> emitter) {
        try {
            // 方式1: 直接抓取天气网站
            String[] weatherSites = {
                    "https://www.weather.com.cn/weather/" + getCityCode(city) + ".shtml",
                    "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(city + "天气", "UTF-8")
            };

            String weatherData = null;
            for (String site : weatherSites) {
                try {
                    weatherData = webCrawlerTools.crawlWebPage(site);
                    if (weatherData.contains("success") && weatherData.contains("true")) {
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (weatherData != null && weatherData.contains("\"success\": true")) {
                emitter.next("   ✅ 已获取" + city + "天气数据\n");
                return weatherData;
            }

            // 方式2: 尝试搜索获取
            String searchUrl = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(city + "天气 温度", "UTF-8");
            String searchResult = webCrawlerTools.crawlWebPage(searchUrl);

            emitter.next("   ✅ 已获取" + city +"天气搜索结果\n");
            return searchResult;

        } catch (Exception e) {
            emitter.next("   ⚠️ 获取天气失败: " + e.getMessage() + "\n");
            return "获取失败: " + e.getMessage();
        }
    }

    private String getCityCode(String city) {
        // 简单映射
        Map<String, String> cityCodes = Map.of(
                "北京", "101010100",
                "上海", "101020100",
                "广州", "101280101",
                "深圳", "101280601",
                "杭州", "101210101",
                "成都", "101270101"
        );
        return cityCodes.getOrDefault(city, "101010100");
    }

    /**
     * 生成出行建议
     */
    private String generateTravelAdvice(Map<String, Object> context, reactor.core.publisher.FluxSink<String> emitter) {
        try {
            // 收集所有天气数据
            StringBuilder weatherInfo = new StringBuilder();
            weatherInfo.append("已获取的天气数据：\n");
            for (Map.Entry<String, String> entry : weatherCache.entrySet()) {
                weatherInfo.append("- ").append(entry.getKey()).append("\n");
            }

            String prompt = """
                根据用户的出行需求和已获取的天气数据，给出专业的出行建议。

                用户需求：%s

                %s

                请给出：
                1. 目的地天气概况
                2. 出行建议（穿衣、携带物品等）
                3. 注意事项

                注意：
                - 如果天气数据不足，请基于一般认知给出建议
                - 建议要实用、具体
                - 用友好的语气
                """.formatted(executionHistory.get(0), weatherInfo.toString());

            String advice = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            emitter.next("   💡 " + advice + "\n");
            return advice;

        } catch (Exception e) {
            return "建议生成失败: " + e.getMessage();
        }
    }

    // ========== 最终结果生成 ==========

    private String generateFinalAnswer(String originalRequest, Map<String, Object> context) {
        try {
            // 构建上下文摘要
            StringBuilder summary = new StringBuilder();
            summary.append("执行摘要：\n");
            for (String history : executionHistory) {
                summary.append("- ").append(history).append("\n");
            }
            summary.append("\n天气数据：\n");
            for (Map.Entry<String, String> entry : weatherCache.entrySet()) {
                summary.append("- ").append(entry.getKey()).append(": 已获取\n");
            }

            return summary.toString();

        } catch (Exception e) {
            return "无法生成最终结果";
        }
    }

    // ========== 数据类 ==========

    private static class TaskStep {
        String id;
        String type;
        String description;
        Map<String, Object> params = new HashMap<>();
    }
}