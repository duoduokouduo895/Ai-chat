package org.example.ai_agent.dto.auth;

import lombok.Data;

@Data
public class UserRegistrationDto {

    private String username;
    private String password;
    private String email;

}
