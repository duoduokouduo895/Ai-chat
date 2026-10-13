package org.example.ai_agent.auth.service;

import org.example.ai_agent.auth.dto.AdminProfileDto;
import reactor.core.publisher.Mono;

public interface AdminAuthService {
    Mono<String> login(String username, String password);
    Mono<Void> logout(String authorizationHeader);
    Mono<AdminProfileDto> getCurrentAdmin(String username);
}
