package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationDTO {
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private Integer messageCount;
    private String preview;
}
