package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 网页爬虫工具 - 提供网页内容抓取、数据提取功能
 * 支持：反爬应对、代理、Cookie、限速等高级功能
 */
@Slf4j
@Component
public class WebCrawlerTools {

    private static final int TIMEOUT = 30000;

    // 多种 User-Agent 轮换
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    // 常用请求头
    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>() {{
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        put("Accept-Encoding", "gzip, deflate, br");
        put("Connection", "keep-alive");
        put("Upgrade-Insecure-Requests", "1");
        put("Sec-Fetch-Dest", "document");
        put("Sec-Fetch-Mode", "navigate");
        put("Sec-Fetch-Site", "none");
        put("Sec-Fetch-User", "?1");
        put("Cache-Control", "max-age=0");
    }};

    /**
     * 抓取网页内容
     *
     * @param url 要抓取的网页URL
     * @return 网页内容JSON
     */
    @Tool(description = "抓取网页的标题、文本内容、链接和图片。返回包含标题、主要内容、链接列表、图片列表的JSON。适用于获取网页摘要或数据采集。")
    public String crawlWebPage(
            @ToolParam(description = "要抓取的网页URL，必须以http://或https://开头") String url) {

        log.info("🕷️ 开始抓取网页: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[0])
                    .timeout(TIMEOUT)
                    .get();

            // 提取标题
            String title = doc.title();

            // 提取主要文本内容（移除脚本和样式）
            doc.select("script, style, nav, footer, header").remove();
            String textContent = doc.body().text();

            // 限制文本长度
            if (textContent.length() > 5000) {
                textContent = textContent.substring(0, 5000) + "...";
            }

            // 提取所有链接
            List<String> links = new ArrayList<>();
            Elements linkElements = doc.select("a[href]");
            for (Element link : linkElements) {
                String href = link.attr("abs:href");
                if (!href.isEmpty() && !links.contains(href)) {
                    links.add(href);
                }
            }

            // 提取所有图片
            List<String> images = new ArrayList<>();
            Elements imgElements = doc.select("img[src]");
            for (Element img : imgElements) {
                String src = img.attr("abs:src");
                if (!src.isEmpty() && !images.contains(src)) {
                    images.add(src);
                }
            }

            // 构建结果JSON
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("title", title);
            result.put("textContent", textContent);
            result.put("linksCount", links.size());
            result.put("links", links.stream().limit(20).toList());
            result.put("imagesCount", images.size());
            result.put("images", images.stream().limit(10).toList());

            log.info("✅ 网页抓取成功: {} - 标题: {}", url, title);
            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 网页抓取失败: {} - 错误: {}", url, e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "url", url,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 抓取表格数据
     *
     * @param url 要抓取的网页URL
     * @return 表格数据JSON
     */
    @Tool(description = "提取网页中的表格数据。返回表格的行列数据，适用于抓取排行榜、统计数据等结构化数据。")
    public String crawlTableData(
            @ToolParam(description = "要抓取表格数据的网页URL") String url) {

        log.info("📊 开始抓取表格数据: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[0])
                    .timeout(TIMEOUT)
                    .get();

            List<Map<String, Object>> tables = new ArrayList<>();
            Elements tableElements = doc.select("table");

            for (int i = 0; i < tableElements.size(); i++) {
                Element table = tableElements.get(i);
                Map<String, Object> tableData = new LinkedHashMap<>();
                tableData.put("tableIndex", i);

                // 获取表头
                List<String> headers = new ArrayList<>();
                Elements headerElements = table.select("thead th, thead td");
                for (Element th : headerElements) {
                    headers.add(th.text());
                }

                // 如果没有thead，使用第一个tr作为表头
                if (headers.isEmpty()) {
                    Elements firstRow = table.select("tr:first-child th, tr:first-child td");
                    for (Element th : firstRow) {
                        headers.add(th.text());
                    }
                }

                tableData.put("headers", headers);

                // 获取表格行数据
                List<Map<String, String>> rows = new ArrayList<>();
                Elements bodyRows = table.select("tbody tr");
                if (bodyRows.isEmpty()) {
                    bodyRows = table.select("tr");
                }

                for (Element row : bodyRows) {
                    Elements cells = row.select("td, th");
                    if (cells.isEmpty()) continue;

                    Map<String, String> rowData = new LinkedHashMap<>();
                    for (int j = 0; j < cells.size() && j < headers.size(); j++) {
                        rowData.put(headers.get(j), cells.get(j).text());
                    }
                    if (!rowData.isEmpty()) {
                        rows.add(rowData);
                    }
                }

                tableData.put("rows", rows);
                tableData.put("rowCount", rows.size());

                if (!rows.isEmpty()) {
                    tables.add(tableData);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("tablesCount", tables.size());
            result.put("tables", tables);

            log.info("✅ 表格抓取成功: {} - 发现 {} 个表格", url, tables.size());
            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 表格抓取失败: {} - 错误: {}", url, e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "url", url,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 搜索并抓取多个网页
     *
     * @param urls 要抓取的URL列表（用逗号分隔）
     * @return 多个网页的内容汇总
     */
    @Tool(description = "批量抓取多个网页的内容。输入多个URL（用逗号分隔），返回每个网页的标题和内容摘要。")
    public String crawlMultiplePages(
            @ToolParam(description = "多个URL，用逗号分隔，例如: https://example.com,https://example2.com") String urls) {

        log.info("📚 开始批量抓取网页");

        String[] urlArray = urls.split(",");
        List<Map<String, Object>> results = new ArrayList<>();

        for (String url : urlArray) {
            url = url.trim();
            if (url.isEmpty()) continue;

            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENTS[0])
                        .timeout(TIMEOUT)
                        .get();

                doc.select("script, style, nav, footer, header").remove();

                Map<String, Object> pageData = new LinkedHashMap<>();
                pageData.put("url", url);
                pageData.put("title", doc.title());
                pageData.put("content", doc.body().text().substring(0, Math.min(doc.body().text().length(), 2000)));
                results.add(pageData);

            } catch (Exception e) {
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("url", url);
                errorData.put("error", e.getMessage());
                results.add(errorData);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("totalPages", urlArray.length);
        result.put("successful", results.size());
        result.put("pages", results);

        return toJson(result);
    }

    /**
     * 从网页提取特定CSS选择器的元素
     *
     * @param url        网页URL
     * @param cssSelector CSS选择器，如 ".article-content" 或 "#main-title"
     * @return 匹配的元素内容
     */
    @Tool(description = "使用CSS选择器提取网页中的特定元素。适用于精准抓取网页的特定部分，如文章内容、评论等。")
    public String extractBySelector(
            @ToolParam(description = "要抓取的网页URL") String url,
            @ToolParam(description = "CSS选择器，如 '.classname', '#idname', 'tagname' 等") String cssSelector) {

        log.info("🎯 使用选择器 {} 抓取网页: {}", cssSelector, url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[0])
                    .timeout(TIMEOUT)
                    .get();

            Elements elements = doc.select(cssSelector);

            List<String> contents = new ArrayList<>();
            for (Element el : elements) {
                contents.add(el.text());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("cssSelector", cssSelector);
            result.put("matchCount", elements.size());
            result.put("contents", contents);

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "url", url,
                    "cssSelector", cssSelector,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 反爬虫应对方法 ==========

    /**
     * 高级抓取 - 支持自定义请求头、代理、Cookie
     *
     * @param url          目标 URL
     * @param userAgent    自定义 User-Agent（为空则随机）
     * @param proxyHost    代理主机（可选）
     * @param proxyPort    代理端口
     * @param cookies      Cookie（格式：key1=value1; key2=value2）
     * @param referer      Referer 头
     * @return 抓取结果
     */
    @Tool(description = "高级网页抓取，支持反爬应对：自定义User-Agent、代理IP、Cookie、Referer等。适用于被反爬的网站。")
    public String advancedCrawl(
            @ToolParam(description = "目标网页URL") String url,
            @ToolParam(description = "自定义User-Agent，为空则随机选择浏览器") String userAgent,
            @ToolParam(description = "代理服务器主机（可选，如需要）") String proxyHost,
            @ToolParam(description = "代理服务器端口") int proxyPort,
            @ToolParam(description = "Cookie字符串，格式：key1=value1; key2=value2") String cookies,
            @ToolParam(description = "Referer来源页面") String referer) {

        log.info("🕷️ 高级抓取: {} (UA: {}, Proxy: {}:{})", url, userAgent != null ? "自定义" : "随机", proxyHost, proxyPort);

        try {
            // 构建连接
            Connection conn = Jsoup.connect(url)
                    .timeout(TIMEOUT)
                    .followRedirects(true);

            // 设置 User-Agent（随机或自定义）
            String ua = (userAgent == null || userAgent.isEmpty())
                    ? USER_AGENTS[new Random().nextInt(USER_AGENTS.length)]
                    : userAgent;
            conn.userAgent(ua);

            // 设置默认请求头
            for (Map.Entry<String, String> entry : DEFAULT_HEADERS.entrySet()) {
                conn.header(entry.getKey(), entry.getValue());
            }

            // 设置代理
            if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
                conn.proxy(proxyHost, proxyPort);
            }

            // 设置 Cookie
            if (cookies != null && !cookies.isEmpty()) {
                String[] cookiePairs = cookies.split(";");
                for (String pair : cookiePairs) {
                    String[] kv = pair.trim().split("=", 2);
                    if (kv.length == 2) {
                        conn.cookie(kv[0].trim(), kv[1].trim());
                    }
                }
            }

            // 设置 Referer
            if (referer != null && !referer.isEmpty()) {
                conn.header("Referer", referer);
            }

            // 执行请求
            Document doc = conn.get();

            // 提取内容
            String title = doc.title();
            doc.select("script, style, nav, footer, header").remove();
            String textContent = doc.body().text();

            if (textContent.length() > 5000) {
                textContent = textContent.substring(0, 5000) + "...";
            }

            // 提取链接
            List<String> links = new ArrayList<>();
            for (Element link : doc.select("a[href]")) {
                String href = link.attr("abs:href");
                if (!href.isEmpty() && !links.contains(href) && href.startsWith("http")) {
                    links.add(href);
                }
            }

            // 检查是否有反爬痕迹
            String antiBot = detectAntiBot(doc);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("title", title);
            result.put("textContent", textContent);
            result.put("userAgent", ua);
            result.put("usedProxy", proxyHost != null && !proxyHost.isEmpty());
            result.put("antiBotDetected", antiBot);
            result.put("linksCount", links.size());
            result.put("links", links.stream().limit(20).toList());

            log.info("✅ 高级抓取成功: {}", title);
            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 高级抓取失败: {}", e.getMessage());
            String suggestion = getSuggestion(e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "url", url,
                    "error", e.getMessage(),
                    "suggestion", suggestion
            ));
        }
    }

    /**
     * 带限速的批量抓取 - 避免触发频率限制
     *
     * @param urls       多个 URL（用逗号分隔）
     * @param delayMs    请求间隔（毫秒），建议 1000-3000
     * @return 批量抓取结果
     */
    @Tool(description = "带请求限速的批量抓取。设置请求间隔避免触发网站的频率限制，适合批量抓取多个页面。")
    public String crawlWithDelay(
            @ToolParam(description = "多个URL，用逗号分隔") String urls,
            @ToolParam(description = "请求间隔毫秒数，建议1000-3000ms") int delayMs) {

        log.info("📚 带限速的批量抓取，间隔: {}ms", delayMs);

        String[] urlArray = urls.split(",");
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < urlArray.length; i++) {
            String url = urlArray[i].trim();
            if (url.isEmpty()) continue;

            log.info("🕷️ 抓取 {}/{}: {}", i + 1, urlArray.length, url);

            try {
                // 随机 User-Agent
                String ua = USER_AGENTS[new Random().nextInt(USER_AGENTS.length)];

                Document doc = Jsoup.connect(url)
                        .userAgent(ua)
                        .timeout(TIMEOUT)
                        .get();

                doc.select("script, style, nav, footer, header").remove();

                Map<String, Object> pageData = new LinkedHashMap<>();
                pageData.put("url", url);
                pageData.put("title", doc.title());
                pageData.put("content", doc.body().text().substring(0, Math.min(doc.body().text().length(), 2000)));
                pageData.put("status", "success");
                results.add(pageData);

            } catch (Exception e) {
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("url", url);
                errorData.put("error", e.getMessage());
                errorData.put("status", "failed");
                results.add(errorData);
            }

            // 限速（最后一条不需要等待）
            if (i < urlArray.length - 1 && delayMs > 0) {
                try {
                    // 添加随机波动，避免固定间隔被检测
                    int actualDelay = delayMs + new Random().nextInt(1000);
                    Thread.sleep(actualDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long successCount = results.stream().filter(r -> "success".equals(r.get("status"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("totalUrls", urlArray.length);
        result.put("successful", successCount);
        result.put("failed", urlArray.length - successCount);
        result.put("delayMs", delayMs);
        result.put("pages", results);

        return toJson(result);
    }

    /**
     * 检测反爬虫机制
     *
     * @param doc 抓取的文档
     * @return 检测结果
     */
    private String detectAntiBot(Document doc) {
        String html = doc.html().toLowerCase();

        // 检测常见的反爬页面特征
        if (html.contains("访问频率") || html.contains("请求过于频繁")) {
            return "频率限制";
        }
        if (html.contains("验证码") || html.contains("captcha") || html.contains("安全验证")) {
            return "验证码";
        }
        if (html.contains("禁止访问") || html.contains("access denied") || html.contains("forbidden")) {
            return "禁止访问";
        }
        if (html.contains("您的ip") || html.contains("your ip") || html.contains("ip地址异常")) {
            return "IP限制";
        }
        if (html.contains("登录") && doc.select("input[type=password]").size() > 0) {
            return "需要登录";
        }

        // 检测是否被重定向到异常页面
        String title = doc.title().toLowerCase();
        if (title.contains("error") || title.contains("warning") || title.contains("blocked")) {
            return "异常页面";
        }

        return "无";
    }

    /**
     * 根据错误信息给出建议
     */
    private String getSuggestion(String error) {
        String lower = error.toLowerCase();

        if (lower.contains("connect") || lower.contains("connection")) {
            return "建议：检查网络连接，或尝试使用代理";
        }
        if (lower.contains("timeout")) {
            return "建议：网站响应慢，增加超时时间或使用代理";
        }
        if (lower.contains("403") || lower.contains("forbidden") || lower.contains("denied")) {
            return "建议：需要更高的权限，尝试更换User-Agent或使用Cookie";
        }
        if (lower.contains("429") || lower.contains("too many")) {
            return "建议：请求过于频繁，使用限速功能并增加间隔时间";
        }
        if (lower.contains("captcha") || lower.contains("验证码")) {
            return "建议：需要人工处理验证码，当前无法自动抓取";
        }

        return "建议：检查URL是否正确，或尝试其他方法";
    }

    // ========== 辅助方法 ==========

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (++count < map.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String str = (String) value;
            str = str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return "\"" + str + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List) {
            return formatList((List<?>) value);
        }
        if (value instanceof Map) {
            return toJson((Map<String, Object>) value);
        }
        return "\"" + value.toString() + "\"";
    }

    private String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append("  ").append(formatValue(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}