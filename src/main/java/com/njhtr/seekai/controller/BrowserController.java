package com.njhtr.seekai.controller;

import com.njhtr.seekai.browser.BrowserSession;
import com.njhtr.seekai.browser.BrowserSession.SessionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 浏览器自动化 API
 */
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
public class BrowserController {

    private final BrowserSession browserSession;

    /**
     * 创建新会话
     */
    @PostMapping("/session/create")
    public Map<String, Object> createSession(
            @RequestParam(defaultValue = "true") boolean headless
    ) {
        String sessionId = "session-" + System.currentTimeMillis();
        SessionInfo info = browserSession.createSession(sessionId, headless);

        return Map.of(
            "success", true,
            "sessionId", sessionId,
            "url", info.getCurrentUrl() != null ? info.getCurrentUrl() : ""
        );
    }

    /**
     * 导航到 URL
     */
    @PostMapping("/navigate")
    public Map<String, Object> navigate(
            @RequestParam String url,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.navigate(sessionId, url);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 点击元素
     */
    @PostMapping("/click")
    public Map<String, Object> click(
            @RequestParam String selector,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.click(sessionId, selector);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 输入文本
     */
    @PostMapping("/type")
    public Map<String, Object> type(
            @RequestParam String selector,
            @RequestParam String text,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.type(sessionId, selector, text);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 获取页面内容
     */
    @GetMapping("/content")
    public Map<String, Object> getContent(
            @RequestParam(required = false) String selector,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.getContent(sessionId, selector);
        return Map.of(
            "content", result,
            "sessionId", sessionId
        );
    }

    /**
     * 截图
     */
    @PostMapping("/screenshot")
    public Map<String, Object> screenshot(
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.screenshot(sessionId, path);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 滚动
     */
    @PostMapping("/scroll")
    public Map<String, Object> scroll(
            @RequestParam int pixels,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.scroll(sessionId, pixels);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 等待元素
     */
    @PostMapping("/wait")
    public Map<String, Object> waitFor(
            @RequestParam String selector,
            @RequestParam(defaultValue = "5000") int timeout,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.waitFor(sessionId, selector, timeout);
        return Map.of(
            "success", result.contains("成功"),
            "result", result
        );
    }

    /**
     * 执行 JavaScript
     */
    @PostMapping("/execute")
    public Map<String, Object> executeScript(
            @RequestParam String script,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.executeScript(sessionId, script);
        return Map.of(
            "result", result
        );
    }

    /**
     * 获取链接
     */
    @GetMapping("/links")
    public Map<String, Object> getLinks(
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String result = browserSession.getLinks(sessionId);
        return Map.of(
            "links", result
        );
    }

    /**
     * 获取会话状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus(
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        return browserSession.getSessionStatus(sessionId);
    }

    /**
     * 获取所有会话
     */
    @GetMapping("/sessions")
    public Map<String, Object> getAllSessions() {
        List<String> sessions = browserSession.getAllSessions();
        return Map.of(
            "sessions", sessions,
            "count", sessions.size()
        );
    }

    /**
     * 关闭会话
     */
    @DeleteMapping("/session")
    public Map<String, Object> closeSession(
            @RequestParam String sessionId
    ) {
        browserSession.closeSession(sessionId);
        return Map.of(
            "success", true,
            "message", "会话已关闭"
        );
    }

    /**
     * 批量填写表单
     */
    @PostMapping("/fill-form")
    public Map<String, Object> fillForm(
            @RequestParam Map<String, String> formData,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        // 移除 sessionId 参数
        formData.remove("sessionId");
        String result = browserSession.fillForm(sessionId, formData);
        return Map.of(
            "result", result
        );
    }

    /**
     * 搜索
     */
    @PostMapping("/search")
    public Map<String, Object> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "https://www.baidu.com") String engine,
            @RequestParam(defaultValue = "default") String sessionId
    ) {
        String encodeKeyword = keyword.replace(" ", "+");
        String searchUrl = engine.contains("baidu")
            ? engine + "/s?wd=" + encodeKeyword
            : engine + "/search?q=" + encodeKeyword;

        String navResult = browserSession.navigate(sessionId, searchUrl);
        String content = browserSession.getContent(sessionId, null);

        return Map.of(
            "navigation", navResult,
            "content", content.substring(0, Math.min(2000, content.length()))
        );
    }
}