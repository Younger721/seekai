package com.njhtr.seekai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {
    private Long id;
    private String type;
    private String content;
    private java.time.LocalDateTime timestamp;
}
