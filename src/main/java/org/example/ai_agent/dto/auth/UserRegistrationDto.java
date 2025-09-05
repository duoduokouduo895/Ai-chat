package org.example.ai_agent.dto.auth;

import lombok.Data;

@Data
public class UserRegistrationDto {

    private String username;
    private String password;
    private String email;
    private String guestId; // 可选，用于游客升级
}
