package com.njhtr.seekai.context;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 重要性评分器
 * 算法: 关键词 + 实体 + 位置 + 时间衰减
 */
@Component
public class ImportanceScorer {

    // 高权重关键词
    private static final Set<String> HIGH_PRIORITY_KEYWORDS = Set.of(
        "bug", "error", "fix", "重要", "关键", "必须", "紧急",
        "api", "接口", "数据", "database", "sql", "记住", "别忘了",
        "首先", "然后", "最后", "结果", "成功", "失败", "问题"
    );

    // 实体模式
    private static final Pattern CODE_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`]+`");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+|\\d{4}[-/]\\d{2}[-/]\\d{2}");

    /**
     * 计算单条消息的重要性
     */
    public double score(ContextItem item, int totalMessages, int currentIndex) {
        double score = item.getBaseImportance();

        // 1. 关键词加权
        score += keywordBonus(item.getContent());

        // 2. 实体加权 (代码、URL、数字)
        score += entityBonus(item.getContent());

        // 3. 位置加权 (越近越重要)
        double positionWeight = 1.0 + (double) currentIndex / totalMessages * 0.3;
        score *= positionWeight;

        // 4. 长度惩罚 (太长/太短都会降低权重)
        score *= lengthPenalty(item.getContent());

        // 5. 禁止词惩罚
        score += forbiddenPenalty(item.getContent());

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 关键词加分
     */
    private double keywordBonus(String content) {
        String lower = content.toLowerCase();
        double bonus = 0;
        for (String kw : HIGH_PRIORITY_KEYWORDS) {
            if (lower.contains(kw)) {
                bonus += 0.05;
            }
        }
        return Math.min(0.2, bonus);
    }

    /**
     * 实体加分 (代码/URL/数字)
     */
    private double entityBonus(String content) {
        double bonus = 0;
        if (CODE_PATTERN.matcher(content).find()) bonus += 0.15;
        if (URL_PATTERN.matcher(content).find()) bonus += 0.1;
        if (NUMBER_PATTERN.matcher(content).find()) bonus += 0.05;
        return Math.min(0.25, bonus);
    }

    /**
     * 长度惩罚
     */
    private double lengthPenalty(String content) {
        int len = content.length();
        if (len < 10) return 0.8;      // 太短
        if (len > 2000) return 0.7;    // 太长
        if (len > 500) return 0.9;     // 较长
        return 1.0;
    }

    /**
     * 禁止词惩罚
     */
    private double forbiddenPenalty(String content) {
        String lower = content.toLowerCase();
        // 简单的客套话/填充词
        if (lower.contains("谢谢") && lower.length() < 50) return -0.1;
        if (lower.contains("好的") && lower.length() < 30) return -0.1;
        if (lower.contains("明白") && lower.length() < 30) return -0.1;
        return 0;
    }

    /**
     * 对一组消息进行重要性排序
     */
    public List<ContextItem> rankByImportance(List<ContextItem> items) {
        int total = items.size();
        return items.stream()
            .peek(item -> item.setImportance(score(item, total, items.indexOf(item))))
            .sorted((a, b) -> Double.compare(b.getImportance(), a.getImportance()))
            .toList();
    }
}