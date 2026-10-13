package org.example.ai_agent.auth.service;

import org.example.ai_agent.auth.entity.User;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.search.service.SearchService;
import org.example.ai_agent.auth.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private ReactiveSetOperations<String, String> setOperations;

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

    @Test
    void guestUpgradeKeepsTheSameUserIdAndRevokesTheGuestToken() {
        User guest = new User();
        guest.setUserId(42L);
        guest.setUsername("Duog_old");
        guest.setPassword("old-hash");
        guest.setIsGuest(true);

        var registration = new org.example.ai_agent.auth.dto.UserRegistrationDto();
        registration.setUsername("alice");
        registration.setEmail("alice@example.com");
        registration.setPassword("Password123");

        Date expiration = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
        when(userRepository.findById(42L)).thenReturn(Mono.just(guest));
        when(userRepository.existsByUsernameAndUserIdNot("alice", 42L)).thenReturn(Mono.just(false));
        when(userRepository.existsByEmailAndUserIdNot("alice@example.com", 42L)).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("Password123")).thenReturn("new-hash");
        when(userRepository.save(guest)).thenReturn(Mono.just(guest));
        when(jwtUtil.generateToken("alice", 42L, false)).thenReturn("new-token");
        when(jwtUtil.getIdFromToken("new-token")).thenReturn("new-jti");
        when(jwtUtil.getExpirationDateFromToken("new-token")).thenReturn(expiration);
        when(jwtUtil.refreshSessionTtl("new-token")).thenReturn(Duration.ofHours(1));
        when(jwtUtil.getUserIdFromToken("old-token")).thenReturn(42L);
        when(jwtUtil.getIdFromToken("old-token")).thenReturn("old-jti");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.delete("token:jti:old-jti", "token:refresh-jti:old-jti")).thenReturn(Mono.just(1L));

        StepVerifier.create(service().upgradeGuestUser(registration, 42L, "Bearer old-token"))
                .expectNext("new-token")
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals(42L, guest.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("alice", guest.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("alice@example.com", guest.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals("new-hash", guest.getPassword());
        org.junit.jupiter.api.Assertions.assertFalse(guest.getIsGuest());
        verify(userRepository).save(guest);
        verify(redisTemplate).delete("token:jti:old-jti", "token:refresh-jti:old-jti");
        verify(setOperations).remove("token:user:42:jtis", "old-jti");
    }

    @Test
    void guestUpgradeRejectsAnAlreadyUsedUsernameWithoutChangingTheGuest() {
        User guest = new User();
        guest.setUserId(42L);
        guest.setIsGuest(true);
        var registration = new org.example.ai_agent.auth.dto.UserRegistrationDto();
        registration.setUsername("taken");
        registration.setEmail("alice@example.com");
        registration.setPassword("Password123");

        when(userRepository.findById(42L)).thenReturn(Mono.just(guest));
        when(userRepository.existsByUsernameAndUserIdNot("taken", 42L)).thenReturn(Mono.just(true));

        StepVerifier.create(service().upgradeGuestUser(registration, 42L, "Bearer old-token"))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("用户名已被占用"))
                .verify();

        verify(userRepository, never()).save(any(User.class));
        verify(jwtUtil, never()).generateToken(anyString(), any(), anyBoolean());
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
