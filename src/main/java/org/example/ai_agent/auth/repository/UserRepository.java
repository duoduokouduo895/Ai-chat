package org.example.ai_agent.auth.repository;

import org.example.ai_agent.auth.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByUsername(String username);

    Mono<Boolean> existsByUsername(String username);

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsernameAndUserIdNot(String username, Long userId);

    Mono<Boolean> existsByEmailAndUserIdNot(String email, Long userId);

    Mono<Long> countByIsGuest(Boolean isGuest);

    @Query("SELECT * FROM users " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR username LIKE CONCAT('%', :keyword, '%') OR email LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:guestOnly IS NULL OR is_guest = :guestOnly) " +
           "ORDER BY created_at DESC " +
           "LIMIT :limit OFFSET :offset")
    Flux<User> findAdminUsers(@Param("keyword") String keyword,
                              @Param("guestOnly") Boolean guestOnly,
                              @Param("limit") long limit,
                              @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM users " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR username LIKE CONCAT('%', :keyword, '%') OR email LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:guestOnly IS NULL OR is_guest = :guestOnly)")
    Mono<Long> countAdminUsers(@Param("keyword") String keyword,
                               @Param("guestOnly") Boolean guestOnly);

    @Query("UPDATE users SET last_active_at = :lastActiveAt " +
           "WHERE user_id = :userId AND is_guest = 1 " +
           "AND (last_active_at IS NULL OR last_active_at < :writeThreshold)")
    Mono<Integer> touchGuestLastActiveAt(@Param("userId") Long userId,
                                         @Param("lastActiveAt") java.time.LocalDateTime lastActiveAt,
                                         @Param("writeThreshold") java.time.LocalDateTime writeThreshold);
}
