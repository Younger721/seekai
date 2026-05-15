package com.njhtr.seekai.skill;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * 技能系统配置
 */
@Configuration
@RequiredArgsConstructor
public class SkillConfig {

    private final SkillRegistry skillRegistry;

    // 注入所有内置执行器
    private final BuiltInSkillExecutors.ChatSkillExecutor chatSkillExecutor;
    private final BuiltInSkillExecutors.CodeGeneratorSkillExecutor codeGeneratorSkillExecutor;
    private final BuiltInSkillExecutors.DataAnalysisSkillExecutor dataAnalysisSkillExecutor;
    private final BuiltInSkillExecutors.TranslatorSkillExecutor translatorSkillExecutor;
    private final BuiltInSkillExecutors.SummarizerSkillExecutor summarizerSkillExecutor;

    @PostConstruct
    public void registerExecutors() {
        // 注册执行器
        skillRegistry.registerExecutor("chat", chatSkillExecutor);
        skillRegistry.registerExecutor("code_generator", codeGeneratorSkillExecutor);
        skillRegistry.registerExecutor("data_analysis", dataAnalysisSkillExecutor);
        skillRegistry.registerExecutor("translator", translatorSkillExecutor);
        skillRegistry.registerExecutor("summarizer", summarizerSkillExecutor);
    }
}