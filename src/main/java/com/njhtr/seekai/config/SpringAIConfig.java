package com.njhtr.seekai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Configuration
public class SpringAIConfig {

    /**
     * 主数据源配置 (MySQL)，用于 MyBatis 存取聊天记录等
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties().initializeDataSourceBuilder().build();
    }

    /**
     * 为 PgVector 专门配置一个数据源 (PostgreSQL)
     */
    @Bean
    @ConfigurationProperties("spring.vector-datasource")
    public DataSourceProperties vectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource vectorDataSource() {
        return vectorDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate vectorJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("vectorDataSource") DataSource vectorDataSource) {
        return new JdbcTemplate(vectorDataSource);
    }
    
    /**
     * 手动装配 PgVectorStore，确保它使用的是 PostgreSQL 的 JdbcTemplate
     */
    @Bean
    public VectorStore vectorStore(
            @org.springframework.beans.factory.annotation.Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate,
            EmbeddingModel embeddingModel) {
            
        return PgVectorStore.builder(vectorJdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .build();
    }

    private final ChatModel chatModel;

    public SpringAIConfig(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * ChatClient Builder（用于构建多 Agent）
     */
    @Bean
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder(chatModel);
    }

    /**
     * 默认 ChatClient（保持向后兼容）
     */
    @Bean
    public ChatClient chatClient() {
        return ChatClient.create(chatModel);
    }

    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(30)
                .build();
    }
}
