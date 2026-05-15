package com.njhtr.seekai.controller;

import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.ApiResponse;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.repository.ConversationRepository;
import com.njhtr.seekai.service.AgentRoutingService;
import com.njhtr.seekai.service.IntelligentRoutingService;
import com.njhtr.seekai.service.MultiAgentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能路由控制器 - 自动选择合适的 Agent
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/smart")
@RequiredArgsConstructor
public class SmartAgentController {

    private final AgentRoutingService routingService;
    private final ConversationRepository conversationRepository;
    
    /**
     * 智能聊天（自动路由）- 非流式
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentResponse>> smartChat(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        
        // 优先使用前端传来的 conversationId，如果没有则使用或创建 Session 中的
        String finalConvId = getConversationId(conversationId, session);
        log.info("📝 开始处理消息，conversationId={}, message={}", finalConvId, message);
        
        try {
            // 1. 保存用户消息
            log.info("💾 准备保存用户消息...");
            int saved1 = conversationRepository.saveMessage(finalConvId, message, "user", null);
            log.info("💾 用户消息保存结果：{} 行", saved1);
            
            // 获取历史消息
            List<MessageDTO> historyMessages = conversationRepository.findMessagesByConversationId(finalConvId);
            
            // 2. 处理请求
            AgentResponse response = routingService.processRequest(message, finalConvId, historyMessages);
            
            // 3. 保存 AI 回复
            log.info("💾 准备保存 AI 回复...");
            int saved2 = conversationRepository.saveMessage(
                finalConvId, 
                response.getContent(), 
                "assistant", 
                response.getAgentName()
            );
            log.info("💾 AI 回复保存结果：{} 行", saved2);
            
            log.info("✅ 对话保存完成，conversationId={}", finalConvId);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("❌ 保存对话失败：{}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取会话 ID：优先使用前端传来的，否则使用 Session 中存储的，最后降级为 Session ID
     */
    private String getConversationId(String conversationId, HttpSession session) {
        // 1. 如果前端传了 conversationId，优先使用
        if (conversationId != null && !conversationId.isEmpty()) {
            log.info("✅ 使用前端传来的 conversationId: {}", conversationId);
            return conversationId;
        }
        
        // 2. 尝试从 Session 属性中获取
        String sessionConvId = (String) session.getAttribute("conversationId");
        if (sessionConvId != null) {
            log.info("🔄 使用 Session 中存储的 conversationId: {}", sessionConvId);
            return sessionConvId;
        }
        
        // 3. 降级使用 Session ID（保证向后兼容）
        String sessionId = session.getId();
        log.info("⚠️ 降级使用 Session ID: {}", sessionId);
        return sessionId;
    }
    
    /**
     * 智能聊天（自动路由）- 流式 SSE
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> smartChatStream(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        
        // 优先使用前端传来的 conversationId，如果没有则使用或创建 Session 中的
        String finalConvId = getConversationId(conversationId, session);
        log.info("📝 开始流式处理，conversationId={}, message={}", finalConvId, message);
        
        // 1. 先保存用户消息
        conversationRepository.saveMessage(finalConvId, message, "user", null);
        log.info("💾 已保存用户消息到数据库");
        
        // 获取历史消息
        List<MessageDTO> historyMessages = conversationRepository.findMessagesByConversationId(finalConvId);
        
        // 2. 用于累积 AI 的完整回复
        StringBuilder fullResponse = new StringBuilder();
        
        // 3. 流式输出并保存
        return routingService.processStream(message, finalConvId, historyMessages)
            .doOnNext(chunk -> {
                fullResponse.append(chunk);
                log.debug("📤 发送流式数据：{}", chunk);
            })
            .doOnComplete(() -> {
                // 4. 完成后保存 AI 回复
                String responseText = fullResponse.toString();
                conversationRepository.saveMessage(
                    finalConvId,
                    responseText,
                    "assistant",
                    null // 暂时流式未获取 agentName，后续可优化
                );
                log.info("💾 已保存 AI 回复到数据库，长度：{}", responseText.length());
                log.info("✅ 流式传输完成");
            })
            .doOnError(error -> log.error("❌ 流式传输错误：{}", error.getMessage()));
    }
    
    /**
     * 获取可用 Agent 列表
     */
    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<MultiAgentService.AgentInfo>>> listAgents() {
        List<MultiAgentService.AgentInfo> agents = routingService.getAvailableAgents();
        return ResponseEntity.ok(ApiResponse.success(agents));
    }
    
    /**
     * 测试：查看当前会话的数据库记录
     */
    @GetMapping("/test/db")
    public ResponseEntity<ApiResponse<Object>> testDatabase(HttpSession session) {
        String conversationId = session.getId();
        
        // 查询该会话的所有消息
        var messages = conversationRepository.findMessagesByConversationId(conversationId);
        
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of(
            "conversationId", conversationId,
            "messageCount", messages.size(),
            "messages", messages
        )));
    }
}
