package com.njhtr.seekai.service;

import com.njhtr.seekai.dto.ConversationDTO;
import com.njhtr.seekai.dto.ConversationDetailDTO;
import com.njhtr.seekai.dto.ConversationSummaryDTO;
import com.njhtr.seekai.dto.MessageDTO;
import com.njhtr.seekai.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ChatMemory chatMemory;

    public List<ConversationDTO> getAllConversations() {
        List<ConversationSummaryDTO> conversations = conversationRepository.findAllConversations();
        
        return conversations.stream()
            .map(summary -> {
                String preview = conversationRepository.getPreviewMessage(summary.getConversationId());
                
                return ConversationDTO.builder()
                    .conversationId(summary.getConversationId())
                    .createdAt(summary.getCreatedAt())
                    .lastMessageAt(summary.getLastMessageAt())
                    .messageCount(summary.getMessageCount())
                    .preview(preview)
                    .build();
            })
            .toList();
    }

    public ConversationDetailDTO getConversationById(String conversationId) {
        if (!conversationRepository.existsByConversationId(conversationId)) {
            throw new RuntimeException("会话不存在");
        }

        java.time.LocalDateTime createdAt = conversationRepository.getConversationCreatedAt(conversationId);
        List<MessageDTO> messages = conversationRepository.findMessagesByConversationId(conversationId);
        Integer messageCount = messages.size();

        return ConversationDetailDTO.builder()
            .conversationId(conversationId)
            .createdAt(createdAt)
            .messageCount(messageCount)
            .messages(messages)
            .build();
    }

    public String createConversation() {
        String conversationId = java.util.UUID.randomUUID().toString();
        // 初始化会话：在数据库中创建一条记录
        // 使用空消息作为初始记录
        conversationRepository.saveMessage(conversationId, "", "system", null);
        return conversationId;
    }

    @Transactional
    public void deleteConversation(String conversationId) {
        if (!conversationRepository.existsByConversationId(conversationId)) {
            throw new RuntimeException("会话不存在");
        }
        
        conversationRepository.deleteByConversationId(conversationId);
        chatMemory.clear(conversationId);
    }

    @Transactional
    public void clearConversationMessages(String conversationId) {
        if (!conversationRepository.existsByConversationId(conversationId)) {
            throw new RuntimeException("会话不存在");
        }
        
        // 删除所有消息但保留会话记录
        conversationRepository.deleteMessagesByConversationId(conversationId);
        chatMemory.clear(conversationId);
    }
}
