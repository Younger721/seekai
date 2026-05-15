package com.njhtr.seekai.browser;

import com.njhtr.seekai.browser.BrowserSession.SessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 浏览器自动化工具 - 供 AI Agent 调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserTools {

    private final BrowserSession browserSession;

    // 默认会话 ID
    private static final String DEFAULT_SESSION = "default";

    // ========== 基础操作 ==========

    @Tool(name = "browser_navigate", description = "导航到指定网页 URL。可以打开任意网站，获取页面内容。")
    public String navigate(
            @ToolParam(description = "要打开的网页 URL，以 http:// 或 https:// 开头") String url
    ) {
        log.info("🧭 导航: {}", url);
        return browserSession.navigate(DEFAULT_SESSION, url);
    }

    @Tool(name = "browser_click", description = "点击页面上的元素。通过 CSS 选择器指定要点击的元素，如按钮、链接等。")
    public String click(
            @ToolParam(description = "CSS 选择器，如 #submit-btn, .login-button, a[href]") String selector
    ) {
        log.info("👆 点击: {}", selector);
        return browserSession.click(DEFAULT_SESSION, selector);
    }

    @Tool(name = "browser_type", description = "在输入框中输入文本。适用于填写表单、搜索框等。")
    public String type(
            @ToolParam(description = "输入框的 CSS 选择器，如 #username, input[name=email]") String selector,
            @ToolParam(description = "要输入的文本内容") String text
    ) {
        log.info("⌨️ 输入: {} -> {}", selector, text);
        return browserSession.type(DEFAULT_SESSION, selector, text);
    }

    @Tool(name = "browser_submit", description = "提交表单。点击提交按钮或直接提交表单。")
    public String submit(
            @ToolParam(description = "提交按钮或表单的 CSS 选择器") String selector
    ) {
        return browserSession.click(DEFAULT_SESSION, selector);
    }

    // ========== 内容获取 ==========

    @Tool(name = "browser_get_content", description = "获取页面内容。可以获取整个页面或指定元素的内容。")
    public String getContent(
            @ToolParam(description = "CSS 选择器，为空则获取整个页面内容", required = false) String selector
    ) {
        return browserSession.getContent(DEFAULT_SESSION, selector);
    }

    @Tool(name = "browser_get_links", description = "获取页面所有链接。返回页面中所有可点击的链接 URL。")
    public String getLinks() {
        return browserSession.getLinks(DEFAULT_SESSION);
    }

    @Tool(name = "browser_screenshot", description = "截取当前页面截图。保存为图片文件。")
    public String screenshot(
            @ToolParam(description = "截图保存路径，为空则使用默认路径", required = false) String path
    ) {
        return browserSession.screenshot(DEFAULT_SESSION, path);
    }

    // ========== 交互操作 ==========

    @Tool(name = "browser_scroll", description = "滚动页面。向上或向下滚动指定像素。")
    public String scroll(
            @ToolParam(description = "滚动像素数，正数向下，负数向上") int pixels
    ) {
        return browserSession.scroll(DEFAULT_SESSION, pixels);
    }

    @Tool(name = "browser_wait", description = "等待元素出现。等待指定选择器的元素加载完成。")
    public String waitFor(
            @ToolParam(description = "要等待的 CSS 选择器") String selector,
            @ToolParam(description = "超时时间(毫秒)") int timeout
    ) {
        return browserSession.waitFor(DEFAULT_SESSION, selector, timeout);
    }

    @Tool(name = "browser_execute_script", description = "执行 JavaScript 代码。可以在页面中执行任意 JS 操作。")
    public String executeScript(
            @ToolParam(description = "要执行的 JavaScript 代码") String script
    ) {
        return browserSession.executeScript(DEFAULT_SESSION, script);
    }

    // ========== 会话管理 ==========

    @Tool(name = "browser_status", description = "获取当前浏览器状态。包括当前 URL、页面标题等。")
    public String getStatus() {
        var status = browserSession.getSessionStatus(DEFAULT_SESSION);
        return String.format("""
            当前会话状态:
            - URL: %s
            - 标题: %s
            - 最近活动: %s
            """,
            status.get("url"),
            status.get("title"),
            status.get("lastActive")
        );
    }

    @Tool(name = "browser_fill_form", description = "批量填写表单。一次性填写多个表单字段。")
    public String fillForm(
            @ToolParam(description = "表单数据，JSON 格式，如 {\"#email\": \"test@example.com\", \"#password\": \"123456\"}") String formData
    ) {
        try {
            // 解析 JSON 格式的表单数据
            Map<String, String> data = parseJsonToMap(formData);
            return browserSession.fillForm(DEFAULT_SESSION, data);
        } catch (Exception e) {
            return "❌ 解析表单数据失败: " + e.getMessage();
        }
    }

    // ========== 便捷方法 ==========

    /**
     * 搜索并获取结果 (组合操作)
     */
    @Tool(name = "browser_search", description = "在搜索引擎中搜索关键词并获取结果。")
    public String search(
            @ToolParam(description = "搜索引擎 URL，如 https://www.google.com, https://www.baidu.com") String searchEngine,
            @ToolParam(description = "搜索关键词") String keyword
    ) {
        String encodeKeyword = keyword.replace(" ", "+");
        String searchUrl = searchEngine.contains("baidu")
            ? searchEngine + "/s?wd=" + encodeKeyword
            : searchEngine + "/search?q=" + encodeKeyword;

        String navResult = browserSession.navigate(DEFAULT_SESSION, searchUrl);
        return navResult + "\n\n" + browserSession.getContent(DEFAULT_SESSION, null);
    }

    /**
     * 登录流程 (组合操作)
     */
    @Tool(name = "browser_login", description = "执行登录操作。填入用户名密码并点击登录按钮。")
    public String login(
            @ToolParam(description = "用户名输入框的 CSS 选择器") String usernameSelector,
            @ToolParam(description = "密码输入框的 CSS 选择器") String passwordSelector,
            @ToolParam(description = "登录按钮的 CSS 选择器") String buttonSelector,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password
    ) {
        StringBuilder result = new StringBuilder();

        result.append(browserSession.type(DEFAULT_SESSION, usernameSelector, username)).append("\n");
        result.append(browserSession.type(DEFAULT_SESSION, passwordSelector, password)).append("\n");
        result.append(browserSession.click(DEFAULT_SESSION, buttonSelector)).append("\n");

        // 等待页面跳转
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        result.append("\n当前 URL: ").append(browserSession.getSessionStatus(DEFAULT_SESSION).get("url"));

        return result.toString();
    }

    /**
     * 简单 JSON 解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseJsonToMap(String json) {
        // 简单的 JSON 解析 (实际生产环境建议使用 Jackson/Gson)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        Map<String, String> result = new java.util.HashMap<>();

        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }

        return result;
    }
}