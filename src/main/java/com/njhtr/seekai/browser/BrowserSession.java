package com.njhtr.seekai.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 浏览器会话管理
 * 一个会话 = 一个独立的浏览器实例/页面
 */
@Slf4j
@Component
public class BrowserSession {

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private Playwright playwright;
    private Browser browser;

    /**
     * 会话信息
     */
    @Data
    public static class SessionInfo {
        String sessionId;
        Page page;
        BrowserContext context;
        String currentUrl;
        String title;
        LocalDateTime createdAt;
        LocalDateTime lastActive;
        Map<String, String> cookies;
        boolean isHeadless;
    }

    /**
     * 初始化 Playwright (Lazy)
     */
    private Playwright getPlaywright() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        return playwright;
    }

    /**
     * 获取或创建浏览器
     */
    private Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            browser = getPlaywright().chromium()
                .launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--no-sandbox"
                    )));
        }
        return browser;
    }

    /**
     * 创建新会话
     */
    public SessionInfo createSession(String sessionId, boolean headless) {
        try {
            Browser browser = getBrowser();
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            Page page = context.newPage();

            SessionInfo info = new SessionInfo();
            info.setSessionId(sessionId);
            info.setPage(page);
            info.setContext(context);
            info.setHeadless(headless);
            info.setCreatedAt(LocalDateTime.now());
            info.setLastActive(LocalDateTime.now());
            info.setCookies(new HashMap<>());

            sessions.put(sessionId, info);

            log.info("🆕 创建浏览器会话: {}", sessionId);
            return info;

        } catch (Exception e) {
            log.error("❌ 创建会话失败: {}", e.getMessage());
            throw new RuntimeException("创建浏览器会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话
     */
    public SessionInfo getSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> createSession(id, true));
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            try {
                info.getPage().close();
                info.getContext().close();
                log.info("🔒 关闭会话: {}", sessionId);
            } catch (Exception e) {
                log.warn("⚠️ 关闭会话异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 导航到 URL
     */
    public String navigate(String sessionId, String url) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            // 等待网络空闲
            Response response = page.navigate(url, new Page.NavigateOptions()
                .setTimeout(30000));

            info.setCurrentUrl(page.url());
            info.setTitle(page.title());
            info.setLastActive(LocalDateTime.now());

            // 更新 cookies
            updateCookies(info);

            String result = String.format("""
                ✅ 导航成功
                - URL: %s
                - 标题: %s
                - 状态: %d
                """, url, page.title(), response != null ? response.status() : 200);

            log.info("🌐 导航: {} -> {}", url, page.title());
            return result;

        } catch (Exception e) {
            log.error("❌ 导航失败: {}", e.getMessage());
            return "❌ 导航失败: " + e.getMessage();
        }
    }

    /**
     * 点击元素
     */
    public String click(String sessionId, String selector) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            page.click(selector, new Page.ClickOptions().setTimeout(10000));
            info.setLastActive(LocalDateTime.now());

            log.info("👆 点击: {}", selector);
            return "✅ 点击成功: " + selector;

        } catch (Exception e) {
            return "❌ 点击失败: " + e.getMessage();
        }
    }

    /**
     * 输入文本
     */
    public String type(String sessionId, String selector, String text) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            // 清空并输入文本
            page.locator(selector).fill(text);
            info.setLastActive(LocalDateTime.now());

            log.info("⌨️ 输入: {} -> {}", selector, text.substring(0, Math.min(20, text.length())));
            return "✅ 输入成功: " + selector + " = " + text;

        } catch (Exception e) {
            return "❌ 输入失败: " + e.getMessage();
        }
    }

    /**
     * 填写表单
     */
    public String fillForm(String sessionId, Map<String, String> data) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            try {
                page.fill(entry.getKey(), entry.getValue());
                result.append("✅ ").append(entry.getKey()).append("\n");
            } catch (Exception e) {
                result.append("❌ ").append(entry.getKey()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        info.setLastActive(LocalDateTime.now());

        return result.toString();
    }

    /**
     * 获取页面内容
     */
    public String getContent(String sessionId, String selector) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            if (selector != null && !selector.isEmpty()) {
                String content = page.locator(selector).innerText();
                return "内容 (" + selector + "):\n" + content;
            } else {
                // 获取主要内容
                String content = page.content();
                // 移除脚本和样式
                content = content.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
                content = content.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");

                // 提取 body 文本
                String text = page.locator("body").innerText();
                return "页面内容:\n" + text.substring(0, Math.min(5000, text.length()));
            }
        } catch (Exception e) {
            return "❌ 获取内容失败: " + e.getMessage();
        }
    }

    /**
     * 截图
     */
    public String screenshot(String sessionId, String path) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            String filePath = path != null ? path : "screenshots/" + sessionId + ".png";

            // 确保目录存在
            java.nio.file.Files.createDirectories(Path.of(filePath).getParent());

            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setPath(Path.of(filePath))
                .setFullPage(false));

            info.setLastActive(LocalDateTime.now());
            log.info("📸 截图: {}", filePath);

            return "✅ 截图保存到: " + filePath;

        } catch (Exception e) {
            return "❌ 截图失败: " + e.getMessage();
        }
    }

    /**
     * 滚动页面
     */
    public String scroll(String sessionId, int pixels) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            if (pixels > 0) {
                page.evaluate("window.scrollBy(0, " + pixels + ")");
            } else {
                page.evaluate("window.scrollBy(0, " + Math.abs(pixels) + ")");
            }
            info.setLastActive(LocalDateTime.now());

            log.info("📜 滚动: {}px", pixels);
            return "✅ 滚动 " + pixels + " 像素";

        } catch (Exception e) {
            return "❌ 滚动失败: " + e.getMessage();
        }
    }

    /**
     * 等待元素出现
     */
    public String waitFor(String sessionId, String selector, int timeout) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setTimeout(timeout > 0 ? timeout : 5000));

            return "✅ 元素出现: " + selector;

        } catch (Exception e) {
            return "❌ 等待超时: " + selector;
        }
    }

    /**
     * 执行 JavaScript
     */
    public String executeScript(String sessionId, String script) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            Object result = page.evaluate(script);
            info.setLastActive(LocalDateTime.now());

            return "执行结果:\n" + result;

        } catch (Exception e) {
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * 获取所有链接
     */
    public String getLinks(String sessionId) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            Object linksObj = page.evalOnSelectorAll("a[href]", """
                elements => elements.map(e => e.href)
                """);
            @SuppressWarnings("unchecked")
            List<String> links = (List<String>)(List<?>) linksObj;

            StringBuilder sb = new StringBuilder("页面链接 (共 " + links.size() + " 个):\n");
            for (String link : links.stream().limit(20).toList()) {
                sb.append("- ").append(link).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "❌ 获取链接失败: " + e.getMessage();
        }
    }

    /**
     * 获取表单数据
     */
    public String getFormData(String sessionId, String formSelector) {
        SessionInfo info = getSession(sessionId);
        Page page = info.getPage();

        try {
            // 获取所有输入框的值
            Object dataObj = page.evalOnSelectorAll(formSelector + " input, " + formSelector + " textarea, " + formSelector + " select", """
                elements => {
                    return elements.map(e => {
                        return e.name + '=' + e.value;
                    }).join('\\n');
                }
                """);

            String data = dataObj != null ? dataObj.toString() : "";
            return "表单数据:\n" + data;

        } catch (Exception e) {
            return "❌ 获取表单失败: " + e.getMessage();
        }
    }

    /**
     * 关闭浏览器
     */
    public void shutdown() {
        sessions.forEach((id, info) -> closeSession(id));
        sessions.clear();

        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }

        log.info("🔴 浏览器已关闭");
    }

    /**
     * 更新 cookies
     */
    private void updateCookies(SessionInfo info) {
        try {
            List<Cookie> cookies = info.getContext().cookies();
            Map<String, String> cookieMap = new HashMap<>();
            for (Cookie cookie : cookies) {
                // Playwright Java Cookie uses public fields, not getters
                String name = cookie.name;
                String value = cookie.value;
                cookieMap.put(name, value);
            }
            info.setCookies(cookieMap);
        } catch (Exception e) {
            log.warn("⚠️ 获取 cookies 失败: {}", e.getMessage());








            
        }
    }

    /**
     * 获取会话状态
     */
    public Map<String, Object> getSessionStatus(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            return Map.of("status", "not_found");
        }

        return Map.of(
            "sessionId", sessionId,
            "url", info.getCurrentUrl() != null ? info.getCurrentUrl() : "",
            "title", info.getTitle() != null ? info.getTitle() : "",
            "createdAt", info.getCreatedAt().toString(),
            "lastActive", info.getLastActive().toString(),
            "cookiesCount", info.getCookies().size()
        );
    }

    /**
     * 获取所有会话
     */
    public List<String> getAllSessions() {
        return new ArrayList<>(sessions.keySet());
    }
}