package org.example.ai_agent.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSearchHistoryItemDto {
    private Long id;
    private Long userId;
    private String username;
    private String query;
    private String type;
    private LocalDateTime timestamp;
}
