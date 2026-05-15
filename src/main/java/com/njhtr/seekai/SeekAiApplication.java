package com.njhtr.seekai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;

// 排除 PgVector 的自动配置，因为我们在 SpringAIConfig 中手动配置了多数据源
@SpringBootApplication(exclude = {PgVectorStoreAutoConfiguration.class})
public class SeekAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeekAiApplication.class, args);
    }
}
