package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.GraphicsEnvironment;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 计算机控制工具 - 实现浏览器控制、键盘鼠标操作
 * 支持：打开应用、输入文字、点击、截图、搜索等
 */
@Slf4j
@Component
public class ComputerControlTools {

    private Robot robot;
    private Clipboard clipboard;

    public ComputerControlTools() {
        try {
            // 检查是否在无头模式
            if (GraphicsEnvironment.isHeadless()) {
                log.warn("⚠️ 当前运行在无头模式，部分功能可能不可用");
            } else {
                this.robot = new Robot();
                robot.setAutoDelay(100);
                this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            }
        } catch (AWTException e) {
            log.error("❌ 初始化 Robot 失败: {}", e.getMessage());
        } catch (HeadlessException e) {
            log.warn("⚠️ 无法初始化图形环境（HeadlessException）: {}", e.getMessage());
        }
    }

    // ========== 浏览器操作 ==========

    /**
     * 打开浏览器并访问网址
     *
     * @param url 要访问的网址
     * @return 操作结果
     */
    @Tool(description = "打开系统默认浏览器并访问指定网址。")
    public String openBrowser(
            @ToolParam(description = "要访问的网址，如 https://www.baidu.com") String url) {

        log.info("🌐 打开浏览器: {}", url);

        try {
            // 验证 URL 格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // 使用系统命令打开浏览器
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                // Linux
                pb = new ProcessBuilder("xdg-open", url);
            }

            pb.start();

            return toJson(Map.of(
                    "success", true,
                    "action", "打开浏览器",
                    "url", url,
                    "message", "✅ 已打开浏览器访问: " + url
            ));

        } catch (Exception e) {
            log.error("❌ 打开浏览器失败: {}", e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 搜索功能（在默认搜索引擎搜索）
     *
     * @param keyword 搜索关键词
     * @param engine  搜索引擎（baidu/google/bing）
     * @return 操作结果
     */
    @Tool(description = "打开浏览器并进行搜索。支持百度、Google、Bing。")
    public String searchOnline(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "搜索引擎：baidu/google/bing，默认baidu") String engine) {

        log.info("🔍 搜索: {} (引擎: {})", keyword, engine);

        try {
            String searchUrl = buildSearchUrl(keyword, engine);
            return openBrowser(searchUrl);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    private String buildSearchUrl(String keyword, String engine) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
            return switch (engine.toLowerCase()) {
                case "google" -> "https://www.google.com/search?q=" + encoded;
                case "bing" -> "https://www.bing.com/search?q=" + encoded;
                default -> "https://www.baidu.com/s?wd=" + encoded;
            };
        } catch (Exception e) {
            return "https://www.baidu.com/s?wd=" + keyword;
        }
    }

    // ========== 键盘操作 ==========

    /**
     * 模拟按键
     *
     * @param key 按键名称：enter/esc/ctrl/v/c/a 等
     * @return 操作结果
     */
    @Tool(description = "模拟键盘按键。支持：enter, esc, ctrl, alt, tab, space, 方向键等。")
    public String pressKey(
            @ToolParam(description = "按键名称，如 enter, esc, ctrl, tab, space, a, f1 等") String key) {

        log.info("⌨️ 按键: {}", key);

        if (robot == null) {
            return toJson(Map.of(
                    "success", false,
                    "error", "Robot 未初始化（可能运行在无头模式）"
            ));
        }

        try {
            int keyCode = getKeyCode(key);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);

            return toJson(Map.of(
                    "success", true,
                    "action", "按键",
                    "key", key,
                    "message", "✅ 已按下: " + key
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 输入文字
     *
     * @param text 要输入的文字
     * @return 操作结果
     */
    @Tool(description = "模拟键盘输入文字。")
    public String typeText(
            @ToolParam(description = "要输入的文字") String text) {

        log.info("⌨️ 输入文字: {}", text);

        if (robot == null || clipboard == null) {
            return toJson(Map.of(
                    "success", false,
                    "error", "图形环境未初始化（可能运行在无头模式）"
            ));
        }

        try {
            // 使用剪贴板输入中文
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);

            // Ctrl+V 粘贴
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            return toJson(Map.of(
                    "success", true,
                    "action", "输入文字",
                    "text", text,
                    "message", "✅ 已输入: " + text
            ));

        } catch (Exception e) {
            // 回退方案：逐字符输入
            try {
                for (char c : text.toCharArray()) {
                    typeCharacter(c);
                }
                return toJson(Map.of(
                        "success", true,
                        "action", "输入文字",
                        "text", text,
                        "message", "✅ 已输入: " + text
                ));
            } catch (Exception ex) {
                return toJson(Map.of(
                        "success", false,
                        "error", ex.getMessage()
                ));
            }
        }
    }

    /**
     * 复制文本
     *
     * @param text 要复制的文本
     * @return 操作结果
     */
    @Tool(description = "复制文本到剪贴板。")
    public String copyToClipboard(
            @ToolParam(description = "要复制的文本") String text) {

        log.info("📋 复制到剪贴板");

        if (clipboard == null) {
            return toJson(Map.of(
                    "success", false,
                    "error", "剪贴板未初始化（可能运行在无头模式）"
            ));
        }

        try {
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);

            return toJson(Map.of(
                    "success", true,
                    "action", "复制",
                    "text", text,
                    "message", "✅ 已复制到剪贴板"
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 鼠标操作 ==========

    /**
     * 移动鼠标
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @return 操作结果
     */
    @Tool(description = "移动鼠标到指定坐标。")
    public String moveMouse(
            @ToolParam(description = "X 坐标") int x,
            @ToolParam(description = "Y 坐标") int y) {

        log.info("🖱️ 移动鼠标到: {},{}", x, y);

        if (robot == null) {
            return toJson(Map.of(
                    "success", false,
                    "error", "Robot 未初始化（可能运行在无头模式）"
            ));
        }

        try {
            robot.mouseMove(x, y);

            return toJson(Map.of(
                    "success", true,
                    "action", "移动鼠标",
                    "x", x,
                    "y", y,
                    "message", "✅ 鼠标已移动到: " + x + ", " + y
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 点击鼠标
     *
     * @param button 按钮：left/right/center
     * @return 操作结果
     */
    @Tool(description = "模拟鼠标点击。")
    public String clickMouse(
            @ToolParam(description = "鼠标按钮：left(左键), right(右键), center(中键)") String button) {

        log.info("🖱️ 鼠标点击: {}", button);

        if (robot == null) {
            return toJson(Map.of(
                    "success", false,
                    "error", "Robot 未初始化（可能运行在无头模式）"
            ));
        }

        try {
            int mask = switch (button.toLowerCase()) {
                case "right" -> InputEvent.BUTTON3_DOWN_MASK;
                case "center" -> InputEvent.BUTTON2_DOWN_MASK;
                default -> InputEvent.BUTTON1_DOWN_MASK;
            };

            robot.mousePress(mask);
            robot.mouseRelease(mask);

            return toJson(Map.of(
                    "success", true,
                    "action", "点击鼠标",
                    "button", button,
                    "message", "✅ 已点击: " + button
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 系统操作 ==========

    /**
     * 打开应用程序
     *
     * @param appName 应用名称或路径
     * @return 操作结果
     */
    @Tool(description = "打开指定的应用程序。")
    public String openApplication(
            @ToolParam(description = "应用程序名称或路径，如 notepad, calc, chrome") String appName) {

        log.info("📂 打开应用: {}", appName);

        try {
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", appName);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-a", appName);
            } else {
                pb = new ProcessBuilder(appName);
            }

            pb.start();

            return toJson(Map.of(
                    "success", true,
                    "action", "打开应用",
                    "app", appName,
                    "message", "✅ 已打开: " + appName
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 执行系统命令
     *
     * @param command 系统命令
     * @return 命令输出
     */
    @Tool(description = "执行系统终端命令。")
    public String executeCommand(
            @ToolParam(description = "要执行的系统命令") String command) {

        log.info("💻 执行命令: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("bash", "-c", command);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);

            return toJson(Map.of(
                    "success", process.exitValue() == 0,
                    "command", command,
                    "exitCode", process.exitValue(),
                    "output", output.toString()
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "command", command,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 网页内容获取 ==========

    /**
     * 获取网页内容（不需要浏览器）
     *
     * @param url 网址
     * @return 网页内容
     */
    @Tool(description = "直接获取网页内容（无需打开浏览器）。适用于快速获取网页数据。")
    public String fetchWebContent(
            @ToolParam(description = "要获取内容的网页URL") String url) {

        log.info("📄 获取网页内容: {}", url);

        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            StringBuilder content = new StringBuilder();
            URL website = new URL(url);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(website.openStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 100) {
                    content.append(line).append("\n");
                    count++;
                }
            }

            // 提取关键信息
            String text = content.toString();
            String title = extractTitle(text);
            String weatherInfo = extractWeatherInfo(text);

            return toJson(Map.of(
                    "success", true,
                    "url", url,
                    "title", title,
                    "weatherInfo", weatherInfo,
                    "content", text.substring(0, Math.min(text.length(), 3000))
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "url", url,
                    "error", e.getMessage()
            ));
        }
    }

    private String extractTitle(String html) {
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        if (start >= 0 && end > start) {
            return html.substring(start + 7, end).trim();
        }
        return "未知";
    }

    private String extractWeatherInfo(String html) {
        // 简单的天气信息提取（实际需要更复杂的解析）
        String[] keywords = {"天气", "temperature", "weather", "℃", "°C"};
        for (String kw : keywords) {
            if (html.toLowerCase().contains(kw.toLowerCase())) {
                return "检测到天气相关内容";
            }
        }
        return "未检测到天气信息";
    }

    // ========== 辅助方法 ==========

    private int getKeyCode(String key) {
        return switch (key.toLowerCase()) {
            case "enter" -> KeyEvent.VK_ENTER;
            case "esc" -> KeyEvent.VK_ESCAPE;
            case "space" -> KeyEvent.VK_SPACE;
            case "tab" -> KeyEvent.VK_TAB;
            case "ctrl" -> KeyEvent.VK_CONTROL;
            case "alt" -> KeyEvent.VK_ALT;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "up" -> KeyEvent.VK_UP;
            case "down" -> KeyEvent.VK_DOWN;
            case "left" -> KeyEvent.VK_LEFT;
            case "right" -> KeyEvent.VK_RIGHT;
            case "backspace" -> KeyEvent.VK_BACK_SPACE;
            case "delete" -> KeyEvent.VK_DELETE;
            case "home" -> KeyEvent.VK_HOME;
            case "end" -> KeyEvent.VK_END;
            case "f1" -> KeyEvent.VK_F1;
            case "f2" -> KeyEvent.VK_F2;
            case "f3" -> KeyEvent.VK_F3;
            default -> {
                if (key.length() == 1) {
                    yield KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
                }
                yield KeyEvent.VK_UNDEFINED;
            }
        };
    }

    private void typeCharacter(char c) throws AWTException {
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
        if (keyCode != KeyEvent.VK_UNDEFINED) {
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
        }
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (++count < map.size()) json.append(",");
            json.append("\n");
        }
        json.append("}");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String str = (String) value;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            return "\"" + str + "\"";
        }
        return value.toString();
    }
}