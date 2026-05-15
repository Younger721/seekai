package com.njhtr.seekai.controller;

import com.njhtr.seekai.context.SmartChatMemory;
import com.njhtr.seekai.context.SmartContextManager;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能上下文管理 API
 */
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final SmartChatMemory smartMemory;
    private final SmartContextManager contextManager;

    /**
     * 获取上下文统计
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam String conversationId) {
        var stats = smartMemory.getStats(conversationId);
        return Map.of(
            "conversationId", conversationId,
            "l1_hot", stats.l1Count(),
            "l2_warm", stats.l2Count(),
            "l3_cold", stats.l3Count(),
            "total", stats.total()
        );
    }

    /**
     * 获取优化后的上下文字符串
     */
    @GetMapping("/optimized")
    public Map<String, Object> getOptimizedContext(@RequestParam String conversationId) {
        String context = smartMemory.getOptimizedContext(conversationId);
        return Map.of(
            "conversationId", conversationId,
            "context", context,
            "length", context.length()
        );
    }

    /**
     * 获取所有原始消息
     */
    @GetMapping("/messages")
    public List<Message> getMessages(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int lastN
    ) {
        if (lastN > 0) {
            return smartMemory.get(conversationId, lastN);
        }
        return smartMemory.get(conversationId, 20);
    }

    /**
     * 检索相关上下文
     */
    @GetMapping("/retrieve")
    public List<Message> retrieve(
            @RequestParam String conversationId,
            @RequestParam String query
    ) {
        return smartMemory.retrieve(conversationId, query);
    }

    /**
     * 归档重要信息到 L3
     */
    @PostMapping("/archive")
    public Map<String, Object> archive(
            @RequestParam String conversationId,
            @RequestParam String key,
            @RequestParam String value
    ) {
        smartMemory.archive(conversationId, key, value);
        return Map.of("success", true, "message", "已归档到长期记忆");
    }

    /**
     * 获取 L3 长期记忆
     */
    @GetMapping("/archive")
    public List<String> getArchive(@RequestParam String conversationId) {
        return contextManager.getL3KeyInfo(conversationId).stream()
            .map(i -> i.getContent())
            .toList();
    }

    /**
     * 清空上下文
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clear(@RequestParam String conversationId) {
        smartMemory.clear(conversationId);
        return Map.of("success", true, "message", "上下文已清空");
    }
}