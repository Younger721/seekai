package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.browser.BrowserTools;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 浏览器控制 Agent
 * 使用 AI 智能控制浏览器自动操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserAgent implements Agent {

    private static final String AGENT_NAME = "BROWSER_AGENT";

    private static final String SYSTEM_PROMPT = """
        你是一个浏览器自动化助手。你可以控制浏览器执行以下操作：

        可用工具:
        - browser_navigate: 打开网页
        - browser_click: 点击元素
        - browser_type: 输入文本
        - browser_get_content: 获取页面内容
        - browser_get_links: 获取页面链接
        - browser_screenshot: 截图
        - browser_scroll: 滚动页面
        - browser_wait: 等待元素
        - browser_execute_script: 执行 JavaScript
        - browser_search: 搜索
        - browser_fill_form: 批量填表

        使用规则:
        1. 先导航到目标页面
        2. 分析页面结构，找到需要的元素选择器
        3. 执行点击、输入等操作
        4. 获取结果并返回给用户

        重要提示:
        - 大多数网站登录需要先填写表单，再点击登录按钮
        - 动态加载的内容需要等待
        - 如果遇到问题，尝试截图查看当前状态
        """;

    private final ChatClient chatClient;
    private final BrowserTools browserTools;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "浏览器自动化助手 - 可以帮你操作网页、填表单、抓数据";
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        String userTask = request.getMessage();

        log.info("🌐 BrowserAgent 处理任务: {}", userTask);

        try {
            // 分析任务并生成浏览器操作
            String plan = analyzeTask(userTask);

            // 执行操作
            String result = executePlan(plan, userTask);

            return AgentResponse.builder()
                    .success(true)
                    .message(result)
                    .agentName(AGENT_NAME)
                    .build();

        } catch (Exception e) {
            log.error("❌ 浏览器任务失败: {}", e.getMessage());
            return AgentResponse.builder()
                    .success(false)
                    .message("任务执行失败: " + e.getMessage())
                    .agentName(AGENT_NAME)
                    .build();
        }
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        // 流式处理类似 chat，但逐步返回
        return Flux.just(chat(request).getMessage());
    }

    /**
     * 分析用户任务
     */
    private String analyzeTask(String task) {
        String prompt = String.format("""
            分析以下浏览器自动化任务，给出具体的操作步骤。

            任务: %s

            请用以下格式回答:
            1. 首先导航到 [URL]
            2. 然后 [操作1]
            3. 接着 [操作2]
            ...

            仅返回操作步骤，不要包含其他内容。
            """, task);

        String response = chatClient.prompt(prompt).call().content();
        log.debug("📋 任务分析: {}", response);

        return response;
    }

    /**
     * 执行计划
     */
    private String executePlan(String plan, String originalTask) {
        StringBuilder results = new StringBuilder();

        // 智能任务处理
        String lowerTask = originalTask.toLowerCase();

        // 处理搜索任务
        if (lowerTask.contains("搜索") || lowerTask.contains("search")) {
            String keyword = extractKeyword(originalTask);
            String engine = lowerTask.contains("google") ? "https://www.google.com" : "https://www.baidu.com";

            results.append("🔍 搜索: ").append(keyword).append("\n\n");
            results.append(browserTools.search(engine, keyword));
        }
        // 处理登录任务
        else if (lowerTask.contains("登录") || lowerTask.contains("login")) {
            results.append(handleLogin(originalTask));
        }
        // 处理获取内容
        else if (lowerTask.contains("获取") || lowerTask.contains("打开") || lowerTask.contains("抓取")) {
            String url = extractUrl(originalTask);
            if (url != null) {
                results.append(browserTools.navigate(url)).append("\n\n");
                results.append(browserTools.getContent(null));
            } else {
                results.append("❌ 无法识别目标 URL");
            }
        }
        // 处理截图
        else if (lowerTask.contains("截图") || lowerTask.contains("screenshot")) {
            results.append(browserTools.screenshot(null));
        }
        else {
            // 默认：尝试提取 URL 并导航
            String url = extractUrl(originalTask);
            if (url != null) {
                results.append(browserTools.navigate(url)).append("\n\n");
                results.append(browserTools.getContent(null));
            } else {
                results.append("❌ 无法理解任务。请明确告诉我你想做什么，比如:\n");
                results.append("- 打开某个网站\n");
                results.append("- 搜索某个内容\n");
                results.append("- 登录某个网站\n");
            }
        }

        return results.toString();
    }

    /**
     * 处理登录任务
     */
    private String handleLogin(String task) {
        // 简单检测常见网站
        if (task.contains("github")) {
            return browserTools.login(
                    "#login_field",
                    "#password",
                    "[type=submit]",
                    "your-username",
                    "your-password"
            );
        } else if (task.contains("qq") || task.contains("邮箱")) {
            return browserTools.login(
                    "#u",
                    "#p",
                    "[class=btn]'",
                    "your-account",
                    "your-password"
            );
        } else {
            return "❌ 请提供具体的登录信息（用户名、密码输入框的选择器）";
        }
    }

    /**
     * 提取关键词
     */
    private String extractKeyword(String task) {
        // 简单提取搜索关键词
        String[] patterns = {"搜索(.*?)$", "search(.*?)$", "找(.*?)$"};
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(task);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return task;
    }

    /**
     * 从任务中提取 URL
     */
    private String extractUrl(String task) {
        // 提取 URL
        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile("https?://[^\\s]+");
        java.util.regex.Matcher m = urlPattern.matcher(task);

        if (m.find()) {
            return m.group();
        }

        // 常见网站处理
        if (task.contains("github")) return "https://github.com";
        if (task.contains("百度")) return "https://www.baidu.com";
        if (task.contains("谷歌")) return "https://www.google.com";
        if (task.contains("bilibili")) return "https://www.bilibili.com";
        if (task.contains("知乎")) return "https://www.zhihu.com";

        return null;
    }
}