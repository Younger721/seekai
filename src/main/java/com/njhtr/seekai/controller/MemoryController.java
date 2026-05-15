package com.njhtr.seekai.controller;

import com.njhtr.seekai.memory.MemoryService;
import com.njhtr.seekai.memory.UserHabit;
import com.njhtr.seekai.memory.UserMemory;
import com.njhtr.seekai.memory.UserPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记忆系统 API - "越用越懂你"
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * 手动添加记忆
     */
    @PostMapping("/remember")
    public Map<String, Object> remember(
            @RequestParam String userId,
            @RequestParam String content,
            @RequestParam(required = false) String memoryType,
            @RequestParam(required = false, defaultValue = "0.5") Double importance
    ) {
        memoryService.remember(userId, content,
            memoryType != null ? memoryType : "IMPORTANT",
            importance);
        return Map.of("success", true, "message", "记忆已保存");
    }

    /**
     * 获取用户所有记忆
     */
    @GetMapping("/memories")
    public List<UserMemory> getMemories(@RequestParam String userId) {
        return memoryService.getUserMemories(userId);
    }

    /**
     * 获取指定类型记忆
     */
    @GetMapping("/memories/{type}")
    public List<UserMemory> getMemoriesByType(
            @RequestParam String userId,
            @PathVariable String type
    ) {
        return memoryService.getUserMemoriesByType(userId, type);
    }

    /**
     * 获取用户偏好
     */
    @GetMapping("/preferences")
    public List<UserPreference> getPreferences(@RequestParam String userId) {
        return memoryService.getUserPreferences(userId);
    }

    /**
     * 获取用户习惯
     */
    @GetMapping("/habits")
    public List<UserHabit> getHabits(@RequestParam String userId) {
        return memoryService.getUserHabits(userId);
    }

    /**
     * 获取高频习惯
     */
    @GetMapping("/habits/frequent")
    public List<UserHabit> getFrequentHabits(
            @RequestParam String userId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return memoryService.getFrequentHabits(userId, limit);
    }

    /**
     * 获取用户完整画像 (供 AI 使用)
     */
    @GetMapping("/context")
    public Map<String, Object> getUserContext(@RequestParam String userId) {
        return Map.of(
            "userId", userId,
            "context", memoryService.buildUserContext(userId),
            "preferences", memoryService.getUserPreferences(userId),
            "habits", memoryService.getFrequentHabits(userId, 5)
        );
    }

    /**
     * 记录用户习惯 (自动接口)
     */
    @PostMapping("/habit")
    public Map<String, Object> recordHabit(
            @RequestParam String userId,
            @RequestParam String habitType,
            @RequestParam String habitKey,
            @RequestParam(required = false) String habitValue,
            @RequestParam(required = false) String context
    ) {
        memoryService.recordHabit(userId, habitType, habitKey,
            habitValue != null ? habitValue : habitKey,
            context);
        return Map.of("success", true);
    }

    /**
     * 学习用户偏好
     */
    @PostMapping("/learn")
    public Map<String, Object> learn(
            @RequestParam String userId,
            @RequestParam String preferenceKey,
            @RequestParam String preferenceValue
    ) {
        memoryService.learnPreference(userId, preferenceKey, preferenceValue);
        return Map.of("success", true, "message", "偏好已学习");
    }

    /**
     * 分析并自动学习 (基于对话)
     */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestParam String userId,
            @RequestParam String userMessage,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String agentResponse
    ) {
        memoryService.analyzeAndLearn(userId, userMessage,
            agentName != null ? agentName : "GENERAL",
            agentResponse != null ? agentResponse : "");
        return Map.of("success", true, "message", "学习完成");
    }

    /**
     * 获取用户使用的最佳 Agent 推荐
     */
    @GetMapping("/recommend/agent")
    public Map<String, Object> recommendAgent(@RequestParam String userId) {
        List<UserHabit> agentHabits = memoryService.getFrequentHabits(userId, 1)
            .stream()
            .filter(h -> "agent".equals(h.getHabitType()))
            .toList();

        if (agentHabits.isEmpty()) {
            return Map.of(
                "recommended", "GENERAL_HELPER",
                "reason", "暂无使用习惯，推荐通用助手"
            );
        }

        UserHabit topHabit = agentHabits.get(0);
        return Map.of(
            "recommended", topHabit.getHabitValue(),
            "frequency", topHabit.getFrequency(),
            "reason", String.format("您已使用 %d 次", topHabit.getFrequency())
        );
    }
}