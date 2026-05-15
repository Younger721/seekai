package com.njhtr.seekai.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能注册中心
 * 管理所有可用技能
 */
@Slf4j
@Component
public class SkillRegistry {

    // 技能存储: skillId -> Skill
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    // 执行器存储: type -> executor
    private final Map<String, SkillExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册技能
     */
    public void registerSkill(Skill skill) {
        if (skill.getId() == null) {
            skill.setId(skill.getName().toLowerCase().replace(" ", "_"));
        }
        skills.put(skill.getId(), skill);
        log.info("📦 注册技能: {} ({})", skill.getName(), skill.getId());
    }

    /**
     * 注册执行器
     */
    public void registerExecutor(String type, SkillExecutor executor) {
        executors.put(type, executor);
        log.info("⚙️ 注册执行器: {}", type);
    }

    /**
     * 获取技能
     */
    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }

    /**
     * 获取所有技能
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 根据分类获取技能
     */
    public List<Skill> getSkillsByCategory(String category) {
        return skills.values().stream()
            .filter(s -> category.equals(s.getCategory()))
            .toList();
    }

    /**
     * 根据标签搜索技能
     */
    public List<Skill> searchByTag(String tag) {
        return skills.values().stream()
            .filter(s -> s.getTags() != null && s.getTags().contains(tag))
            .toList();
    }

    /**
     * 根据触发词查找技能
     */
    public Skill findByTrigger(String trigger) {
        return skills.values().stream()
            .filter(s -> trigger.equals(s.getTrigger()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 查找相关技能 (根据名称/描述匹配)
     */
    public List<Skill> findRelated(String query) {
        String lowerQuery = query.toLowerCase();
        return skills.values().stream()
            .filter(s ->
                s.getName().toLowerCase().contains(lowerQuery) ||
                s.getDescription().toLowerCase().contains(lowerQuery) ||
                (s.getTags() != null && s.getTags().stream()
                    .anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
            )
            .sorted((a, b) -> Integer.compare(b.getUsageCount(), a.getUsageCount()))
            .toList();
    }

    /**
     * 执行技能
     */
    public SkillExecutor.SkillResult executeSkill(String skillId, Map<String, Object> params,
                                                    SkillExecutor.ExecutionContext context) {
        Skill skill = getSkill(skillId);
        if (skill == null) {
            return SkillExecutor.SkillResult.fail("技能不存在: " + skillId);
        }

        // 检查权限
        if (context.getPermissionChecker() != null) {
            boolean hasPermission = context.getPermissionChecker()
                .hasPermission(skill.getPermission());
            if (!hasPermission) {
                return SkillExecutor.SkillResult.fail("权限不足，需要: " + skill.getPermission());
            }
        }

        // 获取执行器
        SkillExecutor executor = findExecutor(skill);
        if (executor == null) {
            return SkillExecutor.SkillResult.fail("没有找到对应的执行器");
        }

        // 验证参数
        if (!executor.validate(skill, params)) {
            return SkillExecutor.SkillResult.fail("参数验证失败");
        }

        // 执行
        long start = System.currentTimeMillis();
        SkillExecutor.SkillResult result = executor.execute(skill, params, context);

        // 更新使用统计
        skill.setUsageCount(skill.getUsageCount() + 1);
        result.setExecutionTimeMs(System.currentTimeMillis() - start);

        return result;
    }

    /**
     * 查找执行器
     */
    private SkillExecutor findExecutor(Skill skill) {
        String type = skill.getConfig() != null
            ? (String) skill.getConfig().get("executorType")
            : null;

        if (type != null) {
            return executors.get(type);
        }

        // 尝试从能力中推断
        if (skill.getCapabilities() != null && !skill.getCapabilities().isEmpty()) {
            String capName = skill.getCapabilities().get(0).getName().toLowerCase();
            for (Map.Entry<String, SkillExecutor> entry : executors.entrySet()) {
                if (capName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // 默认返回第一个可用的
        return executors.values().stream().findFirst().orElse(null);
    }

    /**
     * 移除技能
     */
    public void unregisterSkill(String skillId) {
        skills.remove(skillId);
        log.info("🗑️ 移除技能: {}", skillId);
    }

    /**
     * 获取技能统计
     */
    public Map<String, Object> getStats() {
        Map<String, Long> categoryCount = skills.values().stream()
            .collect(Collectors.groupingBy(Skill::getCategory, Collectors.counting()));

        return Map.of(
            "totalSkills", (long) skills.size(),
            "totalExecutors", (long) executors.size(),
            "byCategory", categoryCount,
            "topSkills", skills.values().stream()
                .sorted((a, b) -> Integer.compare(b.getUsageCount(), a.getUsageCount()))
                .limit(5)
                .map(s -> Map.of("name", s.getName(), "usage", s.getUsageCount()))
                .toList()
        );
    }

    /**
     * 初始化内置技能
     */
    @PostConstruct
    public void init() {
        log.info("🔧 初始化技能系统...");

        // 注册内置技能
        registerBuiltInSkills();

        // 加载插件技能
        loadPluginSkills();

        log.info("✅ 技能系统初始化完成，共 {} 个技能", skills.size());
    }

    private void registerBuiltInSkills() {
        // AI 对话技能
        Skill chatSkill = new Skill();
        chatSkill.setId("ai_chat");
        chatSkill.setName("AI 对话");
        chatSkill.setDescription("与 AI 进行对话，获得智能回复");
        chatSkill.setCategory("assistant");
        chatSkill.setTrigger("chat");
        chatSkill.setSource("builtin");
        chatSkill.setTags(List.of("ai", "chat", "对话"));
        chatSkill.setPermission(Skill.Permission.READ_ONLY);
        registerSkill(chatSkill);

        // 代码生成技能
        Skill codeSkill = new Skill();
        codeSkill.setId("code_generator");
        codeSkill.setName("代码生成");
        codeSkill.setDescription("根据需求自动生成代码");
        codeSkill.setCategory("development");
        codeSkill.setTrigger("code");
        codeSkill.setSource("builtin");
        codeSkill.setTags(List.of("code", "generator", "编程"));
        codeSkill.setPermission(Skill.Permission.READ_ONLY);
        registerSkill(codeSkill);

        // 数据分析技能
        Skill analyzeSkill = new Skill();
        analyzeSkill.setId("data_analysis");
        analyzeSkill.setName("数据分析");
        analyzeSkill.setDescription("分析数据并生成报告");
        analyzeSkill.setCategory("analysis");
        analyzeSkill.setTrigger("analyze");
        analyzeSkill.setSource("builtin");
        analyzeSkill.setTags(List.of("data", "analysis", "分析"));
        analyzeSkill.setPermission(Skill.Permission.READ_ONLY);
        registerSkill(analyzeSkill);

        // 翻译技能
        Skill translateSkill = new Skill();
        translateSkill.setId("translator");
        translateSkill.setName("翻译");
        translateSkill.setDescription("多语言翻译");
        translateSkill.setCategory("tool");
        translateSkill.setTrigger("translate");
        translateSkill.setSource("builtin");
        translateSkill.setTags(List.of("translate", "translation", "语言"));
        translateSkill.setPermission(Skill.Permission.READ_ONLY);
        registerSkill(translateSkill);

        // 摘要技能
        Skill summarySkill = new Skill();
        summarySkill.setId("summarizer");
        summarySkill.setName("摘要");
        summarySkill.setDescription("将长文本压缩成简短摘要");
        summarySkill.setCategory("tool");
        summarySkill.setTrigger("summarize");
        summarySkill.setSource("builtin");
        summarySkill.setTags(List.of("summary", "摘要", "压缩"));
        summarySkill.setPermission(Skill.Permission.READ_ONLY);
        registerSkill(summarySkill);
    }

    private void loadPluginSkills() {
        // TODO: 从插件目录加载自定义技能
        log.info("📂 扫描插件目录...");
    }
}