package org.example.ai_agent.service.impl.identity;

import org.example.ai_agent.service.identity.ActorIdentityLookup;
import org.example.ai_agent.repository.auth.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RepositoryActorIdentityLookup implements ActorIdentityLookup {

    private final UserRepository userRepository;

    public RepositoryActorIdentityLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<ActorIdentity> findByUsername(String username) {
        Mono<org.example.ai_agent.entity.User> user = userRepository.findByUsername(username);
        return (user == null ? Mono.<org.example.ai_agent.entity.User>empty() : user)
                .map(userEntity -> new ActorIdentity(userEntity.getUserId(),
                        Boolean.TRUE.equals(userEntity.getIsGuest())));
    }

    @Override
    public Mono<Long> requireExistingUserId(Long userId, String errorMessage) {
        if (userId == null || userId <= 0) {
            return Mono.error(new IllegalArgumentException(errorMessage));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(errorMessage)))
                .map(user -> user.getUserId());
    }
}
