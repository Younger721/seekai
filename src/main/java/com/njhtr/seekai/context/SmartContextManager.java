package com.njhtr.seekai.context;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 智能上下文管理器
 *
 * 算法策略：
 * 1. 分层存储: L1(热) -> L2(温) -> L3(冷)
 * 2. 动态摘要: 超过阈值自动压缩
 * 3. 选择性检索: 只加载相关上下文
 * 4. 成本优化: token 预算分配
 */
@Slf4j
@Component
public class SmartContextManager {

    // 配置参数
    private static final int L1_SIZE = 6;           // L1 热区保留最近 6 条
    private static final int L2_SIZE = 20;          // L2 温区保留 20 条
    private static final int L1_TOKEN_BUDGET = 2000;  // L1 最大 token
    private static final int L2_SUMMARY_BUDGET = 800; // L2 摘要最大 token
    private static final double COMPRESSION_RATIO = 0.2; // 压缩比

    private final ImportanceScorer scorer;
    private final ContextSummarizer summarizer;

    // 内存缓存: conversationId -> ContextLayer
    private final Map<String, ContextLayer> contextCache = new ConcurrentHashMap<>();

    public SmartContextManager(ImportanceScorer scorer, ContextSummarizer summarizer) {
        this.scorer = scorer;
        this.summarizer = summarizer;
    }

    /**
     * 添加消息到上下文
     */
    public void addMessage(String conversationId, String role, String content, String agentName) {
        ContextLayer layer = contextCache.computeIfAbsent(conversationId, k -> new ContextLayer(conversationId));

        ContextItem item = new ContextItem(role, content);
        item.setAgentName(agentName);
        item.setTimestamp(LocalDateTime.now());

        // 入库 L1
        layer.l1Items.add(item);

        // 触发压缩检查
        if (layer.l1Items.size() > L1_SIZE * 1.5) {
            compressLayer1ToL2(layer);
        }

        log.debug("📬 添加消息到 {} 的 L1，当前 L1 大小: {}", conversationId, layer.l1Items.size());
    }

    /**
     * L1 -> L2 压缩
     */
    private void compressLayer1ToL2(ContextLayer layer) {
        if (layer.l1Items.size() < L1_SIZE) return;

        // 保留最近 L1_SIZE 条，其余压缩到 L2
        List<ContextItem> toCompress = new ArrayList<>(layer.l1Items);
        List<ContextItem> keepInL1 = toCompress.subList(Math.max(0, toCompress.size() - L1_SIZE), toCompress.size());

        List<ContextItem> compressThese = toCompress.subList(0, toCompress.size() - L1_SIZE);
        if (!compressThese.isEmpty()) {
            // 重要性排序后选择性保留
            List<ContextItem> important = scorer.rankByImportance(compressThese).stream()
                .limit(L2_SIZE)
                .toList();

            // 标记为已摘要
            important.forEach(i -> i.setSummarized(true));

            // 生成摘要
            String summary = summarizer.summarize(compressThese);
            ContextItem summaryItem = new ContextItem("summary", summary);
            summaryItem.setSummarized(true);
            summaryItem.setImportance(0.7);

            layer.l2Items.add(summaryItem);
            layer.l2Items.addAll(important);

            // 限制 L2 大小
            if (layer.l2Items.size() > L2_SIZE * 2) {
                trimL2(layer);
            }
        }

        // 清空 L1，保留最新的
        layer.l1Items.clear();
        layer.l1Items.addAll(keepInL1);

        log.info("🔄 {} 压缩了 {} 条消息到 L2", layer.conversationId, compressThese.size());
    }

    /**
     * 裁剪 L2 (保留高重要性)
     */
    private void trimL2(ContextLayer layer) {
        List<ContextItem> sorted = scorer.rankByImportance(layer.l2Items);
        layer.l2Items = new LinkedList<>(sorted.subList(0, L2_SIZE));
        log.debug("✂️ L2 裁剪到 {} 条", L2_SIZE);
    }

    /**
     * 获取完整上下文 (用于 AI)
     */
    public List<ContextItem> getContext(String conversationId) {
        ContextLayer layer = contextCache.get(conversationId);
        if (layer == null) return List.of();

        List<ContextItem> result = new ArrayList<>();

        // L1: 完整保留
        result.addAll(layer.l1Items);

        // L2: 加载高重要性 + 摘要
        List<ContextItem> importantL2 = scorer.rankByImportance(layer.l2Items).stream()
            .limit(5)
            .toList();
        result.addAll(importantL2);

        // L3: 仅关键信息
        result.addAll(layer.l3KeyInfo.stream().limit(3).toList());

        // 按时间排序
        result.sort(Comparator.comparing(ContextItem::getTimestamp));

        return result;
    }

    /**
     * 获取优化后的 prompt 内容 (token 优化)
     */
    public String getOptimizedContext(String conversationId) {
        List<ContextItem> items = getContext(conversationId);

        StringBuilder sb = new StringBuilder();
        sb.append("## 对话历史\n\n");

        // 分组输出
        List<ContextItem> l1Items = items.stream()
            .filter(i -> !i.isSummarized() && items.indexOf(i) >= items.size() - L1_SIZE)
            .toList();

        List<ContextItem> summaryItems = items.stream()
            .filter(ContextItem::isSummarized)
            .toList();

        if (!l1Items.isEmpty()) {
            sb.append("### 最近对话:\n");
            for (ContextItem item : l1Items) {
                sb.append(String.format("%s: %s\n\n", item.getRole(), truncate(item.getContent(), 500)));
            }
        }

        if (!summaryItems.isEmpty()) {
            sb.append("\n### 历史摘要:\n");
            for (ContextItem item : summaryItems) {
                sb.append(String.format("- %s\n", truncate(item.getContent(), 200)));
            }
        }

        return sb.toString();
    }

    /**
     * 选择性检索上下文 (RAG 风格)
     */
    public List<ContextItem> retrieveRelevantContext(String conversationId, String query) {
        ContextLayer layer = contextCache.get(conversationId);
        if (layer == null) return List.of();

        // 简单关键词匹配 (生产环境可用向量检索)
        String lowerQuery = query.toLowerCase();
        Set<String> keywords = extractKeywords(lowerQuery);

        List<ContextItem> allItems = new ArrayList<>();
        allItems.addAll(layer.l1Items);
        allItems.addAll(layer.l2Items);
        allItems.addAll(layer.l3KeyInfo);

        return allItems.stream()
            .filter(item -> {
                String content = item.getContent().toLowerCase();
                return keywords.stream().anyMatch(content::contains);
            })
            .sorted((a, b) -> Double.compare(b.getImportance(), a.getImportance()))
            .limit(5)
            .toList();
    }

    /**
     * 提取查询关键词
     */
    private Set<String> extractKeywords(String query) {
        // 简单分词
        return Arrays.stream(query.split("[\\s,，。.]"))
            .filter(w -> w.length() > 2)
            .collect(Collectors.toSet());
    }

    /**
     * 移到 L3 (长期存储)
     */
    public void archiveToL3(String conversationId, String key, String value) {
        ContextLayer layer = contextCache.computeIfAbsent(conversationId, k -> new ContextLayer(conversationId));

        ContextItem item = new ContextItem("archive", value);
        item.setContent(key + ": " + value);
        item.setImportance(1.0);

        layer.l3KeyInfo.add(item);
        log.info("📦 {} 归档到 L3: {}", conversationId, key);
    }

    /**
     * 获取 L3 关键信息
     */
    public List<ContextItem> getL3KeyInfo(String conversationId) {
        ContextLayer layer = contextCache.get(conversationId);
        return layer != null ? new ArrayList<>(layer.l3KeyInfo) : List.of();
    }

    /**
     * 清空会话上下文
     */
    public void clear(String conversationId) {
        contextCache.remove(conversationId);
        log.info("🗑️ 清空上下文: {}", conversationId);
    }

    /**
     * 统计信息
     */
    public ContextStats getStats(String conversationId) {
        ContextLayer layer = contextCache.get(conversationId);
        if (layer == null) return new ContextStats(0, 0, 0);

        return new ContextStats(
            layer.l1Items.size(),
            layer.l2Items.size(),
            layer.l3KeyInfo.size()
        );
    }

    private String truncate(String content, int maxLen) {
        if (content.length() <= maxLen) return content;
        return content.substring(0, maxLen) + "...";
    }

    /**
     * 分层存储结构
     */
    @Data
    private static class ContextLayer {
        String conversationId;
        LinkedList<ContextItem> l1Items = new LinkedList<>();  // 热区
        List<ContextItem> l2Items = new ArrayList<>();         // 温区
        List<ContextItem> l3KeyInfo = new ArrayList<>();       // 冷区 (关键信息)

        ContextLayer(String conversationId) {
            this.conversationId = conversationId;
        }
    }

    /**
     * 统计信息
     */
    public record ContextStats(int l1Count, int l2Count, int l3Count) {
        public int total() { return l1Count + l2Count + l3Count; }
    }
}