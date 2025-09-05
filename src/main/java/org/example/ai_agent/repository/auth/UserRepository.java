package org.example.ai_agent.repository.auth;

import org.example.ai_agent.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByUsername(String username);
    Mono<Boolean> existsByUsername(String username);
    Mono<User> findByEmail(String email);
    Mono<Boolean> existsByEmail(String email);
    Mono<User> findByGuestId(String guestId);

    Mono<Boolean> existsByUsernameAndUserIdNot(String username, Long userId);

    Mono<Boolean> existsByEmailAndUserIdNot(String email, Long userId);
    
    /**
     * 查找创建时间超过指定时间且为游客的用户
     * @param cutoffTime 截止时间，早于此时间创建的游客用户将被查找
     * @return 符合条件的游客用户列表
     */
    @Query("SELECT * FROM users WHERE is_guest = true AND created_at < :cutoffTime")
    Flux<User> findGuestUsersCreatedBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * 删除创建时间超过指定时间且为游客的用户
     * @param cutoffTime 截止时间，早于此时间创建的游客用户将被删除
     * @return 删除的用户数量
     */
    @Query("DELETE FROM users WHERE is_guest = true AND created_at < :cutoffTime")
    Mono<Integer> deleteGuestUsersCreatedBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
