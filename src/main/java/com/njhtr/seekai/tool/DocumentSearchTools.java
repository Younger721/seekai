package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档搜索工具 - 提供技术文档检索功能
 */
@Slf4j
@Component
public class DocumentSearchTools {
    
    /**
     * 搜索技术文档
     * 
     * @param query 搜索关键词
     * @param category 文档分类（可选）
     * @return 搜索结果 JSON
     */
    @Tool(description = "搜索技术文档和知识库。返回包含相关文档片段、来源链接和相关性评分的 JSON 结果。适用于查找 API 文档、使用示例、最佳实践等。")
    public String searchDocumentation(
            @ToolParam(description = "搜索关键词或问题") String query,
            @ToolParam(description = "文档分类：framework, database, cloud, tooling 等（可选）") String category) {
        
        log.info("📚 执行文档搜索工具：query={}, category={}", query, category);
        
        // 模拟文档搜索（实际项目可以集成 Elasticsearch、向量数据库等）
        List<Map<String, Object>> results = new ArrayList<>();
        
        // 根据关键词返回模拟结果
        if (query.toLowerCase().contains("spring")) {
            results.add(createDocumentResult(
                "Spring Boot 官方文档",
                "Spring Boot 可以快速创建独立的、生产级的基于 Spring 的应用程序...",
                "https://spring.io/projects/spring-boot",
                0.95
            ));
            results.add(createDocumentResult(
                "Spring AI 快速入门",
                "Spring AI 提供了与 AI 模型交互的简洁 API，支持聊天、图像生成等功能...",
                "https://docs.spring.io/spring-ai/reference/",
                0.88
            ));
        }
        
        if (query.toLowerCase().contains("java") || query.toLowerCase().contains("stream")) {
            results.add(createDocumentResult(
                "Java Stream API 指南",
                "Stream API 是 Java 8 引入的处理集合的强大工具，支持函数式编程风格...",
                "https://docs.oracle.com/javase/tutorial/collections/streams/",
                0.92
            ));
        }
        
        if (query.toLowerCase().contains("database") || query.toLowerCase().contains("sql")) {
            results.add(createDocumentResult(
                "MySQL 性能优化",
                "数据库性能优化包括索引优化、查询优化、表结构设计等多个方面...",
                "https://dev.mysql.com/doc/",
                0.85
            ));
        }
        
        // 如果没有匹配的结果，返回通用提示
        if (results.isEmpty()) {
            results.add(createDocumentResult(
                "通用技术文档",
                "未找到精确匹配的文档。建议：1. 尝试不同的关键词 2. 使用更具体的术语 3. 查阅官方文档",
                "https://stackoverflow.com",
                0.5
            ));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("query", query);
        response.put("category", category != null ? category : "all");
        response.put("resultsCount", results.size());
        response.put("results", results);
        
        String jsonResult = convertToJson(response);
        log.info("✅ 文档搜索完成：找到 {} 个结果", results.size());
        
        return jsonResult;
    }
    
    /**
     * 获取热门文档主题
     */
    @Tool(description = "获取当前热门的技术文档主题和趋势")
    public String getTrendingTopics() {
        log.info("🔥 获取热门文档主题");
        
        List<String> topics = List.of(
            "Spring Boot 3.0 新特性",
            "Java 21 虚拟线程",
            "微服务架构模式",
            "Docker 容器化部署",
            "Kubernetes 编排",
            "React Server Components",
            "TypeScript 5.0 新语法",
            "AI 辅助编程工具"
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("trendingTopics", topics);
        
        return convertToJson(response);
    }
    
    // ========== 辅助方法 ==========
    
    private Map<String, Object> createDocumentResult(String title, String snippet, String sourceUrl, Double relevance) {
        Map<String, Object> result = new HashMap<>();
        result.put("title", title);
        result.put("snippet", snippet);
        result.put("sourceUrl", sourceUrl);
        result.put("relevance", relevance);
        return result;
    }
    
    private String convertToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(entry.getValue()).append("\"");
            } else if (entry.getValue() instanceof List) {
                json.append(convertListToJson((List<?>) entry.getValue()));
            } else {
                json.append(entry.getValue());
            }
            json.append(",\n");
        }
        if (json.charAt(json.length() - 2) == ',') {
            json.setLength(json.length() - 2);
        }
        json.append("\n}");
        return json.toString();
    }
    
    @SuppressWarnings("unchecked")
    private String convertListToJson(List<?> list) {
        StringBuilder json = new StringBuilder("[\n");
        for (Object item : list) {
            json.append("  ");
            if (item instanceof Map) {
                json.append(convertToJson((Map<String, Object>) item));
            } else {
                json.append("\"").append(item).append("\"");
            }
            json.append(",\n");
        }
        if (json.charAt(json.length() - 2) == ',') {
            json.setLength(json.length() - 2);
        }
        json.append("\n]");
        return json.toString();
    }
}
