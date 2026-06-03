package org.example.ai_agent.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDto {
    private Long adminId;
    private String username;
    private String displayName;
    private String email;
}
