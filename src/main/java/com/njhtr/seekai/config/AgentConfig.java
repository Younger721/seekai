package com.njhtr.seekai.config;

import com.njhtr.seekai.agent.AgentRegistry;
import com.njhtr.seekai.agent.context.ConversationContextManager;
import com.njhtr.seekai.agent.impl.CodeExpertAgent;
import com.njhtr.seekai.agent.impl.DocumentAgent;
import com.njhtr.seekai.agent.impl.GeneralAgent;
import com.njhtr.seekai.agent.impl.SearchAgent;
import com.njhtr.seekai.agent.impl.AutoCoderAgent;
import com.njhtr.seekai.agent.impl.DataAgent;
import com.njhtr.seekai.service.BingSearchService;
import com.njhtr.seekai.tool.AstTools;
import com.njhtr.seekai.tool.CodeAnalysisTools;
import com.njhtr.seekai.tool.CodebaseRagTools;
import com.njhtr.seekai.tool.CustomerServiceTools;
import com.njhtr.seekai.tool.DocumentSearchTools;
import com.njhtr.seekai.tool.ExceptionHandlingTools;
import com.njhtr.seekai.tool.WeatherTools;
import com.njhtr.seekai.tool.SystemTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 */
@Configuration
public class AgentConfig {
    
    private final ChatClient.Builder chatClientBuilder;
    
    public AgentConfig(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }
    
    /**
     * 注册通用助手 Agent（带天气工具和系统控制工具）
     */
    @Bean
    public GeneralAgent generalAgent(WeatherTools weatherTools, SystemTools systemTools) {
        return new GeneralAgent(chatClientBuilder, weatherTools, systemTools);
    }
    
    /**
     * 注册代码专家 Agent（带工具）
     */
    @Bean
    public CodeExpertAgent codeExpertAgent(CodeAnalysisTools codeAnalysisTools) {
        return new CodeExpertAgent(chatClientBuilder, codeAnalysisTools);
    }
    
    /**
     * 注册文档助手 Agent
     */
    @Bean
    public DocumentAgent documentAgent() {
        return new DocumentAgent(chatClientBuilder);
    }
    
    /**
     * 注册全自动编程 Agent
     */
    @Bean
    public AutoCoderAgent autoCoderAgent(SystemTools systemTools, AstTools astTools, CodeAnalysisTools codeAnalysisTools, CodebaseRagTools codebaseRagTools) {
        return new AutoCoderAgent(chatClientBuilder, systemTools, astTools, codeAnalysisTools, codebaseRagTools);
    }
    
    /**
     * 注册联网搜索 Agent
     */
    @Bean
    public SearchAgent searchAgent(BingSearchService searchService, SystemTools systemTools) {
        return new SearchAgent(chatClientBuilder.build(), searchService, systemTools);
    }
    
    /**
     * 代码分析工具
     */
    @Bean
    public CodeAnalysisTools codeAnalysisTools() {
        return new CodeAnalysisTools();
    }
    
    /**
     * 天气查询工具（函数式）
     */
    @Bean
    public WeatherTools weatherTools() {
        return new WeatherTools();
    }
    
    /**
     * 文档搜索工具
     */
    @Bean
    public DocumentSearchTools documentSearchTools() {
        return new DocumentSearchTools();
    }
    
    /**
     * 客户服务工具（演示工具上下文和结果转换）
     */
    @Bean
    public CustomerServiceTools customerServiceTools() {
        return new CustomerServiceTools();
    }
    
    /**
     * 初始化 Agent 注册（应用启动时自动执行）
     */
    @Bean
    public AgentRegistry agentRegistry(GeneralAgent generalAgent, 
                                       CodeExpertAgent codeExpertAgent, 
                                       DocumentAgent documentAgent,
                                       SearchAgent searchAgent,
                                       AutoCoderAgent autoCoderAgent,
                                       DataAgent dataAgent) {
        AgentRegistry registry = new AgentRegistry();
        registry.register(generalAgent);
        registry.register(codeExpertAgent);
        registry.register(documentAgent);
        registry.register(searchAgent);
        registry.register(dataAgent);
        
        // 解决依赖注入：给 AutoCoderAgent 注入 registry，用于内部调用 Review
        autoCoderAgent.setAgentRegistry(registry);
        registry.register(autoCoderAgent);
        return registry;
    }
    
    /**
     * 异常处理工具（演示错误恢复）
     */
    @Bean
    public ExceptionHandlingTools exceptionHandlingTools() {
        return new ExceptionHandlingTools();
    }
    
    /**
     * 系统控制工具 (演示执行本地命令)
     */
    @Bean
    public SystemTools systemTools() {
        return new SystemTools();
    }
}
