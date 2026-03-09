package org.example.ai_agent.repository.auth;

import org.example.ai_agent.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByUsername(String username);

    Mono<Boolean> existsByUsername(String username);

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsernameAndUserIdNot(String username, Long userId);

    Mono<Boolean> existsByEmailAndUserIdNot(String email, Long userId);

    @Query("UPDATE users SET last_active_at = :lastActiveAt " +
           "WHERE user_id = :userId AND is_guest = 1 " +
           "AND (last_active_at IS NULL OR last_active_at < :writeThreshold)")
    Mono<Integer> touchGuestLastActiveAt(@Param("userId") Long userId,
                                         @Param("lastActiveAt") java.time.LocalDateTime lastActiveAt,
                                         @Param("writeThreshold") java.time.LocalDateTime writeThreshold);
}
