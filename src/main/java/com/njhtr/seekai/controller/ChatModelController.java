package com.njhtr.seekai.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api")
public class ChatModelController {
    @Resource
    private ChatClient chatClient;
    private ChatMemory chatMemory;

    @GetMapping("/chatAdvisor")
    public String chatAdvisor(String question, String user){
        PromptChatMemoryAdvisor chatMemoryAdvisor = PromptChatMemoryAdvisor
                                                        .builder(chatMemory)
                                                        .conversationId(user)
                                                        .build();
        return chatClient
                .prompt()
                .user(question)
                .advisors(chatMemoryAdvisor)
                .call()
                .content();
    }
}
