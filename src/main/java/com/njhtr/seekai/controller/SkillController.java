package com.njhtr.seekai.controller;

import com.njhtr.seekai.skill.Skill;
import com.njhtr.seekai.skill.SkillExecutor;
import com.njhtr.seekai.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能系统 API
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistry skillRegistry;

    /**
     * 获取所有技能
     */
    @GetMapping("/list")
    public Map<String, Object> listSkills(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag
    ) {
        List<Skill> skills;

        if (category != null) {
            skills = skillRegistry.getSkillsByCategory(category);
        } else if (tag != null) {
            skills = skillRegistry.searchByTag(tag);
        } else {
            skills = skillRegistry.getAllSkills();
        }

        return Map.of(
            "skills", skills,
            "total", skills.size()
        );
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{skillId}")
    public Map<String, Object> getSkill(@PathVariable String skillId) {
        Skill skill = skillRegistry.getSkill(skillId);
        if (skill == null) {
            return Map.of("success", false, "error", "技能不存在");
        }

        return Map.of(
            "success", true,
            "skill", skill
        );
    }

    /**
     * 搜索技能
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q) {
        List<Skill> skills = skillRegistry.findRelated(q);

        return Map.of(
            "query", q,
            "skills", skills,
            "count", skills.size()
        );
    }

    /**
     * 执行技能
     */
    @PostMapping("/execute/{skillId}")
    public Map<String, Object> execute(
            @PathVariable String skillId,
            @RequestBody(required = false) Map<String, Object> params,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId
    ) {
        if (params == null) {
            params = Map.of();
        }

        // 构建执行上下文
        SkillExecutor.ExecutionContext context = new SkillExecutor.ExecutionContext();
        context.setUserId(userId != null ? userId : "anonymous");
        context.setConversationId(conversationId != null ? conversationId : "default");

        // 执行技能
        SkillExecutor.SkillResult result = skillRegistry.executeSkill(skillId, params, context);

        return Map.of(
            "skillId", skillId,
            "success", result.isSuccess(),
            "output", result.getOutput() != null ? result.getOutput() : "",
            "error", result.getError() != null ? result.getError() : "",
            "executionTime", result.getExecutionTimeMs()
        );
    }

    /**
     * 根据触发词执行技能
     */
    @PostMapping("/trigger")
    public Map<String, Object> trigger(
            @RequestParam String trigger,
            @RequestBody(required = false) Map<String, Object> params,
            @RequestParam(required = false) String userId
    ) {
        Skill skill = skillRegistry.findByTrigger(trigger);
        if (skill == null) {
            return Map.of("success", false, "error", "未找到触发词对应的技能: " + trigger);
        }

        return execute(skill.getId(), params, userId, null);
    }

    /**
     * 注册新技能
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Skill skill) {
        if (skill.getName() == null || skill.getName().isEmpty()) {
            return Map.of("success", false, "error", "技能名称不能为空");
        }

        skillRegistry.registerSkill(skill);

        return Map.of(
            "success", true,
            "skillId", skill.getId(),
            "message", "技能注册成功"
        );
    }

    /**
     * 移除技能
     */
    @DeleteMapping("/{skillId}")
    public Map<String, Object> unregister(@PathVariable String skillId) {
        skillRegistry.unregisterSkill(skillId);

        return Map.of(
            "success", true,
            "message", "技能已移除"
        );
    }

    /**
     * 获取技能统计
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return skillRegistry.getStats();
    }

    /**
     * 获取推荐技能
     */
    @GetMapping("/recommend")
    public Map<String, Object> recommend(@RequestParam(required = false) String context) {
        List<Skill> skills = context != null
            ? skillRegistry.findRelated(context)
            : skillRegistry.getAllSkills();

        // 返回前 5 个
        return Map.of(
            "skills", skills.stream().limit(5).toList(),
            "message", context != null
                ? "根据 \"" + context + "\" 推荐"
                : "热门技能推荐"
        );
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        var stats = skillRegistry.getStats();
        return Map.of(
            "status", "ok",
            "totalSkills", stats.get("totalSkills"),
            "totalExecutors", stats.get("totalExecutors")
        );
    }
}