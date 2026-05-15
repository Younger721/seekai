package com.njhtr.seekai.controller;

import com.njhtr.seekai.dto.ApiResponse;
import com.njhtr.seekai.dto.ConversationDTO;
import com.njhtr.seekai.dto.ConversationDetailDTO;
import com.njhtr.seekai.service.ConversationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    /**
     * 获取所有会话列表
     */
    @GetMapping
    public ApiResponse<List<ConversationDTO>> getAllConversations() {
        List<ConversationDTO> conversations = conversationService.getAllConversations();
        return ApiResponse.success(conversations);
    }

    /**
     * 获取指定会话的详细信息（包含所有消息）
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailDTO> getConversationById(@PathVariable String conversationId) {
        ConversationDetailDTO detail = conversationService.getConversationById(conversationId);
        return ApiResponse.success(detail);
    }

    /**
     * 创建新会话
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createConversation(HttpSession session) {
        String conversationId = conversationService.createConversation();
        
        // 将会话 ID 存入 HttpSession，后续聊天会自动使用
        session.setAttribute("conversationId", conversationId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("createdAt", java.time.LocalDateTime.now());
        
        return ApiResponse.success("会话创建成功", result);
    }

    /**
     * 删除整个会话及其所有消息
     */
    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 清空会话的所有消息（保留会话）
     */
    @DeleteMapping("/{conversationId}/messages")
    public ApiResponse<Void> clearConversationMessages(@PathVariable String conversationId) {
        conversationService.clearConversationMessages(conversationId);
        return ApiResponse.success("消息已清空", null);
    }
}
