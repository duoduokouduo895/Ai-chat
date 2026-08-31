package org.example.ai_agent.repository.auth;

import org.example.ai_agent.entity.AdminUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface AdminUserRepository extends ReactiveCrudRepository<AdminUser, Long> {

    Mono<AdminUser> findByUsername(String username);

    @Query("UPDATE admin_users SET last_login_at = :lastLoginAt WHERE admin_id = :adminId")
    Mono<Integer> updateLastLoginAt(Long adminId, LocalDateTime lastLoginAt);
}
