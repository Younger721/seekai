package com.njhtr.seekai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆服务 - 核心记忆系统
 * 实现：越用越懂你
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final UserMemoryRepository memoryRepository;

    // ==================== 记忆管理 ====================

    /**
     * 存储新的用户记忆
     */
    @Transactional
    public void remember(String userId, String content, String memoryType, Double importance) {
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setContent(content);
        memory.setMemoryType(memoryType);
        memory.setImportance(importance != null ? importance : 0.5);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());

        memoryRepository.saveMemory(memory);
        log.info("💾 用户 {} 新增记忆: {} (类型: {})", userId, content.substring(0, Math.min(30, content.length())), memoryType);
    }

    /**
     * 获取用户所有记忆
     */
    public List<UserMemory> getUserMemories(String userId) {
        return memoryRepository.findByUserId(userId);
    }

    /**
     * 按类型获取记忆
     */
    public List<UserMemory> getUserMemoriesByType(String userId, String memoryType) {
        return memoryRepository.findByUserIdAndType(userId, memoryType);
    }

    // ==================== 偏好学习 ====================

    /**
     * 学习用户偏好
     */
    @Transactional
    public void learnPreference(String userId, String preferenceKey, String preferenceValue) {
        UserPreference existing = memoryRepository.findPreference(userId, preferenceKey);

        if (existing != null) {
            // 更新已有偏好
            existing.setPreferenceValue(preferenceValue);
            existing.setSampleCount(existing.getSampleCount() + 1);
            // 置信度随样本增加而提高，上限 0.95
            existing.setConfidence(Math.min(0.95, existing.getConfidence() + 0.1));
            existing.setUpdatedAt(LocalDateTime.now());
            memoryRepository.updatePreference(existing);
            log.debug("📈 更新偏好: {} -> {}", preferenceKey, preferenceValue);
        } else {
            // 创建新偏好
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setPreferenceKey(preferenceKey);
            pref.setPreferenceValue(preferenceValue);
            pref.setConfidence(0.3); // 初始置信度
            pref.setSampleCount(1);
            memoryRepository.savePreference(pref);
            log.debug("🆕 新建偏好: {} -> {}", preferenceKey, preferenceValue);
        }
    }

    /**
     * 获取用户所有偏好
     */
    public List<UserPreference> getUserPreferences(String userId) {
        return memoryRepository.findPreferencesByUserId(userId);
    }

    /**
     * 获取特定偏好
     */
    public Optional<UserPreference> getPreference(String userId, String preferenceKey) {
        return Optional.ofNullable(memoryRepository.findPreference(userId, preferenceKey));
    }

    // ==================== 习惯学习 ====================

    /**
     * 记录用户习惯
     */
    @Transactional
    public void recordHabit(String userId, String habitType, String habitKey, String habitValue, String context) {
        UserHabit existing = memoryRepository.findHabit(userId, habitType, habitKey);

        if (existing != null) {
            // 增加频率
            memoryRepository.incrementHabitFrequency(existing.getId());
            existing.setHabitValue(habitValue);
            existing.setLastContext(context);
            existing.setLastUsedAt(LocalDateTime.now());
            memoryRepository.updateHabit(existing);
        } else {
            // 创建新习惯
            UserHabit habit = new UserHabit();
            habit.setUserId(userId);
            habit.setHabitType(habitType);
            habit.setHabitKey(habitKey);
            habit.setHabitValue(habitValue);
            habit.setFrequency(1);
            habit.setLastContext(context);
            habit.setLastUsedAt(LocalDateTime.now());
            memoryRepository.saveHabit(habit);
        }
        log.debug("📊 记录习惯: {} - {}", habitType, habitKey);
    }

    /**
     * 获取用户所有习惯
     */
    public List<UserHabit> getUserHabits(String userId) {
        return memoryRepository.findHabitsByUserId(userId);
    }

    /**
     * 获取用户高频习惯
     */
    public List<UserHabit> getFrequentHabits(String userId, int limit) {
        return memoryRepository.findFrequentHabits(userId, limit);
    }

    // ==================== 上下文构建 ====================

    /**
     * 构建用户画像上下文 (供 AI 使用)
     */
    public String buildUserContext(String userId) {
        StringBuilder context = new StringBuilder();
        context.append("## 用户画像\n\n");

        // 添加偏好
        List<UserPreference> prefs = getUserPreferences(userId);
        if (!prefs.isEmpty()) {
            context.append("### 用户偏好:\n");
            for (UserPreference pref : prefs) {
                context.append(String.format("- %s: %s (置信度: %.0f%%)\n",
                    pref.getPreferenceKey(), pref.getPreferenceValue(), pref.getConfidence() * 100));
            }
            context.append("\n");
        }

        // 添加高频习惯
        List<UserHabit> habits = getFrequentHabits(userId, 5);
        if (!habits.isEmpty()) {
            context.append("### 用户习惯:\n");
            for (UserHabit habit : habits) {
                context.append(String.format("- 常用 %s: %s (使用 %d 次)\n",
                    habit.getHabitType(), habit.getHabitValue(), habit.getFrequency()));
            }
            context.append("\n");
        }

        // 添加重要记忆
        List<UserMemory> importantMemories = getUserMemoriesByType(userId, "IMPORTANT");
        if (!importantMemories.isEmpty()) {
            context.append("### 重要记忆:\n");
            for (UserMemory mem : importantMemories.stream().limit(3).toList()) {
                context.append(String.format("- %s\n", mem.getContent()));
            }
        }

        return context.toString();
    }

    // ==================== 自动学习 ====================

    /**
     * 分析用户输入并自动学习
     * 从用户消息中提取偏好和习惯
     */
    @Transactional
    public void analyzeAndLearn(String userId, String userMessage, String agentName, String agentResponse) {
        // 1. 记录使用的 Agent 习惯
        recordHabit(userId, "agent", agentName, agentName, userMessage);

        // 2. 简单偏好学习 (可扩展为 ML 模型)
        String lowerMsg = userMessage.toLowerCase();

        // 检测回复长度偏好
        if (lowerMsg.contains("简短") || lowerMsg.contains("简单") || lowerMsg.contains("少")) {
            learnPreference(userId, "response_length", "short");
        } else if (lowerMsg.contains("详细") || lowerMsg.contains("多点") || lowerMsg.contains("完整")) {
            learnPreference(userId, "response_length", "long");
        }

        // 检测语气偏好
        if (lowerMsg.contains("正式") || lowerMsg.contains("专业")) {
            learnPreference(userId, "tone", "formal");
        } else if (lowerMsg.contains("轻松") || lowerMsg.contains("随意") || lowerMsg.contains("幽默")) {
            learnPreference(userId, "tone", "casual");
        }

        // 检测代码风格偏好
        if (lowerMsg.contains("java") || lowerMsg.contains("spring")) {
            learnPreference(userId, "preferred_language", "java");
        } else if (lowerMsg.contains("python")) {
            learnPreference(userId, "preferred_language", "python");
        }

        log.info("🧠 用户 {} 分析学习完成", userId);
    }
}