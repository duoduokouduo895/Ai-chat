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


    Mono<Boolean> existsByUsernameAndUserIdNot(String username, Long userId);

    Mono<Boolean> existsByEmailAndUserIdNot(String email, Long userId);
    

}
