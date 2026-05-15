package com.njhtr.seekai.agent.impl;

import com.njhtr.seekai.agent.Agent;
import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.skill.Skill;
import com.njhtr.seekai.skill.SkillExecutor;
import com.njhtr.seekai.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能 Agent - 自动选择和执行技能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillAgent implements Agent {

    private static final String AGENT_NAME = "SKILL_AGENT";

    private final SkillRegistry skillRegistry;

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "技能执行助手 - 自动选择合适的技能完成任务";
    }

    @Override
    public AgentResponse chat(AgentRequest request) {
        String message = request.getMessage();
        String userId = request.getUserId();

        log.info("🎯 SkillAgent 处理任务: {}", message);

        try {
            // 1. 解析任务意图
            Skill selectedSkill = selectSkill(message);

            if (selectedSkill == null) {
                return AgentResponse.builder()
                    .success(false)
                    .message("🤔 抱歉，我无法理解你的请求。\n\n" +
                        "你可以尝试：\n" +
                        "- 翻译一段文字\n" +
                        "- 写一段代码\n" +
                        "- 分析这些数据\n" +
                        "- 总结这段内容\n")
                    .agentName(AGENT_NAME)
                    .build();
            }

            // 2. 提取参数
            Map<String, Object> params = extractParams(selectedSkill, message);

            // 3. 执行技能
            SkillExecutor.ExecutionContext context = new SkillExecutor.ExecutionContext();
            context.setUserId(userId != null ? userId : "anonymous");
            context.setConversationId(request.getConversationId());

            SkillExecutor.SkillResult result = skillRegistry.executeSkill(
                selectedSkill.getId(), params, context
            );

            // 4. 返回结果
            String response = result.isSuccess()
                ? formatSuccessResponse(selectedSkill, result.getOutput())
                : formatErrorResponse(result.getError());

            return AgentResponse.builder()
                .success(result.isSuccess())
                .message(response)
                .agentName(AGENT_NAME)
                .metadata(Map.of(
                    "skillUsed", selectedSkill.getName(),
                    "executionTime", result.getExecutionTimeMs()
                ))
                .build();

        } catch (Exception e) {
            log.error("❌ SkillAgent 执行失败: {}", e.getMessage());
            return AgentResponse.builder()
                .success(false)
                .message("执行出错: " + e.getMessage())
                .agentName(AGENT_NAME)
                .build();
        }
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.just(chat(request).getMessage());
    }

    /**
     * 选择合适的技能
     */
    private Skill selectSkill(String message) {
        String lower = message.toLowerCase();

        // 1. 先尝试触发词匹配
        for (Skill skill : skillRegistry.getAllSkills()) {
            if (skill.getTrigger() != null &&
                lower.contains(skill.getTrigger().toLowerCase())) {
                log.info("🎯 触发词匹配: {}", skill.getName());
                return skill;
            }
        }

        // 2. 语义匹配
        List<Skill> related = skillRegistry.findRelated(message);
        if (!related.isEmpty()) {
            Skill top = related.get(0);
            // 只有匹配度够高才使用
            if (top.getName().toLowerCase().contains(extractIntent(message)) ||
                top.getDescription().toLowerCase().contains(extractIntent(message))) {
                log.info("🎯 语义匹配: {}", top.getName());
                return top;
            }
        }

        // 3. 关键词匹配
        if (containsAny(lower, "写", "代码", "function", "class", "def ", "method")) {
            return skillRegistry.getSkill("code_generator");
        }
        if (containsAny(lower, "翻译", "translate", "英文", "english", "中文")) {
            return skillRegistry.getSkill("translator");
        }
        if (containsAny(lower, "分析", "数据", "analyze", "统计", "chart")) {
            return skillRegistry.getSkill("data_analysis");
        }
        if (containsAny(lower, "总结", "摘要", "summarize", "概括", "提炼")) {
            return skillRegistry.getSkill("summarizer");
        }
        if (containsAny(lower, "什么", "为什么", "how", "why", "请问")) {
            return skillRegistry.getSkill("ai_chat");
        }

        return null;
    }

    /**
     * 提取参数
     */
    private Map<String, Object> extractParams(Skill skill, String message) {
        Map<String, Object> params = new HashMap<>();

        String skillId = skill.getId();
        String content = message;

        // 移除触发词
        if (skill.getTrigger() != null) {
            content = content.replaceAll("(?i)" + skill.getTrigger(), "").trim();
        }

        switch (skillId) {
            case "ai_chat":
            case "code_generator":
            case "data_analysis":
            case "translator":
            case "summarizer":
                // 提取消息内容作为主要参数
                params.put("message", content);
                params.put("text", content);
                params.put("requirement", content);
                params.put("data", content);
                break;
        }

        // 提取语言参数
        if (message.toLowerCase().contains("英文") || message.toLowerCase().contains("english")) {
            params.put("targetLang", "English");
        } else if (message.toLowerCase().contains("日文") || message.toLowerCase().contains("日语")) {
            params.put("targetLang", "日本語");
        }

        return params;
    }

    /**
     * 提取意图关键词
     */
    private String extractIntent(String message) {
        // 简单提取前几个词
        String[] words = message.split("[\\s,，。]+");
        return words.length > 0 ? words[0].toLowerCase() : "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String formatSuccessResponse(Skill skill, String output) {
        return String.format("""
            ✅ 完成: %s

            %s

            ---
            使用技能: %s
            """, skill.getName(), output, skill.getName());
    }

    private String formatErrorResponse(String error) {
        return "❌ 执行失败: " + error;
    }
}