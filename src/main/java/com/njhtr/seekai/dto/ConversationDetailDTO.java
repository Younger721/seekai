package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationDetailDTO {
    private String conversationId;
    private LocalDateTime createdAt;
    private Integer messageCount;
    private List<MessageDTO> messages;
}
