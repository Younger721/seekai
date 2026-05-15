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
 * 代码分析工具 - 提供代码问题诊断功能
 */
@Slf4j
@Component
public class CodeAnalysisTools {
    
    /**
     * 分析代码中的潜在问题
     * 
     * @param code 要分析的代码
     * @param language 编程语言（java, python, javascript 等）
     * @return 分析报告 JSON
     */
    @Tool(description = "分析代码中的错误、性能和最佳实践问题。返回包含问题列表、严重程度和建议修复方案的 JSON 报告。")
    public String analyzeCode(
            @ToolParam(description = "要分析的源代码代码") String code,
            @ToolParam(description = "编程语言：java, python, javascript, typescript 等") String language) {
        
        log.info("🔧 执行代码分析工具：language={}", language);
        
        // 模拟代码分析（实际项目中可以集成 SonarQube、PMD 等）
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        
        // 简单规则检查
        if (code.contains("System.out.println")) {
            issues.add(createIssue(
                "避免使用 System.out.println",
                "生产代码中应该使用日志框架（如 SLF4J）而不是 System.out.println",
                "MEDIUM",
                "替换为：log.info(\"...\") 或 logger.info(\"...\")"
            ));
        }
        
        if (code.contains("catch") && code.contains("Exception") && !code.contains("log")) {
            issues.add(createIssue(
                "空的 catch 块或未记录的异常处理",
                "捕获异常后应该记录日志或进行处理，不应该忽略",
                "HIGH",
                "在 catch 块中添加日志记录或适当的错误处理逻辑"
            ));
        }
        
        if (code.contains("new Scanner") && !code.contains("close()")) {
            issues.add(createIssue(
                "资源未关闭",
                "Scanner 等 AutoCloseable 资源使用后应该关闭",
                "MEDIUM",
                "使用 try-with-resources 语句：try (Scanner scanner = new Scanner(...)) { ... }"
            ));
        }
        
        if (code.matches(".*\\.equals\\(.*\\).*") && !code.contains("Objects.equals")) {
            issues.add(createIssue(
                "可能的空指针异常",
                "直接调用 .equals() 可能导致 NullPointerException",
                "MEDIUM",
                "使用 Objects.equals(a, b) 或将常量放在前面：\"constant\".equals(variable)"
            ));
        }
        
        result.put("success", true);
        result.put("language", language);
        result.put("issuesCount", issues.size());
        result.put("issues", issues);
        
        String jsonResult = convertToJson(result);
        log.info("✅ 代码分析完成：发现 {} 个问题", issues.size());
        
        return jsonResult;
    }
    
    /**
     * 获取当前时间（用于代码时间戳）
     */
    @Tool(description = "获取当前的日期和时间，用于代码分析的时间戳标记")
    public String getCurrentDateTime() {
        return java.time.LocalDateTime.now().toString();
    }
    
    // ========== 辅助方法 ==========
    
    private Map<String, Object> createIssue(String title, String description, String severity, String suggestion) {
        Map<String, Object> issue = new HashMap<>();
        issue.put("title", title);
        issue.put("description", description);
        issue.put("severity", severity); // HIGH, MEDIUM, LOW
        issue.put("suggestion", suggestion);
        return issue;
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
        // 删除最后一个逗号
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
