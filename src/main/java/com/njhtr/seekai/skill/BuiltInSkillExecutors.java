package com.njhtr.seekai.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内置技能执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltInSkillExecutors {

    private final ChatClient chatClient;

    /**
     * AI 对话技能
     */
    @Component
    public static class ChatSkillExecutor implements SkillExecutor {

        private final ChatClient chatClient;

        public ChatSkillExecutor(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context) {
            long start = System.currentTimeMillis();

            try {
                String message = (String) params.get("message");
                if (message == null || message.isEmpty()) {
                    return SkillResult.fail("缺少 message 参数");
                }

                String systemPrompt = (String) params.getOrDefault("systemPrompt",
                    "你是一个有用的 AI 助手。");

                // 构建 prompt
                String fullPrompt = systemPrompt + "\n\n用户: " + message;

                String response = chatClient.prompt(fullPrompt).call().content();

                return SkillResult.ok(response);

            } catch (Exception e) {
                log.error("❌ Chat 技能执行失败: {}", e.getMessage());
                return SkillResult.fail(e.getMessage());
            }
        }

        @Override
        public boolean validate(Skill skill, Map<String, Object> params) {
            return params.containsKey("message");
        }

        @Override
        public String getType() {
            return "chat";
        }
    }

    /**
     * 代码生成技能
     */
    @Component
    public static class CodeGeneratorSkillExecutor implements SkillExecutor {

        private final ChatClient chatClient;

        public CodeGeneratorSkillExecutor(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context) {
            try {
                String language = (String) params.getOrDefault("language", "python");
                String requirement = (String) params.get("requirement");

                if (requirement == null) {
                    return SkillResult.fail("缺少 requirement 参数");
                }

                String prompt = String.format("""
                    请用 %s 编写代码满足以下需求：
                    %s

                    要求：
                    1. 代码完整可运行
                    2. 添加适当的注释
                    3. 处理可能的异常
                    """, language, requirement);

                String code = chatClient.prompt(prompt).call().content();

                return SkillResult.ok(code);

            } catch (Exception e) {
                return SkillResult.fail(e.getMessage());
            }
        }

        @Override
        public boolean validate(Skill skill, Map<String, Object> params) {
            return params.containsKey("requirement");
        }

        @Override
        public String getType() {
            return "code_generator";
        }
    }

    /**
     * 数据分析技能
     */
    @Component
    public static class DataAnalysisSkillExecutor implements SkillExecutor {

        private final ChatClient chatClient;

        public DataAnalysisSkillExecutor(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context) {
            try {
                String data = (String) params.get("data");
                String question = (String) params.get("question");

                if (data == null || question == null) {
                    return SkillResult.fail("缺少 data 或 question 参数");
                }

                String prompt = String.format("""
                    请分析以下数据并回答问题：

                    数据：
                    %s

                    问题：%s

                    请提供详细的分析报告。
                    """, data, question);

                String analysis = chatClient.prompt(prompt).call().content();

                return SkillResult.ok(analysis);

            } catch (Exception e) {
                return SkillResult.fail(e.getMessage());
            }
        }

        @Override
        public boolean validate(Skill skill, Map<String, Object> params) {
            return params.containsKey("data") && params.containsKey("question");
        }

        @Override
        public String getType() {
            return "data_analysis";
        }
    }

    /**
     * 翻译技能
     */
    @Component
    public static class TranslatorSkillExecutor implements SkillExecutor {

        private final ChatClient chatClient;

        public TranslatorSkillExecutor(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context) {
            try {
                String text = (String) params.get("text");
                String targetLang = (String) params.getOrDefault("targetLang", "中文");
                String sourceLang = (String) params.getOrDefault("sourceLang", "auto");

                if (text == null) {
                    return SkillResult.fail("缺少 text 参数");
                }

                String prompt = String.format("""
                    请将以下%s语言翻译成%s：

                    %s

                    只返回翻译结果，不要有其他内容。
                    """,
                    sourceLang.equals("auto") ? "" : sourceLang + "的",
                    targetLang,
                    text
                );

                String translation = chatClient.prompt(prompt).call().content();

                return SkillResult.ok(translation);

            } catch (Exception e) {
                return SkillResult.fail(e.getMessage());
            }
        }

        @Override
        public boolean validate(Skill skill, Map<String, Object> params) {
            return params.containsKey("text");
        }

        @Override
        public String getType() {
            return "translator";
        }
    }

    /**
     * 摘要技能
     */
    @Component
    public static class SummarizerSkillExecutor implements SkillExecutor {

        private final ChatClient chatClient;

        public SummarizerSkillExecutor(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        @Override
        public SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context) {
            try {
                String text = (String) params.get("text");
                int maxLength = (int) params.getOrDefault("maxLength", 200);

                if (text == null) {
                    return SkillResult.fail("缺少 text 参数");
                }

                String prompt = String.format("""
                    请将以下内容压缩成不超过 %d 字的摘要，保留关键信息：

                    %s

                    只返回摘要内容。
                    """, maxLength, text);

                String summary = chatClient.prompt(prompt).call().content();

                return SkillResult.ok(summary);

            } catch (Exception e) {
                return SkillResult.fail(e.getMessage());
            }
        }

        @Override
        public boolean validate(Skill skill, Map<String, Object> params) {
            return params.containsKey("text");
        }

        @Override
        public String getType() {
            return "summarizer";
        }
    }
}