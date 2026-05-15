package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Random;

/**
 * 异常处理演示工具 - 展示错误处理和恢复机制
 */
@Slf4j
@Component
public class ExceptionHandlingTools {
    
    private final Random random = new Random();
    
    /**
     * 模拟可能失败的操作（演示异常处理）
     */
    @Tool(description = "执行一个可能失败的操作。用于测试系统的错误处理能力。")
    public String riskyOperation(
            @ToolParam(description = "失败概率 (0.0-1.0)，默认 0.3", required = false) Double failureRate) {
        
        log.info("⚠️ 执行风险操作：failureRate={}", failureRate);
        
        double rate = failureRate != null ? failureRate : 0.3;
        
        if (random.nextDouble() < rate) {
            log.error("❌ 操作失败！");
            throw new RuntimeException("模拟的业务异常：数据库连接超时");
        }
        
        return "✅ 操作成功完成！处理了 " + (random.nextInt(100) + 1) + " 条记录。";
    }
    
    /**
     * 模拟 IO 异常（检查型异常）
     */
    @Tool(description = "模拟文件读取操作，可能抛出 IOException")
    public String readFile(
            @ToolParam(description = "文件路径") String filePath) throws IOException {
        
        log.info("📄 读取文件：{}", filePath);
        
        // 模拟文件不存在
        if (!filePath.endsWith(".txt")) {
            throw new IOException("文件不存在或格式不正确：" + filePath);
        }
        
        return "文件内容预览：这是文件的第 1 行...\n这是文件的第 2 行...\n...";
    }
    
    /**
     * 带重试机制的操作（演示恢复逻辑）
     */
    @Tool(description = "执行操作，失败时自动重试。最多重试 3 次。")
    public String operationWithRetry(
            @ToolParam(description = "操作名称") String operationName) {
        
        log.info("🔄 执行带重试的操作：{}", operationName);
        
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("尝试第 {} 次...", attempt);
                
                // 模拟前两次失败，第三次成功
                if (attempt < 3 && random.nextBoolean()) {
                    throw new RuntimeException("临时网络错误");
                }
                
                return String.format("✅ 操作 '%s' 在第 %d 次尝试时成功！", operationName, attempt);
                
            } catch (Exception e) {
                log.warn("第 {} 次尝试失败：{}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    return String.format("❌ 操作 '%s' 失败，已重试 %d 次。最后错误：%s", 
                        operationName, maxRetries, e.getMessage());
                }
            }
        }
        
        return "操作完成";
    }
    
    /**
     * 验证输入（演示参数校验）
     */
    @Tool(description = "验证用户输入的合法性")
    public String validateInput(
            @ToolParam(description = "要验证的输入值") String input,
            @ToolParam(description = "验证类型：email, phone, number", required = false) String type) {
        
        log.info("✓ 验证输入：{}, type={}", input, type);
        
        if (input == null || input.trim().isEmpty()) {
            return "❌ 验证失败：输入不能为空";
        }
        
        String validationType = type != null ? type : "general";
        
        switch (validationType) {
            case "email":
                if (!input.contains("@") || !input.contains(".")) {
                    return "❌ 验证失败：不是有效的邮箱格式";
                }
                return "✅ 邮箱格式正确";
                
            case "phone":
                if (!input.matches("\\d{11}")) {
                    return "❌ 验证失败：手机号应该是 11 位数字";
                }
                return "✅ 手机号格式正确";
                
            case "number":
                try {
                    Double.parseDouble(input);
                    return "✅ 是有效的数字";
                } catch (NumberFormatException e) {
                    return "❌ 验证失败：不是有效的数字";
                }
                
            default:
                return "✅ 通用验证通过：" + input.trim();
        }
    }
}
