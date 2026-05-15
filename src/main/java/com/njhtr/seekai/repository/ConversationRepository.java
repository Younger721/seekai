package com.njhtr.seekai.repository;

import com.njhtr.seekai.dto.ConversationSummaryDTO;
import com.njhtr.seekai.dto.MessageDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationRepository {
    
    List<ConversationSummaryDTO> findAllConversations();
    
    List<MessageDTO> findMessagesByConversationId(@Param("conversationId") String conversationId);
    
    LocalDateTime getConversationCreatedAt(@Param("conversationId") String conversationId);
    
    int deleteByConversationId(@Param("conversationId") String conversationId);
    
    boolean existsByConversationId(@Param("conversationId") String conversationId);
    
    int getMessageCount(@Param("conversationId") String conversationId);
    
    String getPreviewMessage(@Param("conversationId") String conversationId);
    
    // 保存消息到数据库
    int saveMessage(@Param("conversationId") String conversationId,
                    @Param("content") String content,
                    @Param("role") String role,
                    @Param("agentName") String agentName);
    
    // 删除会话中的所有消息（保留会话）
    void deleteMessagesByConversationId(@Param("conversationId") String conversationId);
}
