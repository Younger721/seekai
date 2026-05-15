package com.njhtr.seekai.controller;

import com.njhtr.seekai.dto.AgentRequest;
import com.njhtr.seekai.dto.AgentResponse;
import com.njhtr.seekai.dto.ApiResponse;
import com.njhtr.seekai.service.MultiAgentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * 多 Agent 控制器
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class MultiAgentController {
    
    private final MultiAgentService agentService;
    
    /**
     * 获取所有可用的 Agent
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<MultiAgentService.AgentInfo>>> listAgents() {
        List<MultiAgentService.AgentInfo> agents = agentService.getAvailableAgents();
        return ResponseEntity.ok(ApiResponse.success(agents));
    }
    
    /**
     * 使用指定 Agent 聊天（非流式）
     */
    @PostMapping("/{agentName}/chat")
    public ResponseEntity<ApiResponse<AgentResponse>> chatWithAgent(
            @PathVariable String agentName,
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        
        // 如果没有传入会话 ID，使用当前 session
        String convId = conversationId != null ? conversationId : session.getId();
        
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(convId)
            .build();
        
        AgentResponse response = agentService.processWithAgent(agentName, request);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 使用指定 Agent 聊天（流式，Server-Sent Events）
     */
    @GetMapping(value = "/{agentName}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithAgent(
            @PathVariable String agentName,
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        
        String convId = conversationId != null ? conversationId : session.getId();
        
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(convId)
            .build();
        
        return agentService.streamWithAgent(agentName, request);
    }
    
    /**
     * 快速聊天（自动选择通用助手）
     */
    @PostMapping("/quick")
    public ResponseEntity<ApiResponse<AgentResponse>> quickChat(
            @RequestParam String message,
            HttpSession session) {
        
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .conversationId(session.getId())
            .build();
        
        // 默认使用通用助手
        AgentResponse response = agentService.processWithAgent("GENERAL_HELPER", request);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
