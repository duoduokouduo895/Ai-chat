package org.example.ai_agent.service;

import org.example.ai_agent.dto.auth.AdminProfileDto;
import reactor.core.publisher.Mono;

public interface AdminAuthService {
    Mono<String> login(String username, String password);
    Mono<Void> logout(String authorizationHeader);
    Mono<AdminProfileDto> getCurrentAdmin(String username);
}
