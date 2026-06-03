package org.example.ai_agent.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserItemDto {
    private Long userId;
    private String username;
    private String email;
    private Boolean isGuest;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
}
