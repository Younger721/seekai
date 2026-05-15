package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
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
 * 智能爬虫 Agent - 具备高级爬虫能力
 * 支持：智能 URL 识别、内容类型判断、批量抓取、深度爬取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerAgent implements Agent {

    private final ChatClient chatClient;
    private final WebCrawlerTools webCrawlerTools;

    private final String name = "CRAWLER_AGENT";
    private final String description = "智能爬虫特工。具备高级爬取能力：智能 URL 识别、自动内容类型分析、批量抓取、深度爬取分页内容、AI 智能总结。";

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.create(emitter -> {
            try {
                String message = request.getMessage();
                log.info("🕷️ [CrawlerAgent] 开始智能处理: {}", message);

                // Step 1: 智能提取 URL（支持多种格式）
                List<String> urls = extractUrlsSmart(message);
                if (urls.isEmpty()) {
                    emitter.next("❌ 未检测到有效的网页 URL。\n\n" +
                            "📝 支持的格式：\n" +
                            "- 直接提供 URL：https://example.com\n" +
                            "- 带描述的链接：查看 https://example.com 上的数据\n" +
                            "- 多个 URL：用逗号或空格分隔\n\n" +
                            "💡 请提供要抓取的网页地址。");
                    emitter.complete();
                    return;
                }

                emitter.next("🔍 智能检测到 " + urls.size() + " 个目标网址：\n");
                for (int i = 0; i < urls.size(); i++) {
                    emitter.next("   " + (i + 1) + ". " + urls.get(i) + "\n");
                }
                emitter.next("\n");

                // Step 2: 如果有多个 URL，批量抓取
                if (urls.size() > 1) {
                    emitter.next("📚 开始批量抓取多个页面...\n\n");
                    String batchResult = webCrawlerTools.crawlMultiplePages(String.join(",", urls));

                    // AI 智能分析
                    emitter.next("🤖 正在分析抓取结果...\n");
                    String summary = analyzeAndSummarize(batchResult, "批量网页数据");
                    emitter.next("\n📋 综合分析结果：\n\n" + summary);

                } else {
                    // 单页面深度抓取
                    String url = urls.get(0);
                    emitter.next("🎯 开始深度抓取: " + url + "\n\n");

                    // 智能判断内容类型
                    String contentType = detectContentType(message, url);
                    emitter.next("📊 智能识别内容类型: " + getContentTypeName(contentType) + "\n\n");

                    String result;
                    switch (contentType) {
                        case "table" -> {
                            emitter.next("📈 检测到表格数据，正在提取...\n");
                            result = webCrawlerTools.crawlTableData(url);
                        }
                        case "list" -> {
                            emitter.next("📋 检测到列表页面，正在提取...\n");
                            result = webCrawlerTools.crawlWebPage(url);
                            // 尝试提取分页
                            result += "\n\n" + extractPaginationData(url);
                        }
                        default -> {
                            emitter.next("📄 正在抓取网页内容...\n");
                            result = webCrawlerTools.crawlWebPage(url);
                        }
                    }

                    // AI 深度分析
                    emitter.next("\n🤖 正在进行深度分析与总结...\n");
                    String summary = analyzeAndSummarize(result, getContentTypeName(contentType));
                    emitter.next("\n📋 详细分析报告：\n\n" + summary);

                    // 检查是否适合生成图表
                    String chartSuggestion = suggestChart(result);
                    if (chartSuggestion != null) {
                        emitter.next("\n\n📊 " + chartSuggestion);
                    }
                }

                emitter.next("\n\n✅ 抓取完成！如需生成图表，请使用 @图表 命令。");
                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] 智能抓取失败：{}", name, e.getMessage(), e);
                emitter.next("❌ 抓取失败: " + e.getMessage() + "\n\n" +
                        "可能的原因：\n" +
                        "- URL 无效或已失效\n" +
                        "- 网站禁止爬取\n" +
                        "- 网络连接问题\n\n" +
                        "💡 请尝试其他 URL 或检查网络连接。");
                emitter.complete();
            }
        });
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        return AgentResponse.builder()
                .content("爬虫 Agent 更适合使用流式模式运行以展示智能分析过程。请使用流式接口。")
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

    // ========== 智能方法 ==========

    /**
     * 智能提取 URL - 支持多种格式
     */
    private List<String> extractUrlsSmart(String message) {
        List<String> urls = new ArrayList<>();

        // 1. 标准 URL 格式
        Pattern httpPattern = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)");
        Matcher httpMatcher = httpPattern.matcher(message);
        while (httpMatcher.find()) {
            String url = httpMatcher.group(1);
            url = cleanUrl(url);
            if (isValidUrl(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }

        // 2. 中文描述中的链接 "查看 https://xxx.com"
        Pattern cnPattern = Pattern.compile("[查看看获取请帮查爬抓分析]+\\s+(https?://[^\\s]+)");
        Matcher cnMatcher = cnPattern.matcher(message);
        while (cnMatcher.find()) {
            String url = cleanUrl(cnMatcher.group(1));
            if (isValidUrl(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }

        // 3. 括号内的 URL
        Pattern bracketPattern = Pattern.compile("[（(](https?://[^)）]+)[)）]");
        Matcher bracketMatcher = bracketPattern.matcher(message);
        while (bracketMatcher.find()) {
            String url = cleanUrl(bracketMatcher.group(1));
            if (isValidUrl(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }

        // 4. 分割逗号/空格分隔的多个 URL
        String[] parts = message.split("[,，\\s]+");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("http://") || part.startsWith("https://")) {
                String url = cleanUrl(part);
                if (isValidUrl(url) && !urls.contains(url)) {
                    urls.add(url);
                }
            }
        }

        return urls;
    }

    /**
     * 清理 URL - 移除末尾的标点
     */
    private String cleanUrl(String url) {
        if (url == null) return null;
        // 移除末尾的逗号、句号、引号等
        while (url.length() > 0 && ",.。\"'\"/>]。".contains(url.substring(url.length() - 1))) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 验证 URL 基本有效性
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.length() < 10) return false;
        return url.contains(".") && !url.contains(" ");
    }

    /**
     * 智能判断内容类型
     */
    private String detectContentType(String message, String url) {
        String lower = message.toLowerCase();

        // 用户明确指定
        if (lower.contains("表格") || lower.contains("table") || lower.contains("排行榜") || lower.contains("排名")) {
            return "table";
        }
        if (lower.contains("列表") || lower.contains("list") || lower.contains("文章") || lower.contains("详情")) {
            return "list";
        }
        if (lower.contains("图片") || lower.contains("image") || lower.contains("图库")) {
            return "image";
        }

        // 根据 URL 特征判断
        if (url.contains("table") || url.contains("list") || url.contains("ranking") || url.contains("top")) {
            return "table";
        }

        // 默认智能判断
        return "auto";
    }

    private String getContentTypeName(String type) {
        return switch (type) {
            case "table" -> "表格数据";
            case "list" -> "列表/文章";
            case "image" -> "图片集合";
            default -> "综合内容";
        };
    }

    /**
     * 提取分页数据（简化版）
     */
    private String extractPaginationData(String url) {
        try {
            // 尝试提取可能的分页链接
            String pageResult = webCrawlerTools.crawlWebPage(url);
            // 后续可以扩展：自动检测分页链接并抓取
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * AI 分析与总结
     */
    private String analyzeAndSummarize(String rawData, String contentType) {
        try {
            String prompt = """
                你是一个专业的数据分析师和内容整理专家。请智能分析以下%s内容：

                原始抓取数据：
                %s

                请进行以下处理：
                1. **关键信息提取**：提炼标题、主要数据、关键指标
                2. **结构化整理**：如果有多条数据，整理成表格或列表
                3. **趋势分析**：如有时间序列数据，分析趋势变化
                4. **要点总结**：用简洁的语言总结最重要的 3-5 个要点
                5. **数据提取**：如果有具体数值，提炼成结构化数据（可用于后续图表生成）

                注意：
                - 直接给出分析结果，不要输出思考过程
                - 使用清晰的分隔和格式
                - 如果发现数据可能适合做图表，请明确标注"可图表化"
                - 如果数据中有具体的数值，请尽量提取出来
                """.formatted(contentType, rawData);

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

        } catch (Exception e) {
            log.warn("AI 分析失败，使用原始数据：{}", e.getMessage());
            return rawData;
        }
    }

    /**
     * 建议图表类型
     */
    private String suggestChart(String data) {
        try {
            String prompt = """
                分析以下数据，判断是否适合生成图表，以及适合什么类型的图表。

                数据内容：
                %s

                请直接输出：
                - 如果有具体的数值数据可以图表化：输出 "可以生成[图表类型]图表，例如：X轴用xxx，Y轴用xxx"
                - 如果没有适合图表化的数据：输出 "该数据不适合生成图表"
                - 只需要一行简短的建议即可
                """.formatted(data.substring(0, Math.min(data.length(), 2000)));

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

        } catch (Exception e) {
            return null;
        }
    }
}