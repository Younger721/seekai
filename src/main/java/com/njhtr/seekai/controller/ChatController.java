package com.njhtr.seekai.controller;

import com.njhtr.seekai.context.SmartChatMemory;
import com.njhtr.seekai.memory.MemoryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RestController()
@RequiredArgsConstructor
public class ChatController {
    private final org.springframework.ai.chat.client.ChatClient chatClient;
    private final SmartChatMemory chatMemory;
    private final MemoryService memoryService;

    // ========== 非流式 ==========
    @GetMapping("/api/chat")
    public String chat(
            @RequestParam(value = "message") String message,
            @RequestParam(value = "userId", required = false) String userId,
            HttpSession session
    ) {
        String conversationId = session.getId();
        String effectiveUserId = userId != null ? userId : conversationId;

        // 首次对话：初始化系统提示
        if (chatMemory.get(conversationId).isEmpty()) {
            // 获取用户画像作为系统提示
            String userContext = memoryService.buildUserContext(effectiveUserId);
            String systemPrompt = String.format("""
                You are a helpful AI assistant.
                %s

                记住用户的偏好，尽量用他们喜欢的方式回复。
                """, userContext);

            chatMemory.add(conversationId, new SystemMessage(systemPrompt));
        }

        // 1. 保存用户消息
        chatMemory.add(conversationId, new UserMessage(message));

        // 2. 获取优化后的上下文
        List<Message> history = chatMemory.get(conversationId, 15);

        // 3. 构建 prompt
        Prompt prompt = new Prompt(new ArrayList<>(history));

        // 4. 调用 AI
        String response = chatClient.prompt(prompt).call().content();

        // 5. 保存 AI 回复
        chatMemory.add(conversationId, new AssistantMessage(response));

        // 6. 学习用户习惯和偏好
        memoryService.analyzeAndLearn(effectiveUserId, message, "GENERAL", response);

        return response;
    }

    // ========== 流式 ==========
    @GetMapping(value = "/api/chat/stream", produces = "text/html;charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam(value = "message") String message,
            @RequestParam(value = "userId", required = false) String userId,
            HttpSession session
    ) {
        String conversationId = session.getId();
        String effectiveUserId = userId != null ? userId : conversationId;

        // 首次对话：初始化系统提示
        if (chatMemory.get(conversationId).isEmpty()) {
            String userContext = memoryService.buildUserContext(effectiveUserId);
            String systemPrompt = String.format("""
                You are a helpful AI assistant.
                %s
                """, userContext);

            chatMemory.add(conversationId, new SystemMessage(systemPrompt));
        }

        // 1. 保存用户消息
        chatMemory.add(conversationId, new UserMessage(message));

        // 2. 获取历史
        List<Message> promptMessages = new ArrayList<>(chatMemory.get(conversationId, 15));
        Prompt prompt = new Prompt(promptMessages);

        StringBuilder fullResponse = new StringBuilder();

        return chatClient
                .prompt(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // 3. 保存完整回复
                    chatMemory.add(conversationId, new AssistantMessage(fullResponse.toString()));
                    // 4. 学习用户偏好
                    memoryService.analyzeAndLearn(effectiveUserId, message, "GENERAL", fullResponse.toString());
                });
    }
}