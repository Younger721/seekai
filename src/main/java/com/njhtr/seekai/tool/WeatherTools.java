package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 天气查询工具 - 演示函数式工具调用
 */
@Slf4j
@Component
public class WeatherTools {
    
    /**
     * 天气查询函数
     * 这是一个 Function bean，会被 Spring AI 自动解析为工具
     */
    public Function<WeatherRequest, WeatherResponse> getWeather() {
        return request -> {
            log.info("🌤️ 查询天气：location={}, unit={}", request.location(), request.unit());
            
            // 模拟天气数据（实际项目可以调用天气 API）
            Map<String, Double> mockData = new HashMap<>();
            mockData.put("北京", 25.0);
            mockData.put("上海", 28.0);
            mockData.put("广州", 32.0);
            mockData.put("深圳", 31.0);
            mockData.put("成都", 22.0);
            mockData.put("杭州", 26.0);
            
            double temp = mockData.getOrDefault(request.location(), 20.0);
            
            if ("F".equals(request.unit())) {
                temp = (temp * 9.0 / 5.0) + 32; // 转换为华氏度
            }
            
            String condition = temp > 30 ? "晴朗炎热" : 
                              temp > 25 ? "温暖舒适" : 
                              temp > 20 ? "凉爽宜人" : "有点冷";
            
            return new WeatherResponse(
                request.location(),
                temp,
                request.unit() != null ? request.unit() : "C",
                condition,
                65, // 湿度
                "东南风 2 级" // 风力
            );
        };
    }
    
    /**
     * 天气请求记录
     */
    public record WeatherRequest(
        @ToolParam(description = "城市名称，例如：北京、上海、广州等") String location,
        @ToolParam(description = "温度单位：C 表示摄氏度，F 表示华氏度（可选，默认为 C）") String unit
    ) {}
    
    /**
     * 天气响应记录
     */
    public record WeatherResponse(
        String location,
        Double temperature,
        String unit,
        String condition,
        Integer humidity,
        String wind
    ) {}
}
