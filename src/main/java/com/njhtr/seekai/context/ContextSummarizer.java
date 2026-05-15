package com.njhtr.seekai.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 上下文摘要生成器
 * 使用 LLM 生成高质量对话摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextSummarizer {

    private final ChatClient chatClient;

    private static final String SUMMARY_SYSTEM_PROMPT = """
        你是一个对话摘要专家。你的任务是将多轮对话压缩成简洁的摘要，保留关键信息。

        摘要要求：
        1. 保留用户核心意图和需求
        2. 记录重要的技术决策或结论
        3. 保留关键的代码片段或配置
        4. 记住重要的事实和数据
        5. 用简洁的语言，不超过原始内容的 20%

        输出格式：
        - 使用 bullet points
        - 每条摘要以 [角色] 开头
        - 包含具体的技术细节
        """;

    /**
     * 摘要一组消息
     */
    public String summarize(List<ContextItem> items) {
        if (items.isEmpty()) return "";
        if (items.size() <= 3) {
            return items.stream()
                .map(i -> String.format("[%s] %s", i.getRole(), i.getContent()))
                .collect(Collectors.joining("\n"));
        }

        try {
            // 构建对话内容
            String conversation = items.stream()
                .map(i -> String.format("%s: %s", i.getRole(), i.getContent()))
                .collect(Collectors.joining("\n\n"));

            // 使用正确的 Spring AI 1.0 API
            String promptText = "请将以下对话压缩成简洁摘要：\n\n" + conversation;

            String summary = chatClient.prompt(SUMMARY_SYSTEM_PROMPT)
                .user(promptText)
                .call()
                .content();

            log.debug("📝 生成了 {} 条消息的摘要", items.size());
            return summary;

        } catch (Exception e) {
            log.warn("⚠️ 摘要生成失败，使用简单拼接: {}", e.getMessage());
            // 降级方案：简单拼接
            return items.stream()
                .filter(i -> i.getImportance() > 0.5)
                .map(i -> i.getContent().substring(0, Math.min(100, i.getContent().length())))
                .collect(Collectors.joining(" | "));
        }
    }

    /**
     * 增量摘要 - 将新消息追加到现有摘要
     */
    public String incrementalSummarize(String existingSummary, List<ContextItem> newItems) {
        if (newItems.isEmpty()) return existingSummary;
        if (existingSummary == null || existingSummary.isEmpty()) {
            return summarize(newItems);
        }

        try {
            String newContent = newItems.stream()
                .map(i -> String.format("%s: %s", i.getRole(), i.getContent()))
                .collect(Collectors.joining("\n\n"));

            String prompt = String.format("""
                现有摘要：
                %s

                新对话：
                %s

                请将新对话整合到现有摘要中，更新关键信息，保持简洁。
                """, existingSummary, newContent);

            return chatClient.prompt(prompt).call().content();

        } catch (Exception e) {
            log.warn("⚠️ 增量摘要失败: {}", e.getMessage());
            return existingSummary + "\n" + summarize(newItems);
        }
    }

    /**
     * 提取关键实体和信息
     */
    public List<String> extractKeyEntities(List<ContextItem> items) {
        String content = items.stream()
            .map(ContextItem::getContent)
            .collect(Collectors.joining(" "));

        // 简单实体提取 (生产环境可用 NER 模型)
        return List.of(
            extractCodeSnippets(content),
            extractUrls(content),
            extractDecisions(items)
        ).stream().flatMap(List::stream).toList();
    }

    private List<String> extractCodeSnippets(String content) {
        return java.util.regex.Pattern.compile("```\\w*\\n([\\s\\S]*?)```")
            .matcher(content)
            .results()
            .map(m -> "[CODE] " + m.group(1).split("\n")[0])
            .limit(5)
            .toList();
    }

    private List<String> extractUrls(String content) {
        return java.util.regex.Pattern.compile("https?://[^\\s]+")
            .matcher(content)
            .results()
            .map(m -> "[URL] " + m.group())
            .limit(5)
            .toList();
    }

    private List<String> extractDecisions(List<ContextItem> items) {
        return items.stream()
            .filter(i -> i.getContent().contains("决定") || i.getContent().contains("使用"))
            .map(i -> "[DECISION] " + i.getContent().substring(0, Math.min(80, i.getContent().length())))
            .limit(5)
            .toList();
    }
}