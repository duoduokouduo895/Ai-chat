package org.example.ai_agent.service.impl.auth;

import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private SearchService searchService;
    @Mock
    private RegisteredUserCleanupService cleanupService;

    @Test
    void adminDeleteCleansResourcesBeforeDeletingRegisteredUser() {
        User user = new User();
        user.setUserId(42L);
        user.setIsGuest(false);
        when(userRepository.findById(42L)).thenReturn(Mono.just(user));
        when(cleanupService.cleanup(user)).thenReturn(Mono.empty());
        when(userRepository.deleteById(42L)).thenReturn(Mono.empty());

        StepVerifier.create(service().deleteRegisteredUserAsAdmin(42L))
                .verifyComplete();

        InOrder inOrder = inOrder(cleanupService, userRepository);
        inOrder.verify(cleanupService).cleanup(user);
        inOrder.verify(userRepository).deleteById(42L);
    }

    @Test
    void adminDeleteRejectsGuestUser() {
        User user = new User();
        user.setUserId(42L);
        user.setIsGuest(true);
        when(userRepository.findById(42L)).thenReturn(Mono.just(user));

        StepVerifier.create(service().deleteRegisteredUserAsAdmin(42L))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("游客账号"))
                .verify();

        verify(cleanupService, never()).cleanup(user);
        verify(userRepository, never()).deleteById(42L);
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(
                userRepository,
                passwordEncoder,
                jwtUtil,
                redisTemplate,
                searchService,
                cleanupService
        );
    }
}
