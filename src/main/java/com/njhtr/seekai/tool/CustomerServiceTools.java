package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户服务工具 - 简化版本（移除 ToolContext 和自定义 Converter）
 */
@Slf4j
@Component
public class CustomerServiceTools {
    
    /**
     * 获取客户信息
     */
    @Tool(description = "获取客户的详细信息，包括基本信息、订单历史和账户状态")
    public String getCustomerInfo(@ToolParam(description = "客户 ID") Long customerId) {
        log.info("👤 查询客户信息：id={}", customerId);
        
        // 直接返回 JSON 字符串
        return String.format("""
            {
              "customerId": %d,
              "name": "张三",
              "email": "zhangsan@example.com",
              "phone": "138****1234",
              "level": "VIP",
              "registerDate": "%s",
              "totalOrders": 156,
              "accountStatus": "ACTIVE"
            }
            """, customerId, LocalDateTime.now().minusYears(2));
    }
    
    /**
     * 更新客户邮箱（演示可选参数）
     */
    @Tool(description = "更新客户的电子邮件地址")
    public String updateCustomerEmail(
            @ToolParam(description = "客户 ID") Long customerId,
            @ToolParam(description = "新的电子邮件地址", required = false) String email) {
        
        log.info("✉️ 更新客户邮箱：id={}, email={}", customerId, email);
        
        if (email == null || email.isEmpty()) {
            return "错误：邮箱地址不能为空";
        }
        
        return String.format("成功更新客户 %d 的邮箱为：%s", customerId, email);
    }
    
    /**
     * 获取客户订单（直接返回模式）
     */
    @Tool(
        description = "获取客户的订单列表。此工具直接返回订单数据，不需要 AI 后处理",
        returnDirect = true
    )
    public String getCustomerOrders(
            @ToolParam(description = "客户 ID") Long customerId,
            @ToolParam(description = "页码，默认 1", required = false) Integer page,
            @ToolParam(description = "每页数量，默认 10", required = false) Integer size) {
        
        log.info("📦 查询客户订单：id={}, page={}, size={}", customerId, page, size);
        
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 10;
        
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"customerId\": ").append(customerId).append(",\n");
        json.append("  \"page\": ").append(pageNum).append(",\n");
        json.append("  \"size\": ").append(pageSize).append(",\n");
        json.append("  \"total\": 156,\n");
        json.append("  \"orders\": [\n");
        
        for (int i = 0; i < Math.min(5, pageSize); i++) {
            json.append("    {\n");
            json.append("      \"orderId\": ").append(1000L + i).append(",\n");
            json.append("      \"orderNo\": \"ORD-").append(System.currentTimeMillis()).append("-").append(i).append("\",\n");
            json.append("      \"amount\": ").append(100.0 + i * 50).append(",\n");
            json.append("      \"status\": \"").append(i % 2 == 0 ? "COMPLETED" : "PENDING").append("\",\n");
            json.append("      \"createTime\": \"").append(LocalDateTime.now().minusDays(i)).append("\"\n");
            json.append("    }");
            if (i < Math.min(4, pageSize - 1)) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n}");
        return json.toString();
    }
}
